package com.fintrackai

import com.fintrackai.domain.sms.SmsAnonymizer
import org.junit.Assert.*
import org.junit.Test

class SmsAnonymizerTest {

    // ── Canonical acceptance test ────────────────────────────────────────────

    @Test
    fun `toSkeleton canonical bank SMS`() {
        // Currency prefix preserved; account keyword preserved; merchant not detected
        // because MERCHANT_PATTERNS stop at '/' in a/c — accepted limitation
        val input = "Sent Rs. 1,200.00 to Swiggy food from A/c 5678 on 04/04/2026 Ref 12345"
        val s = SmsAnonymizer.toSkeleton(input)
        assertTrue(s.contains("rs."))          // currency prefix preserved
        assertTrue(s.contains("<AMOUNT>"))
        assertFalse(s.contains("1200") || s.contains("1,200"))
        assertTrue(s.contains("a/c"))          // account keyword preserved
        assertTrue(s.contains("<ACCOUNT>"))
        assertFalse(s.contains("5678"))
        assertTrue(s.contains("<DATE>"))
        assertFalse(s.contains("2026"))
        assertTrue(s.contains("<REF>"))
        assertFalse(s.contains("12345"))
    }

    // ── Amount (currency prefix stays, number masked) ────────────────────────

    @Test
    fun `toSkeleton preserves Rs prefix and masks amount`() {
        val s = SmsAnonymizer.toSkeleton("Rs.500 debited")
        assertTrue(s.startsWith("rs."))
        assertTrue(s.contains("<AMOUNT>"))
        assertFalse(s.contains("500"))
    }

    @Test
    fun `toSkeleton preserves INR prefix and masks amount`() {
        val s = SmsAnonymizer.toSkeleton("INR 2,500.00 credited to account")
        assertTrue(s.contains("inr"))
        assertTrue(s.contains("<AMOUNT>"))
        assertFalse(s.contains("2500") || s.contains("2,500"))
    }

    @Test
    fun `toSkeleton preserves rupee symbol and masks amount`() {
        val s = SmsAnonymizer.toSkeleton("₹1000 spent at merchant")
        assertTrue(s.contains("₹"))
        assertTrue(s.contains("<AMOUNT>"))
        assertFalse(s.contains("1000"))
    }

    @Test
    fun `toSkeleton masks amount with commas`() {
        val s = SmsAnonymizer.toSkeleton("Rs.1,00,000.00 transferred")
        assertFalse(s.any { it.isDigit() })
        assertTrue(s.contains("<AMOUNT>"))
    }

    // ── Bank name ────────────────────────────────────────────────────────────

    @Test
    fun `toSkeleton replaces HDFC with BANK`() {
        val s = SmsAnonymizer.toSkeleton("HDFC Bank alert Rs.100 debited")
        assertFalse(s.contains("hdfc", ignoreCase = true))
        assertTrue(s.contains("<BANK>"))
    }

    @Test
    fun `toSkeleton replaces ICICI with BANK`() {
        val s = SmsAnonymizer.toSkeleton("Your ICICI account debited")
        assertTrue(s.contains("<BANK>"))
    }

    // ── Date ─────────────────────────────────────────────────────────────────

    @Test
    fun `toSkeleton replaces dd slash mm slash yyyy`() {
        val s = SmsAnonymizer.toSkeleton("transaction on 15/01/2026")
        assertFalse(s.contains("15") || s.contains("2026"))
        assertTrue(s.contains("<DATE>"))
    }

    @Test
    fun `toSkeleton replaces dd Mon yyyy`() {
        val s = SmsAnonymizer.toSkeleton("on 01-Jan-2026")
        assertFalse(s.contains("2026"))
        assertTrue(s.contains("<DATE>"))
    }

    @Test
    fun `toSkeleton replaces ISO date`() {
        val s = SmsAnonymizer.toSkeleton("2026-04-05 transfer processed")
        assertFalse(s.contains("2026"))
        assertTrue(s.contains("<DATE>"))
    }

    // ── Time ─────────────────────────────────────────────────────────────────

    @Test
    fun `toSkeleton replaces HH colon MM time`() {
        val s = SmsAnonymizer.toSkeleton("at 14:30 today")
        assertFalse(s.contains("14:30"))
        assertTrue(s.contains("<TIME>"))
    }

    // ── Account (keyword stays, number masked) ───────────────────────────────

    @Test
    fun `toSkeleton preserves a slash c keyword and masks number`() {
        val s = SmsAnonymizer.toSkeleton("debited from A/c 5678")
        assertTrue(s.contains("a/c"))
        assertFalse(s.contains("5678"))
        assertTrue(s.contains("<ACCOUNT>"))
    }

    @Test
    fun `toSkeleton preserves account keyword and masks number`() {
        val s = SmsAnonymizer.toSkeleton("your account 123456789 balance")
        assertTrue(s.contains("account"))
        assertFalse(s.contains("123456789"))
        assertTrue(s.contains("<ACCOUNT>"))
    }

