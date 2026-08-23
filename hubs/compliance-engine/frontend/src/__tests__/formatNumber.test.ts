import { describe, it, expect } from "vitest";
import { formatNumber, formatEur } from "../lib/formatNumber";

describe("formatNumber", () => {
  it("groups thousands with a plain space", () => {
    expect(formatNumber(1234)).toBe("1 234");
    expect(formatNumber(1234567)).toBe("1 234 567");
  });

  it("does not group numbers under 1000", () => {
    expect(formatNumber(999)).toBe("999");
    expect(formatNumber(0)).toBe("0");
  });

  it("respects minimumFractionDigits, padding with zeros", () => {
    expect(formatNumber(1234.5, { minimumFractionDigits: 2, maximumFractionDigits: 2 })).toBe("1 234,50");
  });

  it("rounds to maximumFractionDigits", () => {
    expect(formatNumber(1234.567, { maximumFractionDigits: 2 })).toBe("1 234,57");
  });

  it("trims trailing zero decimals down to the minimum", () => {
    expect(formatNumber(1234.1, { maximumFractionDigits: 2 })).toBe("1 234,1");
    expect(formatNumber(1234, { minimumFractionDigits: 0, maximumFractionDigits: 2 })).toBe("1 234");
  });

  it("formats negative numbers", () => {
    expect(formatNumber(-1234)).toBe("-1 234");
    expect(formatNumber(-1234.5, { maximumFractionDigits: 1 })).toBe("-1 234,5");
  });

  it("does not render a negative sign for a value that rounds to zero", () => {
    expect(formatNumber(-0.001, { maximumFractionDigits: 0 })).toBe("0");
  });

  it("defaults to zero fraction digits when no options are given", () => {
    expect(formatNumber(1234.9)).toBe("1 235");
  });
});

describe("formatEur", () => {
  it("appends the euro symbol with a space", () => {
    expect(formatEur(1234)).toBe("1 234 €");
  });

  it("forwards formatting options", () => {
    expect(formatEur(1234.5, { minimumFractionDigits: 2, maximumFractionDigits: 2 })).toBe("1 234,50 €");
  });
});
