"use client";

import { useActionState } from "react";
import {
  createWalletDevice,
  deleteWalletCardMapping,
  revokeWalletDevice,
  type CreateDeviceResult,
} from "@/lib/actions/wallet";
import { Badge, Button, CardTitle, ErrorText, inputClass } from "@/components/ui";

type Device = {
  id: string;
  name: string;
  createdAt: string;
  lastSeenAt: string | null;
  revoked: boolean;
  clientVersion: string | null;
};
type Mapping = { id: string; cardKey: string; accountId: string; accountName: string };

// `<form action>` wants `(formData) => void | Promise<void>`, but the wallet
// actions return `ActionResult` for programmatic callers. Adapt with void
// wrappers rather than changing the actions' shared shape (same pattern as
// wallet-inbox.tsx, minus the "use server" directive — this file is already
// a client component, so these just call the imported server actions).
async function revokeDeviceAction(formData: FormData) {
  await revokeWalletDevice(formData);
}

async function deleteMappingAction(formData: FormData) {
  await deleteWalletCardMapping(formData);
}

export function WalletDevicesPanel({
  devices,
  mappings,
}: {
  devices: Device[];
  mappings: Mapping[];
}) {
  const [result, formAction, pending] = useActionState<CreateDeviceResult | null, FormData>(
    createWalletDevice,
    null,
  );

  return (
    <div className="flex flex-col gap-8">
      <section className="flex flex-col gap-3">
        <CardTitle>Add device</CardTitle>
        <p className="text-xs text-muted">
          Install the PurseKeep Android app from the{" "}
          <a className="underline" href="https://github.com/martomarzo/pursekeep/releases/latest">latest release</a>, then create a device here and scan its QR code.
        </p>
        <form action={formAction} className="flex flex-wrap items-center gap-2">
          <input
            name="name"
            placeholder="e.g. Martin's Pixel"
            required
            maxLength={60}
            className={`${inputClass} w-auto`}
          />
          <Button type="submit" variant="secondary" size="sm" disabled={pending}>
            Create token
          </Button>
        </form>
        <ErrorText>{result && !result.ok ? result.error : null}</ErrorText>
        {result?.ok && result.token && (
          <div className="rounded-xl border border-border bg-surface p-3 text-sm">
            <p className="font-medium">
              Pair &ldquo;{result.deviceName}&rdquo; now — this code is shown once:
            </p>
            {result.qrSvg && (
              <div
                className="mx-auto my-3 w-60 rounded bg-white p-2 [&>svg]:h-auto [&>svg]:w-full"
                aria-label="Pairing QR code"
                dangerouslySetInnerHTML={{ __html: result.qrSvg }}
              />
            )}
            <p className="text-xs text-muted">
              In the PurseKeep Android app tap <strong>Pair this phone → Scan QR code</strong>. Or
              enter the token by hand:
            </p>
            <code className="mt-1 block break-all rounded bg-surface-muted p-2 text-xs">
              {result.token}
            </code>
          </div>
        )}
      </section>

      <section className="flex flex-col gap-3">
        <CardTitle>Devices</CardTitle>
        {devices.length === 0 && <p className="text-sm text-muted">No devices yet.</p>}
        <ul className="flex flex-col gap-2">
          {devices.map((d) => (
            <li
              key={d.id}
              className="flex items-center justify-between gap-3 rounded-xl border border-border bg-surface px-3 py-2.5 text-sm"
            >
              <div>
                <span className="font-medium">{d.name}</span>
                <span className="ml-2 text-xs text-faint">
                  added {d.createdAt}
                  {d.lastSeenAt ? ` · last seen ${d.lastSeenAt}` : " · never used"}
                  {d.clientVersion ? ` · ${d.clientVersion}` : ""}
                </span>
              </div>
              {d.revoked ? (
                <Badge>revoked</Badge>
              ) : (
                <form action={revokeDeviceAction}>
                  <input type="hidden" name="id" value={d.id} />
                  <button
                    type="submit"
                    className="text-xs text-danger underline-offset-2 hover:underline"
                  >
                    Revoke
                  </button>
                </form>
              )}
            </li>
          ))}
        </ul>
      </section>

      <section className="flex flex-col gap-3">
        <CardTitle>Card mappings</CardTitle>
        <p className="text-xs text-faint">
          Created from the Wallet inbox (&ldquo;remember card&rdquo;). Delete one to re-teach it
          on the next capture.
        </p>
        {mappings.length === 0 && <p className="text-sm text-muted">No mappings yet.</p>}
        <ul className="flex flex-col gap-2">
          {mappings.map((m) => (
            <li
              key={m.id}
              className="flex items-center justify-between gap-3 rounded-xl border border-border bg-surface px-3 py-2.5 text-sm"
            >
              <span>
                <code className="rounded bg-surface-muted px-1.5 py-0.5 text-xs">{m.cardKey}</code>{" "}
                → {m.accountName}
              </span>
              <form action={deleteMappingAction}>
                <input type="hidden" name="id" value={m.id} />
                <button
                  type="submit"
                  className="text-xs text-danger underline-offset-2 hover:underline"
                >
                  Delete
                </button>
              </form>
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
