package com.fintrackai.domain.category

import com.fintrackai.domain.model.CustomCategory
import java.util.Locale

object CategoryCatalogHelper {

    fun mergeOptions(custom: List<CustomCategory>): List<CategoryOption> {
        val overrideById = custom.associateBy { it.id.lowercase(Locale.ROOT) }
        val hiddenIds = custom.filter { it.hidden }.map { it.id.lowercase(Locale.ROOT) }.toSet()
        val builtIns = BUILT_IN_CATEGORIES
            .filter { (id, _, _) -> id !in hiddenIds }
            .map { (id, icon, color) ->
                val override = overrideById[id]
                if (override != null && !override.hidden) {
                    CategoryOption(id, override.icon, override.color, override.name)
                } else {
                    CategoryOption(id, icon, color, builtInLabel(id))
                }
            }
        val trueCustom = custom
            .filter { it.id.lowercase(Locale.ROOT) !in BUILT_IN_CATEGORY_IDS && !it.hidden }
            .map { CategoryOption(it.id, it.icon, it.color, it.name) }
        return builtIns + trueCustom
    }

    fun categoryIcon(categoryId: String, custom: List<CustomCategory>): String {
        val key = categoryId.lowercase(Locale.ROOT)
        // Custom override takes priority (built-in may have been renamed)
        custom.find { it.id.equals(categoryId, ignoreCase = true) }?.icon?.let { return it }
        BUILT_IN_CATEGORIES.find { it.first == key }?.second?.let { return it }
        return "❓"
    }

    fun categoryColor(categoryId: String, custom: List<CustomCategory>): String {
        val key = categoryId.lowercase(Locale.ROOT)
        custom.find { it.id.equals(categoryId, ignoreCase = true) }?.color?.let { return it }
        BUILT_IN_CATEGORIES.find { it.first == key }?.third?.let { return it }
        return DEFAULT_CUSTOM_CATEGORY_COLOR_HEX
    }

    fun categoryLabel(categoryId: String, custom: List<CustomCategory>): String {
        val key = categoryId.lowercase(Locale.ROOT)
        // Custom override takes priority (built-in may have been renamed)
        custom.find { it.id.equals(categoryId, ignoreCase = true) }?.name?.let { return it }
        BUILT_IN_CATEGORIES.find { it.first == key }?.let { return builtInLabel(it.first) }
        return categoryId.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }
    }

    /**
     * Slug for storing on [Transaction.category]; avoids colliding with built-in ids and existing custom ids.
     */
    fun uniqueCustomId(name: String, existingCustomIds: Set<String>): String {
        val base = name.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifEmpty { "custom" }
        val reserved = BUILT_IN_CATEGORY_IDS + existingCustomIds.map { it.lowercase(Locale.ROOT) }
        var id = base
        var n = 2
        while (id.lowercase(Locale.ROOT) in reserved) {
            id = "${base}_$n"
            n++
        }
        return id
    }

    private fun builtInLabel(id: String): String =
        id.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }
}
