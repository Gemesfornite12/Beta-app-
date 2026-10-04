#!/usr/bin/env python3
"""Static, read-only checks for the isolated Social .test RTDB rule subtree."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
rules = json.loads((ROOT / "database.rules.json").read_text())["rules"]
social = rules["social_test"]

expected_root_keys = {
    "audio_projects", "calls", "calls_test", "chats", "documents", "presence",
    "sara_chat_history", "sara_knowledge", "social_test", "typing", "ultimate", "users",
}
assert set(rules) == expected_root_keys, "unexpected root RTDB rule scope"
assert ".read" not in social and ".write" not in social, "do not grant broad social_test access"
assert {key for key in social if not key.startswith(".")} == {
    "profiles", "directory", "postsByUser", "publicFeed", "following", "followers",
    "followRequests", "likes", "comments", "savedPosts", "preferences", "storiesByUser", "publicStories",
}

visibility = social["likes"]["$ownerUid"]["$postId"][".read"]
comment_post = social["comments"]["$ownerUid"]["$postId"]
comment = comment_post["$commentId"]
assert comment_post[".read"] == visibility, "comment reads must match existing post visibility/follower access"
assert comment_post[".indexOn"] == ["createdAt"]
write = comment[".write"]
for required in (
    "auth != null", "!data.exists()", "newData.exists()",
    "newData.child('authorUid').val() == auth.uid",
    "root.child('social_test').child('postsByUser').child($ownerUid).child($postId).exists()",
    "root.child('social_test').child('profiles').child($ownerUid).child('visibility').val() == 'public'",
    "root.child('social_test').child('followers').child($ownerUid).child(auth.uid).child('status').val() == 'accepted'",
):
    assert required in write, f"comment create rule missing {required}"
validate = comment[".validate"]
for required in (
    "newData.numChildren() == 5", "newData.child('authorUid').val() == auth.uid",
    "newData.child('text').isString()", "newData.child('text').val().matches(/.*\\S.*/) ",
    "newData.child('text').val().length <= 1000", "newData.child('createdAt').isNumber()",
):
    # Regex whitespace is normalized independently below.
    if required.endswith(" "):
        assert required.rstrip() in validate
    else:
        assert required in validate, f"comment validation missing {required}"

saved_uid = social["savedPosts"]["$uid"]
assert saved_uid[".read"] == "auth != null && auth.uid == $uid"
saved = saved_uid["$ownerUid"]["$postId"]
saved_write = saved[".write"]
assert "auth != null && auth.uid == $uid" in saved_write
assert "!newData.exists()" in saved_write, "owner must be able to remove a saved entry"
assert "root.child('social_test').child('postsByUser').child($ownerUid).child($postId).exists()" in saved_write
assert "child('mediaType').val() == 'video'" in saved_write
assert "profiles').child($ownerUid).child('visibility').val() == 'public'" in saved_write
assert "followers').child($ownerUid).child(auth.uid).child('status').val() == 'accepted'" in saved_write
saved_validate = saved[".validate"]
for required in (
    "newData.numChildren() == 3", "newData.child('ownerUid').val() == $ownerUid",
    "newData.child('postId').val() == $postId", "newData.child('savedAt').isNumber()",
):
    assert required in saved_validate, f"savedPosts validation missing {required}"

# The existing likes layout and per-user leaf remain present and unchanged in behavior.
assert social["likes"]["$ownerUid"]["$postId"]["$likerUid"][".write"] == (
    "auth != null && auth.uid == $likerUid && root.child('social_test').child('postsByUser').child($ownerUid).child($postId).exists() && "
    "(auth.uid == $ownerUid || root.child('social_test').child('profiles').child($ownerUid).child('visibility').val() == 'public' || "
    "root.child('social_test').child('followers').child($ownerUid).child(auth.uid).child('status').val() == 'accepted')"
)
assert social["likes"]["$ownerUid"]["$postId"]["$likerUid"][".validate"] == "!newData.exists() || newData.val() == true"
print("Social .test RTDB rules scope and interaction constraints passed (static/read-only).")
