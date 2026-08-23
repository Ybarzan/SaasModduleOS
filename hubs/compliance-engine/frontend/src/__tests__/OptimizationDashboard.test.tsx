import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import toast from "react-hot-toast";
import OptimizationDashboard from "../pages/OptimizationDashboard";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    optimization: {
      getStats: vi.fn(),
      getLaneAnalysis: vi.fn(),
      getRecommendations: vi.fn(),
      getConsolidation: vi.fn(),
      analyzeRoutes: vi.fn(),
      predict: vi.fn(),
      acceptOptimization: vi.fn(),
      acceptConsolidation: vi.fn(),
      findConsolidation: vi.fn(),
    },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const stats = {
  totalRoutes: 12,
  totalOptimizations: 5,
  totalSavings: 3400,
  acceptedSavings: 1200,
  avgConfidence: 0.82,
  pendingOptimizations: 3,
  totalConsolidationOpportunities: 2,
  consolidationSavings: 500,
};

const recommendations = [
  {
    id: "rec1",
    origin: "FR",
    destination: "DE",
    transportMode: "ROAD",
    recommendedCarrier: "DHL",
    confidence: 0.75,
    predictedCost: 450,
    savingsPercent: 12,
    savingsEstimate: 60,
    status: "PENDING",
  },
];

function mockDefaults() {
  vi.mocked(incokalkAPI.optimization.getStats).mockResolvedValue(stats as never);
  vi.mocked(incokalkAPI.optimization.getLaneAnalysis).mockResolvedValue([] as never);
  vi.mocked(incokalkAPI.optimization.getRecommendations).mockResolvedValue([] as never);
  vi.mocked(incokalkAPI.optimization.getConsolidation).mockResolvedValue([] as never);
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <OptimizationDashboard />
    </QueryClientProvider>
  );
}

describe("OptimizationDashboard page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockDefaults();
  });

  it("renders the stats cards", async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("12")).toBeInTheDocument();
    });
    expect(screen.getByText("82%")).toBeInTheDocument();
  });

  // NOTE: several JSX text nodes/attributes in OptimizationDashboard.tsx contain literal
  // "é"-style escape sequences instead of real accented characters — JSX text/attribute
  // literals don't interpret backslash escapes the way JS string literals do, so the page
  // genuinely renders text like "Prédire le tarif" today (flagged separately as a bug,
  // see task_* for the fix). Matchers below target accent-free substrings so they hold
  // regardless of whether that's fixed later.

  it("requires origin and destination before predicting", async () => {
    renderPage();
    await waitFor(() => screen.getByText("12"));
    fireEvent.click(screen.getByRole("button", { name: /tarif/ }));
    expect(toast.error).toHaveBeenCalledWith("Origine et destination requis");
    expect(incokalkAPI.optimization.predict).not.toHaveBeenCalled();
  });

  it("predicts a rate and shows the result", async () => {
    vi.mocked(incokalkAPI.optimization.predict).mockResolvedValue({
      predictedCost: 500,
      recommendedCarrier: "DHL",
      confidence: 0.9,
      savingsEstimate: 40,
    } as never);
    renderPage();
    await waitFor(() => screen.getByText("12"));

    fireEvent.change(screen.getByPlaceholderText("Ex: FR"), { target: { value: "FR" } });
    fireEvent.change(screen.getByPlaceholderText("Ex: DE"), { target: { value: "DE" } });
    fireEvent.click(screen.getByRole("button", { name: /tarif/ }));

    await waitFor(() => {
      expect(incokalkAPI.optimization.predict).toHaveBeenCalledWith(
        expect.objectContaining({ origin: "FR", destination: "DE" })
      );
    });
    await waitFor(() => {
      expect(screen.getByText("DHL")).toBeInTheDocument();
    });
  });

  it("shows the empty state for lanes, recommendations, and consolidation", async () => {
    renderPage();
    await waitFor(() => screen.getByText("12"));
    expect(screen.getByText(/Aucune lane/)).toBeInTheDocument();
    expect(screen.getByText("Aucune recommandation disponible. Lancez une analyse des routes.")).toBeInTheDocument();
    expect(screen.getByText(/Aucune opportunit/)).toBeInTheDocument();
  });

  it("triggers a route analysis", async () => {
    vi.mocked(incokalkAPI.optimization.analyzeRoutes).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("12"));

    fireEvent.click(screen.getByText("Analyser les routes"));
    await waitFor(() => {
      expect(incokalkAPI.optimization.analyzeRoutes).toHaveBeenCalled();
    });
  });

  it("accepts a pending recommendation", async () => {
    vi.mocked(incokalkAPI.optimization.getRecommendations).mockResolvedValue(recommendations as never);
    vi.mocked(incokalkAPI.optimization.acceptOptimization).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText(/DHL/));

    fireEvent.click(screen.getByText("Accepter"));
    await waitFor(() => {
      expect(incokalkAPI.optimization.acceptOptimization).toHaveBeenCalledWith("rec1");
    });
  });
});
