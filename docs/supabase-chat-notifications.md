# Chat notifications for OmniStudio `.test`

This path avoids deploying a Firebase Cloud Function. After a test APK saves a message to Realtime Database, it calls the `notify-chat-message` Supabase Edge Function with the message and channel IDs plus the sender's Firebase ID token.

The function validates the ID token with Firebase Identity Toolkit, then uses a server-only Google service-account credential to confirm the message and membership in Realtime Database, find recipient device tokens in Firestore, and send data-only FCM messages. It skips messages not marked `pushEnvironment: "test"` and only targets device records whose `appId` is `com.aistudio.omnistudio.wkspea.test`.

## Required server secrets

Set these only in the Supabase project secret store; never in GitHub, the Android project, or chat:

- `FIREBASE_WEB_API_KEY` — the Firebase Web API key for `omnistudio-caaf5`. The existing secure media-delete function uses the same secret.
- `FIREBASE_SERVICE_ACCOUNT_JSON` — the full JSON contents of a service-account key for `omnistudio-caaf5`. Its account needs permission to read Realtime Database and Firestore and send Firebase Cloud Messaging messages. Keep the private key server-side only.

Supabase provides `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` to Edge Functions automatically; this notification function does not use the Supabase service-role key.

## Deployment

1. In Supabase, open the existing project used by OmniStudio and set the two secrets above.
2. From the repository root, link the Supabase CLI to that project and deploy `notify-chat-message`.
3. Do not deploy this function to the public APK flow. The test-message and package-ID checks are intentional safeguards.
4. Install the latest `.test` APK on both devices, sign in, grant Android notification permission, enable the chat's notification switch, and test with the receiving app in the background.

The Edge Function is source code only until deployed. The sender message is not failed if the notification request fails. The function can return `503` until both secrets and the required Google permissions are configured.
