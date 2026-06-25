package com.fintrackai.ui.components

import com.fintrackai.domain.transactions.TransactionLinkConstants
import com.fintrackai.domain.transactions.TransactionLinkResult

object TransactionLinkUiMessages {
    fun message(result: TransactionLinkResult): String? = when (result) {
        TransactionLinkResult.Success -> TransactionLinkConstants.LINK_SUCCESS
        TransactionLinkResult.NotFound -> TransactionLinkConstants.ERR_NOT_FOUND
        TransactionLinkResult.InvalidPair -> TransactionLinkConstants.ERR_INVALID_PAIR
        TransactionLinkResult.AlreadyLinked -> TransactionLinkConstants.ERR_ALREADY_LINKED
    }
}
