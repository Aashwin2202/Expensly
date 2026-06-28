package com.fintrackai.domain.sms

/**
 * Maps a TRAI-registered SMS sender address to a canonical bank key.
 *
 * The key is the same string used in [SmsConstants.BANK_NAMES] and pattern comments
 * (e.g. "HDFC", "SBI", "ICICI"). Returns null when the sender is unknown or doesn't
 * map to a bank with dedicated patterns.
 *
 * Resolution order:
 *   1. Strip optional operator prefix (e.g. "VK-" in "VK-HDFCBK")
 *   2. Walk TOKEN_TO_BANK from longest token first (prevents "SBI" matching "SBIBNK")
 *   3. Return first hit, or null
 */
object BankSenderDetector {

    /**
     * Ordered list of (token, bankKey) pairs. Longer/more-specific tokens first so
     * e.g. "SBIBNK" wins over a hypothetical shorter prefix before we reach a short entry.
     */
    private val TOKEN_TO_BANK: List<Pair<String, String>> = listOf(
        // HDFC
        "HDFCBK"    to "HDFC",
        "HDFCCA"    to "HDFC",
        "HDFCCRD"   to "HDFC",
        "HDFCSMS"   to "HDFC",
        "HDFCUPI"   to "HDFC",
        "ATMHDF"    to "HDFC",
        // ICICI
        "ICICIB"    to "ICICI",
        "ICICIO"    to "ICICI",
        "ICICIC"    to "ICICI",
        "ICICID"    to "ICICI",
        "ICICIJ"    to "ICICI",
        "ICICIT"    to "ICICI",
        "ICICIK"    to "ICICI",
        "ICICIP"    to "ICICI",
        "ICICIUPI"  to "ICICI",
        "ATMICI"    to "ICICI",
        // SBI
        "SBIBNK"    to "SBI",
        "SBIINB"    to "SBI",
        "SBICRD"    to "SBI",
        "SBIPSG"    to "SBI",
        "SBIPAY"    to "SBI",
        "SBICARDS"  to "SBI",
        "SBIPRE"    to "SBI",
        "SBIRGV"    to "SBI",
        "SBIMSS"    to "SBI",
        "SBIUPI"    to "SBI",
        "ATMSBI"    to "SBI",
        // Axis
        "AXISBK"    to "AXIS",
        "AXISCARD"  to "AXIS",
        "AXISOL"    to "AXIS",
        "AXISUPI"   to "AXIS",
        "ATMAXI"    to "AXIS",
        // Kotak
        "KOTAKB"    to "KOTAK",
        "KOTAKBK"   to "KOTAK",
        "KOTAKP"    to "KOTAK",
        "KTKBNK"    to "KOTAK",
        "KOTAKUPI"  to "KOTAK",
        "ATMKTK"    to "KOTAK",
        // YES Bank
        "YESBNK"    to "YES",
        "YESBANK"   to "YES",
        "YESBKG"    to "YES",
        "YESUPI"    to "YES",
        // IDFC FIRST
        "IDFCFB"    to "IDFC",
        "IDFCFBK"   to "IDFC",
        "IDFCFI"    to "IDFC",
        "IDFCFY"    to "IDFC",
        "IDFCUPI"   to "IDFC",
        // IDBI
        "IDBIBK"    to "IDBI",
        "IDBIBNK"   to "IDBI",
        "IDBIOT"    to "IDBI",
        "IDBIUPI"   to "IDBI",
        "ATMIDB"    to "IDBI",
        // PNB
        "PNBINB"    to "PNB",
        "PNBSMS"    to "PNB",
        "PNBANK"    to "PNB",
        "PNBUPI"    to "PNB",
        "ATMPNB"    to "PNB",
        // Bank of India
        "BOIIND"    to "BOI",
        "BOISMS"    to "BOI",
        "BOIBNK"    to "BOI",
        "BOIUPI"    to "BOI",
        "ATMBOI"    to "BOI",
        // Bank of Baroda
        "BARODAW"   to "BOB",
        "BOBTXN"    to "BOB",
        "BOBSMS"    to "BOB",
        "BOBCARD"   to "BOB",
        "BOBUPI"    to "BOB",
        "ATMBOB"    to "BOB",
        // Canara
        "CANBNK"    to "CANARA",
        "CANARA"    to "CANARA",
        "CANUPI"    to "CANARA",
        "ATMCAN"    to "CANARA",
        // Indian Bank
        "INDBNK"    to "INDIAN BANK",
        "ATMINB"    to "INDIAN BANK",
        // IndusInd
        "INDUSI"    to "INDUSIND",
        "INDUPI"    to "INDUSIND",
        // Federal
        "FEDBNK"    to "FEDERAL",
        "FEDUPI"    to "FEDERAL",
        "ATMFED"    to "FEDERAL",
        // RBL
        "RBLBNK"    to "RBL",
        "RBLCRD"    to "RBL",
        "RBLBANK"   to "RBL",
        "RBLUPI"    to "RBL",
        // Bandhan
        "BANDHN"    to "BANDHAN",
        "BDBL"      to "BANDHAN",
        // AU Small Finance
        "AUBLUK"    to "AU SMALL",
        "AUIBNK"    to "AU SMALL",
        "AUBANK"    to "AU SMALL",
        "AUUPI"     to "AU SMALL",
        // South Indian Bank
        "SIBSMS"    to "SOUTH INDIAN BANK",
        "SOUIBNK"   to "SOUTH INDIAN BANK",
        "SIBNET"    to "SOUTH INDIAN BANK",
        "SIBMOB"    to "SOUTH INDIAN BANK",
        "SIBPAY"    to "SOUTH INDIAN BANK",
        "SIBUPI"    to "SOUTH INDIAN BANK",
        "SIBIBL"    to "SOUTH INDIAN BANK",
        "SIBOTB"    to "SOUTH INDIAN BANK",
        // Karnataka Bank
        "KARNAT"    to "KARNATAKA",
        // Utkarsh Small Finance
        "UTKDBK"    to "UTKARSH",
        "UTKSFB"    to "UTKARSH",
        "UTKSBK"    to "UTKARSH",
        "UTKMIS"    to "UTKARSH",
        // Union Bank
        "UNIONQ"    to "UNION",
        "UNIONB"    to "UNION",
        "UBIIBK"    to "UNION",
        // IOB
        "IOBCHN"    to "IOB",
        "IOBANK"    to "IOB",
        "IOBUPI"    to "IOB",
        // DBS
        "DBSBNK"    to "DBS",
        "DBSINB"    to "DBS",
        "DBSSMS"    to "DBS",
        // Citi
        "CITIBK"    to "CITI",
        "CITIBR"    to "CITI",
        // HSBC
        "HSBCIN"    to "HSBC",
        "HSBCBB"    to "HSBC",
        // Jana Small Finance
        "JANABK"    to "JANA",
        "JANATR"    to "JANA",
        "JANATX"    to "JANA",
        // Equitas
        "ESFBNK"    to "EQUITAS",
        "EQUITAS"   to "EQUITAS",
        // SLC Bank
        "SLCBNK"    to "SLC BANK"
    )

    /**
     * Returns the canonical bank key for [senderAddress], or null if unknown.
     * The returned key matches entries in [SmsConstants.BANK_NAMES].
     */
    fun detect(senderAddress: String): String? {
        val key = SmsTransactionalSenderCodes.compactSenderKey(senderAddress)
        for ((token, bank) in TOKEN_TO_BANK) {
            if (key.contains(token)) return bank
        }
        return null
    }
}
