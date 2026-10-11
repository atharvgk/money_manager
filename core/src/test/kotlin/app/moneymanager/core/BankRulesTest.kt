package app.moneymanager.core

import app.moneymanager.core.T.check
import app.moneymanager.core.T.txn
import app.moneymanager.core.model.AccountKind
import app.moneymanager.core.model.Channel
import app.moneymanager.core.model.TxnHint
import app.moneymanager.core.model.TxnType.CREDIT
import app.moneymanager.core.model.TxnType.DEBIT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic messages modelled on real formats. Add a case here whenever you add a rule. */
class BankRulesTest {

    // ------------------------------------------------------------------ HDFC

    @Test fun hdfc_upi_sent_multiline() {
        val t = txn(
            "VM-HDFCBK-S",
            "Sent Rs.450.00\nFrom HDFC Bank A/C *1234\nTo SWIGGY\nOn 05/10/25\nRef 527812345678\nNot You?\nCall 18002586161/SMS BLOCK UPI to 7308080808",
        )
        t.check(DEBIT, 450.0, account = "1234", merchant = "Swiggy", ref = "527812345678")
        assertEquals("hdfc_upi_sent", t.ruleId)
        assertEquals(Channel.UPI, t.channel)
        assertEquals("HDFC Bank", t.bank)
    }

    @Test fun hdfc_amt_sent_to_person() {
        txn("AD-HDFCBK", "Amt Sent Rs.500.00\nFrom HDFC Bank A/C *1234\nTo RAHUL KUMAR SHARMA\nOn 05-10\nRef 527812345679\nNot You? Call 18002586161")
            .check(DEBIT, 500.0, account = "1234", merchant = "Rahul Kumar Sharma", ref = "527812345679")
    }

    @Test fun hdfc_upi_debit_to_vpa() {
        txn(
            "VM-HDFCBK",
            "Rs.450.00 debited from a/c **1234 on 05-10-25 to VPA swiggy@axisbank(UPI Ref No 527812345678). Not you? Call on 18002586161 to report",
        ).check(DEBIT, 450.0, account = "1234", merchant = "Swiggy", vpa = "swiggy@axisbank", ref = "527812345678")
    }

    @Test fun hdfc_money_received() {
        txn(
            "VM-HDFCBK",
            "Money Received - INR 1,000.00 in your HDFC Bank A/c xx1234 on 05-10-25 by A/c linked to VPA john.doe@okaxis (UPI Ref No. 527812345678).",
        ).check(CREDIT, 1000.0, account = "1234", merchant = "John Doe", vpa = "john.doe@okaxis", ref = "527812345678")
    }

    @Test fun hdfc_credited_from_vpa() {
        txn("VM-HDFCBK", "Rs.1000.00 credited to HDFC Bank A/c XX1234 on 05-10-25 from VPA john@okaxis (UPI 527812345678)")
            .check(CREDIT, 1000.0, account = "1234", vpa = "john@okaxis", ref = "527812345678")
    }

    @Test fun hdfc_card_spent_with_time_and_balance() {
        val t = txn(
            "JD-HDFCBK-T",
            "Spent Rs.1234.50 On HDFC Bank Card 5678 At AMAZON PAY INDIA On 2025-10-05:14:23:11 Bal Rs.45678.00 Not You? Call 18002586161/SMS BLOCK DC 5678 to 7308080808",
        )
        t.check(DEBIT, 1234.50, account = "5678", merchant = "Amazon Pay India", balance = 45678.0)
        assertEquals(T.at(2025, 10, 5, 14, 23, 11), t.timestamp)
        assertNull("phone number must not become a ref", t.reference)
    }

    @Test fun hdfc_credit_card_upi_txn() {
        txn("VM-HDFCBK", "Txn Rs.499.00\nOn HDFC Bank Card 5678\nAt NETFLIX\nby UPI 527812345670\nOn 05-10\nNot You?\nCall 18002586161/SMS BLOCK CC 5678 to 7308080808")
            .check(DEBIT, 499.0, account = "5678", merchant = "Netflix", ref = "527812345670")
    }

