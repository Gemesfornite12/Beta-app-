export type PushEnvironment = "test" | "production";

const TEST_ANDROID_PACKAGE = "com.aistudio.omnistudio.wkspea.test";
const PRODUCTION_ANDROID_PACKAGE = "com.aistudio.omnistudio.wkspea";

/** Missing environment is treated as test for compatibility with older .test clients. */
export function resolvePushEnvironment(value: unknown): PushEnvironment | null {
  if (value == null) return "test";
  const environment = typeof value === "string" ? value.trim() : "";
  return environment === "test" || environment === "production" ? environment : null;
}

export function androidPackageFor(environment: PushEnvironment): string {
  return environment === "test" ? TEST_ANDROID_PACKAGE : PRODUCTION_ANDROID_PACKAGE;
}

export function callSignalPath(
  environment: PushEnvironment,
  channelId: string,
  callId: string,
): string {
  return environment === "test"
    ? `calls_test/${callId}`
    : `calls/${channelId}/${callId}`;
}
