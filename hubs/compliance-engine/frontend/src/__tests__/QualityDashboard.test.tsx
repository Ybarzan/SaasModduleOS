import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import QualityDashboard from "../pages/QualityDashboard";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: { quality: { metrics: vi.fn() } },
}));

const report = {
  overall: { opportunities: 500, defects: 12, yieldPct: 97.6, dpmo: 24000, sigma: 3.48 },
  characteristics: [
    {
      key: "on_time_delivery",
      label: "Livraison à l'heure",
      description: "Expéditions livrées avant la date estimée",
      result: { opportunities: 300, defects: 8, yieldPct: 97.3, dpmo: 26667, sigma: 3.44 },
    },
    {
      key: "customs_accuracy",
      label: "Conformité douanière",
      description: "Déclarations sans anomalie",
      result: { opportunities: 0, defects: 0, yieldPct: 0, dpmo: 0, sigma: null },
    },
  ],
};

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <QualityDashboard />
    </QueryClientProvider>
  );
}

describe("QualityDashboard page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("shows a loading state while metrics are fetched", () => {
    vi.mocked(incokalkAPI.quality.metrics).mockReturnValue(new Promise(() => {}) as never);
    const { container } = renderPage();
    expect(container.querySelector(".animate-spin")).toBeInTheDocument();
  });

  it("shows an error message when metrics fail to load", async () => {
    vi.mocked(incokalkAPI.quality.metrics).mockRejectedValue(new Error("boom"));
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Impossible de charger les indicateurs qualité.")).toBeInTheDocument();
    });
  });

  it("renders the overall sigma level and CTQ cards", async () => {
    vi.mocked(incokalkAPI.quality.metrics).mockResolvedValue({ data: report } as never);
    renderPage();

    await waitFor(() => {
      expect(screen.getByText("3.48σ")).toBeInTheDocument();
    });
    expect(screen.getByText("Livraison à l'heure")).toBeInTheDocument();
    expect(screen.getByText("8 / 300")).toBeInTheDocument();
  });

  it("shows a no-data message for a CTQ with zero opportunities", async () => {
    vi.mocked(incokalkAPI.quality.metrics).mockResolvedValue({ data: report } as never);
    renderPage();
    await waitFor(() => screen.getByText("Conformité douanière"));
    expect(screen.getByText("Pas encore assez de données pour ce calcul")).toBeInTheDocument();
  });
});
