const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json",
};

const FIREBASE_PROJECT_ID = "omnistudio-caaf5";
const FIREBASE_DATABASE_URL = "https://omnistudio-caaf5-default-rtdb.firebaseio.com";
const TEST_ANDROID_PACKAGE = "com.aistudio.omnistudio.wkspea.test";
const MAX_ID_TOKEN_LENGTH = 4096;
const MAX_ID_LENGTH = 256;
const GOOGLE_OAUTH_TOKEN_URL = "https://oauth2.googleapis.com/token";
const FIREBASE_IDENTITY_LOOKUP_URL = "https://identitytoolkit.googleapis.com/v1/accounts:lookup";
const GOOGLE_SCOPE = "https://www.googleapis.com/auth/cloud-platform";

type FirebaseIdentity = {
  uid: string;
  email: string;
};

type ServiceAccount = {
  client_email: string;
  private_key: string;
  project_id: string;
};

type DataMap = Record<string, unknown>;

type FirestoreDocument = {
  name?: string;
  fields?: Record<string, { stringValue?: string }>;
};

function jsonResponse(body: Record<string, unknown>, status: number): Response {
  return new Response(JSON.stringify(body), { status, headers: corsHeaders });
}

function text(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

function normalizeEmail(value: unknown): string {
  return text(value).toLowerCase();
}

function extractBearerToken(request: Request): string | null {
  const match = /^Bearer\s+(\S+)$/i.exec(request.headers.get("authorization") ?? "");
  if (!match || match[1].length > MAX_ID_TOKEN_LENGTH) return null;
  return match[1];
}

function isValidDatabaseKey(value: unknown): value is string {
  return typeof value === "string" && value.length > 0 && value.length <= MAX_ID_LENGTH && /^[A-Za-z0-9_.-]+$/.test(value);
}

function asMembers(value: unknown): DataMap[] {
  if (Array.isArray(value)) {
    return value.filter((item): item is DataMap => !!item && typeof item === "object");
  }
  if (value && typeof value === "object") {
    return Object.values(value as Record<string, unknown>)
      .filter((item): item is DataMap => !!item && typeof item === "object");
  }
  return [];
}

async function verifyFirebaseIdToken(idToken: string, apiKey: string): Promise<FirebaseIdentity | null> {
  const response = await fetch(`${FIREBASE_IDENTITY_LOOKUP_URL}?key=${encodeURIComponent(apiKey)}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ idToken }),
  });
  if (!response.ok) return null;
  const payload = await response.json() as { users?: Array<{ localId?: string; email?: string; disabled?: boolean }> };
  const user = payload.users?.length === 1 ? payload.users[0] : undefined;
  const uid = text(user?.localId);
  const email = normalizeEmail(user?.email);
  if (!uid || !email || user?.disabled === true) return null;
  return { uid, email };
}

function decodePrivateKey(pem: string): Uint8Array {
  const clean = pem
    .replace(/-----BEGIN PRIVATE KEY-----/g, "")
    .replace(/-----END PRIVATE KEY-----/g, "")
    .replace(/\\n/g, "\n")
    .replace(/\s/g, "");
  const binary = atob(clean);
  return Uint8Array.from(binary, (character) => character.charCodeAt(0));
}

function base64Url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

async function getGoogleAccessToken(serviceAccount: ServiceAccount): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const header = base64Url(new TextEncoder().encode(JSON.stringify({ alg: "RS256", typ: "JWT" })));
  const claims = base64Url(new TextEncoder().encode(JSON.stringify({
    iss: serviceAccount.client_email,
    scope: GOOGLE_SCOPE,
    aud: GOOGLE_OAUTH_TOKEN_URL,
    iat: now,
    exp: now + 3600,
  })));
  const unsignedJwt = `${header}.${claims}`;
  const privateKey = serviceAccount.private_key.replace(/\\n/g, "\n");
  const key = await crypto.subtle.importKey(
    "pkcs8",
    decodePrivateKey(privateKey),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = new Uint8Array(await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(unsignedJwt),
  ));
  const assertion = `${unsignedJwt}.${base64Url(signature)}`;
  const response = await fetch(GOOGLE_OAUTH_TOKEN_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  if (!response.ok) throw new Error(`Google OAuth token request failed (${response.status})`);
  const tokenBody = await response.json() as { access_token?: string };
  if (!tokenBody.access_token) throw new Error("Google OAuth response had no access token");
  return tokenBody.access_token;
}

async function firebaseGet(path: string, accessToken: string): Promise<unknown> {
  const encodedPath = path.split("/").map(encodeURIComponent).join("/");
  const response = await fetch(`${FIREBASE_DATABASE_URL}/${encodedPath}.json`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  if (!response.ok) throw new Error(`Firebase Database read failed (${response.status})`);
  return await response.json();
}

function firestoreString(document: FirestoreDocument, field: string): string {
  return text(document.fields?.[field]?.stringValue);
}

async function findTestDeviceTokens(emails: string[], accessToken: string): Promise<Array<{ token: string; documentName: string }>> {
  const unique = new Map<string, string>();
  for (const email of emails) {
    const query = {
      structuredQuery: {
        from: [{ collectionId: "fcm_device_tokens" }],
        where: {
          fieldFilter: {
            field: { fieldPath: "email" },
            op: "EQUAL",
            value: { stringValue: email },
          },
        },
        limit: 1000,
      },
    };
    const response = await fetch(
      `https://firestore.googleapis.com/v1/projects/${FIREBASE_PROJECT_ID}/databases/(default)/documents:runQuery`,
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${accessToken}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify(query),
      },
    );
    if (!response.ok) throw new Error(`Firebase token lookup failed (${response.status})`);
    const rows = await response.json() as Array<{ document?: FirestoreDocument }>;
    for (const row of rows) {
      const doc = row.document;
      if (!doc?.name) continue;
      if (firestoreString(doc, "appId") !== TEST_ANDROID_PACKAGE) continue;
      const token = firestoreString(doc, "fcmToken");
      if (token) unique.set(token, doc.name);
    }
  }
  return [...unique.entries()].map(([token, documentName]) => ({ token, documentName }));
}

