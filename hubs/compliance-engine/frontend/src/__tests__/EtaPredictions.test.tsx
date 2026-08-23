import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import toast from "react-hot-toast";
import EtaPredictions from "../pages/EtaPredictions";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    eta: { list: vi.fn(), stats: vi.fn(), predict: vi.fn(), updateActual: vi.fn() },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const predictions = [
  {
    id: "p1",
    origin: "CN",
    destination: "FR",
    mode: "SEA",
    carrierName: "MSC",
    predictedArrival: "2026-09-15T00:00:00Z",
    confidencePercent: 82,
    confidenceLevel: "HIGH",
    baselineDays: 30,
    predictedDays: 33,
    carrierEstimateDays: 32,
    varianceDays: 3,
    riskFactors: "seasonal,congestion",
    seasonalFactor: 5,
    congestionFactor: 3,
    customsDelayDays: 1,
    weatherDelayDays: 0,
    isOnTime: null,
    actualArrival: null,
    actualDays: null,
    predictionAccuracy: null,
    notes: null,
    createdAt: "2026-08-15T00:00:00Z",
  },
];

function mockDefaults() {
  vi.mocked(incokalkAPI.eta.list).mockResolvedValue({ data: predictions } as never);
  vi.mocked(incokalkAPI.eta.stats).mockResolvedValue({
    data: { total: 1, avgAccuracy: 90, onTimePercent: 80, avgDays: 33 },
  } as never);
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <EtaPredictions />
    </QueryClientProvider>
  );
}

describe("EtaPredictions page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockDefaults();
  });

  it("shows stats and the latest prediction summary", async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Dernière prédiction")).toBeInTheDocument();
    });
    expect(screen.getByText("90%")).toBeInTheDocument();
    expect(screen.getByText("+3 jours")).toBeInTheDocument();
  });

  it("shows the empty state when there are no predictions", async () => {
    vi.mocked(incokalkAPI.eta.list).mockResolvedValue({ data: [] } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Aucune prédiction pour le moment")).toBeInTheDocument();
    });
  });

  it("generates a new prediction", async () => {
    vi.mocked(incokalkAPI.eta.predict).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("Dernière prédiction"));

    const selects = screen.getAllByRole("combobox");
    fireEvent.change(selects[0], { target: { value: "FR" } });
    fireEvent.change(selects[1], { target: { value: "DE" } });
    fireEvent.click(screen.getByRole("button", { name: /Prédire ETA/ }));

    await waitFor(() => {
      expect(incokalkAPI.eta.predict).toHaveBeenCalledWith(
        expect.objectContaining({ origin: "FR", destination: "DE", mode: "SEA" })
      );
    });
    expect(toast.success).toHaveBeenCalledWith("Prédiction générée avec succès");
  });

  it("expands a history row to show risk factors", async () => {
    renderPage();
    await waitFor(() => screen.getByText("MSC"));

    fireEvent.click(screen.getByText("MSC"));
    await waitFor(() => {
      expect(screen.getAllByText("Période de forte affluence").length).toBe(2);
    });
  });

  it("records the actual arrival date for a prediction", async () => {
    vi.mocked(incokalkAPI.eta.updateActual).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("MSC"));
    fireEvent.click(screen.getByText("MSC"));
    await waitFor(() => screen.getByText("Enregistrer l'arrivée réelle"));

    const dateInputs = document.querySelectorAll('input[type="date"]');
    fireEvent.change(dateInputs[dateInputs.length - 1], { target: { value: "2026-09-18" } });

    await waitFor(() => {
      expect(incokalkAPI.eta.updateActual).toHaveBeenCalledWith("p1", { actualArrival: "2026-09-18" });
    });
    expect(toast.success).toHaveBeenCalledWith("Arrivée réelle enregistrée");
  });
});
