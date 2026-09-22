"use client";

import Link from "next/link";
import { useActionState } from "react";
import { login } from "@/lib/actions/auth";
import { Button, ErrorText, inputClass, labelClass } from "@/components/ui";

export function LoginForm({
  showRegisterLink,
  inviteCode,
  callbackUrl,
}: {
  showRegisterLink: boolean;
  inviteCode?: string;
  callbackUrl?: string;
}) {
  const [state, formAction, pending] = useActionState(login, null);

  return (
    <form action={formAction} className="flex flex-col gap-4">
      {callbackUrl && <input type="hidden" name="callbackUrl" value={callbackUrl} />}

      <div className="flex flex-col gap-1">
        <label htmlFor="email" className={labelClass}>
          Email
        </label>
        <input
          id="email"
          name="email"
          type="email"
          autoComplete="email"
          required
          className={inputClass}
        />
      </div>

      <div className="flex flex-col gap-1">
        <label htmlFor="password" className={labelClass}>
          Password
        </label>
        <input
          id="password"
          name="password"
          type="password"
          autoComplete="current-password"
          required
          className={inputClass}
        />
      </div>

      {state && !state.ok && <ErrorText>{state.error}</ErrorText>}

      <Button type="submit" disabled={pending} className="mt-2 w-full">
        {pending ? "Signing in..." : "Sign in"}
      </Button>

      {showRegisterLink && (
        <p className="text-center text-sm text-muted">
          Don&apos;t have an account?{" "}
          <Link href="/register" className="font-medium text-foreground underline">
            Register
          </Link>
        </p>
      )}

      {inviteCode && (
        <p className="text-center text-sm text-muted">
          Have an invite?{" "}
          <Link
            href={`/register?invite=${encodeURIComponent(inviteCode)}`}
            className="font-medium text-foreground underline"
          >
            Create your account
          </Link>
        </p>
      )}
    </form>
  );
}
