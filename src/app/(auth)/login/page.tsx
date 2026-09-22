import { LoginForm } from "@/components/login-form";
import { registrationMode } from "@/lib/registration";

export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<{ invite?: string; callbackUrl?: string }>;
}) {
  const { invite, callbackUrl } = await searchParams;
  const mode = registrationMode();

  return (
    <LoginForm showRegisterLink={mode === "open"} inviteCode={invite} callbackUrl={callbackUrl} />
  );
}
