import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import FinancialReports from "../pages/FinancialReports";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    financials: {
      dashboard: vi.fn(),
      byCarrier: vi.fn(),
      byLane: vi.fn(),
      listShipments: vi.fn(),
    },
  },
}));

const dashboard = {
  totalRevenue: 125000,
  totalCost: 98000,
  totalMargin: 27000,
  marginPercent: 21.6,
  shipmentCount: 42,
  avgRevenuePerShipment: 2976,
  avgMarginPerShipment: 642.8,
};
const carrierData = [{ carrier: "DHL", revenue: 50000, cost: 38000, margin: 12000, shipments: 15 }];
const laneData = [{ lane: "CN → FR", revenue: 30000, cost: 22000, margin: 8000, shipments: 10 }];

function mockDefaults() {
  vi.mocked(incokalkAPI.financials.dashboard).mockResolvedValue({ data: dashboard } as never);
  vi.mocked(incokalkAPI.financials.byCarrier).mockResolvedValue({ data: carrierData } as never);
  vi.mocked(incokalkAPI.financials.byLane).mockResolvedValue({ data: laneData } as never);
  vi.mocked(incokalkAPI.financials.listShipments).mockResolvedValue({ data: [] } as never);
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <FinancialReports />
    </QueryClientProvider>
  );
}

describe("FinancialReports page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockDefaults();
  });

  it("renders the dashboard summary cards", async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("42")).toBeInTheDocument();
    });
    expect(screen.getByText(/125\s?000/)).toBeInTheDocument();
  });

  it("shows carrier performance by default", async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("DHL")).toBeInTheDocument();
    });
  });

  it("switches to the lane tab", async () => {
    renderPage();
    await waitFor(() => screen.getByText("DHL"));

    fireEvent.click(screen.getByText("Par lane"));
    await waitFor(() => {
      expect(incokalkAPI.financials.byLane).toHaveBeenCalled();
    });
    await waitFor(() => {
      expect(screen.getByText("CN → FR")).toBeInTheDocument();
    });
  });

  it("shows the empty state when there is no shipment data", async () => {
    renderPage();
    await waitFor(() => screen.getByText("DHL"));

    fireEvent.click(screen.getByText("Par expédition"));
    await waitFor(() => {
      expect(incokalkAPI.financials.listShipments).toHaveBeenCalled();
    });
  });
});
