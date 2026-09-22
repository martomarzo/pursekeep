import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";
import { z } from "zod";
import { amountToMinor, captureHash, parseCapture } from "@/lib/wallet/engine";
import { capturePayloadSchema } from "@/lib/wallet/types";

const dir = path.resolve(__dirname, "..", "android", "contract", "fixtures");
const load = (name: string) => JSON.parse(readFileSync(path.join(dir, name), "utf8"));

const pairingSchema = z.object({
  v: z.literal(1),
  app: z.literal("pursekeep"),
  url: z.string().url().startsWith("https://"),
  token: z.string().min(1),
});

describe("wallet capture contract v1 (fixtures shared with the Android app)", () => {
  for (const f of ["android-wallet.json", "android-wallet-test.json", "android-bank.json", "ios-transaction.json"]) {
    it(`${f} parses with capturePayloadSchema`, () => {
      expect(capturePayloadSchema.safeParse(load(f)).success).toBe(true);
    });
  }

  it("the app's test capture parses to 1 cent EUR, card 0000, merchant 'PurseKeep test'", () => {
    const payload = capturePayloadSchema.parse(load("android-wallet-test.json"));
    const parsed = parseCapture(payload)!;
    expect(parsed.currency).toBe("EUR");
    expect(amountToMinor(parsed.amountRaw, "EUR")).toBe(1);
    expect(parsed.cardKey).toBe("0000");
    expect(parsed.merchant).toBe("PurseKeep test");
  });

  it("dedupe hash of the wallet fixture is stable", () => {
    const payload = capturePayloadSchema.parse(load("android-wallet.json"));
    // Recorded on first run; a change here means every phone would re-send duplicates.
    expect(captureHash("00000000-0000-0000-0000-000000000000", payload)).toBe("0a79ef62074b3f27befc664b3287c3e01de49fd2db72b98caaf181c9bb48b044");
  });

  it("qr-pairing.json matches the pairing schema", () => {
    expect(pairingSchema.safeParse(load("qr-pairing.json")).success).toBe(true);
  });
});
