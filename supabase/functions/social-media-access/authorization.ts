export type SocialMediaRecord = {
  ownerUid?: string;
  mediaPath?: string;
  mediaItems?: Array<{ mediaPath?: string }>;
  expiresAt?: number;
};

/** Accept both legacy single-media records and the shared carousel/split-video items. */
export function recordContainsMediaPath(record: SocialMediaRecord, mediaPath: string): boolean {
  return record.mediaPath === mediaPath ||
    (Array.isArray(record.mediaItems) && record.mediaItems.some((item) => item?.mediaPath === mediaPath));
}

/** Validate only the existing shared social_test/{uid}/{posts|stories}/{file} layout. */
export function safePath(value: unknown): string | null {
  const MAX_PATH_LENGTH = 512;
  if (typeof value !== "string" || value.length === 0 || value.length > MAX_PATH_LENGTH) return null;
  let decoded: string;
  try {
    decoded = decodeURIComponent(value);
  } catch {
    return null;
  }
  if (decoded !== value || value.includes("\\") || /[\u0000-\u001f\u007f]/.test(value)) return null;
  const parts = value.split("/");
  if (
    parts.length !== 4 ||
    parts[0] !== "social_test" ||
    !/^[A-Za-z0-9_-]{1,128}$/.test(parts[1]) ||
    (parts[2] !== "posts" && parts[2] !== "stories") ||
    !/^[A-Za-z0-9_-]{1,100}\.(jpg|png|webp|gif|heic|heif|mp4|mov|webm|3gp|m4v)$/.test(parts[3])
  ) return null;
  return value;
}
