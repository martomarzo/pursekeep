export type ThemePref = "system" | "light" | "dark";

export const THEME_KEY = "pk.theme";

export function applyTheme(
  pref: ThemePref,
  root: { setAttribute(n: string, v: string): void; removeAttribute(n: string): void },
): void {
  if (pref === "system") {
    root.removeAttribute("data-theme");
  } else {
    root.setAttribute("data-theme", pref);
  }
}
