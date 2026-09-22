import { describe, expect, it } from "vitest";
import { clientKey, SlidingWindowLimiter } from "@/lib/rate-limit";

function fakeClock(start = 0) {
  let now = start;
  return {
    now: () => now,
    advance: (ms: number) => {
      now += ms;
    },
  };
}

describe("SlidingWindowLimiter", () => {
  it("allows up to the limit and blocks the next attempt", () => {
    const clock = fakeClock();
    const limiter = new SlidingWindowLimiter({ limit: 3, windowMs: 1000 }, clock.now);

    for (let i = 0; i < 3; i++) {
      expect(limiter.check("a").allowed).toBe(true);
      limiter.fail("a");
    }

    const result = limiter.check("a");
    expect(result.allowed).toBe(false);
    expect(result.retryAfterMs).toBeGreaterThan(0);
    expect(result.retryAfterMs).toBeLessThanOrEqual(1000);
  });

  it("computes retryAfterMs based on the oldest attempt in the window", () => {
    const clock = fakeClock();
    const limiter = new SlidingWindowLimiter({ limit: 2, windowMs: 1000 }, clock.now);

    limiter.fail("a"); // t=0
    clock.advance(400);
    limiter.fail("a"); // t=400

    const result = limiter.check("a");
    expect(result.allowed).toBe(false);
    // oldest attempt (t=0) expires at t=1000; now is t=400 -> 600ms left.
    expect(result.retryAfterMs).toBe(600);
  });

  it("frees the key once the window has fully elapsed", () => {
    const clock = fakeClock();
    const limiter = new SlidingWindowLimiter({ limit: 1, windowMs: 1000 }, clock.now);

    limiter.fail("a");
    expect(limiter.check("a").allowed).toBe(false);

    clock.advance(1001);
    expect(limiter.check("a").allowed).toBe(true);
  });

  it("reset clears a key immediately", () => {
    const clock = fakeClock();
    const limiter = new SlidingWindowLimiter({ limit: 1, windowMs: 1000 }, clock.now);

    limiter.fail("a");
    expect(limiter.check("a").allowed).toBe(false);

    limiter.reset("a");
    expect(limiter.check("a").allowed).toBe(true);
  });

  it("tracks keys independently", () => {
    const clock = fakeClock();
    const limiter = new SlidingWindowLimiter({ limit: 1, windowMs: 1000 }, clock.now);

    limiter.fail("a");
    expect(limiter.check("a").allowed).toBe(false);
    expect(limiter.check("b").allowed).toBe(true);
  });

  it("check does not itself record an attempt", () => {
    const clock = fakeClock();
    const limiter = new SlidingWindowLimiter({ limit: 1, windowMs: 1000 }, clock.now);

    limiter.check("a");
    limiter.check("a");
    expect(limiter.check("a").allowed).toBe(true);
  });
});

describe("clientKey", () => {
  it("uses the first IP in x-forwarded-for", () => {
    const headers = new Headers({ "x-forwarded-for": "1.2.3.4, 5.6.7.8" });
    expect(clientKey(headers, "user@example.com")).toBe("1.2.3.4|user@example.com");
  });

  it("falls back to x-real-ip", () => {
    const headers = new Headers({ "x-real-ip": "9.9.9.9" });
    expect(clientKey(headers)).toBe("9.9.9.9|");
  });

  it("falls back to unknown when no IP header is present", () => {
    const headers = new Headers();
    expect(clientKey(headers, "Extra")).toBe("unknown|extra");
  });

  it("lowercases the whole key", () => {
    const headers = new Headers({ "x-forwarded-for": "1.2.3.4" });
    expect(clientKey(headers, "User@Example.com")).toBe("1.2.3.4|user@example.com");
  });
});
