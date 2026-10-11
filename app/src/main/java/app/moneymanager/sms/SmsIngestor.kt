package app.moneymanager.sms

import app.moneymanager.core.categorize.Categorizer
import app.moneymanager.core.model.ParseResult
import app.moneymanager.core.model.SmsMessage
import app.moneymanager.core.parse.SmsParser
import app.moneymanager.data.Repository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZoneId

/** SMS -> parse -> dedupe -> categorise -> store. Used by the live receiver, the 6-hourly sync and rescans. */
class SmsIngestor(private val repo: Repository) {
    data class Stats(
        var scanned: Int = 0,
        var added: Int = 0,
        var duplicates: Int = 0,
        var unparsed: Int = 0,
        var ignored: Int = 0,
        var alreadyKnown: Int = 0,
        val newTransactionIds: MutableList<Long> = ArrayList(),
    )

    // The receiver and the background sync can run at the same time; one at a time keeps dedupe correct.
    private val mutex = Mutex()

    suspend fun ingest(messages: List<SmsMessage>, onProgress: suspend (done: Int, stats: Stats) -> Unit = { _, _ -> }): Stats =
        mutex.withLock {
            val stats = Stats()
            val zone = ZoneId.systemDefault()
            // An SMS can arrive before the app has ever been opened; make sure categories and rules exist.
            repo.ensureSeeded()
            val categorizer = repo.categorizer()
            for (chunk in messages.chunked(200)) {
                repo.inTransaction {
                    for (m in chunk) process(m, zone, categorizer, stats)
                }
                onProgress(stats.scanned, stats)
            }
            stats
        }

    private suspend fun process(m: SmsMessage, zone: ZoneId, categorizer: Categorizer, stats: Stats) {
        stats.scanned++
        val hash = SmsReader.hash(m)
        if (repo.transactionHashExists(hash)) {
            stats.alreadyKnown++
            return
        }
        // Unparsed messages are re-checked on every scan, so a parser update can pick them up later.
        val previouslyUnparsed = repo.unparsedByHash(hash)
        // One odd message must never stop a whole inbox scan: file it under "Unparsed" instead.
        val result = try {
            SmsParser.parse(m, zone)
        } catch (e: Exception) {
            ParseResult.Unparsed("Parser error: ${e.javaClass.simpleName}")
        }
        when (val r = result) {
            is ParseResult.Ignored -> {
                stats.ignored++
                previouslyUnparsed?.let { repo.deleteUnparsed(it.id) }
            }
            is ParseResult.Unparsed -> {
                stats.unparsed++
                if (previouslyUnparsed == null) repo.insertUnparsed(m, hash, r.reason)
            }
            is ParseResult.Parsed -> {
                val t = r.txn
                val dup = repo.findDuplicate(t)
                if (dup != null) {
                    repo.mergeDuplicate(dup, t, categorizer)
                    stats.duplicates++
                } else {
                    val id = repo.insertParsed(t, hash, categorizer.categorize(t.type, t.merchant, t.vpa, t.hints))
                    if (id > 0) {
                        stats.added++
                        stats.newTransactionIds += id
                    }
                }
                previouslyUnparsed?.let { repo.deleteUnparsed(it.id) }
            }
        }
    }
}
