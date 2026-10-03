package com.example

import com.example.ui.viewmodel.AuthenticationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthTest {

    @Test
    fun testAuthenticationStateDefaults() {
        val state = AuthenticationState()
        assertFalse(state.isLoading)
        assertFalse(state.isGoogleLoading)
        assertFalse(state.isAuthenticated)
        assertFalse(state.isRegisterMode)
        assertNull(state.currentUser)
        assertNull(state.userEmail)
        assertEquals("", state.emailInput)
        assertEquals("", state.passwordInput)
        assertNull(state.errorMessage)
    }

    @Test
    fun testToggleAuthMode() {
        var state = AuthenticationState(isRegisterMode = false)
        state = state.copy(isRegisterMode = !state.isRegisterMode)
        assertTrue(state.isRegisterMode)

        state = state.copy(isRegisterMode = !state.isRegisterMode)
        assertFalse(state.isRegisterMode)
    }

    @Test
    fun testAuthenticatedStateTransition() {
        val state = AuthenticationState(
            isAuthenticated = true,
            userEmail = "gonzalez24029@gmail.com",
            userDisplayName = "Alexis González"
        )
        assertTrue(state.isAuthenticated)
        assertEquals("gonzalez24029@gmail.com", state.userEmail)
        assertEquals("Alexis González", state.userDisplayName)
    }
}
