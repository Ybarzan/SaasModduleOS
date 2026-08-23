import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import toast from "react-hot-toast";
import Calculator from "../pages/Calculator";
import { incokalkAPI } from "../lib/api";
import { useAuthStore } from "../stores/auth";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    incoterms: { getAll: vi.fn() },
    simulation: { calculate: vi.fn() },
    logistics: { calculateTrucking: vi.fn() },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const incoterms = [
  { id: "1", code: "EXW", fullName: "Ex Works" },
  { id: "2", code: "FOB", fullName: "Free On Board" },
];

const simulationResponse = {
  incotermFullName: "Free On Board",
  buyerRiskScore: 3,
  riskLevel: "MEDIUM",
  estimatedDays: 21,
  buyerCosts: { freight: 500, exportCustoms: 50 },
  sellerCosts: {},
  totalBuyerCost: 1234.56,
  totalSellerCost: 800,
  responsibilities: {},
  recommendations: [],
  warnings: [],
  buyerRisks: [],
  complianceAlerts: [],
  comparison: [],
};

function renderCalculator() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <Calculator />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe("Calculator page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.setState({ token: "tok", refreshToken: "r", user: { id: "u1", email: "a@b.com", firstName: "A", lastName: "B", role: "USER" } as never });
    vi.mocked(incokalkAPI.incoterms.getAll).mockResolvedValue({ data: incoterms } as never);
  });

  it("loads and lists the available incoterms", async () => {
    renderCalculator();
    await waitFor(() => {
      expect(screen.getByText("EXW — Ex Works")).toBeInTheDocument();
    });
    expect(screen.getByText("FOB — Free On Board")).toBeInTheDocument();
  });

  it("shows a placeholder until an incoterm is selected and a calculation runs", async () => {
    renderCalculator();
    await waitFor(() => screen.getByText("EXW — Ex Works"));
    expect(screen.getByText("Sélectionnez un Incoterm et cliquez sur « Calculer »")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Calculer/ })).toBeDisabled();
  });

  it("auto-calculates once an incoterm is selected (debounced) and renders the result", async () => {
    vi.mocked(incokalkAPI.simulation.calculate).mockResolvedValue({ data: simulationResponse } as never);

    renderCalculator();
    await waitFor(() => screen.getByText("EXW — Ex Works"));

    const select = screen.getByDisplayValue("Sélectionner un Incoterm");
    fireEvent.change(select, { target: { value: "2" } });

    await waitFor(
      () => {
        expect(incokalkAPI.simulation.calculate).toHaveBeenCalledWith(
          expect.objectContaining({ incoterm: "FOB" })
        );
      },
      { timeout: 2000 }
    );

    await waitFor(() => {
      expect(screen.getByText("1 234,56 €")).toBeInTheDocument();
    });
    expect(toast.success).toHaveBeenCalledWith("Calcul effectué avec succès !");
  });

  it("shows an error toast when the calculation fails", async () => {
    vi.mocked(incokalkAPI.simulation.calculate).mockRejectedValue({
      response: { data: { message: "Paramètres invalides" } },
    });

    renderCalculator();
    await waitFor(() => screen.getByText("EXW — Ex Works"));
    const select = screen.getByDisplayValue("Sélectionner un Incoterm");
    fireEvent.change(select, { target: { value: "2" } });

    await waitFor(
      () => {
        expect(toast.error).toHaveBeenCalledWith("Paramètres invalides");
      },
      { timeout: 2000 }
    );
  });
});
