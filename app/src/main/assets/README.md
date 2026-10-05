# MediaPipe model assets

The on-device Social camera models are bundled from Google's official MediaPipe model storage:

- `face_landmarker.task` — Face Landmarker float16 model bundle: https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task
- `selfie_segmenter.tflite` — Selfie Segmenter float16 model: https://storage.googleapis.com/mediapipe-models/image_segmenter/selfie_segmenter/float16/1/selfie_segmenter.tflite

SHA-256 checksums verified against the official downloads:

- `face_landmarker.task`: `64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff`
- `selfie_segmenter.tflite`: `191ac9529ae506ee0beefa6b2c945a172dab9d07d1e802a290a4e4038226658b`

Both tasks run on-device; images and camera frames are not sent to a server for these effects.
