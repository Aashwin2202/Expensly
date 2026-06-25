package com.fintrackai.domain.category

/** Built-in category id, emoji icon, color hex (matches RN defaults). */
val BUILT_IN_CATEGORIES: List<Triple<String, String, String>> = listOf(
    Triple("food",          "🍕", "#FF4500"), // vivid orange-red
    Triple("groceries",     "🛒", "#00C49A"), // bright teal-green
    Triple("shopping",      "🛍️", "#FF1F6B"), // hot pink-red
    Triple("travel",        "✈️", "#D946EF"), // fuchsia
    Triple("rent",          "🏠", "#8B5CF6"), // bright violet
    Triple("entertainment", "🎬", "#F59E0B"), // vivid amber
    Triple("bills",         "📄", "#EF4444"), // pure red
    Triple("health",        "💊", "#22C55E"), // vivid green
    Triple("salary",        "💰", "#3B82F6"), // bright blue
    Triple("investment",    "📈", "#F97316"), // orange
    Triple("others",        "❔", "#00B4D8")  // sky blue
)

val BUILT_IN_CATEGORY_IDS: Set<String> = BUILT_IN_CATEGORIES.map { it.first }.toSet()

const val DEFAULT_CUSTOM_CATEGORY_COLOR_HEX = "#06B6D4"

val CUSTOM_CATEGORY_COLOR_PALETTE: List<String> = listOf(
    "#F97316", // orange
    "#EAB308", // yellow
    "#84CC16", // lime
    "#14B8A6", // teal
    "#6366F1", // indigo
    "#EC4899", // pink
    "#F43F5E", // rose
    "#0EA5E9", // light blue
    "#10B981", // emerald
    "#D946EF", // fuchsia
    "#FB923C", // light orange
    "#A3E635", // bright lime
    "#2DD4BF", // cyan-teal
    "#818CF8", // soft indigo
    "#F472B6", // soft pink
)
