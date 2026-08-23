import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import LandedCostCalculator from "../pages/LandedCostCalculator";
import { incokalkAPI } from "../lib/api";
import { useAuthStore } from "../stores/auth";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    landedCosts: { list: vi.fn(), stats: vi.fn(), calculate: vi.fn(), delete: vi.fn(), whatIf: vi.fn(), share: vi.fn() },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const calcResult = {
  id: "lc1",
  calculationName: "Test",
  originCountry: "CN",
  destinationCountry: "FR",
  incoterm: "CIF",
  hsCode: "",
  transportMode: "SEA",
  productValue: 1000,
  currency: "EUR",
  freightCost: 100,
  insuranceCost: 10,
  portCharges: 0,
  customsFees: 0,
  handlingFees: 0,
  lastMileCost: 0,
  dutyAmount: 120,
  dutyRate: 12,
  vatAmount: 200,
  vatRate: 20,
  totalLandedCost: 1430,
  unitCount: 1,
  totalLandedCostPerUnit: 1430,
  margin: 0,
  marginPercent: 0,
  sellingPrice: 0,
  notes: "",
  createdAt: "2026-08-01T00:00:00Z",
};

function loginAsUser() {
  useAuthStore.setState({
    token: "tok",
    refreshToken: "r",
    user: { id: "u1", email: "a@b.com", firstName: "A", lastName: "B", role: "USER" } as never,
  });
}

function mockDefaults() {
  vi.mocked(incokalkAPI.landedCosts.list).mockResolvedValue({ data: [calcResult] } as never);
  vi.mocked(incokalkAPI.landedCosts.stats).mockResolvedValue({ data: { total: 1, avgTotalLandedCost: 1430, avgMargin: 0 } } as never);
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <LandedCostCalculator />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe("LandedCostCalculator page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    loginAsUser();
    mockDefaults();
  });

  afterEach(() => {
    useAuthStore.setState({ token: null, refreshToken: null, user: null });
  });

  it("calculates a landed cost", async () => {
    vi.mocked(incokalkAPI.landedCosts.calculate).mockResolvedValue({ data: calcResult } as never);
    const { container } = renderPage();
    await waitFor(() => screen.getByText("Calculs précédents"));

    fireEvent.change(screen.getAllByPlaceholderText("0.00")[0], { target: { value: "1000" } });
    fireEvent.click(container.querySelector('form button[type="submit"]')!);

    await waitFor(() => {
      expect(incokalkAPI.landedCosts.calculate).toHaveBeenCalledWith(
        expect.objectContaining({ productValue: 1000 })
      );
    });
    expect(screen.getByText("Ventilation des coûts")).toBeInTheDocument();
  });

  it("suggests next-step actions once a result is computed", async () => {
    vi.mocked(incokalkAPI.landedCosts.calculate).mockResolvedValue({ data: calcResult } as never);
    const { container } = renderPage();
    await waitFor(() => screen.getByText("Calculs précédents"));

    expect(screen.queryByText("Prochaine étape")).not.toBeInTheDocument();

    fireEvent.change(screen.getAllByPlaceholderText("0.00")[0], { target: { value: "1000" } });
    fireEvent.click(container.querySelector('form button[type="submit"]')!);

    await waitFor(() => {
      expect(screen.getByText("Prochaine étape")).toBeInTheDocument();
    });
    expect(screen.getByRole("link", { name: /Créer l'expédition/ })).toHaveAttribute("href", "/shipments");
    expect(screen.getByRole("link", { name: /Assurer la marchandise/ })).toHaveAttribute("href", "/assurance-cargo");
  });

  it("shows the empty state when there are no saved calculations", async () => {
    vi.mocked(incokalkAPI.landedCosts.list).mockResolvedValue({ data: [] } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Aucun calcul enregistré")).toBeInTheDocument();
    });
  });

  it("deletes a calculation after confirmation", async () => {
    vi.mocked(incokalkAPI.landedCosts.delete).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByTitle("Supprimer"));

    fireEvent.click(screen.getByTitle("Supprimer"));
    fireEvent.click(screen.getByText("Oui"));

    await waitFor(() => {
      expect(incokalkAPI.landedCosts.delete).toHaveBeenCalledWith("lc1");
    });
  });

  it("compares what-if scenarios", async () => {
    vi.mocked(incokalkAPI.landedCosts.whatIf).mockResolvedValue({ data: [calcResult, calcResult] } as never);
    renderPage();
    await waitFor(() => screen.getByText("Calculs précédents"));

    fireEvent.click(screen.getByRole("button", { name: /What-If/ }));
    fireEvent.click(screen.getByRole("button", { name: /Comparer les scénarios/ }));

    await waitFor(() => {
      expect(incokalkAPI.landedCosts.whatIf).toHaveBeenCalledWith(
        expect.arrayContaining([expect.objectContaining({ calculationName: "Scénario A" })])
      );
    });
  });
});
