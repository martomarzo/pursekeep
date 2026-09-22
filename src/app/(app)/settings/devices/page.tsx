import { requireUserId } from "@/lib/session";
import { listWalletCardMappings, listWalletDevices } from "@/lib/queries";
import { WalletDevicesPanel } from "@/components/wallet-devices-panel";
import { ButtonLink, PageHeader } from "@/components/ui";

export default async function DevicesSettingsPage() {
  const userId = await requireUserId();
  const [devices, mappings] = await Promise.all([
    listWalletDevices(userId),
    listWalletCardMappings(userId),
  ]);

  return (
    <div className="flex flex-1 flex-col gap-8">
      <PageHeader
        title="Devices"
        description="Phones running the PurseKeep app. Each device gets a token, shown once as a QR code."
      />
      <ButtonLink href="/wallet" variant="secondary" size="sm">
        Captured payments
      </ButtonLink>
      <WalletDevicesPanel
        devices={devices.map((d) => ({
          id: d.id,
          name: d.name,
          createdAt: d.createdAt.toISOString().slice(0, 10),
          lastSeenAt: d.lastSeenAt ? d.lastSeenAt.toISOString().slice(0, 16).replace("T", " ") : null,
          revoked: d.revokedAt != null,
          clientVersion: d.clientVersion ?? null,
        }))}
        mappings={mappings}
      />
    </div>
  );
}
