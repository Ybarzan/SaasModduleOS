import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { timeAgo } from "@/lib/utils";
import { STATUS_LABELS, STATUS_CONFIG, INCOTERMS, COUNTRIES } from "@/lib/constants";

describe("timeAgo", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('returns "à l\'instant" for less than 60 seconds ago', () => {
    vi.setSystemTime(new Date("2026-07-22T12:00:30Z"));
    expect(timeAgo("2026-07-22T12:00:00Z")).toBe("à l'instant");
  });

  it('returns "à l\'instant" for exactly 0 seconds', () => {
    vi.setSystemTime(new Date("2026-07-22T12:00:00Z"));
    expect(timeAgo("2026-07-22T12:00:00Z")).toBe("à l'instant");
  });

  it("returns minutes for 60-3599 seconds", () => {
    vi.setSystemTime(new Date("2026-07-22T12:05:00Z"));
    expect(timeAgo("2026-07-22T12:00:00Z")).toBe("il y a 5 min");
  });

  it("returns 1 min for exactly 60 seconds", () => {
    vi.setSystemTime(new Date("2026-07-22T12:01:00Z"));
    expect(timeAgo("2026-07-22T12:00:00Z")).toBe("il y a 1 min");
  });

  it("returns 59 min for 59 minutes", () => {
    vi.setSystemTime(new Date("2026-07-22T12:59:00Z"));
    expect(timeAgo("2026-07-22T12:00:00Z")).toBe("il y a 59 min");
  });

  it("returns hours for 3600-86399 seconds", () => {
    vi.setSystemTime(new Date("2026-07-22T15:00:00Z"));
    expect(timeAgo("2026-07-22T12:00:00Z")).toBe("il y a 3h");
  });

  it("returns 1h for exactly 3600 seconds", () => {
    vi.setSystemTime(new Date("2026-07-22T13:00:00Z"));
    expect(timeAgo("2026-07-22T12:00:00Z")).toBe("il y a 1h");
  });

  it("returns 23h for 23 hours", () => {
    vi.setSystemTime(new Date("2026-07-23T11:00:00Z"));
    expect(timeAgo("2026-07-22T12:00:00Z")).toBe("il y a 23h");
  });

  it("returns days for >= 86400 seconds", () => {
    vi.setSystemTime(new Date("2026-07-24T12:00:00Z"));
    expect(timeAgo("2026-07-22T12:00:00Z")).toBe("il y a 2j");
  });

  it("returns 1j for exactly 1 day", () => {
    vi.setSystemTime(new Date("2026-07-23T12:00:00Z"));
    expect(timeAgo("2026-07-22T12:00:00Z")).toBe("il y a 1j");
  });

  it("returns 30j for 30 days", () => {
    vi.setSystemTime(new Date("2026-08-21T12:00:00Z"));
    expect(timeAgo("2026-07-22T12:00:00Z")).toBe("il y a 30j");
  });
});

describe("STATUS_LABELS", () => {
  it("has all expected status keys", () => {
    expect(Object.keys(STATUS_LABELS)).toEqual([
      "DRAFT",
      "QUOTED",
      "BOOKED",
      "IN_TRANSIT",
      "DELIVERED",
      "CANCELLED",
    ]);
  });

  it("maps DRAFT to Brouillon", () => {
    expect(STATUS_LABELS.DRAFT).toBe("Brouillon");
  });

  it("maps IN_TRANSIT to En transit", () => {
    expect(STATUS_LABELS.IN_TRANSIT).toBe("En transit");
  });

  it("maps DELIVERED to Livré", () => {
    expect(STATUS_LABELS.DELIVERED).toBe("Livré");
  });

  it("returns undefined for unknown status", () => {
    expect(STATUS_LABELS["UNKNOWN"]).toBeUndefined();
  });
});

describe("STATUS_CONFIG", () => {
  it("has config for every status", () => {
    for (const key of Object.keys(STATUS_LABELS)) {
      expect(STATUS_CONFIG[key]).toBeDefined();
      expect(STATUS_CONFIG[key].label).toBe(STATUS_LABELS[key]);
    }
  });

  it("each config has color and bg classes", () => {
    for (const config of Object.values(STATUS_CONFIG)) {
      expect(config.color).toMatch(/^text-/);
      expect(config.bg).toMatch(/^bg-/);
    }
  });

  it("DRAFT has neutral colors", () => {
    expect(STATUS_CONFIG.DRAFT.color).toBe("text-ink-soft");
    expect(STATUS_CONFIG.DRAFT.bg).toBe("bg-surface-2");
  });

  it("CANCELLED has danger colors", () => {
    expect(STATUS_CONFIG.CANCELLED.color).toBe("text-danger");
    expect(STATUS_CONFIG.CANCELLED.bg).toBe("bg-danger/10");
  });
});

describe("INCOTERMS", () => {
  it("contains all 11 standard incoterms", () => {
    expect(INCOTERMS).toHaveLength(11);
  });

  it("includes common incoterms", () => {
    expect(INCOTERMS).toContain("FOB");
    expect(INCOTERMS).toContain("CIF");
    expect(INCOTERMS).toContain("EXW");
    expect(INCOTERMS).toContain("DDP");
  });
});

describe("COUNTRIES", () => {
  it("contains Morocco", () => {
    expect(COUNTRIES).toContain("Maroc");
  });

  it("contains France", () => {
    expect(COUNTRIES).toContain("France");
  });

  it("has at least 20 countries", () => {
    expect(COUNTRIES.length).toBeGreaterThanOrEqual(20);
  });

  it("has no empty strings", () => {
    expect(COUNTRIES.every((c) => c.length > 0)).toBe(true);
  });
});
