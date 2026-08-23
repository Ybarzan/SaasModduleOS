import { describe, it, expect } from "vitest";
import { STATUS_CONFIG, STATUS_LABELS, CLIENT_STATUS_CONFIG, TRACKING_STATUS_COLORS, INCOTERMS, COUNTRIES } from "../lib/constants";

describe("constants", () => {
  describe("STATUS_CONFIG", () => {
    it("has all 6 shipment statuses", () => {
      expect(Object.keys(STATUS_CONFIG)).toEqual(
        expect.arrayContaining(["DRAFT", "QUOTED", "BOOKED", "IN_TRANSIT", "DELIVERED", "CANCELLED"])
      );
      expect(Object.keys(STATUS_CONFIG)).toHaveLength(6);
    });

    it("each status has label, color, and bg", () => {
      Object.values(STATUS_CONFIG).forEach((config) => {
        expect(config.label).toBeTruthy();
        expect(config.color).toMatch(/^text-/);
        expect(config.bg).toMatch(/^bg-/);
      });
    });

    it("DRAFT is labeled Brouillon", () => {
      expect(STATUS_CONFIG.DRAFT.label).toBe("Brouillon");
    });

    it("DELIVERED is success-colored", () => {
      expect(STATUS_CONFIG.DELIVERED.bg).toContain("success");
    });
  });

  describe("STATUS_LABELS", () => {
    it("has same keys as STATUS_CONFIG", () => {
      expect(Object.keys(STATUS_LABELS)).toEqual(Object.keys(STATUS_CONFIG));
    });

    it("labels match STATUS_CONFIG labels", () => {
      Object.keys(STATUS_LABELS).forEach((key) => {
        expect(STATUS_LABELS[key]).toBe(STATUS_CONFIG[key].label);
      });
    });
  });

  describe("CLIENT_STATUS_CONFIG", () => {
    it("has all 6 statuses", () => {
      expect(Object.keys(CLIENT_STATUS_CONFIG)).toHaveLength(6);
    });

    it("each has label, color, and icon component", () => {
      Object.values(CLIENT_STATUS_CONFIG).forEach((config) => {
        expect(config.label).toBeTruthy();
        expect(config.color).toBeTruthy();
        expect(config.icon).toBeDefined();
      });
    });
  });

  describe("TRACKING_STATUS_COLORS", () => {
    it("has tracking statuses", () => {
      expect(Object.keys(TRACKING_STATUS_COLORS)).toContain("IN_TRANSIT");
      expect(Object.keys(TRACKING_STATUS_COLORS)).toContain("DELIVERED");
      expect(Object.keys(TRACKING_STATUS_COLORS)).toContain("DEPARTED");
    });
  });

  describe("INCOTERMS", () => {
    it("has 11 Incoterms 2020", () => {
      expect(INCOTERMS).toHaveLength(11);
    });

    it("includes all standard codes", () => {
      expect(INCOTERMS).toEqual(
        expect.arrayContaining(["EXW", "FCA", "FAS", "FOB", "CFR", "CIF", "CPT", "CIP", "DAP", "DPU", "DDP"])
      );
    });
  });

  describe("COUNTRIES", () => {
    it("has at least 20 countries", () => {
      expect(COUNTRIES.length).toBeGreaterThanOrEqual(20);
    });

    it("includes France", () => {
      expect(COUNTRIES).toContain("France");
    });

    it("includes major trading partners", () => {
      expect(COUNTRIES).toContain("Chine");
      expect(COUNTRIES).toContain("États-Unis");
      expect(COUNTRIES).toContain("Maroc");
      expect(COUNTRIES).toContain("Allemagne");
    });

    it("no duplicates", () => {
      expect(new Set(COUNTRIES).size).toBe(COUNTRIES.length);
    });
  });
});
