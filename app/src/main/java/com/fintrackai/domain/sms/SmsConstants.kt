package com.fintrackai.domain.sms

object SmsConstants {
    // Each entry is matched as a whole word (word-boundary anchored) in SmsFilter.
    // Keep entries lowercase; SmsFilter lowercases the body before matching.
    val TRANSACTION_KEYWORDS = listOf(
        "debited", "credited", "withdrawn", "withdrawal", "transferred",
        "purchase", "sent", "spent", "paid", "received", "deposited", "executed", "loaded",
        "deducted"
    )

    // Compiled as whole-word patterns to avoid false positives like "consent" matching "sent".
    val TRANSACTION_KEYWORD_PATTERNS: List<Regex> by lazy {
        TRANSACTION_KEYWORDS.map { kw -> Regex("""\b${Regex.escape(kw)}\b""", RegexOption.IGNORE_CASE) }
    }
    
    val FRAUD_CONSENT_PATTERNS = listOf(
        Regex("""require\s+(?:your\s+)?consent\b""", RegexOption.IGNORE_CASE),
        Regex("""\bfunds?\b.{0,60}\bavailable\b.{0,60}\b(?:disbursement|disburse|claim|consent|release)\b""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
        Regex("""\b(?:disbursement|disburse)\b.{0,80}\b(?:consent|approve|click|link)\b""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
    )

    val PROMOTIONAL_PATTERNS = listOf(
        // "Earn N / NX points" — rewards offer
        Regex("""\bearn\b.{0,40}\bpoints?\b""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
        // "T&C" or "T&C-" — terms & conditions footnote, absent from real alerts
        Regex("""\bT&C\b""", RegexOption.IGNORE_CASE),
        // "Offer valid till / valid from" — promotional validity window
        Regex("""\boffer\s+valid\b""", RegexOption.IGNORE_CASE),
        // "cashback on transactions" / "get cashback" — cashback promo, not a credit alert
        Regex("""\bget\s+(?:\d+%\s+)?cashback\b""", RegexOption.IGNORE_CASE),
    )

    val FAILED_TRANSACTION_KEYWORDS = listOf(
        "declined", "failed", "not successful", "unsuccessful", "could not be processed",
        "transaction failed", "payment failed", "payment declined", "insufficient funds",
        "insufficient balance", "transaction unsuccessful", "could not complete"
    )

    // Patterns that indicate a future/pending transaction, not a completed one.
    // These should be excluded even if transaction keywords are present.
    val PENDING_TRANSACTION_PATTERNS = listOf(
        Regex("""is\s+scheduled\s+on\b""", RegexOption.IGNORE_CASE),
        Regex("""(?:debit|payment)\s+(?:of\s+)?(?:Rs\.?|INR|₹)\s*[\d,]+(?:\.\d{2})?\s+is\s+scheduled""", RegexOption.IGNORE_CASE),
        Regex("""auto\s*pay\s+for\s+.+?is\s+scheduled""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
        Regex("""(?:please\s+)?(?:ensure|maintain)\s+sufficient\s+balance""", RegexOption.IGNORE_CASE),
        Regex("""(?:shall|will)\s+be\s+auto[\s-]?debited\b""", RegexOption.IGNORE_CASE),
        Regex("""will\s+be\s+(?:debited|deducted|charged|processed)\b""", RegexOption.IGNORE_CASE),
        Regex("""mandate\s+(?:registered|created|approved|set\s+up)""", RegexOption.IGNORE_CASE),
        // Incoming UPI mandate request — "received a upi-mandate request ... authorize"
        Regex("""(?:upi[\s-])?mandate\s+request\b""", RegexOption.IGNORE_CASE),
        // NACH/Auto-pay mandate acknowledgement — "received today for processing", "received for processing"
        Regex("""(?:NACH|UMRN|Auto\s*Pay).{0,80}received\s+(?:today\s+)?for\s+processing""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
        // "Freq MNTH/QRTR/WEEK" — mandate frequency marker unique to NACH notifications
        Regex("""Freq\s+(?:MNTH|QRTR|WEEK|DAIL|BIMN|MIAN|YRLY|ZZZZ)""", RegexOption.IGNORE_CASE),
    )

    val OTP_KEYWORDS = listOf(
        "otp", "one time password", "one-time password", "do not share",
        "never share", "valid for", "verification code", "auth code",
        "use this code", "login code", "due"
    )

    val BANK_NAMES = listOf(
        "HDFC", "ICICI", "SBI", "AXIS", "KOTAK", "YES", "IDFC", "PNB", "PUNJAB", "RBL", "DBS", "HSBC", "CITI",
        "BOI", "INDIAN BANK", "UTKARSH", "SOUTH INDIAN BANK", "CANARA", "UNION", "BANK OF BARODA", "BOB",
        "CENTRAL", "UCO", "KARNATAKA", "FEDERAL", "BANDHAN", "AU SMALL", "JANA", "EQUITAS"
    )

    val EMI_CONVERSION_PATTERNS = listOf(
        Regex("""converted\s+to\s+\d+\s+months?\s+emi""", RegexOption.IGNORE_CASE),
        Regex("""converted\s+to\s+emi""", RegexOption.IGNORE_CASE),
        Regex("""amort(?:ization)?\s+schedule""", RegexOption.IGNORE_CASE),
        Regex("""emi\s+amt\s+is\s+exclusive""", RegexOption.IGNORE_CASE),
    )

    val CREDIT_CARD_STATEMENT_PATTERNS = listOf(
        Regex("""outstanding\s+of\s+(?:Rs\.?|INR|₹|rs\.?)\s*\d+(?:[.,]\d{3})*(?:\.\d{1,2})?\s+on\s+your\s+credit\s+card.*?due\s+on""", RegexOption.IGNORE_CASE),
        Regex("""Min\.?\s+Amount\s+Due""", RegexOption.IGNORE_CASE),
        Regex("""outstanding.*due\s+on""", RegexOption.IGNORE_CASE),
        Regex("""Please\s+ignore\s+if\s+already\s+paid""", RegexOption.IGNORE_CASE),
        // "Ignore if paid" / "ignore if already paid" — credit card bill payment reminders
        Regex("""ignore\s+if\s+(?:already\s+)?paid""", RegexOption.IGNORE_CASE),
        // "missed on DD-Mon-YY" — payment due/missed date pattern in bill reminders
        Regex("""missed\s+on\s+\d{1,2}[-/]\w{3}[-/]\d{2,4}""", RegexOption.IGNORE_CASE),
    )

    // Words that are never a merchant — banking keywords, prepositions, structural SMS terms.
    // Used to reject false-positive captures from MERCHANT_PATTERNS.
    val MERCHANT_PATTERN_BLOCKLIST = setOf(
        "upi", "neft", "rtgs", "imps", "vpa", "ref", "refno", "utr", "txn", "id",
        "a/c", "ac", "acct", "account", "bank", "card", "debit", "credit",
        "your", "you", "the", "this", "our", "their",
        "rs", "inr", "bal", "balance", "avl", "lmt", "limit",
        "not", "call", "sms", "dial", "block", "report", "helpline",
        "on", "at", "via", "from", "to", "for", "by", "in", "of",
        "payment", "transaction", "transfer", "amount", "date", "time",
    )

    val MERCHANT_PATTERNS = listOf(

        // "For: Swiggy" — colon-anchored, low false-positive risk
        Regex(
            """For:\s+([A-Za-z][A-Za-z0-9 &._*-]+?)(?:\s+(?:From|Via|on|at)\b|$)""",
            RegexOption.IGNORE_CASE
        ),

        // "paid to Swiggy" / "trf to Amazon" / "debited to Sahjad" — require strong verb before "to"
        Regex(
            """(?:paid|trf|transferred|sent|debited)\s+to\s+([A-Za-z][A-Za-z0-9 &._*-]+?)(?:\s+(?:on|at|via|ref|txn|id|for)\b|$)""",
            RegexOption.IGNORE_CASE
        ),

        // "for payee Sahjad Ahmad for rs." — "for payee <name> for" pattern
        Regex(
            """for\s+payee\s+([A-Za-z][A-Za-z0-9 &._*-]+?)(?:\s+for\b|\s+(?:on|at|via|ref|txn|id)\b|$)""",
            RegexOption.IGNORE_CASE
        ),

        // "Merchant: Swiggy" / "Payee: FNP" — explicit label, safe
        // Exclude "beneficiary" — it's followed by account numbers, not names
        Regex(
            """(?:merchant|payee)[\s:]+([A-Za-z][A-Za-z0-9 &._*-]+?)(?:\s+(?:ref|txn|utr|on|at)\b|\.|,|$)""",
            RegexOption.IGNORE_CASE
        ),
    )

    val MERCHANT_SUFFIXES = listOf(
        Regex("""\s+group\s+private\s+limited\.?$""", RegexOption.IGNORE_CASE),
        Regex("""\s+group\s+private\s+limit\.?$""", RegexOption.IGNORE_CASE),
        Regex("""\s+group\s+pvt\s+ltd\.?$""", RegexOption.IGNORE_CASE),
        Regex("""\s+group\s+private\s+ltd\.?$""", RegexOption.IGNORE_CASE),
        Regex("""\s+group\s+pvt\.?$""", RegexOption.IGNORE_CASE),
        Regex("""\s+group\s+limited\.?$""", RegexOption.IGNORE_CASE),
        Regex("""\s+group\s+ltd\.?$""", RegexOption.IGNORE_CASE),
        Regex("""\s+pvt\s+ltd\.?$""", RegexOption.IGNORE_CASE),
        Regex("""\s+private\s+limited\.?$""", RegexOption.IGNORE_CASE),
        Regex("""\s+private\s+limit\.?$""", RegexOption.IGNORE_CASE),
        Regex("""\s+private\s+ltd\.?$""", RegexOption.IGNORE_CASE),
        Regex("""\s+pvt\s+ltd$""", RegexOption.IGNORE_CASE),
        Regex("""\s+ltd\.?$""", RegexOption.IGNORE_CASE),
        Regex("""\s+limited\.?$""", RegexOption.IGNORE_CASE),
        Regex("""\s+inc\.?$""", RegexOption.IGNORE_CASE),
        Regex("""\s+incorporated\.?$""", RegexOption.IGNORE_CASE),
        Regex("""\s+corp\.?$""", RegexOption.IGNORE_CASE),
        Regex("""\s+corporation\.?$""", RegexOption.IGNORE_CASE),
        Regex("""\s+llc\.?$""", RegexOption.IGNORE_CASE),
        Regex("""\s+llp\.?$""", RegexOption.IGNORE_CASE),
        Regex("""\s+limited\s+liability\s+partnership\.?$""", RegexOption.IGNORE_CASE),
        Regex("""\s+group\.?$""", RegexOption.IGNORE_CASE)
    )

    val CREDIT_CARD_COMPANIES = listOf(
        "SBI\\s+CARDS?",
        "HDFC\\s+CARD",
        "ICICI\\s+CARD",
        "AXIS\\s+CARD",
        "KOTAK\\s+CARD",
        "YES\\s+BANK\\s+CARD",
        "RBL\\s+CARD",
        "AMEX",
        "AMERICAN\\s+EXPRESS",
        "CREDIT\\s+CARD"
    )

    val MERCHANT_CATEGORIES = mapOf(
        "swiggy" to "food", "zomato" to "food", "food panda" to "food",
        "uber eats" to "food", "domino" to "food", "pizza hut" to "food",
        "kfc" to "food", "mcdonald" to "food", "mc donalds" to "food",
        "burger king" to "food", "subway" to "food",
        "haldiram" to "food", "dominos" to "food", "dominos pizza" to "food",
        "burfiwala" to "food",
        "zepto" to "groceries", "dunzo" to "groceries", "blinkit" to "groceries",
        "big basket" to "groceries", "bigbasket" to "groceries", "grofers" to "groceries",
        "dmart" to "groceries", "reliance fresh" to "groceries", "spencer" to "groceries",
        "amazon" to "shopping", "flipkart" to "shopping", "myntra" to "shopping",
        "puma" to "shopping", "nike" to "shopping", "adidas" to "shopping",
        "reebok" to "shopping", "nykaa" to "shopping", "ajio" to "shopping",
        "meesho" to "shopping", "snapdeal" to "shopping", "paytm mall" to "shopping",
        "westside" to "shopping", "shoppers stop" to "shopping", "lifestyle" to "shopping",
        "pantaloons" to "shopping", "reliance trends" to "shopping",
        "uber" to "travel", "ola" to "travel", "rapido" to "travel",
        "in drive" to "travel", "indrive" to "travel", "irctc" to "travel",
        "indian railways" to "travel", "make my trip" to "travel", "makemytrip" to "travel",
        "goibibo" to "travel", "ibibo" to "travel", "cleartrip" to "travel",
        "yatra" to "travel", "indigo" to "travel", "air india" to "travel",
        "spicejet" to "travel", "vistara" to "travel", "happyfares" to "travel",
        "akasa air" to "travel", "go air" to "travel", "air asia" to "travel",
        "fastag" to "travel", "toll" to "travel",
        "hpcl" to "travel", "iocl" to "travel", "bpcl" to "travel",
        "reliance petrol" to "travel", "shell" to "travel",
        "petrol pump" to "travel", "petrolpump" to "travel",
        "google play" to "entertainment", "netflix" to "entertainment",
        "prime video" to "entertainment", "hotstar" to "entertainment",
        "disney" to "entertainment", "sony liv" to "entertainment",
        "zee5" to "entertainment", "voot" to "entertainment",
        "alt balaji" to "entertainment", "spotify" to "entertainment",
        "dream11" to "entertainment", "my11circle" to "entertainment",
        "youtube premium" to "entertainment", "youtube" to "entertainment",
        "bookmyshow" to "entertainment", "pvr" to "entertainment",
        "inox" to "entertainment", "cinepolis" to "entertainment",
        "bsnl" to "bills", "airtel" to "bills", "jio" to "bills",
        "vodafone" to "bills", "idea" to "bills", "vi" to "bills",
        "tata sky" to "bills", "dish tv" to "bills", "d2h" to "bills",
        "sun direct" to "bills", "reliance energy" to "bills",
        "tata power" to "bills", "adani" to "bills",
        "apple store" to "bills", "amazon store" to "bills",
        "flipkart store" to "bills", "myntra store" to "bills",
        "ajio store" to "bills", "meesho store" to "bills",
        "snapdeal store" to "bills", "paytm mall store" to "bills",
        "westside store" to "bills", "shoppers stop store" to "bills",
        "lifestyle store" to "bills", "pantaloons store" to "bills",
        "reliance trends store" to "bills", "torrent" to "bills",
        "indane" to "bills", "hp gas" to "bills", "bharat gas" to "bills",
        "lic" to "bills", "hdfc life" to "bills", "icici prudential" to "bills",
        "sbi life" to "bills", "bajaj allianz" to "bills",
        "star health" to "bills", "new india" to "bills",
        "national insurance" to "bills",
        "cred club" to "bills",
        "cred" to "bills",
        "pharmeasy" to "health", "1mg" to "health", "netmeds" to "health",
        "apollo" to "health", "fortis" to "health", "max hospital" to "health",
        "manipal" to "health", "cure.fit" to "health", "cult.fit" to "health",
        "golds gym" to "health", "talwalkars" to "health",
        "salary" to "salary",
        "zerodha" to "investment", "groww" to "investment", "upstox" to "investment",
        "angel one" to "investment", "icici direct" to "investment",
        "hdfc securities" to "investment", "kotak securities" to "investment",
        "indian clearing" to "investment",
        "gold sip" to "investment", "sip" to "investment",
        "mutual fund" to "investment", "mf sip" to "investment"
    )

    val WORD_CATEGORIES = mapOf(
        "travel" to "travel", "trip" to "travel", "metro" to "travel",
        "railway" to "travel", "railways" to "travel", "airport" to "travel",
        "airline" to "travel", "airlines" to "travel", "taxi" to "travel",
        "cab" to "travel", "bus" to "travel", "train" to "travel", "checkpost" to "travel",
        "flight" to "travel", "hotel" to "travel", "booking" to "travel",
        "shopping" to "shopping", "shop" to "shopping", "fashion" to "shopping",
        "apparels" to "shopping", "apparel" to "shopping", "clothing" to "shopping",
        "clothes" to "shopping", "store" to "shopping", "outlet" to "shopping",
        "showroom" to "shopping", "mall" to "shopping", "market" to "shopping",
        "bazaar" to "shopping", "retail" to "shopping",
        "food" to "food", "tiffin" to "food", "pizza" to "food",
        "kitchen" to "food", "restaurant" to "food", "cafe" to "food",
        "fruit" to "food", "vegetables" to "food",
        "coffee" to "food", "bakery" to "food", "baker" to "food",
        "dining" to "food", "food court" to "food", "foodcourt" to "food",
        "fast food" to "food", "fastfood" to "food", "burger" to "food",
        "chinese" to "food", "north indian" to "food", "south indian" to "food",
        "biryani" to "food", "tandoor" to "food",
        "grocery" to "groceries", "groceries" to "groceries", "dairy" to "groceries",
        "supermarket" to "groceries", "super market" to "groceries",
        "hypermarket" to "groceries", "hyper market" to "groceries",
        "provisions" to "groceries", "kirana" to "groceries",
        "general store" to "groceries", "generalstore" to "groceries",
        "health" to "health", "medicine" to "health", "pharmacy" to "health",
        "pharma" to "health", "medical" to "health", "hospital" to "health",
        "clinic" to "health", "diagnostic" to "health", "lab" to "health",
        "chemists" to "health", "chemist" to "health",
        "laboratory" to "health", "doctor" to "health", "dental" to "health",
        "eye care" to "health", "eyecare" to "health", "wellness" to "health",
        "fitness" to "health", "gym" to "health", "yoga" to "health",
        "bills" to "bills", "electricity" to "bills", "power" to "bills",
        "water" to "bills", "internet" to "bills", "receipt" to "bills",
        "receipts" to "bills", "broadband" to "bills", "wifi" to "bills",
        "dth" to "bills", "cable" to "bills", "mobile" to "bills",
        "recharge" to "bills", "prepaid" to "bills", "postpaid" to "bills",
        "entertainment" to "entertainment", "cinema" to "entertainment",
        "movie" to "entertainment", "theatre" to "entertainment",
        "theater" to "entertainment", "multiplex" to "entertainment",
        "games" to "entertainment", "gaming" to "entertainment",
        "music" to "entertainment", "streaming" to "entertainment",
        "subscription" to "entertainment",
        "investment" to "investment",
        "sip" to "investment"
    )

    val SQL_FORBIDDEN = listOf("INSERT", "UPDATE", "DELETE", "DROP", "ALTER")

    val MONTH_NAMES = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
}