    @Test fun hdfc_neft_salary_credit() {
        val t = txn(
            "VM-HDFCBK",
            "Update! INR 25,000.00 deposited in HDFC Bank A/c XX1234 on 01-OCT-25 for NEFT Cr-ICIC0000123-ACME CORP PVT LTD-SALARY OCT 2025-ICICN52025100112345678.Avl bal INR 1,23,456.00. Cheque deposits in A/C are subject to clearing",
            T.at(2025, 10, 1, 10, 5),
        )
        t.check(CREDIT, 25000.0, account = "1234", merchant = "Acme Corp", balance = 123456.0)
        assertTrue(TxnHint.SALARY in t.hints)
    }

    @Test fun hdfc_ach_debit_info() {
        val t = txn("VM-HDFCBK", "UPDATE: Your A/c XX1234 debited for INR 1,500.00 on 05-OCT-25. Info: ACH D- BAJAJ FINANCE LTD-123456789. Avl bal:INR 12,000.00")
        t.check(DEBIT, 1500.0, account = "1234", merchant = "Bajaj Finance", balance = 12000.0)
        assertEquals(Channel.ACH, t.channel)
    }

    @Test fun hyphenated_merchant_is_title_cased() {
        txn("VM-HDFCBK", "UPDATE: Your A/c XX1234 debited for INR 5,000.00 on 10-OCT-25. Info: ACH D- GROWW-SIP-12345678. Avl bal:INR 52,000.00", T.at(2025, 10, 10, 7))
            .check(DEBIT, 5000.0, account = "1234", merchant = "Groww-Sip")
    }

    @Test fun hdfc_atm_withdrawal() {
        val t = txn(
            "VM-HDFCBK",
            "Thank you for using your HDFC Bank Debit Card ending 5678 for ATM withdrawal for Rs 2000 in MUMBAI at S1ANMU12 on 2025-10-05:10:20:30. Avl bal: Rs 12,345.00",
        )
        t.check(DEBIT, 2000.0, account = "5678", merchant = "ATM", balance = 12345.0)
        assertTrue(TxnHint.ATM in t.hints)
        assertEquals(AccountKind.DEBIT_CARD, t.accountKind)
    }

    @Test fun hdfc_credit_card_payment_received() {
        val t = txn(
            "VM-HDFCBK",
            "DEAR CARDMEMBER, PAYMENT OF RS. 5000.00 RECEIVED TOWARDS YOUR HDFC BANK CREDIT CARD ENDING 5678 ON 05/10/2025. YOUR AVAILABLE LIMIT IS RS. 1,00,000.00",
        )
        t.check(CREDIT, 5000.0, account = "5678")
        assertTrue(TxnHint.CC_BILL_PAYMENT in t.hints)
    }

    @Test fun cred_bill_payment_from_bank_side() {
        val t = txn("VM-HDFCBK", "Rs.12000.00 debited from a/c **1234 on 05-10-25 to VPA cred.club@axisbank(UPI Ref No 527812345611). Not you? Call on 18002586161 to report")
        t.check(DEBIT, 12000.0, account = "1234", vpa = "cred.club@axisbank")
        assertTrue(TxnHint.CC_BILL_PAYMENT in t.hints)
    }

    // ------------------------------------------------------------------ SBI

    @Test fun sbi_upi_debit() {
        txn("BZ-SBIUPI", "Dear UPI user A/C X1234 debited by 450.0 on date 05Oct25 trf to SWIGGY Refno 527812345678. If not u? call 1800111109. -SBI")
            .check(DEBIT, 450.0, account = "1234", merchant = "Swiggy", ref = "527812345678")
    }

    @Test fun sbi_upi_credit_without_name() {
        txn("BZ-SBIUPI", "Dear SBI UPI User, ur A/cX1234 credited by Rs500 on 05Oct25 by  (Ref no 527812345678)")
            .check(CREDIT, 500.0, account = "1234", ref = "527812345678")
    }

    @Test fun sbi_upi_credit_with_name() {
        txn("VM-SBIINB", "Dear SBI User, your A/c X1234-credited by Rs.500 on 05Oct25 transfer from JOHN DOE Ref No 527812345678 -SBI")
            .check(CREDIT, 500.0, account = "1234", merchant = "John Doe", ref = "527812345678")
    }

