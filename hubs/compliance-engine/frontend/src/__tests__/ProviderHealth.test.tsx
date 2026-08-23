import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import ProviderHealth from "../pages/ProviderHealth";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: { providers: { health: vi.fn() } },
}));

const healthList = [
  { providerType: "SHIPPO", providerName: "Shippo", healthStatus: "HEALTHY", lastHealthCheck: "2026-08-20T10:00:00Z", consecutiveFailures: 0 },
  { providerType: "DHL", providerName: "DHL Express", healthStatus: "DOWN", lastHealthCheck: "2026-08-20T09:00:00Z", consecutiveFailures: 3 },
];

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <ProviderHealth />
    </QueryClientProvider>
  );
}

describe("ProviderHealth page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("shows a loading state then the health summary", async () => {
    vi.mocked(incokalkAPI.providers.health).mockResolvedValue({ data: healthList } as never);
    renderPage();
    expect(screen.getByText("Chargement de l'état de santé...")).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText("Shippo")).toBeInTheDocument();
    });
    expect(screen.getByText("DHL Express")).toBeInTheDocument();
    expect(screen.getAllByText("Hors ligne").length).toBeGreaterThan(0);
    expect(screen.getByText(/échec\(s\)/)).toBeInTheDocument();
  });

  it("shows the empty state when no providers are configured", async () => {
    vi.mocked(incokalkAPI.providers.health).mockResolvedValue({ data: [] } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Aucun fournisseur configuré")).toBeInTheDocument();
    });
  });

  it("computes the summary counts correctly", async () => {
    vi.mocked(incokalkAPI.providers.health).mockResolvedValue({ data: healthList } as never);
    renderPage();
    await waitFor(() => screen.getByText("Shippo"));

    // 2 connected, 1 healthy, 0 degraded, 1 down
    const counts = screen.getAllByText(/^[0-9]+$/).map((el) => el.textContent);
    expect(counts).toEqual(["2", "1", "0", "1"]);
  });
});
