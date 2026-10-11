package app.moneymanager.core.parse

import app.moneymanager.core.format.Amounts
import app.moneymanager.core.model.AccountKind
import app.moneymanager.core.model.Channel
import app.moneymanager.core.model.IgnoreReason
import app.moneymanager.core.model.ParseResult
import app.moneymanager.core.model.ParsedTxn
import app.moneymanager.core.model.SmsMessage
import app.moneymanager.core.model.TxnHint
import app.moneymanager.core.model.TxnType
import java.time.ZoneId

/**
 * SMS -> transaction. Pipeline:
 *  1. cheap rejections (promo sender, no amount, personal/non-bank sender without an account number)
 *  2. ignore filters: OTP, collect requests, future/mandate notices, failed txns, promos, balance-only, account info
 *  3. bank-specific regex rules ([BankRules]); first match wins
 *  4. generic extractors fill whatever the rule didn't capture (or everything, if no rule matched)
 */
object SmsParser {
    val IST: ZoneId = ZoneId.of("Asia/Kolkata")

    fun parse(msg: SmsMessage, zone: ZoneId = IST): ParseResult {
        val sender = Senders.analyze(msg.sender)
        val text = Text.normalize(msg.body)
        if (text.isEmpty()) return ParseResult.Ignored(IgnoreReason.NOT_FINANCIAL)
        if (sender.isPromotional) return ParseResult.Ignored(IgnoreReason.PROMOTIONAL)

        if (!Extract.hasAnyAmount(text)) return ParseResult.Ignored(IgnoreReason.NOT_FINANCIAL)
        val genericAmount = Extract.amount(text)
        val verb = Verbs.first(text)
        val hasVerb = verb != null
        val hasAccount = Extract.hasAccountRef(text)
        val bankSender = sender.looksLikeBank || sender.unknown

        // Friends' messages and merchant order updates mention money too; only trust them if they name an account.
        if (!bankSender && !hasAccount) return ParseResult.Ignored(IgnoreReason.NOT_FINANCIAL)
        if (Filters.isOtp(text, hasVerb)) return ParseResult.Ignored(IgnoreReason.OTP)
        if (Filters.isRequest(text)) return ParseResult.Ignored(IgnoreReason.REQUEST)
        if (Filters.isFuture(text)) return ParseResult.Ignored(IgnoreReason.REMINDER)
        if (Filters.isFailed(text) && !(verb?.type == TxnType.CREDIT && Verbs.hasPastCredit(text))) {
            return ParseResult.Ignored(IgnoreReason.FAILED)
        }
        if (Filters.isPromo(text, hasVerb, hasAccount)) return ParseResult.Ignored(IgnoreReason.PROMOTIONAL)
        if (verb == null || genericAmount == null) {
            return when {
                Filters.isReminder(text) -> ParseResult.Ignored(IgnoreReason.REMINDER)
                Filters.isBalanceOnly(text) -> ParseResult.Ignored(IgnoreReason.BALANCE_ONLY)
                Filters.isAccountInfo(text) -> ParseResult.Ignored(IgnoreReason.ACCOUNT_INFO)
                bankSender && hasAccount && Filters.mentionsBanking(text) -> ParseResult.Unparsed(
                    if (verb == null) "Has an amount and an account, but no debit/credit wording"
                    else "Has debit/credit wording but no transaction amount",
                )
                else -> ParseResult.Ignored(IgnoreReason.NOT_FINANCIAL)
            }
        }

        // ---- bank-specific rules
        var ruleId = "generic"
        var type = verb.type
        var amount: Long = genericAmount
        var accountDigits: String? = null
        var merchantRaw: String? = null
        var vpa: String? = null
        var ref: String? = null
        var balance: Long? = null
        var channel: Channel? = null
        val hints = HashSet<TxnHint>()
        for (rule in BankRules.rules) {
            val sr = rule.senderRegex
            if (sr != null && !sender.unknown && !sr.containsMatchIn(sender.header)) continue
            val m = rule.regex.find(text) ?: continue
            val ruleAmount = rule.group(m, "amount")?.let(Amounts::parseToPaise)?.takeIf { it > 0 } ?: continue
            ruleId = rule.id
            amount = ruleAmount
            rule.type?.let { type = it }
            accountDigits = rule.group(m, "account")
            merchantRaw = rule.group(m, "merchant")?.let(Extract::validMerchant)
            vpa = rule.group(m, "vpa")?.lowercase()
            ref = rule.group(m, "ref")
            balance = rule.group(m, "balance")?.let(Amounts::parseToPaise)
            channel = rule.channel
            hints += rule.hints
            break
        }

        // ---- generic fill-in
        val generic = Extract.primaryAccount(text)
        val account = when {
            accountDigits == null -> generic
            generic != null && generic.digits.endsWith(accountDigits.takeLast(3)) -> generic.copy(digits = accountDigits)
            else -> AccountRef(accountDigits, if (channel == Channel.CARD) AccountKind.CARD else AccountKind.BANK, 0)
        }
        if (ref == null) ref = Extract.reference(text)
        if (merchantRaw == null) merchantRaw = Extract.merchant(text, type)
        if (merchantRaw != null && Extract.isVpa(merchantRaw)) {
            if (vpa == null) vpa = merchantRaw.lowercase()
            merchantRaw = null
        }
        if (vpa == null) vpa = Extract.vpa(text)
        if (balance == null) balance = Extract.balance(text)
        hints += Extract.hints(text, type)

        var display = MerchantNames.display(merchantRaw, vpa)
        if (TxnHint.ATM in hints) display = "ATM"
        val bank = sender.bank ?: Senders.bankFromBody(text)

        return ParseResult.Parsed(
            ParsedTxn(
                type = type,
                amountPaise = amount,
                timestamp = Dates.resolve(text, msg.timestamp, zone),
                account = account?.digits,
                accountKind = Extract.accountKind(text, account, bank),
                bank = bank,
                merchant = display,
                vpa = vpa,
                reference = ref,
                balancePaise = balance,
                channel = channel ?: Extract.channel(text, account, hints),
                hints = hints,
                sender = msg.sender,
                body = msg.body,
                ruleId = ruleId,
            )
        )
    }
}
