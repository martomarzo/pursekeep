import Link from "next/link";
import { RegisterForm } from "@/components/register-form";
import { registrationMode } from "@/lib/registration";

export default async function RegisterPage({
  searchParams,
}: {
  searchParams: Promise<{ invite?: string }>;
}) {
  const { invite } = await searchParams;
  const mode = registrationMode();

  if (mode === "closed") {
    return (
      <div className="flex flex-col gap-4 text-center">
        <p className="text-sm text-muted">Registration is closed.</p>
        <Link href="/login" className="font-medium text-foreground underline">
          Back to sign in
        </Link>
      </div>
    );
  }

  if (mode === "invite" && !invite) {
    return (
      <div className="flex flex-col gap-4 text-center">
        <p className="text-sm text-muted">
          Registration is by invitation. Open the invite link you received, or log in.
        </p>
        <Link href="/login" className="font-medium text-foreground underline">
          Back to sign in
        </Link>
      </div>
    );
  }

  return <RegisterForm inviteCode={invite} />;
}
