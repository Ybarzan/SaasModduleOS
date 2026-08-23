import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import ClientShipmentDetail from "../pages/ClientShipmentDetail";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: { clientPortal: { shipmentDetail: vi.fn() } },
  downloadAuthedFile: vi.fn(),
}));

const shipment = {
  id: "s1",
  orderNumber: "ORD-500",
  status: "IN_TRANSIT",
  incotermCode: "FOB",
  carrierName: "DHL",
  shipperCity: "Shanghai",
  shipperCountry: "CN",
  consigneeCity: "Paris",
  consigneeCountry: "FR",
  finalCost: 1200,
  quotedCost: 1500,
  costCurrency: "EUR",
  goodsDescription: "Électronique grand public",
  weightKg: 500,
  volumeM3: 2.5,
  packagesCount: 10,
  estimatedDeliveryDate: "2026-09-10T00:00:00Z",
  trackingEvents: [
    { id: "e1", status: "DEPARTED", location: "Shanghai", description: "Parti du port", eventTime: "2026-08-01T00:00:00Z" },
  ],
};

function renderPage(id = "s1") {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/client/shipments/${id}`]}>
        <Routes>
          <Route path="/client/shipments/:id" element={<ClientShipmentDetail />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe("ClientShipmentDetail page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("shows shipment route, cost, and cargo info once loaded", async () => {
    vi.mocked(incokalkAPI.clientPortal.shipmentDetail).mockResolvedValue({ data: shipment } as never);
    renderPage();

    await waitFor(() => {
      expect(screen.getByText("ORD-500")).toBeInTheDocument();
    });
    expect(incokalkAPI.clientPortal.shipmentDetail).toHaveBeenCalledWith("s1");
    expect(screen.getAllByText("Shanghai").length).toBeGreaterThan(0);
    expect(screen.getByText("Paris")).toBeInTheDocument();
    expect(screen.getByText(/1\s?200/)).toBeInTheDocument();
  });

  it("shows a not-found message when the shipment doesn't resolve", async () => {
    vi.mocked(incokalkAPI.clientPortal.shipmentDetail).mockResolvedValue({ data: undefined } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Expédition introuvable")).toBeInTheDocument();
    });
  });

  it("shows the tracking timeline", async () => {
    vi.mocked(incokalkAPI.clientPortal.shipmentDetail).mockResolvedValue({ data: shipment } as never);
    renderPage();
    await waitFor(() => screen.getByText("ORD-500"));
    expect(screen.getByText("Parti du port")).toBeInTheDocument();
    expect(screen.getByText("DEPARTED")).toBeInTheDocument();
  });

  it("shows an empty tracking message when there are no events", async () => {
    vi.mocked(incokalkAPI.clientPortal.shipmentDetail).mockResolvedValue({
      data: { ...shipment, trackingEvents: [] },
    } as never);
    renderPage();
    await waitFor(() => screen.getByText("ORD-500"));
    expect(screen.getByText("Aucun événement de suivi disponible")).toBeInTheDocument();
  });
});
