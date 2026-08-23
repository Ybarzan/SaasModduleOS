import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import CsrdReport from "../pages/CsrdReport";
import { incokalkAPI } from "../lib/api";
import { useAuthStore } from "../stores/auth";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    csrd: { report: vi.fn() },
  },
}));

const report = {
  companyId: "c1",
  reportPeriod: "2026 T2",
  totalEmissionsCO2: 1250.5,
  scope1: 300,
  scope2: 450,
  scope3: 500.5,
  emissionsByLane: [{ lane: "CN-FR Maritime", co2Tonnes: 200, percentage: 16 }],
  offsetCreditsPurchased: 500,
  offsetCreditsRetired: 300,
  netEmissions: 950.5,
  esrsE1Compliant: true,
  recommendations: ["Réduire les émissions Scope 3"],
  generatedAt: "2026-08-01T00:00:00Z",
};

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <CsrdReport />
    </QueryClientProvider>
  );
}

function loginAsUser() {
  useAuthStore.setState({
    token: "tok",
    refreshToken: "r",
    user: { id: "u1", email: "a@b.com", firstName: "A", lastName: "B", role: "USER" } as never,
  });
}

describe("CsrdReport page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(incokalkAPI.csrd.report).mockResolvedValue({ data: report } as never);
  });

  afterEach(() => {
    useAuthStore.setState({ token: null, refreshToken: null, user: null });
  });

  it("renders the emissions scope breakdown and ESRS compliance badge", async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("CN-FR Maritime")).toBeInTheDocument();
    });
    expect(screen.getByText(/Conforme/)).toBeInTheDocument();
    expect(screen.getByText("Réduire les émissions Scope 3")).toBeInTheDocument();
  });

  it("shows an error state with a retry button", async () => {
    vi.mocked(incokalkAPI.csrd.report).mockRejectedValue(new Error("network error"));
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Erreur lors du chargement du rapport")).toBeInTheDocument();
    });

    vi.mocked(incokalkAPI.csrd.report).mockResolvedValue({ data: report } as never);
    fireEvent.click(screen.getByRole("button", { name: "Réessayer" }));
    await waitFor(() => {
      expect(screen.getByText("CN-FR Maritime")).toBeInTheDocument();
    });
  });

  it("hides the refresh button for users without edit rights", async () => {
    renderPage();
    await waitFor(() => screen.getByText("CN-FR Maritime"));
    expect(screen.queryByRole("button", { name: "Actualiser" })).not.toBeInTheDocument();
  });

  it("refetches the report when an editor clicks Actualiser", async () => {
    loginAsUser();
    renderPage();
    await waitFor(() => screen.getByText("CN-FR Maritime"));

    fireEvent.click(screen.getByRole("button", { name: "Actualiser" }));
    await waitFor(() => {
      expect(incokalkAPI.csrd.report).toHaveBeenCalledTimes(2);
    });
  });
});
