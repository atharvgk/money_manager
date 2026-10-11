package app.moneymanager.data

import androidx.room.withTransaction
import app.moneymanager.core.analysis.Dedupe
import app.moneymanager.core.categorize.Categories
import app.moneymanager.core.categorize.Categorization
import app.moneymanager.core.categorize.Categorizer
import app.moneymanager.core.categorize.KeywordRule
import app.moneymanager.core.categorize.SeedRules
import app.moneymanager.core.model.ParseResult
import app.moneymanager.core.model.ParsedTxn
import app.moneymanager.core.model.SmsMessage
import app.moneymanager.core.model.TxnHint
import app.moneymanager.core.model.TxnType
import app.moneymanager.core.parse.MerchantNames
import app.moneymanager.core.parse.SmsParser
import app.moneymanager.data.db.AppDatabase
import app.moneymanager.data.db.CategoryEntity
import app.moneymanager.data.db.RuleEntity
import app.moneymanager.data.db.RuleSource
import app.moneymanager.data.db.Source
import app.moneymanager.data.db.TransactionEntity
import app.moneymanager.data.db.UnparsedEntity
import kotlinx.coroutines.flow.Flow

data class TxnFilter(
    val query: String = "",
    val categories: Set<String> = emptySet(),
    val account: String? = null,
    val from: Long = 0L,
    val to: Long = Long.MAX_VALUE,
)

class Repository(val db: AppDatabase) {
    private val tx = db.transactions()
    private val cats = db.categories()
    private val rulesDao = db.rules()
    private val unparsedDao = db.unparsed()

    val categories: Flow<List<CategoryEntity>> = cats.observeAll()
    val rules: Flow<List<RuleEntity>> = rulesDao.observeAll()
    val unparsed: Flow<List<UnparsedEntity>> = unparsedDao.observeActive()
    val unparsedCount: Flow<Int> = unparsedDao.observeCount()
    val accounts = tx.observeAccounts()
    val creditCardAccounts: Flow<List<String>> = tx.observeCreditCardAccounts()
    val earliestTimestamp: Flow<Long?> = tx.observeEarliest()
    val transactionCount: Flow<Int> = tx.observeCount()

    suspend fun <R> inTransaction(block: suspend () -> R): R = db.withTransaction(block)

    // ------------------------------------------------------------------ seeding

    suspend fun ensureSeeded() {
        // Built-in categories always follow the code (names, emoji, colours), so fixes reach existing installs.
        cats.insertAll(defaultCategoryEntities())
        if (rulesDao.count() == 0) rulesDao.insertIgnore(seedRuleEntities())
    }

    /** Adds back any built-in rules you deleted. Your own rules are kept. */
    suspend fun restoreDefaultRules() {
        rulesDao.insertIgnore(seedRuleEntities())
        recategorizeAll()
    }

    private fun defaultCategoryEntities() = Categories.defaults.mapIndexed { i, c ->
        CategoryEntity(c.key, c.name, c.emoji, c.colorLight, c.colorDark, c.kind.name, i)
    }

    private fun seedRuleEntities() = SeedRules.all.map { (k, c) -> RuleEntity(keyword = k, categoryKey = c, source = RuleSource.SEED) }

    suspend fun categorizer(): Categorizer =
        Categorizer(rulesDao.all().map { KeywordRule(it.keyword, it.categoryKey, it.source == RuleSource.USER) })

    // ------------------------------------------------------------------ queries

    fun observeRange(from: Long, to: Long) = tx.observeRange(from, to)

    fun observeFiltered(f: TxnFilter) = tx.observeFiltered(
        from = f.from,
        to = f.to,
        allCategories = f.categories.isEmpty(),
        categories = f.categories.toList(),
        account = f.account,
        q = f.query.trim(),
    )

    fun observeTransaction(id: Long) = tx.observe(id)

    // ------------------------------------------------------------------ SMS ingestion

    suspend fun transactionHashExists(hash: String) = tx.hashExists(hash)

    suspend fun unparsedByHash(hash: String) = unparsedDao.byHash(hash)

    suspend fun deleteUnparsed(id: Long) = unparsedDao.delete(id)

    suspend fun insertUnparsed(msg: SmsMessage, hash: String, reason: String) {
        val window = 10 * 60 * 1000L
        if (unparsedDao.similarExists(msg.sender, msg.body, msg.timestamp - window, msg.timestamp + window)) return
        unparsedDao.insert(UnparsedEntity(sender = msg.sender, body = msg.body, timestamp = msg.timestamp, smsHash = hash, reason = reason))
    }

    suspend fun findDuplicate(t: ParsedTxn): TransactionEntity? {
        val candidates = tx.dedupeCandidates(
            t.type.name, t.amountPaise, t.reference, t.timestamp - Dedupe.WINDOW_MS, t.timestamp + Dedupe.WINDOW_MS,
        )
        val key = Dedupe.Key(t.type, t.amountPaise, t.timestamp, t.account, t.reference)
        return candidates.firstOrNull {
            Dedupe.isSameTransaction(Dedupe.Key(TxnType.valueOf(it.type), it.amountPaise, it.timestamp, it.account, it.reference), key)
        }
    }

    suspend fun insertParsed(t: ParsedTxn, hash: String, c: Categorization): Long = tx.insert(
        TransactionEntity(
            type = t.type.name,
            amountPaise = t.amountPaise,
            timestamp = t.timestamp,
            account = t.account,
            accountKind = t.accountKind?.name,
            bank = t.bank,
            merchant = t.merchant,
            merchantKey = MerchantNames.key(t.merchant) ?: t.vpa,
            vpa = t.vpa,
            reference = t.reference,
            balancePaise = t.balancePaise,
            channel = t.channel?.name,
            hints = t.hints.joinToString(",") { it.name },
            categoryKey = c.categoryKey,
            excluded = c.autoExclude,
            note = if (c.autoExclude) "Credit card bill payment: excluded automatically so spending isn't counted twice" else null,
            source = Source.SMS,
            sender = t.sender,
            rawText = t.body,
            smsHash = hash,
            ruleId = t.ruleId,
        ),
    )

