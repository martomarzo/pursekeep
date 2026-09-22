export type RegistrationMode = "open" | "invite" | "closed";

/** REGISTRATION_MODE env: open (default, self-host) | invite | closed. Unknown values fail closed. */
export function registrationMode(env: NodeJS.ProcessEnv = process.env): RegistrationMode {
  const v = (env.REGISTRATION_MODE ?? "open").trim().toLowerCase();
  if (v === "open" || v === "invite" || v === "closed") return v;
  return "closed";
}
