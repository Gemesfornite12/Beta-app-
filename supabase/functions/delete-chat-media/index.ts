import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.4";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json",
};

const MEDIA_BUCKET = "chat-media";
const ID_TOKEN_MAX_LENGTH = 4096;
const STORAGE_PATH_MAX_LENGTH = 1024;

type IdentityLookupResponse = {
  users?: Array<{ localId?: string }>;
};

function jsonResponse(body: Record<string, unknown>, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: corsHeaders,
  });
}

function extractBearerToken(request: Request): string | null {
  const header = request.headers.get("authorization") ?? "";
  const match = /^Bearer\s+(\S+)$/i.exec(header);
  if (!match || match[1].length > ID_TOKEN_MAX_LENGTH) return null;
  return match[1];
}

/**
 * Firebase ID tokens are verified by Firebase Identity Toolkit. The endpoint
 * validates the signature, expiry, issuer, audience, and project binding before
 * returning the Firebase localId (the authoritative Firebase UID).
 *
 * This deliberately does not accept a UID supplied by the client and does not
 * use a Firebase service-account private key in the Edge Function.
 */
async function verifyFirebaseIdToken(
  idToken: string,
  firebaseWebApiKey: string,
): Promise<string | null> {
  const response = await fetch(
    `https://identitytoolkit.googleapis.com/v1/accounts:lookup?key=${encodeURIComponent(firebaseWebApiKey)}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ idToken }),
    },
  );

  if (!response.ok) return null;

  const body = (await response.json()) as IdentityLookupResponse;
  const uid = body.users?.length === 1 ? body.users[0]?.localId : undefined;
  return uid && uid.length > 0 ? uid : null;
}

function canonicalStoragePath(input: unknown): string | null {
  if (typeof input !== "string" || input.length === 0 || input.length > STORAGE_PATH_MAX_LENGTH) {
    return null;
  }

  // Reject ambiguous URL-encoded paths. The client sends the raw storage path;
  // this prevents encoded slash/dot-segment tricks from changing the ownership check.
  let path: string;
  try {
    path = decodeURIComponent(input);
  } catch {
    return null;
  }
  if (path !== input || path.includes("\\") || /[\u0000-\u001f\u007f]/.test(path)) {
    return null;
  }

  const parts = path.split("/");
  if (
    parts.length < 4 ||
    parts[0] !== "users" ||
    parts[2] !== "media" ||
    parts.some((part) => part.length === 0 || part === "." || part === "..")
  ) {
    return null;
  }
  return path;
}

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }
  if (request.method !== "POST") {
    return jsonResponse({ error: "Method not allowed" }, 405);
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const firebaseWebApiKey = Deno.env.get("FIREBASE_WEB_API_KEY");

  // Never attempt a delete with incomplete server configuration.
  if (!supabaseUrl || !serviceRoleKey || !firebaseWebApiKey) {
    return jsonResponse({ error: "Delete service is not configured" }, 503);
  }

  const idToken = extractBearerToken(request);
  if (!idToken) {
    return jsonResponse({ error: "Firebase authentication required" }, 401);
  }

  let payload: { storagePath?: unknown };
  try {
    payload = (await request.json()) as { storagePath?: unknown };
  } catch {
    return jsonResponse({ error: "Invalid JSON body" }, 400);
  }

  const storagePath = canonicalStoragePath(payload.storagePath);
  if (!storagePath) {
    return jsonResponse({ error: "Invalid media storage path" }, 400);
  }

  let verifiedUid: string | null;
  try {
    verifiedUid = await verifyFirebaseIdToken(idToken, firebaseWebApiKey);
  } catch {
    // Do not expose Identity Toolkit details to an unauthenticated caller.
    return jsonResponse({ error: "Firebase token verification failed" }, 401);
  }
  if (!verifiedUid) {
    return jsonResponse({ error: "Firebase token verification failed" }, 401);
  }

  const pathUid = storagePath.split("/")[1];
  if (pathUid !== verifiedUid) {
    return jsonResponse({ error: "Media path is not owned by the authenticated user" }, 403);
  }

  const supabase = createClient(supabaseUrl, serviceRoleKey, {
    auth: { autoRefreshToken: false, persistSession: false },
  });
  const { error } = await supabase.storage.from(MEDIA_BUCKET).remove([storagePath]);
  if (error) {
    console.error("Supabase media delete failed", { message: error.message });
    return jsonResponse({ error: "Media deletion failed" }, 502);
  }

  return jsonResponse({ deleted: true }, 200);
});