function messagePreview(message: DataMap): string {
  const body = text(message.text);
  if (body) return body.slice(0, 600);
  const mediaType = text(message.mediaType).toLowerCase();
  if (mediaType === "image" || mediaType === "photo") return "Te enviaron una imagen";
  if (mediaType === "video") return "Te enviaron un video";
  if (mediaType === "audio" || mediaType === "voice") return "Te enviaron un audio";
  if (mediaType === "file" || mediaType === "document") return "Te enviaron un archivo";
  return "Nuevo mensaje";
}

async function sendFcm(token: string, payload: Record<string, string>, accessToken: string): Promise<Response> {
  return await fetch(`https://fcm.googleapis.com/v1/projects/${FIREBASE_PROJECT_ID}/messages:send`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      message: {
        token,
        data: payload,
        android: {
          priority: "HIGH",
          collapseKey: `chat_${payload.channelId}`.slice(0, 64),
        },
      },
    }),
  });
}

async function removeStaleToken(documentName: string, accessToken: string): Promise<void> {
  const response = await fetch(`https://firestore.googleapis.com/v1/${documentName}`, {
    method: "DELETE",
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  if (!response.ok && response.status !== 404) {
    console.warn("Could not remove a stale test-device token", { status: response.status });
  }
}

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (request.method !== "POST") return jsonResponse({ error: "Method not allowed" }, 405);

  const idToken = extractBearerToken(request);
  if (!idToken) return jsonResponse({ error: "Firebase authentication required" }, 401);

  const firebaseWebApiKey = Deno.env.get("FIREBASE_WEB_API_KEY");
  const serviceAccountJson = Deno.env.get("FIREBASE_SERVICE_ACCOUNT_JSON");
  if (!firebaseWebApiKey || !serviceAccountJson) {
    return jsonResponse({ error: "Chat notifications are not configured" }, 503);
  }

  let payload: { channelId?: unknown; messageId?: unknown };
  try {
    payload = await request.json();
  } catch {
    return jsonResponse({ error: "Invalid JSON body" }, 400);
  }
  if (!isValidDatabaseKey(payload.channelId) || !isValidDatabaseKey(payload.messageId)) {
    return jsonResponse({ error: "Invalid chat or message identifier" }, 400);
  }

  let identity: FirebaseIdentity | null;
  try {
    identity = await verifyFirebaseIdToken(idToken, firebaseWebApiKey);
  } catch {
    return jsonResponse({ error: "Firebase token verification failed" }, 401);
  }
  if (!identity) return jsonResponse({ error: "Firebase token verification failed" }, 401);

  let serviceAccount: ServiceAccount;
  try {
    serviceAccount = JSON.parse(serviceAccountJson) as ServiceAccount;
  } catch {
    return jsonResponse({ error: "Firebase server credentials are invalid" }, 503);
  }
  if (!serviceAccount.client_email || !serviceAccount.private_key || serviceAccount.project_id !== FIREBASE_PROJECT_ID) {
    return jsonResponse({ error: "Firebase server credentials do not match this project" }, 503);
  }

  try {
    const accessToken = await getGoogleAccessToken(serviceAccount);
    const [channelValue, messageValue] = await Promise.all([
      firebaseGet(`chats/${payload.channelId}`, accessToken),
      firebaseGet(`chats/${payload.channelId}/messages/${payload.messageId}`, accessToken),
    ]);
    const channel = channelValue && typeof channelValue === "object" ? channelValue as DataMap : null;
    const message = messageValue && typeof messageValue === "object" ? messageValue as DataMap : null;
    if (!channel || !message) return jsonResponse({ error: "Chat message not found" }, 404);
    if (message.pushEnvironment !== "test") return jsonResponse({ accepted: true, sent: 0 }, 200);
    if (text(message.senderId) !== identity.uid || normalizeEmail(message.senderEmail) !== identity.email) {
      return jsonResponse({ error: "Message sender does not match the authenticated account" }, 403);
    }

    const members = asMembers(channel.members);
    const memberEmails = [...new Set(members.map((member) => normalizeEmail(member.email)).filter(Boolean))];
    if (!memberEmails.includes(identity.email)) return jsonResponse({ error: "Sender is not a chat member" }, 403);
    const recipientEmails = memberEmails.filter((email) => email !== identity.email);
    if (recipientEmails.length === 0) return jsonResponse({ accepted: true, sent: 0 }, 200);

    const devices = await findTestDeviceTokens(recipientEmails, accessToken);
    if (devices.length === 0) return jsonResponse({ accepted: true, sent: 0 }, 200);

    const isGroup = channel.isGroup === true;
    const fcmPayload = {
      channelId: payload.channelId,
      channelName: text(channel.name) || "Chat",
      senderName: text(message.senderName) || identity.email,
      senderEmail: identity.email,
      text: messagePreview(message),
      isGroup: String(isGroup),
      messageId: payload.messageId,
      pushEnvironment: "test",
    };

    let sent = 0;
    for (const device of devices) {
      const response = await sendFcm(device.token, fcmPayload, accessToken);
      if (response.ok) {
        sent++;
      } else {
        const errorBody = await response.json().catch(() => ({})) as { error?: { details?: Array<{ errorCode?: string }> } };
        const errorCodes = errorBody.error?.details?.map((detail) => detail.errorCode) ?? [];
        if (response.status === 404 || errorCodes.includes("UNREGISTERED")) {
          await removeStaleToken(device.documentName, accessToken);
        } else {
          console.warn("FCM rejected a test chat notification", { status: response.status });
        }
      }
    }

    return jsonResponse({ accepted: true, sent }, 200);
  } catch (error) {
    console.error("Test chat notification request failed", {
      message: error instanceof Error ? error.message : "Unknown server error",
    });
    return jsonResponse({ error: "Chat notification delivery failed" }, 502);
  }
});