    @Test fun sbi_debit_by_transfer() {
        txn("VM-SBIINB", "Dear Customer, Your A/C XXXXX1234 has a debit by transfer of Rs 1,000.00 on 05/10/25. Avl Bal Rs 12,345.67.-SBI")
            .check(DEBIT, 1000.0, account = "1234", balance = 12345.67)
    }

    @Test fun sbi_imps_debit_two_accounts() {
        txn("VM-CBSSBI", "Your a/c no. XXXXXXXX1234 is debited for Rs.2000.00 on 05-10-25 and a/c XXXXXXX5678 credited (IMPS Ref no 527812345678).")
            .check(DEBIT, 2000.0, account = "1234", ref = "527812345678")
    }

    @Test fun sbi_atm() {
        val t = txn(
            "VM-SBIINB",
            "Dear Customer, Rs.2,000 withdrawn at SBI ATM S1BW000123 from A/cX1234 on 05Oct25 Transaction Number 1234. Available Balance Rs 10,000.00. If not withdrawn by you, forward this SMS to 7208933148 / call 1800111109 -SBI",
        )
        t.check(DEBIT, 2000.0, account = "1234", merchant = "ATM", balance = 10000.0)
    }

    @Test fun sbi_card_spent() {
        val t = txn("AD-SBICRD", "Rs.1,234.00 spent on your SBI Credit Card ending 5678 at AMAZON on 05/10/25. Trxn. not done by you? Report at https://sbicard.com/Dispute")
        t.check(DEBIT, 1234.0, account = "5678", merchant = "Amazon")
        assertEquals("SBI Card", t.bank)
        assertEquals(AccountKind.CREDIT_CARD, t.accountKind)
    }

    @Test fun sbi_deposit_from() {
        txn("VM-SBIINB", "Your A/C XXXXX1234 Credited INR 25,000.00 on 01/10/25 -Deposit by transfer from ACME CORP. Avl Bal INR 50,000.00-SBI", T.at(2025, 10, 1, 11))
            .check(CREDIT, 25000.0, account = "1234", merchant = "Acme Corp", balance = 50000.0)
    }

    // ------------------------------------------------------------------ ICICI

    @Test fun icici_upi_debit() {
        txn("VK-ICICIT", "ICICI Bank Acct XX123 debited for Rs 450.00 on 05-Oct-25; SWIGGY credited. UPI:527812345678. Call 18002662 for dispute. SMS BLOCK 123 to 9215676766.")
            .check(DEBIT, 450.0, account = "123", merchant = "Swiggy", ref = "527812345678")
    }

    @Test fun icici_upi_credit() {
        txn("VK-ICICIB", "Dear Customer, Acct XX123 is credited with Rs 1000.00 on 05-Oct-25 from JOHN DOE. UPI:527812345678-ICICI Bank.")
            .check(CREDIT, 1000.0, account = "123", merchant = "John Doe", ref = "527812345678")
    }

    @Test fun icici_card_spent() {
        txn("VK-ICICIB", "INR 1,234.00 spent using ICICI Bank Card XX5678 on 05-Oct-25 on AMAZON. Avl Limit: INR 1,00,000.00. If not you, call 1800 2662/SMS BLOCK 5678 to 9215676766.")
            .check(DEBIT, 1234.0, account = "5678", merchant = "Amazon")
    }

    @Test fun icici_credit_card_used_info() {
        val t = txn(
            "VK-ICICIB",
            "ICICI Bank Credit Card XX5678 has been used for a transaction of INR 499.00 on Oct 05, 2025 at 10:20:30. Info: NETFLIX. The Available Credit Limit on your card is INR 98,765.00 and Total Credit Limit is INR 1,00,000.00.",
        )
        t.check(DEBIT, 499.0, account = "5678", merchant = "Netflix")
        assertEquals(T.at(2025, 10, 5, 10, 20, 30), t.timestamp)
    }

    @Test fun icici_imps_debit() {
        txn("VK-ICICIT", "ICICI Bank Acct XX123 debited with Rs 2,000.00 on 05-Oct-25 & Acct XX456 credited. IMPS:527812345678. Call 18002662 for dispute.")
            .check(DEBIT, 2000.0, account = "123", ref = "527812345678")
    }

