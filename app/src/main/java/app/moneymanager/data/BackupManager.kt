package app.moneymanager.data

import androidx.room.withTransaction
import app.moneymanager.data.db.AppDatabase
import app.moneymanager.data.db.CategoryEntity
import app.moneymanager.data.db.RuleEntity
import app.moneymanager.data.db.TransactionEntity
import app.moneymanager.data.db.UnparsedEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Full backup as JSON (restore on a new phone) and a CSV export for spreadsheets.
 * The JSON contains every table, so an import replaces everything on this phone.
 */
class BackupManager(private val db: AppDatabase) {
    data class ImportResult(val transactions: Int, val rules: Int)

    suspend fun exportJson(out: OutputStream) = withContext(Dispatchers.IO) {
        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", 1)
            .put("exportedAt", System.currentTimeMillis())
            .put("transactions", JSONArray(db.transactions().all().map(::txToJson)))
            .put("categories", JSONArray(db.categories().all().map(::catToJson)))
            .put("rules", JSONArray(db.rules().all().map(::ruleToJson)))
            .put("unparsed", JSONArray(db.unparsed().all().map(::unparsedToJson)))
        out.bufferedWriter(Charsets.UTF_8).use { it.write(root.toString(1)) }
    }

    suspend fun importJson(input: InputStream): ImportResult = withContext(Dispatchers.IO) {
        val root = JSONObject(input.bufferedReader(Charsets.UTF_8).use { it.readText() })
        require(root.optString("format") == FORMAT) { "This file isn't a Money Manager backup." }
        val txns = root.getJSONArray("transactions").objects().map(::txFromJson)
        val categories = root.optJSONArray("categories")?.objects()?.map(::catFromJson).orEmpty()
        val rules = root.optJSONArray("rules")?.objects()?.map(::ruleFromJson).orEmpty()
        val unparsed = root.optJSONArray("unparsed")?.objects()?.map(::unparsedFromJson).orEmpty()
        db.withTransaction {
            db.transactions().clear()
            db.rules().clear()
            db.unparsed().clear()
            if (categories.isNotEmpty()) {
                db.categories().clear()
                db.categories().insertAll(categories)
            }
            db.transactions().insertAll(txns)
            db.rules().insertAll(rules)
            db.unparsed().insertAll(unparsed)
        }
        ImportResult(txns.size, rules.size)
    }

    suspend fun exportCsv(out: OutputStream, zone: ZoneId = ZoneId.systemDefault()) = withContext(Dispatchers.IO) {
        val names = db.categories().all().associate { it.key to it.name }
        val date = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val time = DateTimeFormatter.ofPattern("HH:mm")
        out.bufferedWriter(Charsets.UTF_8).use { w ->
            w.write("﻿") // BOM so Excel opens ₹ and names correctly
            w.write("Date,Time,Type,Amount,Category,Merchant,Account,Bank,UPI ID,Reference,Note,Excluded,Source\n")
            for (t in db.transactions().all()) {
                if (t.deleted) continue
                val at = Instant.ofEpochMilli(t.timestamp).atZone(zone)
                val row = listOf(
                    date.format(at), time.format(at), t.type.lowercase(),
                    "%d.%02d".format(t.amountPaise / 100, t.amountPaise % 100),
                    names[t.categoryKey] ?: t.categoryKey, t.merchant, t.account, t.bank, t.vpa, t.reference, t.note,
                    if (t.excluded) "yes" else "no", t.source.lowercase(),
                )
                w.write(row.joinToString(",") { csv(it) })
                w.write("\n")
            }
        }
    }

    private fun csv(v: String?): String {
        val s = v ?: ""
        return if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun JSONObject.str(k: String): String? = if (isNull(k) || !has(k)) null else getString(k)
    private fun JSONObject.lng(k: String): Long? = if (isNull(k) || !has(k)) null else getLong(k)

    private fun txToJson(t: TransactionEntity) = JSONObject()
        .put("id", t.id).put("type", t.type).put("amountPaise", t.amountPaise).put("timestamp", t.timestamp)
        .put("account", t.account).put("accountKind", t.accountKind).put("bank", t.bank).put("merchant", t.merchant)
        .put("merchantKey", t.merchantKey).put("vpa", t.vpa).put("reference", t.reference).put("balancePaise", t.balancePaise)
        .put("channel", t.channel).put("hints", t.hints).put("categoryKey", t.categoryKey).put("categoryLocked", t.categoryLocked)
        .put("note", t.note).put("excluded", t.excluded).put("deleted", t.deleted).put("source", t.source)
        .put("sender", t.sender).put("rawText", t.rawText).put("smsHash", t.smsHash).put("ruleId", t.ruleId)
        .put("createdAt", t.createdAt)

    private fun txFromJson(o: JSONObject) = TransactionEntity(
        id = o.getLong("id"), type = o.getString("type"), amountPaise = o.getLong("amountPaise"), timestamp = o.getLong("timestamp"),
        account = o.str("account"), accountKind = o.str("accountKind"), bank = o.str("bank"), merchant = o.str("merchant"),
        merchantKey = o.str("merchantKey"), vpa = o.str("vpa"), reference = o.str("reference"), balancePaise = o.lng("balancePaise"),
        channel = o.str("channel"), hints = o.str("hints") ?: "", categoryKey = o.getString("categoryKey"),
        categoryLocked = o.optBoolean("categoryLocked"), note = o.str("note"), excluded = o.optBoolean("excluded"),
        deleted = o.optBoolean("deleted"), source = o.getString("source"), sender = o.str("sender"), rawText = o.str("rawText"),
        smsHash = o.str("smsHash"), ruleId = o.str("ruleId"), createdAt = o.lng("createdAt") ?: System.currentTimeMillis(),
    )

    private fun catToJson(c: CategoryEntity) = JSONObject()
        .put("key", c.key).put("name", c.name).put("emoji", c.emoji).put("colorLight", c.colorLight)
        .put("colorDark", c.colorDark).put("kind", c.kind).put("sortOrder", c.sortOrder)

    private fun catFromJson(o: JSONObject) = CategoryEntity(
        o.getString("key"), o.getString("name"), o.getString("emoji"), o.getLong("colorLight"), o.getLong("colorDark"),
        o.getString("kind"), o.getInt("sortOrder"),
    )

    private fun ruleToJson(r: RuleEntity) = JSONObject()
        .put("id", r.id).put("keyword", r.keyword).put("categoryKey", r.categoryKey).put("source", r.source).put("createdAt", r.createdAt)

    private fun ruleFromJson(o: JSONObject) = RuleEntity(
        o.getLong("id"), o.getString("keyword"), o.getString("categoryKey"), o.getString("source"), o.optLong("createdAt"),
    )

    private fun unparsedToJson(u: UnparsedEntity) = JSONObject()
        .put("id", u.id).put("sender", u.sender).put("body", u.body).put("timestamp", u.timestamp)
        .put("smsHash", u.smsHash).put("reason", u.reason).put("dismissed", u.dismissed)

    private fun unparsedFromJson(o: JSONObject) = UnparsedEntity(
        o.getLong("id"), o.getString("sender"), o.getString("body"), o.getLong("timestamp"), o.getString("smsHash"),
        o.optString("reason"), o.optBoolean("dismissed"),
    )

    companion object {
        const val FORMAT = "money-manager-backup"
    }
}
