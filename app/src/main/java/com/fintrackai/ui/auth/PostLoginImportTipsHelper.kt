package com.fintrackai.ui.auth

object PostLoginImportTipsHelper {
    fun tipIndexForElapsedSeconds(elapsedSeconds: Long, tipCount: Int): Int {
        if (tipCount <= 0) return 0
        val interval = 8L
        return ((elapsedSeconds / interval) % tipCount).toInt()
    }
}
