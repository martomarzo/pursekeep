import { describe, expect, it } from "vitest";
import { parseClientVersion } from "@/lib/wallet/client-version";

describe("parseClientVersion", () => {
  it("keeps a well-formed android header", () => {
    expect(parseClientVersion("android/0.1.0+1")).toBe("android/0.1.0+1");
  });
  it("returns null for missing or blank", () => {
    expect(parseClientVersion(null)).toBeNull();
    expect(parseClientVersion("   ")).toBeNull();
  });
  it("rejects junk and truncates long values", () => {
    expect(parseClientVersion("<script>")).toBeNull();
    expect(parseClientVersion("android/" + "9".repeat(200))).toHaveLength(100);
  });
});
