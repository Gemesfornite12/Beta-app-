package com.example.data.supabase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SupabaseUploadPermissionTest {
    private val uid = "firebaseUid123"
    private val mime = "image/jpeg"
    private val project = "https://ovttmxwtljfqizcetoxk.supabase.co"
    private val path = "users/$uid/media/550e8400-e29b-41d4-a716-446655440000/550e8400-e29b-41d4-a716-446655440001.jpg"
    private val relativeSignedUrl = "/object/upload/sign/chat-media/users/$uid/media/550e8400-e29b-41d4-a716-446655440000/550e8400-e29b-41d4-a716-446655440001.jpg?token=temporary-upload-token"

    @Test
    fun acceptsFreshUploadPermissionAndNormalizesRelativeUrl() {
        val result = parseSupabaseUploadPermission(
            response(path, relativeSignedUrl, mime), uid, mime, project
        )

        assertEquals(path, result.storagePath)
        assertEquals(project + "/storage/v1" + relativeSignedUrl, result.signedUrl)
    }

    @Test
    fun rejectsPathForDifferentUid() {
        assertThrows(IllegalArgumentException::class.java) {
            parseSupabaseUploadPermission(response(path, relativeSignedUrl, mime), "anotherUid", mime, project)
        }
    }

    @Test
    fun rejectsUntrustedHostOrNonHttpsSignedUrl() {
        assertThrows(IllegalArgumentException::class.java) {
            parseSupabaseUploadPermission(
                response(path, "https://attacker.example/storage/v1/object/upload/sign/chat-media/$path?token=x", mime),
                uid, mime, project
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseSupabaseUploadPermission(
                response(path, "http://ovttmxwtljfqizcetoxk.supabase.co/storage/v1/object/upload/sign/chat-media/$path?token=x", mime),
                uid, mime, project
            )
        }
    }

    @Test
    fun rejectsSignedUrlForADifferentObjectPath() {
        assertThrows(IllegalArgumentException::class.java) {
            parseSupabaseUploadPermission(
                response(path, "/object/upload/sign/chat-media/users/$uid/media/550e8400-e29b-41d4-a716-446655440000/550e8400-e29b-41d4-a716-446655440002.jpg?token=x", mime),
                uid, mime, project
            )
        }
    }

    @Test
    fun rejectsMissingPermissionTokenMalformedPathAndMimeMismatch() {
        assertThrows(IllegalArgumentException::class.java) {
            parseSupabaseUploadPermission(
                response(path, "/object/upload/sign/chat-media/$path", mime), uid, mime, project
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseSupabaseUploadPermission(
                response("users/$uid/media/../other.jpg", relativeSignedUrl, mime), uid, mime, project
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseSupabaseUploadPermission(response(path, relativeSignedUrl, "video/mp4"), uid, mime, project)
        }
    }

    private fun response(storagePath: String, signedUrl: String, mimeType: String) =
        """{"storagePath":"$storagePath","signedUrl":"$signedUrl","mimeType":"$mimeType"}"""
}
