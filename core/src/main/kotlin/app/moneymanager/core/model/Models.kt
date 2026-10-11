package app.moneymanager.core.model

/** A single SMS as read from the inbox or the SMS_RECEIVED broadcast. */
data class SmsMessage(
    val sender: String,
    val body: String,
    /** Epoch millis when the message was sent/received. */
    val timestamp: Long,
)

enum class TxnType { DEBIT, CREDIT }

enum class AccountKind { BANK, DEBIT_CARD, CREDIT_CARD, CARD, WALLET }

enum class Channel { UPI, CARD, NEFT, IMPS, RTGS, ATM, ACH, NETBANKING, WALLET, CHEQUE, OTHER }

/** Extra facts the parser noticed; the categoriser uses them. */
enum class TxnHint {
    ATM,
    SALARY,
    REFUND,
    CASHBACK,
    INTEREST,
    /** Paying a credit-card bill, or the card side of that payment. Double-counts spending, so auto-excluded. */
    CC_BILL_PAYMENT,
    /** UPI person-to-person (Axis "P2A"). */
    P2P,
    /** UPI person-to-merchant (Axis "P2M"). */
    P2M,
    EMI,
    AUTOPAY,
}

data class ParsedTxn(
    val type: TxnType,
    val amountPaise: Long,
    val timestamp: Long,
    /** Last 3–4 digits of the account or card, if present. */
    val account: String?,
    val accountKind: AccountKind?,
    val bank: String?,
    /** Cleaned, human-readable payee/payer name. */
    val merchant: String?,
    val vpa: String?,
    val reference: String?,
    val balancePaise: Long?,
    val channel: Channel?,
    val hints: Set<TxnHint>,
    val sender: String,
    val body: String,
    /** Id of the regex rule that matched (useful for debugging). */
    val ruleId: String,
)

enum class IgnoreReason {
    NOT_FINANCIAL,
    OTP,
    PROMOTIONAL,
    BALANCE_ONLY,
    FAILED,
    /** Bills due, statements, "will be debited", mandates being set up. */
    REMINDER,
    /** UPI collect requests: money someone asked for, not money that moved. */
    REQUEST,
    /** Logins, beneficiary added, card blocked, limits changed… */
    ACCOUNT_INFO,
}

sealed interface ParseResult {
    data class Parsed(val txn: ParsedTxn) : ParseResult
    data class Ignored(val reason: IgnoreReason) : ParseResult
    /** Looks like a bank transaction but no rule could extract it. Shown on the "Unparsed" screen. */
    data class Unparsed(val reason: String) : ParseResult
}
