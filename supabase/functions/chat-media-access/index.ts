import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.4";
import {
  createStoragePath,
  extractBearerToken,
  parseUploadRequest,
} from "./validation.ts";

type IdentityLookupResponse = { users?: Array<{ localId?: string }> };

const MEDIA_BUCKET = "chat-media";
const MAX_REQUEST_BODY_BYTES = 16 * 1024;
const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json; charset=utf-8",
  "Cache-Control": "no-store",
};

function jsonResponse(body: Record<string, unknown>, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: corsHeaders });
}

async function verifyFirebaseIdToken(idToken: string, webApiKey: string): Promise<string | null> {
  const response = await fetch(
    `https://identitytoolkit.googleapis.com/v1/accounts:lookup?key=${encodeURIComponent(webApiKey)}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ idToken }),
    },
  );
  if (!response.ok) return null;
  const body = (await response.json()) as IdentityLookupResponse;
  const uid = body.users?.length === 1 ? body.users[0]?.localId : undefined;
  return uid && /^[A-Za-z0-9_-]{1,128}$/.test(uid) ? uid : null;
}

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (request.method !== "POST") return jsonResponse({ error: "Method not allowed" }, 405);

  const contentType = request.headers.get("content-type")?.split(";", 1)[0].trim().toLowerCase();
  if (contentType !== "application/json") return jsonResponse({ error: "JSON body required" }, 415);
  const contentLength = request.headers.get("content-length");
  if (contentLength && (!/^\d+$/.test(contentLength) || Number(contentLength) > MAX_REQUEST_BODY_BYTES)) {
    return jsonResponse({ error: "Upload permission request is too large" }, 413);
  }

  const idToken = extractBearerToken(request.headers.get("authorization"));
  if (!idToken) return jsonResponse({ error: "Firebase authentication required" }, 401);

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const firebaseWebApiKey = Deno.env.get("FIREBASE_WEB_API_KEY");
  if (!supabaseUrl || !serviceRoleKey || !firebaseWebApiKey) {
    return jsonResponse({ error: "Chat media upload is not configured" }, 503);
  }

  let uid: string | null;
  try {
    uid = await verifyFirebaseIdToken(idToken, firebaseWebApiKey);
  } catch {
    return jsonResponse({ error: "Firebase token verification failed" }, 401);
  }
  if (!uid) return jsonResponse({ error: "Firebase token verification failed" }, 401);

  let input: unknown;
  try {
    const reader = request.body?.getReader();
    if (!reader) return jsonResponse({ error: "Invalid JSON body" }, 400);
    const chunks: Uint8Array[] = [];
    let totalBytes = 0;
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      totalBytes += value.byteLength;
      if (totalBytes > MAX_REQUEST_BODY_BYTES) {
        await reader.cancel();
        return jsonResponse({ error: "Upload permission request is too large" }, 413);
      }
      chunks.push(value);
    }
    const body = new Uint8Array(totalBytes);
    let offset = 0;
    for (const chunk of chunks) {
      body.set(chunk, offset);
      offset += chunk.byteLength;
    }
    input = JSON.parse(new TextDecoder().decode(body));
  } catch {
    return jsonResponse({ error: "Invalid JSON body" }, 400);
  }
  const upload = parseUploadRequest(input);
  if (!upload) return jsonResponse({ error: "Unsupported media type or size" }, 400);

  // The authenticated UID is the only identity/path input. Client-provided UIDs,
  // storage paths, and filenames are rejected by parseUploadRequest.
  const storagePath = createStoragePath(uid, upload.mimeType);
  const supabase = createClient(supabaseUrl, serviceRoleKey, {
    auth: { autoRefreshToken: false, persistSession: false },
  });
  const { data, error } = await supabase.storage.from(MEDIA_BUCKET).createSignedUploadUrl(
    storagePath,
    { upsert: false },
  );
  if (error || !data?.signedUrl) {
    console.error("Chat media signed upload permission creation failed");
    return jsonResponse({ error: "Could not prepare a secure upload" }, 502);
  }

  return jsonResponse({
    storagePath,
    signedUrl: data.signedUrl,
    mimeType: upload.mimeType,
    maxUploadBytes: 50 * 1024 * 1024,
  });
});
