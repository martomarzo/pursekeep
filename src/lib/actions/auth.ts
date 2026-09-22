"use server";

import bcrypt from "bcryptjs";
import { eq } from "drizzle-orm";
import { headers } from "next/headers";
import { z } from "zod";
import { signIn } from "@/auth";
import { db } from "@/db";
import { invites, memberships, users } from "@/db/schema";
import { seedPersonalLedger } from "@/db/seed-personal";
import { findUsableInvite } from "./household";
import { registrationMode } from "@/lib/registration";
import { clientKey, loginLimiter, registerLimiter } from "@/lib/rate-limit";

const registerSchema = z.object({
  email: z.email("Enter a valid email"),
  password: z.string().min(8, "Password must be at least 8 characters"),
  displayName: z.string().min(1, "Enter your name").max(80),
});

export type ActionResult = { ok: true } | { ok: false; error: string };

const INVITE_ERROR =
  "Registration is by invitation. Ask a household member for an invite link.";

export async function register(
  _prev: ActionResult | null,
  formData: FormData,
): Promise<ActionResult> {
  const ipKey = clientKey(await headers());
  if (!registerLimiter.check(ipKey).allowed) {
    return { ok: false, error: "Too many attempts. Try again later." };
  }

  const mode = registrationMode();
  if (mode === "closed") {
    return { ok: false, error: "Registration is closed." };
  }

  const parsed = registerSchema.safeParse({
    email: formData.get("email"),
    password: formData.get("password"),
    displayName: formData.get("displayName"),
  });
  if (!parsed.success) {
    registerLimiter.fail(ipKey);
    return { ok: false, error: parsed.error.issues[0].message };
  }
  const email = parsed.data.email.toLowerCase();

  const existing = await db.query.users.findFirst({
    where: eq(users.email, email),
  });
  if (existing) {
    return { ok: false, error: "An account with this email already exists" };
  }

  const inviteCode = String(formData.get("invite") ?? "").trim();
  const invite = inviteCode ? await findUsableInvite(inviteCode) : undefined;
  if (inviteCode && !invite) {
    registerLimiter.fail(ipKey);
    return { ok: false, error: INVITE_ERROR };
  }
  if (!inviteCode && mode === "invite") {
    registerLimiter.fail(ipKey);
    return { ok: false, error: INVITE_ERROR };
  }

  const passwordHash = await bcrypt.hash(parsed.data.password, 12);
  const householdId = await db.transaction(async (tx) => {
    const [user] = await tx
      .insert(users)
      .values({ email, passwordHash, displayName: parsed.data.displayName })
      .returning({ id: users.id });
    await seedPersonalLedger(tx, user.id);
    if (invite) {
      await tx
        .insert(memberships)
        .values({ householdId: invite.householdId, userId: user.id, role: "member" });
      await tx.update(invites).set({ usedByUserId: user.id }).where(eq(invites.id, invite.id));
    }
    return invite?.householdId ?? null;
  });

  await signIn("credentials", {
    email,
    password: parsed.data.password,
    redirectTo: householdId ? `/households/${householdId}` : "/",
  });
  return { ok: true };
}

export async function login(
  _prev: ActionResult | null,
  formData: FormData,
): Promise<ActionResult> {
  const email = String(formData.get("email") ?? "").toLowerCase();
  const key = clientKey(await headers(), email);
  const status = loginLimiter.check(key);
  if (!status.allowed) {
    const minutes = Math.max(1, Math.ceil(status.retryAfterMs / 60_000));
    return { ok: false, error: `Too many failed attempts. Try again in ${minutes} minutes.` };
  }

  const rawCallbackUrl = String(formData.get("callbackUrl") ?? "");
  const redirectTo = rawCallbackUrl.startsWith("/") && !rawCallbackUrl.startsWith("//") ? rawCallbackUrl : "/";

  try {
    await signIn("credentials", {
      email,
      password: String(formData.get("password") ?? ""),
      redirectTo,
    });
    loginLimiter.reset(key);
    return { ok: true };
  } catch (error) {
    // next-auth signals redirects by throwing — rethrow those, resetting the
    // limiter first since a redirect here means signIn succeeded.
    if (error instanceof Error && error.message.includes("NEXT_REDIRECT")) {
      loginLimiter.reset(key);
      throw error;
    }
    loginLimiter.fail(key);
    return { ok: false, error: "Invalid email or password" };
  }
}
