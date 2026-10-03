package com.example

import com.example.ui.navigation.HashRoute
import com.example.ui.navigation.HashRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HashRouterTest {

    @Test
    fun testInitialRoute() {
        val router = HashRouter(initialRoute = HashRoute.HOME)
        assertEquals(HashRoute.HOME, router.currentRoute.value)
        assertEquals("#/home", router.currentHash.value)
        assertFalse(router.canPop())
    }

    @Test
    fun testNavigationBetweenCorePillars() {
        val router = HashRouter(initialRoute = HashRoute.HOME)

        // Navigate to Document Editor
        router.push(HashRoute.DOC_EDITOR)
        assertEquals(HashRoute.DOC_EDITOR, router.currentRoute.value)
        assertEquals("#/editor", router.currentHash.value)
        assertTrue(router.canPop())

        // Navigate to Music Studio
        router.push(HashRoute.MUSIC_STUDIO)
        assertEquals(HashRoute.MUSIC_STUDIO, router.currentRoute.value)
        assertEquals("#/music", router.currentHash.value)

        // Navigate to Chat
        router.push(HashRoute.CHAT)
        assertEquals(HashRoute.CHAT, router.currentRoute.value)
        assertEquals("#/chat", router.currentHash.value)

        // Pop back to Music Studio
        assertTrue(router.pop())
        assertEquals(HashRoute.MUSIC_STUDIO, router.currentRoute.value)

        // Pop back to Document Editor
        assertTrue(router.pop())
        assertEquals(HashRoute.DOC_EDITOR, router.currentRoute.value)

        // Pop back to Home
        assertTrue(router.pop())
        assertEquals(HashRoute.HOME, router.currentRoute.value)

        // Cannot pop further
        assertFalse(router.pop())
    }

    @Test
    fun testHashParsingAndNormalization() {
        assertEquals(HashRoute.DOC_EDITOR, HashRoute.fromHash("#/editor"))
        assertEquals(HashRoute.DOC_EDITOR, HashRoute.fromHash("https://omnistudio.cloud/#/editor"))
        assertEquals(HashRoute.DOC_EDITOR, HashRoute.fromHash("doc_editor"))

        assertEquals(HashRoute.MUSIC_STUDIO, HashRoute.fromHash("#/music"))
        assertEquals(HashRoute.MUSIC_STUDIO, HashRoute.fromHash("omnistudio://app#/studio"))

        assertEquals(HashRoute.CHAT, HashRoute.fromHash("#/chat"))
        assertEquals(HashRoute.HOME, HashRoute.fromHash("#/home"))
        assertEquals(HashRoute.PROFILE, HashRoute.fromHash("#/profile"))
    }
}
