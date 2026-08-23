import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import toast from "react-hot-toast";
import CarrierBookings from "../pages/CarrierBookings";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    carrierBookings: { list: vi.fn(), create: vi.fn(), submit: vi.fn(), cancel: vi.fn() },
    carriers: { getAll: vi.fn() },
    shipments: { getAll: vi.fn() },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const bookings = [
  {
    id: "b1",
    carrierReference: "REF-1",
    carrierTrackingNumber: "",
    carrierBookingStatus: "PENDING",
    errorMessage: "",
    serviceType: "STANDARD",
    specialInstructions: "",
    requestedPickupDate: "",
    estimatedPickupDate: "",
    estimatedTransitDays: 5,
    estimatedDeliveryDate: "",
    quotedCost: 340,
    quotedCostCurrency: "EUR",
    createdAt: "2026-08-01T00:00:00Z",
    shipmentOrder: { id: "s1", orderNumber: "ORD-001" },
    carrier: { id: "c1", name: "DHL", code: "DHL" },
  },
];

function mockDefaults() {
  vi.mocked(incokalkAPI.carrierBookings.list).mockResolvedValue({ data: bookings } as never);
  vi.mocked(incokalkAPI.carriers.getAll).mockResolvedValue({ data: [{ id: "c1", name: "DHL", code: "DHL", active: true }] } as never);
  vi.mocked(incokalkAPI.shipments.getAll).mockResolvedValue({ data: [{ id: "s1", orderNumber: "ORD-001", goodsDescription: "Electronics" }] } as never);
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <CarrierBookings />
    </QueryClientProvider>
  );
}

describe("CarrierBookings page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockDefaults();
  });

  it("lists bookings with their status and carrier", async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("REF-1")).toBeInTheDocument();
    });
    expect(screen.getByText("DHL")).toBeInTheDocument();
    expect(screen.getByText("ORD-001")).toBeInTheDocument();
  });

  it("shows the empty state when there are no bookings", async () => {
    vi.mocked(incokalkAPI.carrierBookings.list).mockResolvedValue({ data: [] } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Aucune réservation")).toBeInTheDocument();
    });
  });

  it("disables the create button until a shipment and carrier are chosen", async () => {
    renderPage();
    await waitFor(() => screen.getByText("REF-1"));
    fireEvent.click(screen.getByText("Nouvelle réservation"));
    expect(screen.getByRole("button", { name: /Créer/ })).toBeDisabled();
  });

  it("creates a new booking", async () => {
    vi.mocked(incokalkAPI.carrierBookings.create).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("REF-1"));
    fireEvent.click(screen.getByText("Nouvelle réservation"));

    const selects = screen.getAllByRole("combobox");
    fireEvent.change(selects[0], { target: { value: "s1" } });
    fireEvent.change(selects[1], { target: { value: "c1" } });
    fireEvent.click(screen.getByRole("button", { name: /Créer/ }));

    await waitFor(() => {
      expect(incokalkAPI.carrierBookings.create).toHaveBeenCalledWith(
        expect.objectContaining({ shipmentOrderId: "s1", carrierId: "c1" })
      );
    });
    expect(toast.success).toHaveBeenCalledWith("Réservation créée");
  });

  it("submits a pending booking to the carrier", async () => {
    vi.mocked(incokalkAPI.carrierBookings.submit).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("REF-1"));

    fireEvent.click(screen.getByText("Soumettre"));
    await waitFor(() => {
      expect(incokalkAPI.carrierBookings.submit).toHaveBeenCalledWith("b1");
    });
    expect(toast.success).toHaveBeenCalledWith("Réservation soumise au transporteur");
  });

  it("cancels a pending booking", async () => {
    vi.mocked(incokalkAPI.carrierBookings.cancel).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("REF-1"));

    fireEvent.click(screen.getByText("Annuler"));
    await waitFor(() => {
      expect(incokalkAPI.carrierBookings.cancel).toHaveBeenCalledWith("b1");
    });
    expect(toast.success).toHaveBeenCalledWith("Réservation annulée");
  });
});
