import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import toast from "react-hot-toast";
import RouteOptimizer from "../pages/RouteOptimizer";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: { logistics: { optimizeRoute: vi.fn() } },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

// Leaflet manipulates real DOM layout APIs jsdom doesn't implement; the map is
// decorative for this page's logic, so stub it out with plain markup.
vi.mock("react-leaflet", () => ({
  MapContainer: ({ children }: { children: React.ReactNode }) => <div data-testid="map">{children}</div>,
  TileLayer: () => null,
  Marker: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  Popup: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  Polyline: () => null,
  useMap: () => ({ fitBounds: vi.fn() }),
}));

vi.mock("leaflet", () => ({
  default: { divIcon: vi.fn(() => ({})) },
}));

const optimizationResult = {
  totalDistanceKm: 465,
  totalStops: 3,
  estimatedHours: 5.5,
  estimatedFuelLiters: 60,
  estimatedFuelCost: 90,
  estimatedTollCost: 40,
  orderedStops: [
    { order: 0, city: "Paris", country: "FR", distanceFromPreviousKm: 0, cumulativeDistanceKm: 0 },
    { order: 1, city: "Bordeaux", country: "FR", distanceFromPreviousKm: 340, cumulativeDistanceKm: 340 },
    { order: 2, city: "Lyon", country: "FR", distanceFromPreviousKm: 125, cumulativeDistanceKm: 465 },
  ],
  recommendation: "Itinéraire optimal via Bordeaux",
};

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <RouteOptimizer />
    </QueryClientProvider>
  );
}

describe("RouteOptimizer page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("shows a placeholder before optimizing", () => {
    renderPage();
    expect(screen.getByText("Ajoutez des stops et cliquez sur « Optimiser »")).toBeInTheDocument();
  });

  it("adds and removes intermediate stops", () => {
    renderPage();
    expect(document.querySelector("span.flex-1.font-medium")?.textContent).toBe("Bordeaux");

    const stopSelect = screen.getByDisplayValue("Ajouter un stop...");
    fireEvent.change(stopSelect, { target: { value: "Lille" } });
    fireEvent.click(stopSelect.parentElement!.querySelector("button")!);
    const stopSpans = document.querySelectorAll("span.flex-1.font-medium");
    expect(Array.from(stopSpans).map((s) => s.textContent)).toContain("Lille");
  });

  it("removes an intermediate stop", () => {
    renderPage();
    const stopRow = document.querySelector("span.flex-1.font-medium")!.closest("div")!;
    fireEvent.click(stopRow.querySelector("button")!);
    expect(document.querySelector("span.flex-1.font-medium")).toBeNull();
  });

  it("optimizes a route and displays the summary", async () => {
    vi.mocked(incokalkAPI.logistics.optimizeRoute).mockResolvedValue({ data: optimizationResult } as never);
    renderPage();

    fireEvent.click(screen.getByRole("button", { name: /Optimiser l'itinéraire/ }));

    await waitFor(() => {
      expect(incokalkAPI.logistics.optimizeRoute).toHaveBeenCalledWith(
        expect.objectContaining({ originCountry: "Paris", destinationCountry: "Lyon" })
      );
    });
    await waitFor(() => {
      expect(screen.getByText("465")).toBeInTheDocument();
    });
    expect(screen.getByText("Itinéraire optimal via Bordeaux")).toBeInTheDocument();
  });

  it("shows an error toast when optimization fails", async () => {
    vi.mocked(incokalkAPI.logistics.optimizeRoute).mockRejectedValue(new Error("boom"));
    renderPage();
    fireEvent.click(screen.getByRole("button", { name: /Optimiser l'itinéraire/ }));

    await waitFor(() => {
      expect(toast.error).toHaveBeenCalledWith("Erreur lors de l'optimisation");
    });
  });
});
