package app.moneymanager.core

import app.moneymanager.core.T.ignored
import app.moneymanager.core.model.IgnoreReason
import app.moneymanager.core.model.ParseResult
import org.junit.Assert.assertTrue
import org.junit.Test

class FiltersTest {
    @Test fun otp_with_amount_is_ignored() {
        ignored("VM-HDFCBK", "123456 is OTP for txn of INR 1,234.00 at AMAZON on HDFC Bank card ending 5678. Valid till 10:20. Do not share OTP for security reasons", IgnoreReason.OTP)
        ignored("VM-ICICIT", "Your OTP for transaction of Rs 500.00 at FLIPKART is 482913. Do not share it with anyone.", IgnoreReason.OTP)
        ignored("AX-SBIINB", "OTP is 552211 for the transaction of Rs 999 on your SBI Debit Card XX5678 at MYNTRA.", IgnoreReason.OTP)
    }

    @Test fun promotional_sender_suffix() {
        ignored("VM-HDFCBK-P", "Get a Personal Loan of up to Rs 10 lakh in 10 seconds! Apply now", IgnoreReason.PROMOTIONAL)
    }

    @Test fun promo_text_from_bank() {
        ignored("VM-HDFCBK", "Dear Customer, you are eligible for a pre-approved personal loan of Rs 5,00,000 at 10.5% p.a. Apply now: https://hdfc.bank/x", IgnoreReason.PROMOTIONAL)
        ignored("VM-ICICIB", "Shop on Amazon and get 10% instant discount up to Rs 1,500 with ICICI Bank Credit Cards. T&C apply", IgnoreReason.PROMOTIONAL)
        ignored("VM-AXISBK", "Congratulations! Your Axis Bank Credit Card limit can be increased to Rs 2,50,000. Click https://axis.bank/abc to upgrade", IgnoreReason.PROMOTIONAL)
    }

    @Test fun balance_only_alert() {
        ignored("VM-HDFCBK", "Available balance in your A/c XX1234 is Rs 12,345.67 as on 05-10-25 10:00. -HDFC Bank", IgnoreReason.BALANCE_ONLY)
        ignored("VM-SBIINB", "Dear Customer, the Avl Bal in your A/c XX1234 is Rs 5,000.00 on 05/10/25. -SBI", IgnoreReason.BALANCE_ONLY)
    }

    @Test fun failed_and_declined() {
        ignored("VM-HDFCBK", "Your UPI transaction of Rs 450.00 to swiggy@icici from A/c XX1234 has failed. Ref 527812345678", IgnoreReason.FAILED)
        ignored("VM-ICICIB", "Dear Customer, your transaction of Rs 450.00 on ICICI Bank Credit Card XX5678 at SWIGGY has been declined due to insufficient limit", IgnoreReason.FAILED)
    }

    @Test fun reminders_and_statements() {
        ignored("VM-HDFCBK", "Your HDFC Bank Credit Card XX5678 statement is generated. Total amount due: Rs 12,345.00, Minimum due: Rs 620.00. Pay by 15-10-2025.", IgnoreReason.REMINDER)
        ignored("VM-ICICIT", "Rs 199.00 will be debited from your a/c XX123 on 10-10-2025 towards NETFLIX mandate.", IgnoreReason.REMINDER)
        ignored("VM-HDFCBK", "UPI Mandate successfully created for Rs 199.00 towards Spotify from A/c XX1234. Ref 527812345678", IgnoreReason.REMINDER)
    }

    @Test fun collect_request() {
        ignored("VM-HDFCBK", "JOHN DOE has requested money from you. On approving, Rs 500.00 will be debited from your A/c XX1234. -HDFC Bank", IgnoreReason.REQUEST)
    }

    @Test fun account_info() {
        ignored("VM-HDFCBK", "You have added beneficiary JOHN DOE to your HDFC Bank A/c XX1234 with a limit of Rs 50,000.", IgnoreReason.ACCOUNT_INFO)
    }

    @Test fun personal_messages_are_not_transactions() {
        ignored("+919876543210", "Hey, I paid Rs 500 for dinner, send me your half", IgnoreReason.NOT_FINANCIAL)
        ignored("VM-SWIGGY", "Your order of Rs 450 has been placed and will be delivered soon", null)
    }

    @Test fun merchant_receipt_without_account_is_ignored() {
        ignored("JM-JIOINF", "Recharge of Rs 299 successful. We have received your payment. Plan valid till 05-11-2025.", IgnoreReason.NOT_FINANCIAL)
    }

    @Test fun bank_message_with_account_but_no_verb_goes_to_unparsed() {
        val r = T.parse("VM-HDFCBK", "A/c XX1234 txn alert: INR 450.00 @ SWIGGY 05-10-25")
        assertTrue("got $r", r is ParseResult.Unparsed)
    }
}
