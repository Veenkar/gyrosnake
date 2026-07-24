package com.gyrosnake.game

import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit cover for the wrap rule itself — the single source of truth for wall crossing. */
class GameBoardWrapTest {

    private val board = GameBoard(columns = 20, rows = 12)

    @Test
    fun `in-bounds points are returned unchanged`() {
        assertEquals(Point(0, 0), board.wrap(Point(0, 0)))
        assertEquals(Point(19, 11), board.wrap(Point(19, 11)))
        assertEquals(Point(7, 3), board.wrap(Point(7, 3)))
    }

    @Test
    fun `stepping one cell off an edge lands on the opposite edge`() {
        assertEquals(Point(19, 5), board.wrap(Point(-1, 5)))
        assertEquals(Point(0, 5), board.wrap(Point(20, 5)))
        assertEquals(Point(8, 11), board.wrap(Point(8, -1)))
        assertEquals(Point(8, 0), board.wrap(Point(8, 12)))
    }

    @Test
    fun `overshoots larger than one board still wrap correctly`() {
        // The snake only ever overshoots by one, but the rule should not depend
        // on that: the old (n + size) % size form broke past a single wrap.
        assertEquals(Point(19, 5), board.wrap(Point(-21, 5)))
        assertEquals(Point(1, 5), board.wrap(Point(41, 5)))
        assertEquals(Point(8, 11), board.wrap(Point(8, -13)))
    }
}
