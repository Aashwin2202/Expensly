package com.fintrackai.domain.transactions

import com.fintrackai.domain.model.Transaction
import kotlin.math.abs

object TransactionLinkHelper {

    /**
     * Computes the net outcome when linking a debit primary with a given total credit offset.
     * FuzzyCancelled is only used for the very first 1:1 link (no prior secondaries).
     */
    fun debitCreditLinkOutcome(
        primaryOrigType: String,
        primaryOrigAmount: Double,
        totalSecondaryAmount: Double,
        isFreshGroup: Boolean
    ): DebitCreditLinkOutcome {
        val isDebitPrimary = primaryOrigType.equals("debit", ignoreCase = true)
        val diff = abs(primaryOrigAmount - totalSecondaryAmount)
        if (isFreshGroup && diff <= TransactionLinkConstants.AMOUNT_FUZZY_TOLERANCE_RUPEES) {
            return DebitCreditLinkOutcome.FuzzyCancelled
        }
        return if (isDebitPrimary) {
            if (totalSecondaryAmount < primaryOrigAmount) {
                DebitCreditLinkOutcome.NetMerged("debit", primaryOrigAmount - totalSecondaryAmount)
            } else {
                DebitCreditLinkOutcome.NetMerged("credit", totalSecondaryAmount - primaryOrigAmount)
            }
        } else {
            if (totalSecondaryAmount < primaryOrigAmount) {
                DebitCreditLinkOutcome.NetMerged("credit", primaryOrigAmount - totalSecondaryAmount)
            } else {
                DebitCreditLinkOutcome.NetMerged("debit", totalSecondaryAmount - primaryOrigAmount)
            }
        }
    }

    fun oppositeType(type: String): String =
        if (type.equals("debit", ignoreCase = true)) "credit" else "debit"

    /**
     * True when this transaction can be used as an anchor for linking:
     * - unlinked transactions (starting a new group)
     * - existing primaries (adding more secondaries to their group)
     * Secondaries (suppressed rows) cannot be anchors.
     */
    fun canOfferLink(tx: Transaction): Boolean =
        !tx.linkSuppressed && tx.amount > 0

    /** True when this transaction is the visible primary of a link group. */
    fun isLinkPrimary(tx: Transaction): Boolean =
        tx.linkGroupId != null && !tx.linkSuppressed

    /** The original type of the anchor, used to determine what "opposite type" to filter for. */
    fun anchorOriginalType(tx: Transaction): String =
        tx.linkStashedType ?: tx.type
}
