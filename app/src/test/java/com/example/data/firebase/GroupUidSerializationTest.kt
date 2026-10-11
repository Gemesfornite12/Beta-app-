package com.example.data.firebase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupUidSerializationTest {
    @Test
    fun newGroupPayloadKeepsCreatorUidAndDisplayFields() {
        val group = ChannelInfo(
            id = "group-1",
            name = "Team",
            description = "",
            iconEmoji = "👥",
            isGroup = true,
            creatorEmail = "owner@example.test",
            creatorName = "Owner",
            creatorUid = "uid-owner",
            members = listOf(
                GroupMember(
                    email = "owner@example.test",
                    name = "Owner",
                    role = "owner",
                    uid = "uid-owner"
                ),
                GroupMember(
                    email = "member@example.test",
                    name = "Member",
                    uid = "uid-member"
                )
            )
        )

        val payload = group.toDatabaseMap()
        assertEquals("uid-owner", payload["creatorUid"])
        assertEquals("owner@example.test", payload["creatorEmail"])
        val members = payload["members"] as List<Map<String, Any>>
        assertEquals("uid-owner", members[0]["uid"])
        assertEquals("owner@example.test", members[0]["email"])
        assertEquals("owner", members[0]["role"])
        assertEquals("uid-member", members[1]["uid"])
    }

    @Test
    fun legacyPayloadDoesNotInventUidFromEmail() {
        val legacyMember = GroupMember(email = "legacy@example.test", name = "Legacy")
        val memberPayload = legacyMember.toDatabaseMap()
        val legacyGroup = ChannelInfo(
            id = "legacy",
            name = "Old group",
            description = "",
            iconEmoji = "👥",
            creatorEmail = "creator@example.test",
            members = listOf(legacyMember)
        ).toDatabaseMap()

        assertFalse(memberPayload.containsKey("uid"))
        assertFalse(legacyGroup.containsKey("creatorUid"))
        assertEquals("legacy@example.test", memberPayload["email"])
        assertTrue(legacyGroup.containsKey("creatorEmail"))
    }
}
