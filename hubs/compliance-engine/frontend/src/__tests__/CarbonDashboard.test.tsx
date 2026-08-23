import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import CarbonDashboard from "../pages/CarbonDashboard";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    carbonOffsets: { list: vi.fn(), dashboard: vi.fn(), create: vi.fn(), delete: vi.fn() },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const offsets = [
  {
    id: "o1",
    co2EmissionsKg: 1200,
    offsetCreditsPurchased: 1000,
    offsetCreditsRetired: 800,
    offsetProvider: "ClimatePartner",
    offsetProjectName: "Reforestation Amazonie",
    offsetProjectType: "reforestation",
    offsetCostPerTon: 15,
    offsetTotalCost: 18,
    offsetCurrency: "EUR",
    certificationId: "CERT-1",
    retiredAt: "",
    status: "TRACKING",
    notes: "",
    createdAt: "2026-08-01T00:00:00Z",
  },
];

const dashboardStats = { totalEmissions: 5000, totalOffset: 3000, netEmissions: 2000, offsetPercent: 60, totalCost: 450 };

function mockDefaults() {
  vi.mocked(incokalkAPI.carbonOffsets.list).mockResolvedValue({ data: offsets } as never);
  vi.mocked(incokalkAPI.carbonOffsets.dashboard).mockResolvedValue({ data: dashboardStats } as never);
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <CarbonDashboard />
    </QueryClientProvider>
  );
}

describe("CarbonDashboard page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockDefaults();
  });

  it("renders stats and the offsets history", async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Reforestation Amazonie")).toBeInTheDocument();
    });
    expect(screen.getByText("60.0%")).toBeInTheDocument();
  });

  it("shows the empty state when there are no offset records", async () => {
    vi.mocked(incokalkAPI.carbonOffsets.list).mockResolvedValue({ data: [] } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Aucun enregistrement carbone")).toBeInTheDocument();
    });
  });

  it("creates a new carbon record", async () => {
    vi.mocked(incokalkAPI.carbonOffsets.create).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("Reforestation Amazonie"));

    fireEvent.click(screen.getByText("Nouvel enregistrement"));
    fireEvent.change(screen.getByPlaceholderText("Nom du fournisseur"), { target: { value: "South Pole" } });
    fireEvent.change(screen.getByPlaceholderText("Nom du projet"), { target: { value: "Eolien Maroc" } });
    const co2Inputs = screen.getAllByPlaceholderText("0");
    fireEvent.change(co2Inputs[0], { target: { value: "500" } });
    fireEvent.click(screen.getByRole("button", { name: "Enregistrer" }));

    await waitFor(() => {
      expect(incokalkAPI.carbonOffsets.create).toHaveBeenCalledWith(
        expect.objectContaining({ co2EmissionsKg: 500, offsetProvider: "South Pole", offsetProjectName: "Eolien Maroc" })
      );
    });
  });

  it("deletes an offset record after confirmation", async () => {
    vi.mocked(incokalkAPI.carbonOffsets.delete).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("Reforestation Amazonie"));

    fireEvent.click(screen.getByTitle("Supprimer"));
    fireEvent.click(screen.getByText("Oui"));

    await waitFor(() => {
      expect(incokalkAPI.carbonOffsets.delete).toHaveBeenCalledWith("o1");
    });
  });
});
