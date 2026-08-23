import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import toast from "react-hot-toast";
import Dashboard from "../pages/Dashboard";
import { incokalkAPI } from "../lib/api";
import { useAuthStore } from "../stores/auth";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    analytics: {
      dashboard: vi.fn(),
      shipmentsOverTime: vi.fn(),
      shipmentsByStatus: vi.fn(),
      costByCarrier: vi.fn(),
      costByMode: vi.fn(),
      topRoutes: vi.fn(),
      incotermUsage: vi.fn(),
      weightDistribution: vi.fn(),
      volumeDistribution: vi.fn(),
      costTrends: vi.fn(),
      carrierPerformance: vi.fn(),
    },
    currencies: { convert: vi.fn() },
    simulation: { getHistory: vi.fn(), delete: vi.fn() },
    billing: { getPlans: vi.fn() },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const stats = {
  totalShipments: 42,
  activeShipments: 5,
  totalShippingCost: 123456,
  averageShippingCost: 2940,
  totalCarriers: 8,
  activeCarriers: 6,
  totalCo2Kg: 1000,
  averageCo2PerShipment: 23.8,
  totalWeightKg: 5000,
  totalVolumeM3: 30,
  totalGoodsValue: 900000,
  simulationsThisMonth: 12,
};

const simulations = [
  {
    id: "sim-1",
    createdAt: "2026-08-01T10:00:00Z",
    incotermCode: "FOB",
    originCountry: "CN",
    destinationCountry: "FR",
    productValue: 5000,
    currency: "EUR",
    totalCost: 6200.5,
  },
];

function mockDefaults() {
  vi.mocked(incokalkAPI.analytics.dashboard).mockResolvedValue({ data: stats } as never);
  vi.mocked(incokalkAPI.analytics.shipmentsOverTime).mockResolvedValue({ data: [] } as never);
  vi.mocked(incokalkAPI.analytics.shipmentsByStatus).mockResolvedValue({ data: [] } as never);
  vi.mocked(incokalkAPI.analytics.costByCarrier).mockResolvedValue({ data: [] } as never);
  vi.mocked(incokalkAPI.analytics.costByMode).mockResolvedValue({ data: [] } as never);
  vi.mocked(incokalkAPI.analytics.topRoutes).mockResolvedValue({ data: [] } as never);
  vi.mocked(incokalkAPI.analytics.incotermUsage).mockResolvedValue({ data: [] } as never);
  vi.mocked(incokalkAPI.analytics.weightDistribution).mockResolvedValue({ data: [] } as never);
  vi.mocked(incokalkAPI.analytics.volumeDistribution).mockResolvedValue({ data: [] } as never);
  vi.mocked(incokalkAPI.analytics.costTrends).mockResolvedValue({ data: [] } as never);
  vi.mocked(incokalkAPI.analytics.carrierPerformance).mockResolvedValue({ data: [] } as never);
  vi.mocked(incokalkAPI.currencies.convert).mockResolvedValue({ data: { from: "EUR", to: "USD", convertedAmount: 1.08 } } as never);
  vi.mocked(incokalkAPI.simulation.getHistory).mockResolvedValue({ data: { content: simulations, totalElements: 1 } } as never);
  vi.mocked(incokalkAPI.billing.getPlans).mockResolvedValue({ data: [{ recommended: true, priceMonthly: 149, priceAnnual: 1519 }] } as never);
}

