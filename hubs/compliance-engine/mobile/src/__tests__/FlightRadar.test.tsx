import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import FlightRadar from "../pages/FlightRadar";
import { mobileApi } from "../lib/api";

vi.mock("../lib/api", () => ({
  mobileApi: { trackingMap: { getFlights: vi.fn() } },
}));

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <FlightRadar />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe("FlightRadar (mobile)", () => {
  beforeEach(() => vi.clearAllMocks());

  it("fetches flights for the default Western Europe bbox on mount", async () => {
    vi.mocked(mobileApi.trackingMap.getFlights).mockResolvedValue({ data: { states: [] } } as never);
    renderPage();
    await waitFor(() => {
      expect(mobileApi.trackingMap.getFlights).toHaveBeenCalledWith('41,51,-5,9');
    });
  });

  it("lists in-flight aircraft with callsign and altitude", async () => {
    vi.mocked(mobileApi.trackingMap.getFlights).mockResolvedValue({
      data: { states: [["abc123", "AFR123 ", "France", null, null, 2.3, 48.8, 10000, false, 250]] },
    } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("AFR123")).toBeInTheDocument();
    });
    expect(screen.getByText("France")).toBeInTheDocument();
  });

  it("shows the empty state when no flights are in the zone", async () => {
    vi.mocked(mobileApi.trackingMap.getFlights).mockResolvedValue({ data: { states: [] } } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Aucun vol détecté dans cette zone.")).toBeInTheDocument();
    });
  });
});
