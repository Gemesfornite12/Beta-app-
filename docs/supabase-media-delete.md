# Secure chat-media deletion

`delete-chat-media` is the only supported path for deleting a chat attachment from
Supabase Storage. The Android client sends its current Firebase ID token and the
raw object path; it never receives or sends a Supabase service-role key.

## Security model

1. The Edge Function receives `Authorization: Bearer <Firebase ID token>` and a
   JSON body such as `{ "storagePath": "users/<firebase-uid>/media/<id>/<name>" }`.
2. It sends the token to Firebase Identity Toolkit `accounts:lookup`. The
   returned `localId` is the authoritative Firebase UID; a UID supplied in the
   request body is never trusted.
3. It accepts only `users/{uid}/media/...` paths and requires the first UID
   segment to equal the verified Firebase `localId`.
4. Only after those checks does it call Supabase Storage with the server-only
   `SUPABASE_SERVICE_ROLE_KEY` against the fixed `chat-media` bucket.

The function intentionally does not contain a Firebase service-account private
key. Identity Toolkit performs server-side Firebase ID-token validation using
the Firebase project's Web API key.

## Supabase setup

From the repository root, link the CLI to the target Supabase project and set the
following **Supabase Edge Function secrets**. Use real values only in the
Supabase project secret store; do not commit them or put them in the Android APK.

```sh
supabase login
supabase link --project-ref <supabase-project-ref>
supabase secrets set \\
  SUPABASE_URL=https://<project-ref>.supabase.co \\
  SUPABASE_SERVICE_ROLE_KEY=<server-only-service-role-key> \\
  FIREBASE_WEB_API_KEY=<firebase-web-api-key>
```

`SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` are normally available to Edge
Functions automatically, but setting them explicitly is harmless. The
`FIREBASE_WEB_API_KEY` must belong to the same Firebase project that issues the
ID tokens used by OmniStudio. The key is used only for the server-side
Identity Toolkit lookup; do not substitute a Firebase private key.

Deploy the function and its JWT setting:

```sh
supabase functions deploy delete-chat-media
```

The checked-in `supabase/config.toml` contains `verify_jwt = false` because the
Supabase gateway must pass through a Firebase token; the function performs the
Firebase verification itself. If deploying with a CLI/config version that does
not read this file, deploy with the equivalent `--no-verify-jwt` option.

The function is not considered deployed by this repository change. Verify it
in the Supabase dashboard or with a real authenticated request after deployment.

## Client behavior

`SupabaseMediaStorageService` calls:

```text
POST https://<project-ref>.supabase.co/functions/v1/delete-chat-media
Authorization: Bearer <Firebase ID token>
apikey: <Supabase publishable key>
Content-Type: application/json

{"storagePath":"users/<firebase-uid>/media/<id>/<filename>"}
```

The existing message-delete action removes the Supabase object first when the
message URL belongs to this project's `chat-media` bucket. If the secure delete
fails, the message is not removed locally or from Firebase, so a deployment or
secret configuration problem cannot silently orphan the object.
