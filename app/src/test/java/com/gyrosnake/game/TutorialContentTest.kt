package com.gyrosnake.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cover for the walkthrough's page order. The tutorial is the first thing a new
 * player sees, and its meaning depends on sequence: the lead-in page has to come
 * immediately before the control pages, or the reader takes four alternative
 * schemes for four things they must learn.
 */
class TutorialContentTest {

    private val steps = TutorialContent.steps

    /** The four schemes, in the order ControlScheme declares them. */
    private val schemeVisuals = listOf(
        TutorialVisual.POINT,
        TutorialVisual.JOYSTICK,
        TutorialVisual.FLICK,
        TutorialVisual.TILT
    )

    @Test
    fun `the lead-in page comes immediately before the control pages`() {
        val lead = steps.indexOfFirst { it.visual == TutorialVisual.SCHEMES }
        assertTrue("no SCHEMES lead-in page", lead >= 0)
        assertEquals(
            schemeVisuals,
            steps.subList(lead + 1, lead + 1 + schemeVisuals.size).map { it.visual }
        )
    }

    @Test
    fun `the lead-in is not the first page`() {
        // The goal page has to land first: what the game is, before how to steer.
        assertEquals(TutorialVisual.SNAKE, steps.first().visual)
        assertTrue(steps.indexOfFirst { it.visual == TutorialVisual.SCHEMES } > 0)
    }

    @Test
    fun `every control scheme has a page`() {
        // Regression: adding a scheme to ControlScheme without a tutorial page
        // leaves it undocumented and unreachable from the walkthrough.
        assertEquals(ControlScheme.values().size, schemeVisuals.size)
        for (visual in schemeVisuals) {
            assertTrue("$visual has no page", steps.any { it.visual == visual })
        }
    }

    @Test
    fun `no page is repeated and every page has distinct text`() {
        assertEquals(steps.size, steps.map { it.titleRes }.distinct().size)
        assertEquals(steps.size, steps.map { it.bodyRes }.distinct().size)
    }
}