function renderDashboard() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <Dashboard />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe("Dashboard page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockDefaults();
  });

  afterEach(() => {
    useAuthStore.setState({ token: null, refreshToken: null, user: null });
  });

  it("prompts to log in when no user is present", () => {
    renderDashboard();
    expect(screen.getByText("Veuillez vous connecter pour voir votre tableau de bord.")).toBeInTheDocument();
  });

  it("renders the KPI cards once analytics data loads", async () => {
    useAuthStore.setState({ token: "tok", refreshToken: "r", user: { id: "u1", email: "a@b.com", firstName: "A", lastName: "B", role: "USER" } as never });
    renderDashboard();

    await waitFor(() => {
      expect(screen.getByText("42")).toBeInTheDocument();
    });
    expect(screen.getByText("8")).toBeInTheDocument();
    expect(screen.getByText("123 456 €")).toBeInTheDocument();
  });

  it("refreshing invalidates analytics and shows a confirmation toast", async () => {
    useAuthStore.setState({ token: "tok", refreshToken: "r", user: { id: "u1", email: "a@b.com", firstName: "A", lastName: "B", role: "USER" } as never });
    renderDashboard();
    await waitFor(() => expect(screen.getByText("42")).toBeInTheDocument());

    fireEvent.click(screen.getByRole("button", { name: /Actualiser/ }));
    expect(toast.success).toHaveBeenCalledWith("Données actualisées");
  });

  it("expands the simulation history and deletes an entry after confirmation", async () => {
    useAuthStore.setState({ token: "tok", refreshToken: "r", user: { id: "u1", email: "a@b.com", firstName: "A", lastName: "B", role: "USER" } as never });
    vi.mocked(incokalkAPI.simulation.delete).mockResolvedValue({} as never);
    const confirmSpy = vi.spyOn(window, "confirm").mockReturnValue(true);

    renderDashboard();
    await waitFor(() => expect(screen.getByText("42")).toBeInTheDocument());

    fireEvent.click(screen.getByText("Historique des simulations"));
    await waitFor(() => {
      expect(screen.getByText("FOB")).toBeInTheDocument();
    });

    const row = screen.getByText("FOB").closest("tr")!;
    fireEvent.click(within(row).getByTitle("Supprimer"));

    expect(confirmSpy).toHaveBeenCalled();
    await waitFor(() => {
      expect(incokalkAPI.simulation.delete).toHaveBeenCalledWith("sim-1");
    });
    await waitFor(() => {
      expect(toast.success).toHaveBeenCalledWith("Simulation supprimée avec succès");
    });
    confirmSpy.mockRestore();
  });

  it("does not delete when the confirmation dialog is dismissed", async () => {
    useAuthStore.setState({ token: "tok", refreshToken: "r", user: { id: "u1", email: "a@b.com", firstName: "A", lastName: "B", role: "USER" } as never });
    const confirmSpy = vi.spyOn(window, "confirm").mockReturnValue(false);

    renderDashboard();
    await waitFor(() => expect(screen.getByText("42")).toBeInTheDocument());
    fireEvent.click(screen.getByText("Historique des simulations"));
    await waitFor(() => screen.getByText("FOB"));

    const row = screen.getByText("FOB").closest("tr")!;
    fireEvent.click(within(row).getByTitle("Supprimer"));

    expect(incokalkAPI.simulation.delete).not.toHaveBeenCalled();
    confirmSpy.mockRestore();
  });

  it("computes the ROI calculator's net savings from the default inputs", async () => {
    useAuthStore.setState({ token: "tok", refreshToken: "r", user: { id: "u1", email: "a@b.com", firstName: "A", lastName: "B", role: "USER" } as never });
    renderDashboard();
    await waitFor(() => expect(screen.getByText("42")).toBeInTheDocument());

    // Defaults: 30 shipments, 250€ avg, 20h saved, 40€/h, 10% margin, plan pro annual 1519/12 ≈ 127€/mois
    // savingsTime = 20*40 = 800, savingsMargin = 0.10*250*30 = 750, total = 1550
    // proMonthly = round(1519/12) = 127, net = 1423
    await waitFor(() => {
      expect(screen.getByText("+1 423 €/mois")).toBeInTheDocument();
    });
  });

  it("shows the simulation quota out of 5 for a FREE-plan user, without an upgrade nudge under 80% usage", async () => {
    useAuthStore.setState({
      token: "tok",
      refreshToken: "r",
      user: { id: "u1", email: "a@b.com", firstName: "A", lastName: "B", role: "USER", plan: "FREE" } as never,
    });
    vi.mocked(incokalkAPI.analytics.dashboard).mockResolvedValue({ data: { ...stats, simulationsThisMonth: 2 } } as never);

    renderDashboard();
    await waitFor(() => expect(screen.getByText("/ 5")).toBeInTheDocument());

    expect(screen.queryByText(/voir les plans/)).not.toBeInTheDocument();
  });

  it("shows an upgrade nudge when a FREE-plan user is near the simulation quota", async () => {
    useAuthStore.setState({
      token: "tok",
      refreshToken: "r",
      user: { id: "u1", email: "a@b.com", firstName: "A", lastName: "B", role: "USER", plan: "FREE" } as never,
    });
    vi.mocked(incokalkAPI.analytics.dashboard).mockResolvedValue({ data: { ...stats, simulationsThisMonth: 4 } } as never);

    renderDashboard();
    await waitFor(() => {
      expect(screen.getByText(/Limite bientôt atteinte/)).toBeInTheDocument();
    });
    const link = screen.getByRole("link", { name: /Limite bientôt atteinte/ });
    expect(link).toHaveAttribute("href", "/pricing");
  });

  it("shows a distinct 'quota reached' nudge once the FREE-plan limit is hit", async () => {
    useAuthStore.setState({
      token: "tok",
      refreshToken: "r",
      user: { id: "u1", email: "a@b.com", firstName: "A", lastName: "B", role: "USER", plan: "FREE" } as never,
    });
    vi.mocked(incokalkAPI.analytics.dashboard).mockResolvedValue({ data: { ...stats, simulationsThisMonth: 5 } } as never);

    renderDashboard();
    await waitFor(() => {
      expect(screen.getByText(/Limite atteinte/)).toBeInTheDocument();
    });
    expect(screen.queryByText(/Limite bientôt atteinte/)).not.toBeInTheDocument();
  });

  it("does not show a quota or upgrade nudge for a paid-plan user", async () => {
    useAuthStore.setState({
      token: "tok",
      refreshToken: "r",
      user: { id: "u1", email: "a@b.com", firstName: "A", lastName: "B", role: "USER", plan: "PRO" } as never,
    });
    vi.mocked(incokalkAPI.analytics.dashboard).mockResolvedValue({ data: { ...stats, simulationsThisMonth: 50 } } as never);

    renderDashboard();
    await waitFor(() => expect(screen.getByText("50")).toBeInTheDocument());

    expect(screen.queryByText("/ 5")).not.toBeInTheDocument();
    expect(screen.queryByText(/voir les plans/)).not.toBeInTheDocument();
  });
});
