# Social face-effect rendering: legacy sample review

Reviewed the official MediaPipe Android `faceeffect` example on 2026-10-07 before adapting its overlay approach:

- [`MainActivity.java`](https://github.com/google-ai-edge/mediapipe/blob/master/mediapipe/examples/android/src/java/com/google/mediapipe/apps/faceeffect/MainActivity.java) (Apache-2.0) selects effects and demonstrates the legacy `multi_face_geometry` stream and pose transform.
- [`BUILD`](https://github.com/google-ai-edge/mediapipe/blob/master/mediapipe/examples/android/src/java/com/google/mediapipe/apps/faceeffect/BUILD) shows that the sample is packaged with the JNI face-effect graph and legacy face-landmark/face-geometry assets.
- [`graphs/face_effect/data`](https://github.com/google-ai-edge/mediapipe/tree/master/mediapipe/graphs/face_effect/data) contains `glasses.pbtxt` mesh geometry and `*.pngblob` graph textures (including glasses and facepaint), rather than standalone Android drawable files.

The reusable idea is to anchor and rotate a decorative effect using face pose/landmarks. Social already applies that idea with the current Tasks Face Landmarker: it maps only the needed normalized landmarks through CameraX to the preview or photo canvas, derives eye-line roll, and draws the ears, glasses, or crown with Kotlin `Canvas` paths. The overlay's Canvas transform is now restored in a `finally` block so it cannot escape into the caller's rendering state.

No legacy asset was copied. The old mesh/texture blobs and graph assets are bound to the C++ JNI graph and its Face Geometry pipeline; they are not directly consumable by the current Kotlin/Compose + Tasks `Canvas` path. No standalone drawable was present in the inspected `faceeffect` app directory. The older detection-rectangle path (including `detections_to_rects`) is likewise not imported: Social's current renderer consumes Tasks landmarks and CameraX coordinate transforms. This avoids importing the old framework, graph calculators, JNI, model stack, or dependencies. See `SocialStickerRendererTest` for Robolectric coverage of visible landmark-anchored rendering at photo and preview canvas sizes and for the no-filter case.

Face analysis and sticker rendering remain local to the device. The port does not enable face identity recognition or add frame/landmark logging.
