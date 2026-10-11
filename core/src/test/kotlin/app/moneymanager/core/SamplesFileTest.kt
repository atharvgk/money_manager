package app.moneymanager.core

import app.moneymanager.core.format.Inr
import app.moneymanager.core.model.ParseResult
import app.moneymanager.core.model.SmsMessage
import app.moneymanager.core.model.TxnType
import app.moneymanager.core.parse.SmsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Runs every message in <project>/samples.txt through the parser.
 *
 * File format (see README and src/test/resources/samples-example.txt):
 *   - Messages are separated by a blank line or a line containing only "---".
 *   - Lines starting with "#" are comments.
 *   - Optional first line "From: VM-HDFCBK" sets the sender.
 *   - Optional last line "=> ..." states what you expect:
 *       => debit 450.00 merchant=Swiggy acct=1234 ref=527812345678
 *       => credit 1000
 *       => ignore
 *
 * Without an "=>" line, the test only requires that a message is either parsed or deliberately ignored
 * (never "Unparsed"). A full report is written to core/build/reports/samples-report.txt.
 */
class SamplesFileTest {
    data class Sample(val sender: String, val body: String, val expect: String?, val line: Int)

    @Test fun samples_txt_parses() {
        val path = System.getProperty("samples.file") ?: "../samples.txt"
        val file = File(path)
        // No samples.txt on this PC (or on a phone/emulator running the tests): nothing to check. Not an
        // "assumption" skip, because Android's test runner reports those as failures.
        if (!file.exists()) {
            println("samples.txt not found at ${file.absolutePath}; nothing to check")
            return
        }
        run(read(file.readLines(Charsets.UTF_8)), System.getProperty("samples.report")?.let(::File))
    }

    /** Keeps the samples.txt reader honest even before a samples.txt exists. */
    @Test fun example_file_format_works() {
        val lines = javaClass.getResourceAsStream("/samples-example.txt")!!.bufferedReader(Charsets.UTF_8).readLines()
        val samples = read(lines)
        assertEquals(4, samples.size)
        assertEquals("VM-HDFCBK-S", samples[0].sender)
        assertEquals("debit 450.00 merchant=Swiggy acct=1234 ref=527812345678", samples[0].expect)
        assertEquals(7, samples[0].body.lines().size)
        run(samples, null)
    }

    private fun run(samples: List<Sample>, reportFile: File?) {
        val report = StringBuilder("samples.txt: ${samples.size} messages\n\n")
        val failures = ArrayList<String>()
        for (s in samples) {
            val result = SmsParser.parse(SmsMessage(s.sender, s.body, T.ts), T.zone)
            val summary = describe(result)
            val oneLine = s.body.replace('\n', '⏎')
            report.append("#${s.line}  ${oneLine.take(120)}\n    -> $summary\n\n")
            val problem = check(result, s.expect)
            if (problem != null) failures += "line ${s.line}: $problem\n  SMS: $oneLine\n  got: $summary"
        }
        reportFile?.apply { parentFile?.mkdirs() }?.writeText(report.toString())
        println(report)
        if (failures.isNotEmpty()) fail("${failures.size} of ${samples.size} samples failed:\n\n" + failures.joinToString("\n\n"))
    }

    private fun describe(r: ParseResult) = when (r) {
        is ParseResult.Parsed -> r.txn.run {
            "${type.name.lowercase()} ${Inr.format(amountPaise)} | merchant=$merchant | vpa=$vpa | acct=$account | " +
                "ref=$reference | bal=${balancePaise?.let(Inr::format)} | rule=$ruleId"
        }
        is ParseResult.Ignored -> "ignored (${r.reason})"
        is ParseResult.Unparsed -> "UNPARSED (${r.reason})"
    }

    private fun check(r: ParseResult, expect: String?): String? {
        if (expect == null) return if (r is ParseResult.Unparsed) "could not be parsed" else null
        val parts = expect.trim().split(Regex("""\s+"""))
        if (parts[0].equals("ignore", true)) return if (r is ParseResult.Ignored) null else "expected ignore"
        if (r !is ParseResult.Parsed) return "expected a transaction"
        val t = r.txn
        val wantType = if (parts[0].equals("credit", true)) TxnType.CREDIT else TxnType.DEBIT
        if (t.type != wantType) return "expected ${wantType.name.lowercase()}"
        parts.getOrNull(1)?.takeIf { it.first().isDigit() }?.let {
            if (Math.round(it.replace(",", "").toDouble() * 100) != t.amountPaise) return "expected amount $it"
        }
        for (kv in parts.drop(1).filter { '=' in it }) {
            val (k, v) = kv.split('=', limit = 2)
            val actual = when (k.lowercase()) {
                "merchant" -> t.merchant
                "acct", "account" -> t.account
                "ref" -> t.reference
                "vpa" -> t.vpa
                else -> continue
            }
            if (!actual.orEmpty().replace(" ", "").equals(v.replace("_", ""), ignoreCase = true)) return "expected $k=$v"
        }
        return null
    }

    private fun read(fileLines: List<String>): List<Sample> {
        val out = ArrayList<Sample>()
        var buf = ArrayList<String>()
        var startLine = 1
        fun flush() {
            val lines = buf.filter { !it.trimStart().startsWith("#") }.toMutableList()
            buf = ArrayList()
            if (lines.isEmpty()) return
            var sender = ""
            if (lines.first().startsWith("From:", ignoreCase = true)) sender = lines.removeAt(0).substringAfter(':').trim()
            var expect: String? = null
            if (lines.isNotEmpty() && lines.last().trimStart().startsWith("=>")) {
                expect = lines.removeAt(lines.lastIndex).trim().removePrefix("=>").trim()
            }
            val body = lines.joinToString("\n").trim()
            if (body.isNotEmpty()) out += Sample(sender, body, expect, startLine)
        }
        fileLines.forEachIndexed { i, raw ->
            val line = raw.trimEnd()
            if (line.isBlank() || line.trim() == "---") {
                flush()
            } else {
                if (buf.isEmpty()) startLine = i + 1
                buf += line
            }
        }
        flush()
        return out
    }
}
