import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import TrackingLookup from "../pages/TrackingLookup";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: { tracking: { lookup: vi.fn() } },
}));

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <TrackingLookup />
    </QueryClientProvider>
  );
}

describe("TrackingLookup page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("disables search until a tracking number is entered", () => {
    renderPage();
    expect(screen.getByRole("button", { name: /Rechercher/ })).toBeDisabled();
  });

  it("switches the input placeholder based on the selected mode", () => {
    renderPage();
    expect(screen.getByPlaceholderText("N° de suivi transporteur")).toBeInTheDocument();
    fireEvent.click(screen.getByText("Maritime"));
    expect(screen.getByPlaceholderText("MMSI (ex: 226000000)")).toBeInTheDocument();
  });

  it("looks up a tracking number and renders the timeline", async () => {
    vi.mocked(incokalkAPI.tracking.lookup).mockResolvedValue({
      data: [
        { status: "IN_TRANSIT", eventTime: "2026-08-01T10:00:00Z", location: "Rotterdam", description: "En mer" },
      ],
    } as never);

    renderPage();
    fireEvent.change(screen.getByPlaceholderText("N° de suivi transporteur"), {
      target: { value: "ABC123" },
    });
    fireEvent.click(screen.getByRole("button", { name: /Rechercher/ }));

    await waitFor(() => {
      expect(incokalkAPI.tracking.lookup).toHaveBeenCalledWith("ABC123", "ROAD");
    });
    await waitFor(() => {
      expect(screen.getByText("Rotterdam")).toBeInTheDocument();
    });
    expect(screen.getByText("IN_TRANSIT")).toBeInTheDocument();
  });

  it("shows an empty state when no results are found", async () => {
    vi.mocked(incokalkAPI.tracking.lookup).mockResolvedValue({ data: [] } as never);
    renderPage();
    fireEvent.change(screen.getByPlaceholderText("N° de suivi transporteur"), {
      target: { value: "UNKNOWN" },
    });
    fireEvent.click(screen.getByRole("button", { name: /Rechercher/ }));

    await waitFor(() => {
      expect(screen.getByText("Aucun résultat trouvé")).toBeInTheDocument();
    });
  });

  it("shows an error state when the lookup fails", async () => {
    vi.mocked(incokalkAPI.tracking.lookup).mockRejectedValue(new Error("not found"));
    renderPage();
    fireEvent.change(screen.getByPlaceholderText("N° de suivi transporteur"), {
      target: { value: "XYZ" },
    });
    fireEvent.click(screen.getByRole("button", { name: /Rechercher/ }));

    await waitFor(() => {
      expect(screen.getByText("Erreur lors de la recherche")).toBeInTheDocument();
    });
  });
});
