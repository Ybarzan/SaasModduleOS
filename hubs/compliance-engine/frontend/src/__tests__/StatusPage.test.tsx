import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import StatusPage from "../pages/StatusPage";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    system: { health: vi.fn() },
  },
}));

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <StatusPage />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe("StatusPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("shows the operational state when the health check reports UP", async () => {
    vi.mocked(incokalkAPI.system.health).mockResolvedValue({ data: { status: "UP" } } as never);
    renderPage();

    await waitFor(() => {
      expect(screen.getByText("Tous les systèmes sont opérationnels")).toBeInTheDocument();
    });
    expect(screen.getByText(/Dernière vérification/)).toBeInTheDocument();
  });

  it("shows the outage state when the health check reports a non-UP status", async () => {
    vi.mocked(incokalkAPI.system.health).mockResolvedValue({ data: { status: "DOWN" } } as never);
    renderPage();

    await waitFor(() => {
      expect(screen.getByText("Interruption de service en cours")).toBeInTheDocument();
    });
  });

  it("shows the outage state when the health check request itself fails", async () => {
    vi.mocked(incokalkAPI.system.health).mockRejectedValue(new Error("network error"));
    renderPage();

    await waitFor(() => {
      expect(screen.getByText("Interruption de service en cours")).toBeInTheDocument();
    });
  });

  it("re-checks status when the refresh button is clicked", async () => {
    vi.mocked(incokalkAPI.system.health).mockResolvedValue({ data: { status: "UP" } } as never);
    renderPage();
    await waitFor(() => screen.getByText("Tous les systèmes sont opérationnels"));

    const initialCalls = vi.mocked(incokalkAPI.system.health).mock.calls.length;
    fireEvent.click(screen.getByRole("button", { name: /Vérifier maintenant/ }));

    await waitFor(() => {
      expect(vi.mocked(incokalkAPI.system.health).mock.calls.length).toBeGreaterThan(initialCalls);
    });
  });
});