    /** A second SMS for an existing transaction: keep the first record, fill in anything it was missing. */
    suspend fun mergeDuplicate(existing: TransactionEntity, t: ParsedTxn, categorizer: Categorizer) {
        val hints = (parseHints(existing.hints) + t.hints)
        var merged = existing.copy(
            merchant = existing.merchant ?: t.merchant,
            merchantKey = existing.merchantKey ?: MerchantNames.key(t.merchant) ?: t.vpa,
            vpa = existing.vpa ?: t.vpa,
            reference = existing.reference ?: t.reference,
            balancePaise = existing.balancePaise ?: t.balancePaise,
            account = existing.account ?: t.account,
            accountKind = existing.accountKind?.takeIf { it != "CARD" } ?: t.accountKind?.name ?: existing.accountKind,
            channel = existing.channel ?: t.channel?.name,
            hints = hints.joinToString(",") { it.name },
        )
        if (!existing.categoryLocked && (existing.merchant == null && merged.merchant != null || existing.vpa == null && merged.vpa != null)) {
            merged = merged.copy(categoryKey = categorizer.categorize(t.type, merged.merchant, merged.vpa, hints).categoryKey)
        }
        if (merged != existing) tx.update(merged)
    }

    // ------------------------------------------------------------------ user edits

    suspend fun updateTransaction(id: Long, categoryKey: String, note: String?, excluded: Boolean, applyToMerchant: Boolean) {
        val t = tx.get(id) ?: return
        val changed = t.categoryKey != categoryKey
        tx.update(
            t.copy(
                categoryKey = categoryKey,
                categoryLocked = t.categoryLocked || changed,
                note = note?.trim()?.ifEmpty { null },
                excluded = excluded,
            ),
        )
        if (changed && applyToMerchant) {
            val keyword = (t.merchantKey ?: t.vpa)?.lowercase() ?: return
            rulesDao.upsert(RuleEntity(keyword = keyword, categoryKey = categoryKey, source = RuleSource.USER))
            if (t.merchantKey != null) tx.setCategoryForMerchant(t.merchantKey, categoryKey)
            recategorizeAll()
        }
    }

    suspend fun addManual(type: TxnType, amountPaise: Long, timestamp: Long, merchant: String?, categoryKey: String, note: String?): Long {
        val name = merchant?.trim()?.ifEmpty { null }
        return tx.insert(
            TransactionEntity(
                type = type.name,
                amountPaise = amountPaise,
                timestamp = timestamp,
                bank = "Cash",
                merchant = name,
                merchantKey = MerchantNames.key(name),
                categoryKey = categoryKey,
                categoryLocked = true,
                note = note?.trim()?.ifEmpty { null },
                source = Source.MANUAL,
            ),
        )
    }

    suspend fun delete(id: Long) {
        val t = tx.get(id) ?: return
        tx.update(t.copy(deleted = true))
    }

    // ------------------------------------------------------------------ rules

    suspend fun saveRule(existingId: Long?, keyword: String, categoryKey: String) {
        val k = keyword.trim().lowercase()
        if (k.isEmpty()) return
        inTransaction {
            if (existingId != null) rulesDao.delete(existingId)
            rulesDao.upsert(RuleEntity(keyword = k, categoryKey = categoryKey, source = RuleSource.USER))
        }
        recategorizeAll()
    }

    suspend fun deleteRule(id: Long) {
        rulesDao.delete(id)
        recategorizeAll()
    }

    /** Re-runs the categoriser on every SMS transaction whose category you haven't set by hand. */
    suspend fun recategorizeAll(): Int {
        val c = categorizer()
        var changed = 0
        inTransaction {
            for (t in tx.unlockedSmsTransactions()) {
                val r = c.categorize(TxnType.valueOf(t.type), t.merchant, t.vpa, parseHints(t.hints))
                if (r.categoryKey != t.categoryKey) {
                    tx.setCategory(t.id, r.categoryKey)
                    changed++
                }
            }
        }
        return changed
    }

    // ------------------------------------------------------------------ data upgrades

    /**
     * Re-reads stored SMS with the current parser to fill in what older versions didn't record.
     * v1: credit card vs debit card vs bank account (for the Bank / Cards split). Categories, notes and
     * amounts are never touched.
     */
    suspend fun upgradeData(fromVersion: Int): Int {
        if (fromVersion >= DATA_VERSION) return DATA_VERSION
        inTransaction {
            for (t in tx.smsTransactions()) {
                val parsed = SmsParser.parse(SmsMessage(t.sender.orEmpty(), t.rawText ?: continue, t.timestamp))
                val p = (parsed as? ParseResult.Parsed)?.txn ?: continue
                val kind = p.accountKind?.name ?: continue
                if (p.account == t.account && kind != t.accountKind) tx.setAccountKind(t.id, kind)
            }
        }
        return DATA_VERSION
    }

    // ------------------------------------------------------------------ unparsed

    suspend fun dismissUnparsed(id: Long) = unparsedDao.dismiss(id)

    companion object {
        const val DATA_VERSION = 1

        fun parseHints(s: String): Set<TxnHint> =
            s.split(',').mapNotNull { n -> runCatching { TxnHint.valueOf(n.trim()) }.getOrNull() }.toSet()
    }
}
