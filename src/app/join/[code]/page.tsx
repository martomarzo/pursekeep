import { redirect } from "next/navigation";
import { auth } from "@/auth";
import { JoinHouseholdForm } from "@/components/join-household-form";
import { Logo } from "@/components/app-shell";
import { Card } from "@/components/ui";
import { registrationMode } from "@/lib/registration";

export default async function JoinInvitePage({
  params,
}: {
  params: Promise<{ code: string }>;
}) {
  const { code } = await params;
  const session = await auth();
  if (!session?.user?.id) {
    if (registrationMode() === "closed") {
      redirect(`/login?callbackUrl=/join/${code}`);
    }
    redirect(`/register?invite=${encodeURIComponent(code)}`);
  }

  return (
    <div className="flex flex-1 flex-col items-center justify-center px-4 py-12">
      <div className="flex w-full max-w-sm flex-col gap-6">
        <div className="flex flex-col items-center gap-2">
          <Logo className="h-10 w-10" />
          <span className="text-xl font-semibold tracking-tight">PurseKeep</span>
          <span className="text-sm text-muted">Household finance</span>
        </div>

        <Card className="flex flex-col gap-4">
          <div className="flex flex-col gap-1 text-center">
            <h1 className="text-lg font-semibold tracking-tight">Join household</h1>
            <p className="text-sm text-muted">
              You&apos;ve been invited to join a household. Confirm below to accept.
            </p>
          </div>
          <JoinHouseholdForm defaultCode={code} />
        </Card>
      </div>
    </div>
  );
}
