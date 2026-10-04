package com.example.ui.screens.social

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SocialVideoFeedLogicTest {
    @Test fun upwardAndDownwardSwipesChooseNextAndPrevious() {
        assertEquals(1, SocialVideoFeedLogic.swipeDirection(-120f, 80f))
        assertEquals(-1, SocialVideoFeedLogic.swipeDirection(120f, 80f))
        assertEquals(0, SocialVideoFeedLogic.swipeDirection(-79f, 80f))
    }

    @Test fun navigationStopsAtFeedBoundaries() {
        assertEquals(1, SocialVideoFeedLogic.adjacentIndex(0, 4, 1))
        assertEquals(2, SocialVideoFeedLogic.adjacentIndex(3, 4, -1))
        assertNull(SocialVideoFeedLogic.adjacentIndex(0, 4, -1))
        assertNull(SocialVideoFeedLogic.adjacentIndex(3, 4, 1))
    }
}
