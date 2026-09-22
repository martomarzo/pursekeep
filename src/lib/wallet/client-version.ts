/** Value of the `X-PurseKeep-Client` header, e.g. "android/0.1.0+1". Only
 *  [A-Za-z0-9._+/-] is kept; anything else means "unknown client". */
export function parseClientVersion(header: string | null): string | null {
  const v = (header ?? "").trim();
  if (!v || !/^[A-Za-z0-9._+/-]+$/.test(v)) return null;
  return v.slice(0, 100);
}
