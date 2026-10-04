import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.4";
import { recordContainsMediaPath, safePath, type SocialMediaRecord } from "./authorization.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json",
};

const MEDIA_BUCKET = "social-test-media";
const MAX_ID_TOKEN_LENGTH = 4096;
const SIGNED_URL_TTL_SECONDS = 600;
const FIREBASE_RTDB_URL = "https://omnistudio-caaf5-default-rtdb.firebaseio.com";

const supportedTypes: Record<string, { extension: string; kind: "image" | "video" }> = {
  "image/jpeg": { extension: "jpg", kind: "image" },
  "image/png": { extension: "png", kind: "image" },
  "image/webp": { extension: "webp", kind: "image" },
  "image/gif": { extension: "gif", kind: "image" },
  "image/heic": { extension: "heic", kind: "image" },
  "image/heif": { extension: "heif", kind: "image" },
  "video/mp4": { extension: "mp4", kind: "video" },
  "video/quicktime": { extension: "mov", kind: "video" },
  "video/webm": { extension: "webm", kind: "video" },
  "video/3gpp": { extension: "3gp", kind: "video" },
  "video/x-m4v": { extension: "m4v", kind: "video" },
};

type IdentityLookupResponse = { users?: Array<{ localId?: string }> };
function jsonResponse(body: Record<string, unknown>, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: corsHeaders });
}

function bearerToken(request: Request): string | null {
  const match = /^Bearer\s+(\S+)$/i.exec(request.headers.get("authorization") ?? "");
  return match && match[1].length <= MAX_ID_TOKEN_LENGTH ? match[1] : null;
}

async function verifyFirebaseIdToken(token: string, webApiKey: string): Promise<string | null> {
  const response = await fetch(
    `https://identitytoolkit.googleapis.com/v1/accounts:lookup?key=${encodeURIComponent(webApiKey)}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ idToken: token }),
    },
  );
  if (!response.ok) return null;
  const body = (await response.json()) as IdentityLookupResponse;
  const uid = body.users?.length === 1 ? body.users[0]?.localId : undefined;
  return uid && uid.length > 0 ? uid : null;
}

async function visibleRecord(
  idToken: string,
  entityType: "post" | "story",
  ownerUid: string,
  entityId: string,
): Promise<SocialMediaRecord | null> {
  if (!/^[A-Za-z0-9_-]{1,128}$/.test(ownerUid) || !/^[A-Za-z0-9_-]{1,128}$/.test(entityId)) return null;
  const node = entityType === "story" ? "storiesByUser" : "postsByUser";
  const url = `${FIREBASE_RTDB_URL}/social_test/${node}/${encodeURIComponent(ownerUid)}/${encodeURIComponent(entityId)}.json?auth=${encodeURIComponent(idToken)}`;
  const response = await fetch(url);
  if (!response.ok) return null;
  const record = (await response.json()) as SocialMediaRecord | null;
  if (!record || record.ownerUid !== ownerUid) return null;
  if (entityType === "story" && (typeof record.expiresAt !== "number" || record.expiresAt <= Date.now())) return null;
  return record;
}

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (request.method !== "POST") return jsonResponse({ error: "Method not allowed" }, 405);

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const firebaseWebApiKey = Deno.env.get("FIREBASE_WEB_API_KEY");
  if (!supabaseUrl || !serviceRoleKey || !firebaseWebApiKey) {
    return jsonResponse({ error: "Social media service is not configured" }, 503);
  }

  const idToken = bearerToken(request);
  if (!idToken) return jsonResponse({ error: "Firebase authentication required" }, 401);

  let uid: string | null;
  try {
    uid = await verifyFirebaseIdToken(idToken, firebaseWebApiKey);
  } catch {
    return jsonResponse({ error: "Firebase token verification failed" }, 401);
  }
  if (!uid) return jsonResponse({ error: "Firebase token verification failed" }, 401);

  let payload: Record<string, unknown>;
  try {
    payload = (await request.json()) as Record<string, unknown>;
  } catch {
    return jsonResponse({ error: "Invalid JSON body" }, 400);
  }

  const supabase = createClient(supabaseUrl, serviceRoleKey, {
    auth: { autoRefreshToken: false, persistSession: false },
  });
  const storage = supabase.storage.from(MEDIA_BUCKET);

  if (payload.action === "create-upload") {
    const mimeType = typeof payload.mimeType === "string" ? payload.mimeType.toLowerCase() : "";
    const mediaInfo = supportedTypes[mimeType];
    const entityType = payload.entityType === "story" ? "stories" : payload.entityType === "post" ? "posts" : null;
    if (!mediaInfo || !entityType) return jsonResponse({ error: "Unsupported media type" }, 400);
    const fileName = `${crypto.randomUUID()}.${mediaInfo.extension}`;
    const storagePath = `social_test/${uid}/${entityType}/${fileName}`;
    const { data, error } = await storage.createSignedUploadUrl(storagePath, { upsert: false });
    if (error || !data?.signedUrl || !data.token) {
      console.error("Social signed upload URL creation failed", { message: error?.message });
      return jsonResponse({ error: "Could not prepare a private upload" }, 502);
    }
    return jsonResponse({ storagePath, signedUrl: data.signedUrl, token: data.token, mimeType });
  }

  if (payload.action === "signed-download") {
    const storagePath = safePath(payload.storagePath);
    const entityType = payload.entityType === "story" ? "story" : payload.entityType === "post" ? "post" : null;
    const entityId = typeof payload.entityId === "string" ? payload.entityId : "";
    if (!storagePath || !entityType) return jsonResponse({ error: "Invalid media request" }, 400);
    const parts = storagePath.split("/");
    const ownerUid = parts[1];
    const expectedFolder = entityType === "story" ? "stories" : "posts";
    if (ownerUid === uid && parts[2] !== expectedFolder) return jsonResponse({ error: "Media path does not match the item" }, 400);
    if (parts[2] !== expectedFolder) return jsonResponse({ error: "Media path does not match the item" }, 400);

    let record: SocialMediaRecord | null;
    try {
      record = await visibleRecord(idToken, entityType, ownerUid, entityId);
    } catch {
      return jsonResponse({ error: "Could not verify social media access" }, 502);
    }
    if (!record || !recordContainsMediaPath(record, storagePath)) {
      return jsonResponse({ error: "Media is unavailable or private" }, 403);
    }

    const { data, error } = await storage.createSignedUrl(storagePath, SIGNED_URL_TTL_SECONDS);
    if (error || !data?.signedUrl) {
      console.error("Social signed download URL creation failed", { message: error?.message });
      return jsonResponse({ error: "Could not prepare a private download" }, 502);
    }
    return jsonResponse({ signedUrl: data.signedUrl, expiresIn: SIGNED_URL_TTL_SECONDS });
  }

  if (payload.action === "delete") {
    const storagePath = safePath(payload.storagePath);
    if (!storagePath || storagePath.split("/")[1] !== uid) {
      return jsonResponse({ error: "Media path is not owned by the authenticated user" }, 403);
    }
    const { error } = await storage.remove([storagePath]);
    if (error) {
      console.error("Social media deletion failed", { message: error.message });
      return jsonResponse({ error: "Media deletion failed" }, 502);
    }
    return jsonResponse({ deleted: true });
  }

  return jsonResponse({ error: "Unknown action" }, 400);
});
