import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import EtaPredictions from "../pages/EtaPredictions";
import { mobileApi } from "../lib/api";
import { useAuthStore } from "../stores/auth";

vi.mock("../lib/api", () => ({
  mobileApi: { eta: { list: vi.fn() } },
}));

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <EtaPredictions />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe("EtaPredictions (mobile)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.setState({ token: "tok", refreshToken: "r", user: { id: "u1", email: "a@b.com", firstName: "A", lastName: "B", role: "MANAGER" }, hasHydrated: true });
  });

  it("restricts the page to managers/admins/owners", () => {
    useAuthStore.setState({ user: { id: "u1", email: "a@b.com", firstName: "A", lastName: "B", role: "USER" } });
    renderPage();
    expect(screen.getByText("Réservé aux managers, administrateurs et propriétaires.")).toBeInTheDocument();
    expect(mobileApi.eta.list).not.toHaveBeenCalled();
  });

  it("lists predictions with confidence", async () => {
    vi.mocked(mobileApi.eta.list).mockResolvedValue({
      data: [{ id: "p1", origin: "Shanghai", destination: "Le Havre", predictedArrival: "2026-09-01T00:00:00Z", confidencePercent: 87, confidenceLevel: "HIGH" }],
    } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Shanghai → Le Havre")).toBeInTheDocument();
    });
    expect(screen.getByText("Fiable · 87%")).toBeInTheDocument();
  });

  it("shows the empty state when there are no predictions", async () => {
    vi.mocked(mobileApi.eta.list).mockResolvedValue({ data: [] } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Aucune prédiction disponible.")).toBeInTheDocument();
    });
  });
});
