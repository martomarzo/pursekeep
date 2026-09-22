/** Pure, in-memory sliding-window rate limiter. No external store — state is
 *  per-process, which is fine for a single-instance deployment (self-host or
 *  a single Render web service); it resets on restart/redeploy. */

export type LimitRule = { limit: number; windowMs: number };

export class SlidingWindowLimiter {
  private readonly attempts = new Map<string, number[]>();

  constructor(
    private readonly rule: LimitRule,
    private readonly now: () => number = Date.now,
  ) {}

  private prune(key: string): number[] {
    const cutoff = this.now() - this.rule.windowMs;
    const existing = this.attempts.get(key);
    if (!existing) return [];
    const pruned = existing.filter((t) => t > cutoff);
    if (pruned.length === 0) {
      this.attempts.delete(key);
    } else if (pruned.length !== existing.length) {
      this.attempts.set(key, pruned);
    }
    return pruned;
  }

  /** Returns whether another attempt is allowed and, if not, how long until it is. Does not record. */
  check(key: string): { allowed: boolean; retryAfterMs: number } {
    const attempts = this.prune(key);
    if (attempts.length < this.rule.limit) {
      return { allowed: true, retryAfterMs: 0 };
    }
    const oldest = attempts[0];
    const retryAfterMs = Math.max(0, oldest + this.rule.windowMs - this.now());
    return { allowed: false, retryAfterMs };
  }

  /** Record a failed attempt. */
  fail(key: string): void {
    const attempts = this.prune(key);
    attempts.push(this.now());
    this.attempts.set(key, attempts);
  }

  /** Clear a key (on success). */
  reset(key: string): void {
    this.attempts.delete(key);
  }
}

export const loginLimiter = new SlidingWindowLimiter({ limit: 10, windowMs: 15 * 60_000 });
export const registerLimiter = new SlidingWindowLimiter({ limit: 5, windowMs: 15 * 60_000 });

/** First IP from x-forwarded-for (or x-real-ip, else "unknown") + "|" + extra, lowercased. */
export function clientKey(headers: Headers, extra = ""): string {
  const forwarded = headers.get("x-forwarded-for");
  const ip = forwarded ? forwarded.split(",")[0].trim() : (headers.get("x-real-ip") ?? "unknown").trim();
  const resolvedIp = ip === "" ? "unknown" : ip;
  return `${resolvedIp}|${extra}`.toLowerCase();
}
