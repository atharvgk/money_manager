package app.moneymanager

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.moneymanager.core.model.SmsMessage
import app.moneymanager.core.model.TxnType
import app.moneymanager.data.BackupManager
import app.moneymanager.data.Repository
import app.moneymanager.data.db.AppDatabase
import app.moneymanager.sms.SmsIngestor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Runs on a phone/emulator against real Room: ingestion, dedupe, rules, and backup/restore. */
@RunWith(AndroidJUnit4::class)
class DataLayerTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun newDb() = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()

    @Test
    fun ingest_dedupe_recategorise_and_backup_roundtrip() = runBlocking {
        val db = newDb()
        val repo = Repository(db)
        repo.ensureSeeded()
        val ingestor = SmsIngestor(repo)
        val now = System.currentTimeMillis()
        val messages = listOf(
            SmsMessage("VM-HDFCBK", "Rs.450.00 debited from a/c **1234 on 05-10-25 to VPA swiggy@axisbank(UPI Ref No 627812345678).", now),
            SmsMessage("VM-HDFCBK", "Sent Rs.450.00 From HDFC Bank A/C *1234 To SWIGGY On 05/10/25 Ref 627812345678", now + 30_000),
            SmsMessage("AX-AXISBK", "INR 1200.00 debited A/c no. XX9876 05-10-25, 12:00:00 UPI/P2A/627812340000/RAHUL SHARMA Not you?", now),
            SmsMessage("VM-HDFCBK", "482913 is OTP for txn of INR 2,499.00 at FLIPKART on HDFC Bank card ending 5678. Do not share OTP", now),
            SmsMessage("VM-HDFCBK", "A/c XX1234 txn alert: INR 999.00 @ BIGBAZAAR 05-10-25", now),
        )

        val stats = ingestor.ingest(messages)
        assertEquals("added", 2, stats.added)
        assertEquals("duplicates", 1, stats.duplicates)
        assertEquals("unparsed", 1, stats.unparsed)
        assertEquals("ignored (OTP)", 1, stats.ignored)
        assertEquals("a rescan imports nothing twice", 0, ingestor.ingest(messages).added)

        val swiggy = db.transactions().all().single { it.merchant == "Swiggy" }
        assertEquals("food", swiggy.categoryKey)
        assertEquals("swiggy@axisbank", swiggy.vpa)

        val rahul = db.transactions().all().single { it.merchant == "Rahul Sharma" }
        assertEquals("transfers_people", rahul.categoryKey)
        repo.updateTransaction(rahul.id, "rent", "October rent", excluded = false, applyToMerchant = true)
        assertEquals("rent", db.transactions().get(rahul.id)!!.categoryKey)
        assertTrue(db.rules().all().any { it.keyword == "rahul sharma" && it.categoryKey == "rent" && it.source == "USER" })

        repo.addManual(TxnType.DEBIT, 5_000, now, "Chai", "food", null)

        // JSON backup -> fresh database -> identical data.
        val json = ByteArrayOutputStream()
        BackupManager(db).exportJson(json)
        val db2 = newDb()
        Repository(db2).ensureSeeded()
        val result = BackupManager(db2).importJson(ByteArrayInputStream(json.toByteArray()))
        assertEquals(3, result.transactions)
        assertEquals(db.transactions().all(), db2.transactions().all())
        assertEquals(db.rules().all().sortedBy { it.keyword }, db2.rules().all().sortedBy { it.keyword })
        assertEquals(db.unparsed().all(), db2.unparsed().all())

        val csv = ByteArrayOutputStream()
        BackupManager(db).exportCsv(csv)
        val lines = csv.toString("UTF-8").trim().lines()
        assertEquals("header + 3 transactions", 4, lines.size)
        assertTrue(lines.any { "Rahul Sharma" in it && "Rent" in it && "1200.00" in it })
        db.close()
        db2.close()
    }
}
