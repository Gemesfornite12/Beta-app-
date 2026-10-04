package com.example.data.social

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialInteractionModelTest {
    @Test fun commentBodyIsTrimmedNonemptyAndLimitedTo1000Characters() {
        assertTrue(SocialComment.isValidBody("  buen video  "))
        assertFalse(SocialComment.isValidBody("   "))
        assertFalse(SocialComment.isValidBody("x".repeat(1001)))
        assertTrue(SocialComment.isValidBody("x".repeat(1000)))
    }

    @Test fun interactionPathsStayUnderApprovedSocialTestNamespace() {
        assertEquals("comments/owner/post", SocialInteractionPaths.comments("owner", "post"))
        assertEquals("savedPosts/viewer/owner/post", SocialInteractionPaths.savedVideo("viewer", "owner", "post"))
    }
}
