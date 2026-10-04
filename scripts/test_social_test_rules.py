#!/usr/bin/env python3
"""Static checks and optional local-emulator compilation for Social .test RTDB rules."""
import json
import os
import shutil
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RULES_PATH = ROOT / "database.rules.json"
rules = json.loads(RULES_PATH.read_text())["rules"]
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
comment_fields = {"authorUid", "authorUsername", "authorDisplayName", "text", "createdAt"}
validate = comment[".validate"]
# The emulator rejects \s/\S and regex line breaks; literal ranges cover whitespace without those escapes.
excluded_chars = (
    chr(9) + "-" + chr(32) + chr(0x84) + "-" + chr(0x86) + " "
    + chr(0xA0) + chr(0x1680) + "".join(chr(cp) for cp in range(0x2000, 0x200B))
    + chr(0x2027) + "-" + chr(0x202A) + chr(0x202F) + chr(0x205F) + chr(0x3000)
)
non_whitespace_match = "newData.child('text').val().matches(/.*[^" + excluded_chars + "].*/)"
assert "newData.hasChildren(['authorUid', 'authorUsername', 'authorDisplayName', 'text', 'createdAt'])" in validate
for required in (
    "newData.child('authorUid').isString()", "newData.child('authorUid').val() == auth.uid",
    "newData.child('authorUsername').isString()", "newData.child('authorDisplayName').isString()",
    "newData.child('text').isString()",
    non_whitespace_match,
    "newData.child('text').val().length <= 1000", "newData.child('createdAt').isNumber()",
):
    assert required in validate, f"comment validation missing {required}"
assert "numChildren" not in validate, "unsupported numChildren() call remains"
assert set(comment) - {".write", ".validate"} == comment_fields | {"$other"}, "comment schema must reject unknown fields"
assert comment["$other"][".validate"] is False, "unexpected comment fields must be rejected"
assert all(comment[field][".validate"] is True for field in comment_fields), "declare allowed comment fields for $other exclusion"

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
saved_fields = {"ownerUid", "postId", "savedAt"}
saved_validate = saved[".validate"]
assert "newData.hasChildren(['ownerUid', 'postId', 'savedAt'])" in saved_validate
for required in (
    "newData.child('ownerUid').isString()", "newData.child('ownerUid').val() == $ownerUid",
    "newData.child('postId').isString()", "newData.child('postId').val() == $postId",
    "newData.child('savedAt').isNumber()",
):
    assert required in saved_validate, f"savedPosts validation missing {required}"
assert "numChildren" not in saved_validate, "unsupported numChildren() call remains"
assert set(saved) - {".write", ".validate"} == saved_fields | {"$other"}, "savedPosts schema must reject unknown fields"
assert saved["$other"][".validate"] is False, "unexpected savedPosts fields must be rejected"
assert all(saved[field][".validate"] is True for field in saved_fields), "declare allowed savedPosts fields for $other exclusion"

# The existing likes layout and per-user leaf remain present and unchanged in behavior.
assert social["likes"]["$ownerUid"]["$postId"]["$likerUid"][".write"] == (
    "auth != null && auth.uid == $likerUid && root.child('social_test').child('postsByUser').child($ownerUid).child($postId).exists() && "
    "(auth.uid == $ownerUid || root.child('social_test').child('profiles').child($ownerUid).child('visibility').val() == 'public' || "
    "root.child('social_test').child('followers').child($ownerUid).child(auth.uid).child('status').val() == 'accepted')"
)
assert social["likes"]["$ownerUid"]["$postId"]["$likerUid"][".validate"] == "!newData.exists() || newData.val() == true"
print("Shared Social RTDB rules scope and interaction constraints passed (static/read-only).")

# If the Firebase CLI is installed, start only a local Database Emulator with these rules.
# The temporary project/config and emulator are isolated; this never contacts live RTDB.
firebase = shutil.which("firebase")
if not firebase:
    print("RTDB emulator compiler check skipped: Firebase CLI is not installed.")
else:
    with tempfile.TemporaryDirectory(prefix="social-test-rtdb-") as temp_dir:
        temp = Path(temp_dir)
        shutil.copy2(RULES_PATH, temp / "database.rules.json")
        config = {
            "database": {"rules": "database.rules.json"},
            "emulators": {"database": {"host": "127.0.0.1", "port": 9001}},
        }
        (temp / "firebase.json").write_text(json.dumps(config))
        env = os.environ.copy()
        env["CI"] = "1"
        try:
            result = subprocess.run(
                [firebase, "emulators:exec", "true", "--project", "demo-social-test", "--only", "database",
                 "--config", str(temp / "firebase.json"), "--non-interactive"],
                cwd=temp, env=env, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                timeout=240, check=False,
            )
        except subprocess.TimeoutExpired as exc:
            raise AssertionError("RTDB emulator compiler check timed out") from exc
        output = result.stdout or ""
        compiler_errors = ("no such method/property", "rules syntax error", "rules compilation error", "error loading database rules")
        if result.returncode != 0 or any(marker in output.lower() for marker in compiler_errors):
            raise AssertionError("RTDB emulator rules compilation failed:\n" + output[-12000:])
        print("RTDB Database Emulator started and accepted the rules (compiler check passed).")
