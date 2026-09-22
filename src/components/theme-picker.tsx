"use client";

import { useSyncExternalStore } from "react";
import { Button } from "@/components/ui";
import { applyTheme, THEME_KEY, type ThemePref } from "@/lib/theme";

const CHANGE_EVENT = "pk-theme-pref-change";

function subscribe(callback: () => void) {
  window.addEventListener("storage", callback);
  window.addEventListener(CHANGE_EVENT, callback);
  return () => {
    window.removeEventListener("storage", callback);
    window.removeEventListener(CHANGE_EVENT, callback);
  };
}

function getSnapshot(): ThemePref {
  try {
    const stored = localStorage.getItem(THEME_KEY);
    if (stored === "light" || stored === "dark") return stored;
  } catch {
    // ignore
  }
  return "system";
}

function getServerSnapshot(): ThemePref {
  return "system";
}

const options: { value: ThemePref; label: string }[] = [
  { value: "system", label: "System" },
  { value: "light", label: "Light" },
  { value: "dark", label: "Dark" },
];

export function ThemePicker() {
  const pref = useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);

  function choose(value: ThemePref) {
    try {
      applyTheme(value, document.documentElement);
      if (value === "system") {
        localStorage.removeItem(THEME_KEY);
      } else {
        localStorage.setItem(THEME_KEY, value);
      }
    } catch {
      // ignore
    }
    window.dispatchEvent(new Event(CHANGE_EVENT));
  }

  return (
    <div className="flex items-center gap-2">
      {options.map((option) => (
        <Button
          key={option.value}
          type="button"
          variant={pref === option.value ? "primary" : "secondary"}
          size="sm"
          onClick={() => choose(option.value)}
        >
          {option.label}
        </Button>
      ))}
    </div>
  );
}
