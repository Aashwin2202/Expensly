package com.fintrackai.ui.accounts

object AccountsConstants {
    /**
     * Bank-specific card gradients keyed by abbreviation (matches BANK_ABBREV_MAP in AccountsScreenHelpers).
     * Colors derived from each bank's primary brand identity.
     */
    val BANK_CARD_GRADIENTS: Map<String, Pair<Long, Long>> = mapOf(
        // HDFC — deep navy blue
        "HDFC"  to (0xFF003366L to 0xFF004C99L),
        // ICICI — deep crimson-red / maroon-red
        "ICICI" to (0xFF8B0000L to 0xFFCC2200L),
        // SBI — sky blue (Tricolour blue)
        "SBI"   to (0xFF1565C0L to 0xFF42A5F5L),
        // Axis — deep burgundy red to magenta-red
        "AXIS"  to (0xFF97003AL to 0xFFCC0055L),
        // Kotak — deep red-orange to vivid red
        "KOTAK" to (0xFFB31B1BL to 0xFFE53935L),
        // IndusInd — deep teal to green
        "IND"   to (0xFF00574BL to 0xFF00897BL),
        // IDFC First — dark navy to blue
        "IDFC"  to (0xFF0D2C6EL to 0xFF1565C0L),
        // Canara — bottle green
        "CAN"   to (0xFF1B5E20L to 0xFF388E3CL),
        // Bandhan — deep orange-red
        "BDH"   to (0xFFBF360CL to 0xFFE64A19L),
        // Federal — deep blue-purple
        "FED"   to (0xFF1A237EL to 0xFF283593L),
        // HSBC — dark charcoal to brand red
        "HSBC"  to (0xFF1C1C1CL to 0xFFDB0011L),
        // Yes Bank — deep purple-violet
        "YES"   to (0xFF4A148CL to 0xFF7B1FA2L),
        // PNB — deep indigo
        "PNB"   to (0xFF1A237EL to 0xFF3949ABL),
        // Bank of Baroda — orange-saffron
        "BOB"   to (0xFFE65100L to 0xFFFF8F00L),
        // Bank of India — deep navy
        "BOI"   to (0xFF003366L to 0xFF1565C0L),
        // AU Small Finance Bank — deep rose-red
        "AU"    to (0xFFB71C1CL to 0xFFE53935L),
        // Indian Overseas Bank — deep teal-green
        "IOB"   to (0xFF004D40L to 0xFF00897BL),
        // Slice — deep violet-purple
        "SLC"   to (0xFF3D1560L to 0xFF7C3AEDL),
    )

    /** Fallback gradient palette when no bank match found. */
    val CREDIT_CARD_GRADIENT_ARGB_PAIRS: List<Pair<Long, Long>> = listOf(
        0xFF1e3a8aL to 0xFF3b82f6L,
        0xFF7c2d12L to 0xFFdc2626L,
        0xFF065f46L to 0xFF10b981L,
        0xFF581c87L to 0xFFa855f7L,
        0xFF831843L to 0xFFec4899L,
        0xFF0c4a6eL to 0xFF06b6d4L
    )
}
