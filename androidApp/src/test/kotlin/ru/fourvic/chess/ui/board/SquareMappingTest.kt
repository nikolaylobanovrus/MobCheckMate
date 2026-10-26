package ru.fourvic.chess.ui.board

import kotlin.test.Test
import kotlin.test.assertEquals

class SquareMappingTest {

    @Test
    fun whiteAtTheBottom() {
        assertEquals("a8", squareAt(column = 0, row = 0, flipped = false))
        assertEquals("h1", squareAt(column = 7, row = 7, flipped = false))
        assertEquals("e2", squareAt(column = 4, row = 6, flipped = false))
    }

    @Test
    fun blackAtTheBottomWhenFlipped() {
        assertEquals("h1", squareAt(column = 0, row = 0, flipped = true))
        assertEquals("a8", squareAt(column = 7, row = 7, flipped = true))
        assertEquals("e7", squareAt(column = 3, row = 6, flipped = true))
    }
}
