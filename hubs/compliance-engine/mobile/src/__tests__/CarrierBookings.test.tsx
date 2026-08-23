import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import CarrierBookings from "../pages/CarrierBookings";
import { mobileApi } from "../lib/api";

vi.mock("../lib/api", () => ({
  mobileApi: { carrierBookings: { list: vi.fn() } },
}));

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <CarrierBookings />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe("CarrierBookings (mobile)", () => {
  beforeEach(() => vi.clearAllMocks());

  it("lists bookings with carrier and status", async () => {
    vi.mocked(mobileApi.carrierBookings.list).mockResolvedValue({
      data: [{ id: "b1", carrierReference: "REF-001", carrierBookingStatus: "CONFIRMED", carrier: { name: "Maersk" } }],
    } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Maersk")).toBeInTheDocument();
    });
    expect(screen.getByText("Confirmée")).toBeInTheDocument();
    expect(screen.getByText(/REF-001/)).toBeInTheDocument();
  });

  it("shows the empty state when there are no bookings", async () => {
    vi.mocked(mobileApi.carrierBookings.list).mockResolvedValue({ data: [] } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Aucune réservation.")).toBeInTheDocument();
    });
  });

  it("shows an error message when loading fails", async () => {
    vi.mocked(mobileApi.carrierBookings.list).mockRejectedValue(new Error("network"));
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Impossible de charger les réservations.")).toBeInTheDocument();
    });
  });
});
