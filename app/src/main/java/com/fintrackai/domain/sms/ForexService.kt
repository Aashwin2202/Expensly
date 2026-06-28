package com.fintrackai.domain.sms

object ForexService {
    private const val TARGET = "INR"
    private const val RATES_API = "https://api.frankfurter.app/latest"

    private val fallbacks = mapOf(
        "USD" to 83.0, "EUR" to 90.0, "GBP" to 105.0, "JPY" to 0.55,
        "AUD" to 54.0, "CAD" to 60.0, "CHF" to 94.0, "SGD" to 62.0, "AED" to 22.6,
        "THB" to 2.3, "MYR" to 18.0, "HKD" to 10.7, "BHD" to 220.0,
        "QAR" to 22.8, "KWD" to 270.0, "OMR" to 216.0, "SAR" to 22.1,
        "CNY" to 11.5, "NZD" to 50.0, "ZAR" to 4.5
    )

    private val validCodes = setOf("USD", "EUR", "GBP", "JPY", "AUD", "CAD", "CHF", "SGD", "AED",
        "THB", "MYR", "HKD", "BHD", "QAR", "KWD", "OMR", "SAR", "CNY", "NZD", "ZAR")

    fun normalizeCurrencyCode(raw: String?): String {
        if (raw.isNullOrBlank()) return "INR"
        val s = raw.trim().uppercase().replace(".", "")
        if (s == "RS" || s == "INR" || s == "₹") return "INR"
        return if (validCodes.contains(s)) s else if (s.length == 3) s else "INR"
    }

    fun convertToINR(amount: Double, fromCurrency: String): Double {
        val code = normalizeCurrencyCode(fromCurrency)
        if (code == "INR") return Math.round(amount * 100.0) / 100.0
        try {
            val url = java.net.URL("$RATES_API?from=$code&to=$TARGET")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            if (connection.responseCode != 200) throw Exception("Forex API error")
            val response = connection.inputStream.bufferedReader().readText()
            val rateMatch = Regex("\"$TARGET\"\\s*:\\s*(\\d+\\.?\\d*)").find(response)
            val rate = rateMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: throw Exception("Invalid rate")
            return Math.round(amount * rate * 100.0) / 100.0
        } catch (_: Exception) {
            val fb = fallbacks[code]
            if (fb != null) return Math.round(amount * fb * 100.0) / 100.0
            // Unknown currency with no fallback — return raw amount so the transaction is still recorded
            return Math.round(amount * 100.0) / 100.0
        }
    }
}