    @Test fun icici_credit_card_payment() {
        val t = txn("VK-ICICIB", "Dear Customer, Payment of INR 10,000.00 has been received on your ICICI Bank Credit Card Account XX5678 on 05-Oct-25. Thank you.")
        t.check(CREDIT, 10000.0, account = "5678")
        assertTrue(TxnHint.CC_BILL_PAYMENT in t.hints)
    }

    // ------------------------------------------------------------------ Axis

    @Test fun axis_upi_debit_p2m() {
        val t = txn("AX-AXISBK-S", "INR 450.00 debited\nA/c no. XX1234\n05-10-25, 14:23:11\nUPI/P2M/527812345678/SWIGGY\nNot you? SMS BLOCKUPI Cust ID to 919951860002\nAxis Bank")
        t.check(DEBIT, 450.0, account = "1234", merchant = "Swiggy", ref = "527812345678")
        assertTrue(TxnHint.P2M in t.hints)
        assertEquals(T.at(2025, 10, 5, 14, 23, 11), t.timestamp)
    }

    @Test fun axis_upi_credit_p2a() {
        val t = txn("AX-AXISBK", "INR 1000.00 credited\nA/c no. XX1234\n05-10-25, 14:23:11 IST\nUPI/P2A/527812345678/JOHN DOE\nNot you? SMS BLOCKUPI Cust ID to 919951860002\nAxis Bank")
        t.check(CREDIT, 1000.0, account = "1234", merchant = "John Doe", ref = "527812345678")
        assertTrue(TxnHint.P2P in t.hints)
    }

    @Test fun axis_upi_single_line() {
        txn("AX-AXISBK", "Debit INR 450.00 A/c no. XX1234 05-10-25 14:23:11 UPI/P2M/527812345678/SWIGGY Not you? SMS BLOCKUPI Cust ID to 919951860002 Axis Bank")
            .check(DEBIT, 450.0, account = "1234", merchant = "Swiggy", ref = "527812345678")
    }

    @Test fun axis_card_spent() {
        txn("AX-AXISBK", "Spent\nCard no. XX5678\nINR 1234.00\n05-10-25 14:23:11\nAMAZON\nAvl Lmt INR 98,766.00\nSMS BLOCK 5678 to 919951860002, if not you - Axis Bank")
            .check(DEBIT, 1234.0, account = "5678", merchant = "Amazon")
    }

    @Test fun axis_old_format_at_merchant() {
        txn("AX-AXISBK", "Your A/c no. XX1234 has been debited with INR 450.00 on 05-10-2025 14:23:11 at SWIGGY. Avl Bal-INR 12,345.00")
            .check(DEBIT, 450.0, account = "1234", merchant = "Swiggy", balance = 12345.0)
    }

    @Test fun axis_neft_salary_slash_format() {
        val t = txn("AX-AXISBK", "INR 85000.00 credited A/c no. XX9876 01-10-25, 09:00:00 IST NEFT/ACME CORP PVT LTD/SALARY Not you? SMS BLOCK to 919951860002 Axis Bank", T.at(2025, 10, 1, 9, 1))
        t.check(CREDIT, 85000.0, account = "9876", merchant = "Acme Corp")
        assertTrue(TxnHint.SALARY in t.hints)
        assertNull("phone number must not become a ref", t.reference)
    }

    @Test fun imps_slash_format() {
        txn("VM-ICICIB", "Rs 2,000.00 debited from Acct XX123 on 05-Oct-25 towards IMPS/P2A/527812345678/JOHN DOE. Avl Bal Rs 5,000.00")
            .check(DEBIT, 2000.0, account = "123", merchant = "John Doe", ref = "527812345678", balance = 5000.0)
    }

    // ------------------------------------------------------------------ Kotak

    @Test fun kotak_upi_sent() {
        txn("VM-KOTAKB", "Sent Rs.450.00 from Kotak Bank AC X1234 to swiggy@ybl on 05-10-25.UPI Ref 527812345678. Not you, https://kotak.com/KBANKT/Fraud")
            .check(DEBIT, 450.0, account = "1234", merchant = "Swiggy", vpa = "swiggy@ybl", ref = "527812345678")
    }

