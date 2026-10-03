# Secure chat-media uploads

The chat-media bucket remains public for reading existing message attachments. Uploads use the `chat-media-access` Edge Function so an Android client never receives Supabase service-role access and cannot choose another user's storage path.

## Request and authorization

The Android app calls `POST /functions/v1/chat-media-access` with:

```http
apikey: <stable Supabase publishable key>
Authorization: Bearer <Firebase ID token>
Content-Type: application/json

{"mediaType":"image","mimeType":"image/jpeg","sizeBytes":12345}
```

The function verifies the Firebase ID token through Identity Toolkit and derives the UID from the verified token; client-supplied UIDs, storage paths, and filenames are rejected. It allows only the media types listed in `supabase/functions/chat-media-access/validation.ts`, enforces the 50 MiB limit against the declared file size, generates a random path under `users/{verifiedUid}/media/`, and asks Supabase Storage for a new signed upload URL for each file. The Android app independently checks actual file size before requesting permission. The bucket's configured file-size limit should remain at or below 50 MiB because a request's declared size cannot itself prevent a malicious client from sending more bytes.

The response contains only the random storage path, a short-lived signed upload URL, and the validated MIME type/limit. The app uploads the bytes directly to that URL, then preserves the existing public object URL and chat-message metadata. Video previews go through the same flow and therefore receive their own upload permission.

The function requires the existing server-side `FIREBASE_WEB_API_KEY` and `SUPABASE_SERVICE_ROLE_KEY` Edge Function environment variables. The service-role key is read only by Deno from `Deno.env`; it must never be placed in Android configuration, source, logs, or a GitHub workflow. No existing project secrets are changed by this feature.

## Stable client key

`SUPABASE_PUBLISHABLE_KEY` in `.env.example` is the stable public project `apikey` used for Supabase requests. It is not a Supabase user/session token and should not be rotated or refreshed periodically. The Firebase ID token is used only to request a new upload permission; the returned signed URL authorizes that individual upload. A failed upload must not trigger a refresh loop for the publishable key.

The Edge Function manually verifies Firebase tokens, so `verify_jwt = false` is set for this function in `supabase/config.toml`; Supabase's gateway must not attempt to interpret a Firebase token as a Supabase JWT. The function applies the project's existing CORS convention and returns `Cache-Control: no-store` for upload permissions.

## Verification

Run the Edge Function validation tests with:

```sh
deno check supabase/functions/chat-media-access/index.ts
deno test supabase/functions/chat-media-access/validation_test.ts
```

Run the Android response-validation tests with:

```sh
gradle testDebugUnitTest --tests com.example.data.supabase.SupabaseUploadPermissionTest
```
