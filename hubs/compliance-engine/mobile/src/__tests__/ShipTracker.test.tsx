import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import ShipTracker from "../pages/ShipTracker";
import { mobileApi } from "../lib/api";

vi.mock("../lib/api", () => ({
  mobileApi: { trackingMap: { searchVessels: vi.fn() } },
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

describe("ShipTracker (mobile)", () => {
  beforeEach(() => vi.clearAllMocks());

  it("does not search with an empty query", () => {
    renderPage();
    expect(screen.getByRole("button", { name: "Rechercher" })).toBeDisabled();
  });

  it("searches and shows vessel position", async () => {
    vi.mocked(mobileApi.trackingMap.searchVessels).mockResolvedValue({
      data: [{ name: "Ever Given", mmsi: "123456789", latitude: 31.2, longitude: 32.3, speed: 12.5 }],
    } as never);
    renderPage();

    fireEvent.change(screen.getByPlaceholderText("Nom du navire ou MMSI"), { target: { value: "Ever Given" } });
    fireEvent.click(screen.getByRole("button", { name: "Rechercher" }));

    await waitFor(() => {
      expect(mobileApi.trackingMap.searchVessels).toHaveBeenCalledWith("Ever Given");
    });
    await waitFor(() => {
      expect(screen.getByText("Ever Given")).toBeInTheDocument();
    });
    expect(screen.getByText("31.2000°, 32.3000°")).toBeInTheDocument();
  });

  it("shows an empty state when no vessel matches", async () => {
    vi.mocked(mobileApi.trackingMap.searchVessels).mockResolvedValue({ data: [] } as never);
    renderPage();

    fireEvent.change(screen.getByPlaceholderText("Nom du navire ou MMSI"), { target: { value: "Unknown" } });
    fireEvent.click(screen.getByRole("button", { name: "Rechercher" }));

    await waitFor(() => {
      expect(screen.getByText("Aucun navire trouvé pour « Unknown ».")).toBeInTheDocument();
    });
  });
});
