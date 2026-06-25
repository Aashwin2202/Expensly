package com.fintrackai.domain.sms

import android.content.Context
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.fintrackai.BuildConfig
import com.fintrackai.work.PatternReportWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates privacy-safe unknown-pattern reporting.
 *
 * When an SMS from a known bank sender cannot be parsed (falls through all
 * extraction patterns), callers invoke [report].  This class:
 *   1. Anonymises the raw body via [SmsAnonymizer.toSkeleton]
 *   2. Computes a SHA-256 fingerprint of the skeleton
 *   3. Enqueues a background [PatternReportWorker] that sends only the skeleton,
 *      hash, and sender-code to Supabase — **no raw SMS, no user identity**
 *
 * No-ops silently if Supabase is not configured (empty URL/key in local.properties).
 */
@Singleton
class PatternReporter @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun report(smsBody: String, senderAddress: String) {
        if (smsBody.isBlank()) return
        // Skip if Supabase is not wired up in this build
        if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_ANON_KEY.isBlank()) return

        val skeleton = SmsAnonymizer.toSkeleton(smsBody)
        if (skeleton.isBlank()) return

        // Hash the structural key (merchant words collapsed to <M>) so that messages
        // differing only in payee name share one row and increment hit_count instead
        // of creating duplicate entries.
        val hash = sha256(structuralKey(skeleton))

        val inputData = Data.Builder()
            .putString(PatternReportWorker.KEY_SKELETON, skeleton)
            .putString(PatternReportWorker.KEY_HASH, hash)
            .putString(PatternReportWorker.KEY_SENDER, senderAddress)
            .build()

        val work = OneTimeWorkRequestBuilder<PatternReportWorker>()
            .setInputData(inputData)
            .build()

        WorkManager.getInstance(context).enqueue(work)
    }

    /**
     * Collapses runs of non-structural words in a skeleton into a single <M> token,
     * producing a canonical key that is identical for messages differing only in payee name.
     *
     * Mirrors the Python _normalise_for_distance() logic in generate_patterns.py.
     * Placeholders are already in the skeleton; we only need to handle bare words
     * the anonymizer left in (merchant names, utility names, etc.).
     */
    private fun structuralKey(skeleton: String): String {
        @Suppress("RegExpRedundantEscape")
        val normalised = skeleton
            // NACH-ECS: scheme code + optional sub-codes/NUM tokens → <M>
            // Separator between nach/ecs can be space, hyphen, or dot.
            .replace(Regex("""nach[\s.\-]ecs[\s.\-]\S+(?:\s+(?:<NUM>|[a-z]\S*))*""", RegexOption.IGNORE_CASE), "<M>")
            // Payee name in "transfer(red)/sent/paid to <payee> <ref-keyword>":
            // everything between "to" and the first reference keyword is the payee.
            .replace(
                Regex("""((?:transfer(?:red)?|sent|paid)\s+to\s+).+?(?=\s+(?:ref|utr|rrn|order\s+(?:id|no)|auth|txn)\b)""",
                    RegexOption.IGNORE_CASE),
                "$1<M>"
            )
            // ATM/branch location: "at +<location> on" — location may embed bank names or
            // <BANK> placeholders inserted by the anonymizer.
            .replace(Regex("""(at\s+\+\s*).+?(\s+on\s)""", RegexOption.IGNORE_CASE), "$1<M>$2")

        val structural = setOf(
            // Articles / conjunctions / prepositions
            "a", "an", "the", "and", "or", "is", "are", "was", "be", "been",
            "have", "has", "do", "does", "will", "would", "could", "should",
            "to", "of", "in", "on", "at", "by", "for", "with", "from", "as",
            // Greetings / salutations
            "hi", "hello", "dear", "customer", "user", "sir", "madam",
            // Amount / balance keywords
            "rs", "inr", "bal", "balance", "avl", "lmt", "limit", "available",
            "amt", "amount", "min", "minimum", "outstanding", "due",
            // Account / card keywords
            "bank", "card", "acct", "account", "debit", "credit", "net", "mob", "app",
            // Transaction verbs (past and present)
            "sent", "paid", "spent", "debited", "credited", "withdrawn", "withdrawal",
            "transferred", "received", "deposited", "executed", "loaded", "deducted",
            "processed", "confirmed", "confirm",
            // Transaction type words
            "purchase", "transaction", "txn", "ref", "utr", "upi", "imps", "neft", "rtgs",
            "transfer", "payment", "towards", "bill", "cash", "pos", "atm",
            // NACH / mandate keywords
            "nach", "ecs", "mandate", "umrn", "auto", "autopay", "scheduled", "frequency", "freq",
            // Alert / fraud-report boilerplate
            "alert", "your", "you", "not", "call", "sms", "report", "block", "dial",
            "info", "via", "no", "if", "done", "fwd", "this", "u", "am", "pm",
            "today", "now", "ignore", "already", "reminder", "missed", "due",
            // Reference introducing words
            "vide",
            // Bank names
            "hdfc", "icici", "sbi", "axis", "kotak", "yes", "idfc", "pnb",
            "rbl", "dbs", "hsbc", "citi", "boi", "canara", "union", "au",
            "iob", "bob", "baroda", "federal", "bandhan", "airtel", "paytm",
        )

        val placeholderRe = Regex("<[A-Z]+>")
        val wordRe = Regex("[a-z][a-z0-9&'._-]*")

        val tokens = normalised.trim().split(" ")
        val out = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            val tok = tokens[i]

            // Clean placeholder: keep as-is
            if (placeholderRe.matches(tok)) {
                out.add(tok)
                i++
                continue
            }

            // Fused token: placeholder glued to alpha chars (e.g. "<REF>b<NUM>uigw").
            // Inherently variable — collapse to <M>.
            if (tok.contains('<')) {
                out.add("<M>")
                i++
                continue
            }

            // Pure-punctuation token (e.g. "-", ":", "/", "+"): discard — it carries no
            // structural meaning and its presence/absence varies by bank.
            val word = wordRe.find(tok)?.value ?: ""
            if (word.isEmpty()) {
                i++
                continue
            }

            if (word !in structural) {
                // Non-structural word: consume this token and any following non-structural
                // word tokens into a single <M>.
                while (i < tokens.size) {
                    val w = wordRe.find(tokens[i])?.value ?: ""
                    if (placeholderRe.matches(tokens[i]) || tokens[i].contains('<') ||
                        w.isEmpty() || w in structural) break
                    i++
                }
                out.add("<M>")
            } else {
                out.add(tok)
                i++
            }
        }
        return out.joinToString(" ")
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