    @Test fun kotak_upi_received() {
        txn("VM-KOTAKB", "Received Rs.1000.00 in your Kotak Bank AC X1234 from john@okaxis on 05-10-25.UPI Ref:527812345678.")
            .check(CREDIT, 1000.0, account = "1234", vpa = "john@okaxis", ref = "527812345678")
    }

    @Test fun kotak_card_spent() {
        txn("VM-KOTAKB", "Rs.1234.00 spent on Kotak Credit Card XX5678 at ZOMATO on 05/10/2025. Avl limit Rs 1,00,000")
            .check(DEBIT, 1234.0, account = "5678", merchant = "Zomato")
    }

    // ------------------------------------------------------------------ Others

    @Test fun bob_dr_cr() {
        txn("VM-BOBTXN", "Rs.450.00 Dr. from A/C XXXXXX1234 and Cr. to swiggy@ybl. Ref:527812345678. AvlBal:Rs12345.00(2025:10:05 14:23:11). Not you? Call 18005700-BOB")
            .check(DEBIT, 450.0, account = "1234", vpa = "swiggy@ybl", merchant = "Swiggy", ref = "527812345678", balance = 12345.0)
    }

    @Test fun canara_upi_slash() {
        txn("VM-CANBNK", "An amount of INR 450.00 has been DEBITED to your account XXX1234 on 05/10/2025 towards UPI/527812345678/swiggy. Total Avail.bal INR 1,234.00. - Canara Bank")
            .check(DEBIT, 450.0, account = "1234", merchant = "Swiggy", ref = "527812345678", balance = 1234.0)
    }

    @Test fun union_bank_mobile_banking() {
        txn("VM-UBOI", "A/c *1234 Debited for Rs:450.00 on 05-10-2025 14:23:11 by Mob Bk ref no 527812345678 Avl Bal Rs:1234.00.If not done by you,call 1800222243 -Union Bank of India")
            .check(DEBIT, 450.0, account = "1234", ref = "527812345678", balance = 1234.0)
    }

    @Test fun pnb_through_upi() {
        txn("VM-PNBSMS", "Your a/c XXXXXXXX1234 is debited for INR 450.00 on 05-10-2025 14:23:11 through UPI. Avl Bal INR 1,234.00 CR. Ref: 527812345678 -PNB")
            .check(DEBIT, 450.0, account = "1234", ref = "527812345678", balance = 1234.0)
    }

    @Test fun indusind_masked_both_ends() {
        txn("VM-INDUSB", "Your A/C 1234XXXX5678 is debited for INR 450.00 towards UPI/527812345678/swiggy@ybl on 05/10/2025.")
            .check(DEBIT, 450.0, account = "5678", vpa = "swiggy@ybl", merchant = "Swiggy", ref = "527812345678")
    }

    @Test fun idfc_new_bal() {
        txn("VM-IDFCFB", "Your A/C XXXXXXX1234 is debited by INR 450.00 on 05/10/25 14:23. New Bal :INR 1,234.00. UPI Ref 527812345678 to swiggy@icici.")
            .check(DEBIT, 450.0, account = "1234", vpa = "swiggy@icici", ref = "527812345678", balance = 1234.0)
    }

    @Test fun amex_spent() {
        txn("AM-AMEXIN", "Alert: You've spent INR 1,234.00 on your AMEX card ** 12345 at UBER INDIA on 5 October 2025 at 10:20 AM IST.")
            .check(DEBIT, 1234.0, account = "2345", merchant = "Uber India")
    }

    @Test fun refund_to_card() {
        val t = txn("VM-HDFCBK", "Refund of Rs 450.00 from AMAZON has been credited to your HDFC Bank Credit Card XX5678 on 05-10-2025")
        t.check(CREDIT, 450.0, account = "5678", merchant = "Amazon")
        assertTrue(TxnHint.REFUND in t.hints)
    }

    @Test fun reversal_of_failed_txn_is_a_credit() {
        val t = txn("VM-HDFCBK", "Rs 450.00 credited to your A/c XX1234 on 06-10-25 towards reversal of failed UPI txn Ref 527812345678", T.at(2025, 10, 6, 9))
        t.check(CREDIT, 450.0, account = "1234", ref = "527812345678")
        assertTrue(TxnHint.REFUND in t.hints)
    }

