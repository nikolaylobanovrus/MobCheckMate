package ru.fourvic.chess.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals

class FlagsTest {
    @Test
    fun countryCodesBecomeFlags() {
        assertEquals("🇷🇺", flagEmoji("RU"))
        assertEquals(flagEmoji("KZ"), flagEmoji("kz"))
        assertEquals("", flagEmoji(null))
        assertEquals("", flagEmoji("RUS"))
        assertEquals("", flagEmoji("1A"))
        assertEquals("Анна", withFlag("Анна", null))
        assertEquals("${flagEmoji("DE")} Lena", withFlag("Lena", "DE"))
    }
}
