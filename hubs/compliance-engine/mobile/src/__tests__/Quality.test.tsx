import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import Quality from "../pages/Quality";
import { mobileApi } from "../lib/api";
import { useAuthStore } from "../stores/auth";

vi.mock("../lib/api", () => ({
  mobileApi: { quality: { metrics: vi.fn() } },
}));

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <Quality />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe("Quality (mobile)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.setState({ token: "tok", refreshToken: "r", user: { id: "u1", email: "a@b.com", firstName: "A", lastName: "B", role: "MANAGER" }, hasHydrated: true });
  });

  it("restricts the page to managers/admins/owners", () => {
    useAuthStore.setState({ user: { id: "u1", email: "a@b.com", firstName: "A", lastName: "B", role: "USER" } });
    renderPage();
    expect(screen.getByText("Réservé aux managers, administrateurs et propriétaires.")).toBeInTheDocument();
    expect(mobileApi.quality.metrics).not.toHaveBeenCalled();
  });

  it("shows the overall sigma level and per-characteristic breakdown", async () => {
    vi.mocked(mobileApi.quality.metrics).mockResolvedValue({
      data: {
        overall: { opportunities: 1000, yieldPct: 98.5, dpmo: 1500, sigma: 4.4 },
        characteristics: [{ key: "on-time", label: "Livraison à temps", result: { opportunities: 500, yieldPct: 96, dpmo: 4000, sigma: 3.2 } }],
      },
    } as never);
    renderPage();

    await waitFor(() => {
      expect(screen.getByText("4.40σ")).toBeInTheDocument();
    });
    expect(screen.getByText("Livraison à temps")).toBeInTheDocument();
    expect(screen.getByText("3.20σ")).toBeInTheDocument();
  });
});