    @Test fun interest_credit() {
        val t = txn("VM-SBIINB", "Interest of Rs 1,234.00 has been credited to your A/c XX1234 on 30-09-2025", T.at(2025, 9, 30, 9))
        t.check(CREDIT, 1234.0, account = "1234")
        assertTrue(TxnHint.INTEREST in t.hints)
    }

    @Test fun autopay_debit() {
        val t = txn("VM-ICICIT", "Rs 199.00 debited from your Acct XX123 on 05-Oct-25 towards NETFLIX via UPI AutoPay. UPI Ref 527812345678")
        t.check(DEBIT, 199.0, account = "123", ref = "527812345678")
        assertTrue(TxnHint.AUTOPAY in t.hints)
    }

    @Test fun body_date_from_earlier_day_keeps_that_day() {
        val sms = T.at(2025, 10, 6, 0, 2)
        val t = txn("VM-HDFCBK", "Rs.450.00 debited from a/c **1234 on 05-10-25 to VPA swiggy@axisbank(UPI Ref No 527812345678).", sms)
        assertEquals(T.at(2025, 10, 5, 0, 2), t.timestamp)
    }

    @Test fun unknown_sender_still_parses_samples() {
        txn("", "Rs.450.00 debited from a/c **1234 on 05-10-25 to VPA swiggy@axisbank(UPI Ref No 527812345678).")
            .check(DEBIT, 450.0, account = "1234")
    }

    // ------------------------------------------------------------------ credit card vs bank account (Home's Bank / Cards split)

    @Test fun credit_cards_are_told_apart_from_debit_cards_and_accounts() {
        fun kind(sender: String, body: String) = txn(sender, body).accountKind
        // "Avl Limit" / "Avl Lmt" / "BLOCK CC" / a card-only issuer → credit card
        assertEquals(AccountKind.CREDIT_CARD, kind("VK-ICICIB", "INR 1,234.00 spent using ICICI Bank Card XX5678 on 05-Oct-25 on AMAZON. Avl Limit: INR 1,00,000.00."))
        assertEquals(AccountKind.CREDIT_CARD, kind("AX-AXISBK", "Spent\nCard no. XX5678\nINR 1234.00\n05-10-25 14:23:11\nAMAZON\nAvl Lmt INR 98,766.00\nAxis Bank"))
        assertEquals(AccountKind.CREDIT_CARD, kind("VM-HDFCBK", "Txn Rs.499.00\nOn HDFC Bank Card 5678\nAt NETFLIX\nby UPI 527812345670\nOn 05-10\nSMS BLOCK CC 5678 to 7308080808"))
        assertEquals(AccountKind.CREDIT_CARD, kind("AM-AMEXIN", "Alert: You've spent INR 1,234.00 on your AMEX card ** 12345 at UBER INDIA on 5 October 2025 at 10:20 AM IST."))
        assertEquals(AccountKind.CREDIT_CARD, kind("VK-ICICIB", "Payment of INR 10,000.00 has been received on your ICICI Bank Credit Card Account XX5678 on 05-Oct-25."))
        assertEquals(AccountKind.CREDIT_CARD, kind("VM-KOTAKB", "Rs.1234.00 spent on Kotak Credit Card XX5678 at ZOMATO on 05/10/2025. Avl limit Rs 1,00,000"))
        // "BLOCK DC" / "Debit Card" → debit card (counts with the bank account)
        assertEquals(AccountKind.DEBIT_CARD, kind("JD-HDFCBK-T", "Spent Rs.1234.50 On HDFC Bank Card 5678 At AMAZON On 2025-10-05:14:23:11 Bal Rs.45678.00 SMS BLOCK DC 5678 to 7308080808"))
        // A bank account debit that mentions paying a credit card stays a bank account.
        assertEquals(AccountKind.BANK, kind("VM-HDFCBK", "Rs 5000.00 debited from A/c XX1234 on 05-10-25 towards HDFC Bank Credit Card bill payment"))
        assertEquals(AccountKind.BANK, kind("VM-HDFCBK", "Rs.450.00 debited from a/c **1234 on 05-10-25 to VPA swiggy@axisbank(UPI Ref No 527812345678)."))
    }
}
