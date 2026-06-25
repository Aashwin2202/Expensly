package com.fintrackai.domain.sms

import java.util.Locale

/**
 * Known transactional SMS header tokens (India): bank debit/credit alerts and card spend alerts.
 *
 * Senders often appear as `XY-HDFCBK` (TRAI operator prefix + entity). We strip the optional
 * leading `^[A-Z]{2,3}-` segment, then match against this allowlist so short roots like `HDFC`
 * are **not** used (those match mutual fund / insurance headers such as `HDFCMF`).
 *
 * Curated from public TRAI header documentation, bank SMS samples, and common registered headers;
 * extend this list if your issuer uses a header not matched here.
 *
 * @see <a href="https://www.trai.gov.in/release-publication/consultation-paper-telecom-commercial-communications-customer-preference-regulation">TRAI TCCCPR</a>
 */
object SmsTransactionalSenderCodes {

    /**
     * Tokens must appear in the compacted sender (letters/digits only, after operator prefix strip).
     * Prefer length ≥ 5 to avoid accidental hits.
     */
    val ALLOWED_TRANSACTIONAL_SENDER_TOKENS: Set<String> = setOf(
        // HDFC Bank (not HDFCMF / mutual fund, not HDFCLI, etc.)
        "HDFCBK", "HDFCCA", "HDFCCRD", "HDFCSMS", "HDFCUPI", "ATMHDF",
        // ICICI Bank
        "ICICIB", "ICICIO", "ICICIC", "ICICID", "ICICIJ", "ICICIT", "ICICIK", "ICICIP", "ICICIUPI", "ATMICI",
        // State Bank of India
        "SBIBNK", "SBIINB", "SBICRD", "SBIPSG", "SBIPAY", "SBICARDS", "SBIPRE", "SBIRGV", "SBIMSS",
        "SBIUPI", "ATMSBI",
        // Axis Bank
        "AXISBK", "AXISCARD", "AXISBK0", "AXISOL", "AXISUPI", "ATMAXI",
        // Kotak
        "KOTAKB", "KOTAKBK", "KOTAKP", "KTKBNK", "KOTAKUPI", "ATMKTK",
        // YES Bank
        "YESBNK", "YESBANK", "YESBKG", "YESUPI",
        // IDFC FIRST Bank
        "IDFCFB", "IDFCFBK", "IDFCFI", "IDFCFY", "IDFCUPI",
        // IDBI
        "IDBIBK", "IDBIBNK", "IDBIOT", "IDBIUPI", "ATMIDB",
        // Punjab National / merged subsidiaries (Oriental, OBC legacy headers still seen)
        "PNBINB", "PNBSMS", "PNBANK", "PNBUPI", "ATMPNB", "ORIENTAL", "OBCBNK", "UNIONQ", "UNIONB", "UBIIBK",
        // Bank of India / Baroda / Canara / IOB
        "BOIIND", "BOISMS", "BOIBNK", "BOIUPI", "ATMBOI",
        "BARODAW", "BOBTXN", "BOBSMS", "BOBCARD", "BOBUPI", "ATMBOB",
        "CANBNK", "CANARA", "CANUPI", "ATMCAN",
        "IOBCHN", "IOBANK", "IOBUPI",
        // Indian / IndusInd / Federal / RBL / Bandhan / AU
        "INDBNK", "INDUSI", "INDUPI", "ATMINB",
        "FEDBNK", "FEDUPI", "ATMFED",
        "RBLBNK", "RBLCRD", "RBLBANK", "RBLUPI",
        "BANDHN", "BDBL", "AUBLUK", "AUIBNK", "AUBANK", "AUUPI",
        // J&K / Karnataka / Tamilnad Mercantile / South Indian Bank
        "JKBANK", "JKBANQ", "KTKBNK", "KARNAT", "TMBL", "TMBANK",
        "SIBSMS", "SOUIBNK", "SIBNET", "SIBMOB", "SIBPAY", "SIBUPI", "SIBIBL", "SIBOTB",
        // Karur Vysya / Catholic Syrian / City Union / DCB
        "KVBLIN", "KVBANK", "KVBSMS", "CSBBNK", "CSBMOB", "CUBANK", "CUBSMS", "DCBBNK", "DCBSMS",
        // Lakshmi Vilas Bank (merged into DBS India, Nov 2020)
        "LVBANK", "LVBNKL",
        // Dhanlaxmi Bank
        "DBNKMS", "DHANLA", "DHANLX",
        // Kerala Gramin Bank / Kerala Bank (co-operative umbrella)
        "KRGBNK", "KLBNKS",
        // Tamilnad Mercantile Bank (additional registrations)
        "TMBBNK", "TMBNKS",
        // Pandyan Grama Bank
        "PGBSMS",
        // Vijaya Bank (merged into Bank of Baroda, April 2019)
        "VIJBNK", "VIJAYB",
        // UCO / Corporation / Central / Andhra (legacy)
        "UCOBNK", "UCOBAN", "CORPBNK", "CORPBK", "CENTBK", "ANDBNK",
        // DBS / Citi / HSBC / Standard Chartered / Deutsche / Barclays / BNP / Woori
        "DBSBNK", "DBSINB", "DBSSMS", "CITIBK", "CITIBR", "HSBCIN", "HSBCBB", "SCBANK", "SCBL",
        "DEUTBK", "DBINBK", "BARCIN", "BNPPAR", "WOORIB",
        // American Express (card)
        "AMEXIN", "AMEXMR",
        // RazorpayX / Cashfree / Instamojo (neo-banking payouts — transactional debit alerts)
        "RZRPAY", "CASHFR", "INSTAM",
        // India Post Payments Bank
        "IPBMSG", "IPPBKL",
        // Equitas / small finance
        "ESFBNK", "EQUITAS",
        // Utkarsh Small Finance Bank
        "UTKDBK", "UTKSFB", "UTKSBK", "UTKMIS",
        // Jana Small Finance Bank
        "JANABK", "JANATR", "JANATX",
        // Ujjivan Small Finance Bank
        "UJJIVN", "UJVNBP",
        // ESAF Small Finance Bank (additional DLT sub-registrations)
        "ESAFSF", "ESAFTR", "ESAFPB", "EASFBT", "ESAFIT", "ESAFOT", "ESAFPR", "ESFUAT",
        // Suryoday Small Finance Bank
        "SMFLTD", "SRYLTD", "SSBFNK",
        // Fincare Small Finance Bank (historical; merged into AU SFB in 2023)
        "FNCARE",
        // Airtel Payments Bank
        "AIRBNK", "AIRBSE", "AIRBSI",
        // Fino Payments Bank
        "FINOBK", "FINOPB",
        // NSDL Payments Bank
        "NSDLPB",
        // Paytm Payments Bank (historical; RBI licence cancelled March 2024)
        "PAYTMB",
        // SVC Bank (Shamrao Vithal Co-operative Bank)
        "SVCBNK", "SVCTXN", "SVCBSM",
        // Abhyudaya Co-operative Bank
        "ACBLBK", "ABHYUD",
        // Saraswat Co-operative Bank
        "SARASW", "SARBNK",
        // Cosmos Co-operative Bank
        "COSMOB", "COSMCO",
        // TJSB Sahakari Bank (Thane Janata Sahakari Bank)
        "TJSBSB", "TJSBST",
        // Jalgaon Janata Sahakari Bank
        "JJSBNK",
        // Bassein Catholic Co-operative Bank
        "BCCBMS",
        // Greater Bombay Co-operative Bank
        "GBCBNK",
        // Zoroastrian Co-operative Bank
        "ZCBBNK",
        // Bank of Maharashtra
        "MAHABK", "MAHBNK", "MAHBKS",
        // Nainital Bank
        "NTLBNK", "NNTBNK",
        // North East Small Finance Bank
        "NESFBN", "NESFBK",
        // Capital Small Finance Bank
        "CAPSFB", "CAPBNK",
        // Shivalik Small Finance Bank
        "SHVSFB", "SHVBNK",
        // Unity Small Finance Bank
        "UNITFB", "UNYSFB",
        // Merged/defunct banks — historical headers still seen on old SMS threads
        // Syndicate Bank (merged into Canara, March 2020)
        "SYNBNK", "SYNDBT", "SYNDCT", "SYNDPG", "SYNIBD", "SYNMOB", "SYNRBD", "SYNRUP", "SYNTAB",
        // Dena Bank (merged into Bank of Baroda, April 2019)
        "DENABK",
        // Allahabad Bank (merged into Indian Bank, April 2020)
        "ALBANK",
        // United Bank of India (merged into PNB, April 2020)
        "UBIBNK", "UBISM", "UBKGBK"
    )

    fun normalizeOperatorPrefix(address: String): String {
        val t = address.uppercase(Locale.US).trim()
        return t.replaceFirst(Regex("""^[A-Z]{2,3}-"""), "")
    }

    /** Alphanumeric-only key for allowlist checks. */
    fun compactSenderKey(address: String): String =
        normalizeOperatorPrefix(address).replace(Regex("""[^A-Z0-9]"""), "")

    /**
     * True if [address] matches a known transactional banking / card-alert header pattern.
     */
    fun isAllowedSender(address: String): Boolean {
        val key = compactSenderKey(address)
        if (key.length < 4) return false
        return ALLOWED_TRANSACTIONAL_SENDER_TOKENS.any { token -> key.contains(token) }
    }

    /** Card spend / card-account alerts often include CRD or CARD in the registered header. */
    fun isCreditCardTransactionalSender(address: String): Boolean {
        val u = address.uppercase(Locale.US)
        return isAllowedSender(address) && (u.contains("CRD") || u.contains("CARD"))
    }
}
