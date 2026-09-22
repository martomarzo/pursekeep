import { describe, expect, it } from "vitest";
import { applyTheme } from "@/lib/theme";

function fakeRoot() {
  const attrs = new Map<string, string>();
  return {
    attrs,
    setAttribute(n: string, v: string) {
      attrs.set(n, v);
    },
    removeAttribute(n: string) {
      attrs.delete(n);
    },
  };
}

describe("applyTheme", () => {
  it("removes data-theme for system", () => {
    const root = fakeRoot();
    root.attrs.set("data-theme", "dark");
    applyTheme("system", root);
    expect(root.attrs.has("data-theme")).toBe(false);
  });

  it("sets data-theme to light", () => {
    const root = fakeRoot();
    applyTheme("light", root);
    expect(root.attrs.get("data-theme")).toBe("light");
  });

  it("sets data-theme to dark", () => {
    const root = fakeRoot();
    applyTheme("dark", root);
    expect(root.attrs.get("data-theme")).toBe("dark");
  });
});
