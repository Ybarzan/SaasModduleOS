import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import toast from "react-hot-toast";
import RateComparison from "../pages/RateComparison";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    carriers: { getAll: vi.fn() },
    shippingRates: { compare: vi.fn() },
    export: { quotesPdf: vi.fn() },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const results = [
  {
    rate: { id: "r1", transportMode: "SEA", baseRate: 400, currency: "EUR", active: true },
    carrierName: "DHL",
    carrierCode: "DHL01",
    estimatedCost: 500,
    transitDaysAvg: 20,
    co2EstimateKg: 120,
  },
];

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <RateComparison />
    </QueryClientProvider>
  );
}

function fillForm(container: HTMLElement) {
  const selects = container.querySelectorAll("select");
  fireEvent.change(selects[0], { target: { value: "France" } });
  fireEvent.change(selects[1], { target: { value: "Chine" } });
  fireEvent.change(selects[2], { target: { value: "SEA" } });
}

describe("RateComparison page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(incokalkAPI.carriers.getAll).mockResolvedValue({ data: [{ id: "c1", active: true }] } as never);
  });

  it("shows a no-carrier message when there are no active carriers and no search yet", async () => {
    vi.mocked(incokalkAPI.carriers.getAll).mockResolvedValue({ data: [] } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Aucun transporteur configuré")).toBeInTheDocument();
    });
  });

  it("requires all mandatory fields before comparing", async () => {
    const { container } = renderPage();
    await waitFor(() => screen.getByText("Comparateur de tarifs multi-transporteurs"));
    fireEvent.click(screen.getByRole("button", { name: /Comparer les tarifs/ }));
    expect(toast.error).toHaveBeenCalledWith("Veuillez remplir les champs obligatoires");
    void container;
  });

  it("fetches and displays comparison results", async () => {
    vi.mocked(incokalkAPI.shippingRates.compare).mockResolvedValue({ data: results } as never);
    const { container } = renderPage();
    await waitFor(() => screen.getByText("Comparateur de tarifs multi-transporteurs"));
    fillForm(container);
    // The query auto-fires once all three fields are set (enabled: !!origin && !!dest && !!mode),
    // so submitting isn't required and the button may already be gone behind the loading view.

    await waitFor(() => {
      expect(incokalkAPI.shippingRates.compare).toHaveBeenCalledWith("France", "Chine", "SEA", undefined);
    });
    await waitFor(() => {
      expect(screen.getByText("DHL")).toBeInTheDocument();
    });
    expect(screen.getByText("500.00 €")).toBeInTheDocument();
    expect(screen.getByText("Meilleur prix")).toBeInTheDocument();
  });

  it("expands rate details when clicked", async () => {
    vi.mocked(incokalkAPI.shippingRates.compare).mockResolvedValue({ data: results } as never);
    const { container } = renderPage();
    await waitFor(() => screen.getByText("Comparateur de tarifs multi-transporteurs"));
    fillForm(container);
    // The query auto-fires once all three fields are set (enabled: !!origin && !!dest && !!mode),
    // so submitting isn't required and the button may already be gone behind the loading view.
    await waitFor(() => screen.getByText("DHL"));

    fireEvent.click(screen.getByText("Voir les détails"));
    expect(screen.getByText("400.00 €")).toBeInTheDocument();
  });

  it("shows an empty-results message when nothing matches", async () => {
    vi.mocked(incokalkAPI.shippingRates.compare).mockResolvedValue({ data: [] } as never);
    const { container } = renderPage();
    await waitFor(() => screen.getByText("Comparateur de tarifs multi-transporteurs"));
    fillForm(container);
    // The query auto-fires once all three fields are set (enabled: !!origin && !!dest && !!mode),
    // so submitting isn't required and the button may already be gone behind the loading view.

    await waitFor(() => {
      expect(screen.getByText("Aucun tarif trouvé")).toBeInTheDocument();
    });
  });
});
