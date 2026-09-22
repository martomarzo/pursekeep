import QRCode from "qrcode";

/** Origin the phone should talk to — AUTH_URL is already the public origin
 *  of this deployment (tailnet today, pursekeep.app later). */
export function serverOrigin(): string {
  const raw = process.env.AUTH_URL ?? "";
  return raw.replace(/\/+$/, "");
}

export type PairingJson = { v: 1; app: "pursekeep"; url: string; token: string };

export function pairingJson(token: string): PairingJson {
  return { v: 1, app: "pursekeep", url: serverOrigin(), token };
}

export async function pairingQrSvg(token: string): Promise<{ svg: string; json: string }> {
  const json = JSON.stringify(pairingJson(token));
  const svg = await QRCode.toString(json, { type: "svg", margin: 1, width: 240, errorCorrectionLevel: "M" });
  return { svg, json };
}
