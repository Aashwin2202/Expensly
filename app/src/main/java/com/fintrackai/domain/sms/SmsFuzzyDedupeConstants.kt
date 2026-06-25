package com.fintrackai.domain.sms

object SmsFuzzyDedupeConstants {
    const val BATCH_KEY_SEPARATOR = "\u0000"

    /**
     * Minimum length (after [SmsDedupeHashHelper.normalizeBody]) for the **shorter** of two bodies
     * before a prefix-style fuzzy match is allowed. Reduces false merges on tiny shared snippets.
     */
    const val MIN_SHORTER_NORMALIZED_LENGTH = 28
}
