import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import SharedLandedCost from "../pages/SharedLandedCost";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    landedCosts: { getPublic: vi.fn() },
  },
}));

const data = {
  id: "lc1",
  calculationName: "Devis Client X",
  originCountry: "CN",
  destinationCountry: "FR",
  incoterm: "CIF",
  hsCode: "",
  transportMode: "SEA",
  productValue: 1000,
  currency: "EUR",
  freightCost: 100,
  insuranceCost: 10,
  dutyAmount: 120,
  dutyRate: 12,
  vatAmount: 200,
  vatRate: 20,
  portCharges: 0,
  customsFees: 0,
  handlingFees: 0,
  lastMileCost: 0,
  totalLandedCost: 1430,
  unitCount: 1,
  totalLandedCostPerUnit: 1430,
  margin: 200,
  marginPercent: 14,
  sellingPrice: 1630,
  notes: "",
};

function renderPage(token: string) {
  return render(
    <MemoryRouter initialEntries={[`/shared/landed-cost/${token}`]}>
      <Routes>
        <Route path="/shared/landed-cost/:token" element={<SharedLandedCost />} />
      </Routes>
    </MemoryRouter>
  );
}

describe("SharedLandedCost page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders the cost breakdown for a valid share token", async () => {
    vi.mocked(incokalkAPI.landedCosts.getPublic).mockResolvedValue({ data } as never);
    renderPage("tok-123");

    await waitFor(() => {
      expect(screen.getByText("Devis Client X")).toBeInTheDocument();
    });
    expect(incokalkAPI.landedCosts.getPublic).toHaveBeenCalledWith("tok-123");
    expect(screen.getByText("Coût total débarqué")).toBeInTheDocument();
  });

  it("shows an invalid-link message when the token fails to resolve", async () => {
    vi.mocked(incokalkAPI.landedCosts.getPublic).mockRejectedValue({
      response: { data: { message: "Lien expiré" } },
    });
    renderPage("expired-token");

    await waitFor(() => {
      expect(screen.getByText("Lien invalide")).toBeInTheDocument();
    });
    expect(screen.getByText("Lien expiré")).toBeInTheDocument();
  });

  it("shows the margin section when a selling price is set", async () => {
    vi.mocked(incokalkAPI.landedCosts.getPublic).mockResolvedValue({ data } as never);
    renderPage("tok-123");

    await waitFor(() => screen.getByText("Devis Client X"));
    expect(screen.getByRole("heading", { name: "Marge" })).toBeInTheDocument();
  });
});
