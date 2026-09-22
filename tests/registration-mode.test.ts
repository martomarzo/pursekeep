import { describe, expect, it } from "vitest";
import { registrationMode } from "@/lib/registration";

function env(vars: Record<string, string | undefined>): NodeJS.ProcessEnv {
  return vars as NodeJS.ProcessEnv;
}

describe("registrationMode", () => {
  it("defaults to open when unset", () => {
    expect(registrationMode(env({}))).toBe("open");
  });

  it("accepts each explicit value", () => {
    expect(registrationMode(env({ REGISTRATION_MODE: "open" }))).toBe("open");
    expect(registrationMode(env({ REGISTRATION_MODE: "invite" }))).toBe("invite");
    expect(registrationMode(env({ REGISTRATION_MODE: "closed" }))).toBe("closed");
  });

  it("fails closed on an unknown value", () => {
    expect(registrationMode(env({ REGISTRATION_MODE: "banana" }))).toBe("closed");
    expect(registrationMode(env({ REGISTRATION_MODE: "" }))).toBe("closed");
  });

  it("tolerates whitespace and case", () => {
    expect(registrationMode(env({ REGISTRATION_MODE: "  Invite  " }))).toBe("invite");
    expect(registrationMode(env({ REGISTRATION_MODE: "CLOSED" }))).toBe("closed");
    expect(registrationMode(env({ REGISTRATION_MODE: " OPEN" }))).toBe("open");
  });
});