    @Test
    fun `toSkeleton preserves card ending keyword and masks number`() {
        val s = SmsAnonymizer.toSkeleton("card ending 9012 was debited")
        assertTrue(s.contains("card ending"))
        assertFalse(s.contains("9012"))
        assertTrue(s.contains("<ACCOUNT>"))
    }

    @Test
    fun `toSkeleton preserves credit card keyword and masks number`() {
        val s = SmsAnonymizer.toSkeleton("spent on credit card 4321")
        assertTrue(s.contains("credit card"))
        assertFalse(s.contains("4321"))
        assertTrue(s.contains("<ACCOUNT>"))
    }

    // ── Reference ────────────────────────────────────────────────────────────

    @Test
    fun `toSkeleton replaces Ref number`() {
        val s = SmsAnonymizer.toSkeleton("Ref 12310944 confirmed")
        assertFalse(s.contains("12310944"))
        assertTrue(s.contains("<REF>"))
    }

    @Test
    fun `toSkeleton replaces UTR number`() {
        val s = SmsAnonymizer.toSkeleton("UTR 998877665544 processed")
        assertFalse(s.contains("998877665544"))
        assertTrue(s.contains("<REF>"))
    }

    @Test
    fun `toSkeleton replaces UPI Ref number`() {
        val s = SmsAnonymizer.toSkeleton("UPI Ref 112233445566 done")
        assertFalse(s.contains("112233445566"))
        assertTrue(s.contains("<REF>"))
    }

    // ── VPA ──────────────────────────────────────────────────────────────────

    @Test
    fun `toSkeleton replaces UPI VPA`() {
        val s = SmsAnonymizer.toSkeleton("sent to user@okhdfc via UPI")
        assertFalse(s.contains("user@okhdfc"))
        assertTrue(s.contains("<VPA>"))
    }

    // ── Merchant (via SmsConstants.MERCHANT_PATTERNS) ────────────────────────

    @Test
    fun `toSkeleton replaces merchant after to anchor`() {
        val s = SmsAnonymizer.toSkeleton("paid to Amazon India on 01/01/2026")
        assertFalse(s.contains("amazon", ignoreCase = true))
        assertTrue(s.contains("to <MERCHANT>"))
    }

    @Test
    fun `toSkeleton replaces single-word merchant`() {
        val s = SmsAnonymizer.toSkeleton("Rs.200 paid to Zomato")
        assertFalse(s.contains("zomato", ignoreCase = true))
        assertTrue(s.contains("to <MERCHANT>"))
    }

    @Test
    fun `toSkeleton replaces merchant at anchor`() {
        val s = SmsAnonymizer.toSkeleton("INR 1500 spent at BigBasket on 01/01/2026")
        assertFalse(s.contains("bigbasket", ignoreCase = true))
        assertTrue(s.contains("at <MERCHANT>"))
    }

    @Test
    fun `toSkeleton produces single MERCHANT token`() {
        val s = SmsAnonymizer.toSkeleton("Rs.500 to Merchant Name With Extra Words on 01/01/2026")
        assertEquals(1, s.split("<MERCHANT>").size - 1)
    }

    @Test
    fun `toSkeleton does not replace a slash c as merchant`() {
        val s = SmsAnonymizer.toSkeleton("Rs.100 debited from A/c 1234 on 01/01/2026")
        assertFalse(s.contains("<MERCHANT>"))
        assertTrue(s.contains("<ACCOUNT>"))
    }

    // ── No raw numbers guarantee ─────────────────────────────────────────────

    @Test
    fun `skeleton contains no raw digits`() {
        val inputs = listOf(
            "Sent Rs. 1,200.00 to Swiggy food from A/c 5678 on 04/04/2026 Ref 12345",
            "Rs.500 debited from your HDFC Bank A/C XX1234 on 15/01/2024 to abc@upi Ref 999",
            "Credit Alert! Rs.25,000.00 credited to your A/C XX1234 on 01/02/2024",
            "INR 1500 spent on credit card 4321 at BigBasket on 12-Mar-2026 at 10:30 AM"
        )
        for (input in inputs) {
            val skeleton = SmsAnonymizer.toSkeleton(input)
            assertFalse(
                "Skeleton still contains digits: $skeleton",
                skeleton.contains(Regex("""\d"""))
            )
        }
    }

    // ── Whitespace & idempotence ──────────────────────────────────────────────

    @Test
    fun `toSkeleton normalises multiple spaces`() {
        val s = SmsAnonymizer.toSkeleton("Rs.500   debited   from   your   account")
        assertFalse(s.contains("  "))
    }

    @Test
    fun `same SMS always produces same skeleton`() {
        val input = "Rs.1500 debited from A/c 9876 to merchant@upi Ref 77889900 on 01/01/2026"
        assertEquals(SmsAnonymizer.toSkeleton(input), SmsAnonymizer.toSkeleton(input))
    }

    @Test
    fun `semantically identical SMS with different amounts produce same skeleton`() {
        val s1 = SmsAnonymizer.toSkeleton("Rs.500 debited from A/c 1234 on 01/01/2026 Ref 111")
        val s2 = SmsAnonymizer.toSkeleton("Rs.9999 debited from A/c 5678 on 15/03/2026 Ref 999")
        assertEquals(s1, s2)
    }
}
