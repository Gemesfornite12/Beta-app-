import { androidPackageFor, callSignalPath, resolvePushEnvironment } from "./push-routing.ts";

function assertEquals<T>(actual: T, expected: T, message: string): void {
  if (actual !== expected) throw new Error(`${message}: expected ${expected}, got ${actual}`);
}

Deno.test("legacy requests stay on the isolated test environment", () => {
  assertEquals(resolvePushEnvironment(undefined), "test", "missing environment");
  assertEquals(androidPackageFor("test"), "com.aistudio.omnistudio.wkspea.test", "test package");
  assertEquals(callSignalPath("test", "dm-1", "call-1"), "calls_test/call-1", "test call path");
});

Deno.test("production requests use the production package and call path", () => {
  assertEquals(resolvePushEnvironment("production"), "production", "production environment");
  assertEquals(androidPackageFor("production"), "com.aistudio.omnistudio.wkspea", "production package");
  assertEquals(callSignalPath("production", "dm-1", "call-1"), "calls/dm-1/call-1", "production call path");
});

Deno.test("unknown environments are rejected", () => {
  assertEquals(resolvePushEnvironment("preview"), null, "unknown environment");
  assertEquals(resolvePushEnvironment(123), null, "non-string environment");
});
