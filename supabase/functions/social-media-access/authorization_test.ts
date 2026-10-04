import { recordContainsMediaPath, safePath } from "./authorization.ts";

Deno.test("legacy single-media posts keep signed-download access", () => {
  const path = "social_test/user_1/posts/legacy.mp4";
  if (!recordContainsMediaPath({ mediaPath: path }, path)) {
    throw new Error("legacy mediaPath was rejected");
  }
});

Deno.test("multi-media carousel and split video clips are authorized by mediaItems", () => {
  const clipA = "social_test/user_1/posts/split-part-0001.mp4";
  const clipB = "social_test/user_1/posts/split-part-0002.mp4";
  const record = { mediaPath: clipA, mediaItems: [{ mediaPath: clipA }, { mediaPath: clipB }] };
  if (!recordContainsMediaPath(record, clipA) || !recordContainsMediaPath(record, clipB)) {
    throw new Error("a stored mediaItems path was rejected");
  }
  if (recordContainsMediaPath(record, "social_test/user_1/posts/unlisted.mp4")) {
    throw new Error("an unlisted object path was accepted");
  }
});

Deno.test("safePath preserves only the existing shared social_test layout", () => {
  const splitClip = "social_test/user_1/posts/split-part-0001.mp4";
  if (safePath(splitClip) !== splitClip) {
    throw new Error("standard split clip path was rejected");
  }
  if (
    safePath("social_test/user_1/stories/story.gif") !==
      "social_test/user_1/stories/story.gif"
  ) {
    throw new Error("existing story path was rejected");
  }
  if (safePath("social-production/user_1/posts/post.mp4") !== null) {
    throw new Error("new production namespace was allowed");
  }
  if (safePath("social_test/user_1/posts/splits/part.mp4") !== null) {
    throw new Error("nested split path was allowed");
  }
});
