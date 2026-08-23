import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import FlightRadar from "../pages/FlightRadar";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    trackingMap: { getFlights: vi.fn() },
  },
}));

vi.mock("leaflet", () => {
  const mockMap = {
    on: vi.fn(),
    remove: vi.fn(),
    invalidateSize: vi.fn(),
    getBounds: vi.fn(() => ({
      getSouth: () => 35,
      getNorth: () => 55,
      getWest: () => -5,
      getEast: () => 15,
    })),
  };
  const mockLayer = { addTo: vi.fn().mockReturnThis() };
  const mockMarker = {
    bindPopup: vi.fn().mockReturnThis(),
    addTo: vi.fn().mockReturnThis(),
    on: vi.fn(),
    setLatLng: vi.fn(),
    setIcon: vi.fn(),
  };
  return {
    default: {
      Icon: { Default: { prototype: {}, mergeOptions: vi.fn() } },
      map: vi.fn(() => mockMap),
      tileLayer: vi.fn(() => mockLayer),
      divIcon: vi.fn(() => ({})),
      marker: vi.fn(() => mockMarker),
    },
  };
});

const flightStates = [
  ["abc123", "AFR123  ", "France", 1000, 1000, 2.35, 48.85, 10000, false, 250, 90, 0, [], 10000],
  ["def456", "GRND01  ", "Maroc", 1000, 1000, -7.6, 33.6, 0, true, 0, 0, 0, [], 0],
];

function mockDefaults() {
  vi.mocked(incokalkAPI.trackingMap.getFlights).mockResolvedValue({ data: { states: flightStates } } as never);
}

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

describe("FlightRadar page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockDefaults();
  });

  it("renders the header and the in-flight count", async () => {
    renderPage();
    await waitFor(() => {
      expect(incokalkAPI.trackingMap.getFlights).toHaveBeenCalled();
    });
    await waitFor(() => {
      expect(screen.getByText("1")).toBeInTheDocument();
    });
    expect(screen.getByText("vols affichés")).toBeInTheDocument();
  });

  it("refetches flights when clicking Rafraîchir", async () => {
    renderPage();
    await waitFor(() => expect(incokalkAPI.trackingMap.getFlights).toHaveBeenCalledTimes(1));

    fireEvent.click(screen.getByTitle("Rafraîchir"));
    await waitFor(() => {
      expect(incokalkAPI.trackingMap.getFlights).toHaveBeenCalledTimes(2);
    });
  });
});
