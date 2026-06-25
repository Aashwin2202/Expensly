package com.fintrackai

import com.fintrackai.domain.model.SmsMessage
import com.fintrackai.domain.sms.SmsFilter
import com.fintrackai.domain.sms.TransactionExtractor
import org.junit.Assert.*
import org.junit.Test

class SmsProcessingTest {

    @Test
    fun `filter identifies bank transaction SMS`() {
        val messages = listOf(
            SmsMessage("Rs.500 debited from your HDFC Bank A/C XX1234", 1700000000000, "AX-HDFCBK"),
            SmsMessage("Your OTP is 123456. Do not share with anyone.", 1700000000000, "AX-HDFCBK"),
            SmsMessage("Hello, how are you?", 1700000000000, "FRIEND")
        )
        val filtered = SmsFilter.filterTransactionSMS(messages)
        assertEquals(1, filtered.size)
        assertTrue(filtered[0].body.contains("debited"))
    }

    @Test
    fun `filter excludes HDFC mutual fund and other non-bank HDFC senders`() {
        val messages = listOf(
            SmsMessage(
                "Rs.1000 debited for purchase in HDFC MF scheme",
                1700000000000,
                "VM-HDFCMF"
            ),
            SmsMessage(
                "Units allotted. Rs.5000 debited from your bank",
                1700000000000,
                "HDFCMF"
            )
        )
        val filtered = SmsFilter.filterTransactionSMS(messages)
        assertEquals(0, filtered.size)
    }

    @Test
    fun `filter excludes unknown sender even if body looks like transaction`() {
        val messages = listOf(
            SmsMessage("Rs.500 debited from your A/C XX1234 on 01/01/2024", 1700000000000, "RANDOM")
        )
        assertEquals(0, SmsFilter.filterTransactionSMS(messages).size)
    }

    @Test
    fun `extract transaction data from debit SMS`() {
        val sms = "Rs.1500.00 debited from your A/C XX5678 on 15/01/2024 to Swiggy Ref 123456"
        val result = TransactionExtractor.extractTransactionData(sms)
        assertNotNull(result.amount)
        assertEquals("debit", result.type)
        assertTrue((result.amount ?: 0.0) > 0)
    }

    @Test
    fun `extract transaction data from credit SMS`() {
        val sms = "Credit Alert! Rs.25,000.00 credited to your A/C XX1234 on 01/02/2024"
        val result = TransactionExtractor.extractTransactionData(sms)
        assertNotNull(result.amount)
        assertEquals("credit", result.type)
    }

    @Test
    fun `normalizeDate handles various formats`() {
        assertEquals("2024-01-15", TransactionExtractor.normalizeDate("15/01/2024"))
        assertEquals("2024-01-15", TransactionExtractor.normalizeDate("15-01-2024"))
        assertEquals("2024-01-15", TransactionExtractor.normalizeDate("2024-01-15"))
    }

    @Test
    fun `filter excludes credit card statement messages`() {
        val messages = listOf(
            SmsMessage(
                "outstanding of Rs.15000 on your credit card due on 15/01/2024. Min Amount Due Rs.750. Please ignore if already paid",
                1700000000000,
                "AX-SBICRD"
            )
        )
        val filtered = SmsFilter.filterTransactionSMS(messages)
        assertEquals(0, filtered.size)
    }

    @Test
    fun `filter excludes NACH mandate auto-pay received for processing notification`() {
        val messages = listOf(
            SmsMessage(
                "Auto Pay (HDFC Bank NACH Mandate): Rs. 100000.00 UMRN: HDFC00000234823 To: SBI Card Freq MNTH received today for processing.",
                1700000000000,
                "AX-HDFCBK"
            )
        )
        val filtered = SmsFilter.filterTransactionSMS(messages)
        assertEquals(0, filtered.size)
    }

    @Test
    fun `filter excludes credit card bill payment reminder with ignore if paid`() {
        val messages = listOf(
            SmsMessage(
                "pay total Rs 2401.42 for ICICI Bank Credit Card 0000, missed on 02-Apr-25. Ignore if paid.",
                1700000000000,
                "AX-ICICIB"
            )
        )
        val filtered = SmsFilter.filterTransactionSMS(messages)
        assertEquals(0, filtered.size)
    }

    @Test
    fun `extract 4-digit card numbers`() {
        val text = "Card ending 5678 debited Rs.500"
        val digits = TransactionExtractor.extract4DigitNumbers(text)
        assertTrue(digits.contains("5678"))
    }

    @Test
    fun `cleanMerchantName removes suffixes`() {
        assertEquals("Swiggy", TransactionExtractor.cleanMerchantName("Swiggy Pvt Ltd"))
        assertEquals("Amazon", TransactionExtractor.cleanMerchantName("Amazon Inc."))
    }

    @Test
    fun `extract IMPS transfer with A_c to account`() {
        val sms = """Money Sent-INR 30,200.00
From HDFC Bank A/c XX5988 on 06-12-24
To A/c xxxxxxxxxxxx7130
IMPS Ref-434113143793
Avl bal:INR 1,09,429.38"""
        val result = TransactionExtractor.extractTransactionData(sms)
        assertEquals("debit", result.type)
        assertEquals(30200.0, result.amount, 0.01)
        assertNotNull("Merchant should not be null", result.merchant)
        assertTrue("Merchant should contain IMPS or last 4 digits, not 'A/c'",
            result.merchant?.contains("IMPS", ignoreCase = true) == true ||
            result.merchant?.contains("7130") == true)
        assertFalse("Merchant should not contain 'A/c'", result.merchant?.contains("A/c") == true)
    }

    @Test
    fun `extract HDFC UPI debit to a_c shows UPI Transfer with last4`() {
        val sms = "HDFC Bank:Rs. 24500.00 debited from a/c *5988 on 02/06/26 to a/c **5187 (UPI Ref No. 124114379479). Not you? Call on 18002586161 to report"
        val result = TransactionExtractor.extractTransactionData(sms)
        assertEquals("debit", result.type)
        assertEquals(24500.0, result.amount, 0.01)
        assertTrue("Merchant should be UPI Transfer (5187), got: ${result.merchant}",
            result.merchant == "UPI Transfer (5187)")
    }

    @Test
    fun `extract Axis Bank foreign currency card spend`() {
        val sms = "Spent USD 23.6\nAxis Bank Card no. XX2912\n23-04-26 23:51:41 IST\nCURSOR, AI\nAvl Limit: INR 194185.55\nNot you? SMS BLOCK 2912 to 919951860002"
        val result = TransactionExtractor.extractTransactionData(sms)
        assertEquals("debit", result.type)
        assertEquals(23.6, result.amount ?: 0.0, 0.01)
        assertEquals("USD", result.currency)
        assertEquals("Cursor, Ai", result.merchant)
        assertEquals("Axis Bank Card 2912", result.fullAccount)
    }

    @Test
    fun `extract UPI transfer with VPA strips domain`() {
        val sms = """Sent Rs.4500.00
From HDFC Bank A/C *5988
To laxmibhambak3-1@okaxis
On 01/06/26
Ref 615204872146
Not You?
Call 18002586161/SMS BLOCK UPI to 7308080808"""
        val result = TransactionExtractor.extractTransactionData(sms)
        assertEquals("debit", result.type)
        assertEquals(4500.0, result.amount, 0.01)
        assertEquals("Laxmibhambak3-1", result.merchant, "Merchant should be VPA name without @okaxis domain")
    }
}
