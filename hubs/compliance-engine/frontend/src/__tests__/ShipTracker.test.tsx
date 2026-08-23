import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import ShipTracker from "../pages/ShipTracker";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    trackingMap: { getLiveVessels: vi.fn(), searchVessels: vi.fn() },
    shipments: { getAll: vi.fn() },
  },
}));

// Real leaflet drives actual DOM layout (getBoundingClientRect, etc.) that jsdom doesn't
// implement meaningfully; this page also calls L.map()/L.marker() directly (not via
// react-leaflet), so stub the whole module with inert objects for this page's own logic.
const fakeMap = {
  addTo: vi.fn(function (this: unknown) { return this; }),
  on: vi.fn(),
  invalidateSize: vi.fn(),
  getBounds: vi.fn(() => ({ getSouth: () => 35, getNorth: () => 55, getWest: () => -5, getEast: () => 15 })),
  remove: vi.fn(),
  removeLayer: vi.fn(),
  fitBounds: vi.fn(),
};
const fakeMarker = {
  bindPopup: vi.fn(function (this: unknown) { return this; }),
  addTo: vi.fn(function (this: unknown) { return this; }),
  setLatLng: vi.fn(),
  setIcon: vi.fn(),
  setPopupContent: vi.fn(),
};

vi.mock("leaflet", () => ({
  default: {
    Icon: { Default: { prototype: {}, mergeOptions: vi.fn() } },
    map: vi.fn(() => fakeMap),
    tileLayer: vi.fn(() => ({ addTo: vi.fn() })),
    marker: vi.fn(() => fakeMarker),
    divIcon: vi.fn(() => ({})),
    latLngBounds: vi.fn(() => ({})),
  },
}));

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <ShipTracker />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe("ShipTracker page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(incokalkAPI.shipments.getAll).mockResolvedValue({ data: [] } as never);
    vi.mocked(incokalkAPI.trackingMap.getLiveVessels).mockResolvedValue({
      data: { configured: false, connected: false, vessels: [] },
    } as never);
  });

  it("shows a not-configured banner when live tracking has no API key", async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText(/Suivi en direct non configuré/)).toBeInTheDocument();
    });
  });

  it("shows the live vessel count when tracking is configured and connected", async () => {
    vi.mocked(incokalkAPI.trackingMap.getLiveVessels).mockResolvedValue({
      data: { configured: true, connected: true, vessels: [{ mmsi: "1", latitude: 10, longitude: 20 }] },
    } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText(/navire.*en direct/)).toBeInTheDocument();
    });
  });

  it("shows an invalid-key banner when configured but the AIS connection isn't up", async () => {
    vi.mocked(incokalkAPI.trackingMap.getLiveVessels).mockResolvedValue({
      data: { configured: true, connected: false, vessels: [] },
    } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText(/la connexion au flux échoue/)).toBeInTheDocument();
    });
    expect(screen.getByText("Flux AIS déconnecté")).toBeInTheDocument();
    expect(screen.queryByText(/Suivi en direct non configuré/)).not.toBeInTheDocument();
  });

  it("searches for a vessel", async () => {
    vi.mocked(incokalkAPI.trackingMap.searchVessels).mockResolvedValue({
      data: [{ name: "MSC Gaia", latitude: 10, longitude: 20 }],
    } as never);
    renderPage();
    await waitFor(() => screen.getByPlaceholderText("Rechercher un navire (nom, MMSI)..."));

    fireEvent.change(screen.getByPlaceholderText("Rechercher un navire (nom, MMSI)..."), {
      target: { value: "MSC" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Rechercher" }));

    await waitFor(() => {
      expect(incokalkAPI.trackingMap.searchVessels).toHaveBeenCalledWith("MSC");
    });
  });

  it("lists maritime shipments in transit", async () => {
    vi.mocked(incokalkAPI.shipments.getAll).mockResolvedValue({
      data: [
        {
          id: "s1",
          status: "IN_TRANSIT",
          orderNumber: "ORD-1",
          consigneeCity: "Rotterdam",
          carrier: { transportModes: ["SEA"] },
        },
        {
          id: "s2",
          status: "IN_TRANSIT",
          orderNumber: "ORD-2",
          consigneeCity: "Paris",
          carrier: { transportModes: ["ROAD"] },
        },
      ],
    } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("ORD-1")).toBeInTheDocument();
    });
    expect(screen.queryByText("ORD-2")).not.toBeInTheDocument();
  });
});
