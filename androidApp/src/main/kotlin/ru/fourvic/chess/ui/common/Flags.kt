package ru.fourvic.chess.ui.common

/** The flag emoji for an ISO 3166 country code such as "RU", or an empty string for anything else. */
fun flagEmoji(country: String?): String {
    val code = country?.uppercase() ?: return ""
    if (code.length != 2 || !code.all { it in 'A'..'Z' }) return ""
    return code.map { String(Character.toChars(REGIONAL_INDICATOR_A + (it - 'A'))) }.joinToString("")
}

/** Name with the flag in front, as shown next to boards and in tables. */
fun withFlag(name: String, country: String?): String = flagEmoji(country).let { if (it.isEmpty()) name else "$it $name" }

private const val REGIONAL_INDICATOR_A = 0x1F1E6
