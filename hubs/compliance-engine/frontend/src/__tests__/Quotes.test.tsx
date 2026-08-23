import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import toast from "react-hot-toast";
import Quotes from "../pages/Quotes";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    quotes: { get: vi.fn() },
    export: { quotesPdf: vi.fn() },
  },
}));

vi.mock("react-hot-toast", () => {
  const fn = vi.fn();
  return { default: Object.assign(fn, { success: vi.fn(), error: vi.fn() }) };
});

const quotes = [
  { carrierName: "DHL", rateName: "Standard", transportMode: "SEA", totalCost: 500, currency: "EUR", baseRate: 400, transitDaysMin: 20, transitDaysMax: 25, co2EstimateKg: 120, providerType: "INTERNAL" },
  { carrierName: "FedEx", rateName: "Express", transportMode: "AIR", totalCost: 900, currency: "EUR", baseRate: 800, transitDaysMin: 3, transitDaysMax: 5, co2EstimateKg: 400, providerType: "SHIPPO" },
];

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <Quotes />
    </QueryClientProvider>
  );
}

function fillRequiredFields(container: HTMLElement) {
  const selects = container.querySelectorAll("select");
  fireEvent.change(selects[0], { target: { value: "France" } });
  fireEvent.change(selects[1], { target: { value: "Chine" } });
  const numberInputs = container.querySelectorAll('input[type="number"]');
  fireEvent.change(numberInputs[0], { target: { value: "100" } });
  fireEvent.change(numberInputs[1], { target: { value: "1" } });
}

describe("Quotes page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("shows the empty state before any search", () => {
    renderPage();
    expect(screen.getByText("Recherchez des devis de transport")).toBeInTheDocument();
  });

  it("requires origin/destination before submitting", () => {
    const { container } = renderPage();
    fireEvent.click(screen.getByRole("button", { name: "Demander des devis" }));
    expect(toast.error).toHaveBeenCalledWith("Veuillez sélectionner les pays d'origine et de destination");
    expect(incokalkAPI.quotes.get).not.toHaveBeenCalled();
    void container;
  });

  it("requires a positive weight and volume", () => {
    const { container } = renderPage();
    const selects = container.querySelectorAll("select");
    fireEvent.change(selects[0], { target: { value: "France" } });
    fireEvent.change(selects[1], { target: { value: "Chine" } });
    fireEvent.click(screen.getByRole("button", { name: "Demander des devis" }));
    expect(toast.error).toHaveBeenCalledWith("Le poids et le volume doivent être supérieurs à 0");
  });

  it("fetches and ranks quotes (cheapest / fastest / greenest)", async () => {
    vi.mocked(incokalkAPI.quotes.get).mockResolvedValue({ data: quotes } as never);
    const { container } = renderPage();
    fillRequiredFields(container);
    fireEvent.click(screen.getByRole("button", { name: "Demander des devis" }));

    await waitFor(() => {
      expect(toast.success).toHaveBeenCalledWith("2 tarif(s) trouvé(s)");
    });
    expect(screen.getByText("500.00 €")).toBeInTheDocument();
    expect(screen.getByText("3-5 jours")).toBeInTheDocument();
  });

  it("shows an info toast when no quotes are found", async () => {
    vi.mocked(incokalkAPI.quotes.get).mockResolvedValue({ data: [] } as never);
    const { container } = renderPage();
    fillRequiredFields(container);
    fireEvent.click(screen.getByRole("button", { name: "Demander des devis" }));

    await waitFor(() => {
      expect(toast).toHaveBeenCalledWith("Aucun tarif trouvé pour cette recherche", { icon: "📦" });
    });
  });

  it("shows an error toast when the request fails", async () => {
    vi.mocked(incokalkAPI.quotes.get).mockRejectedValue(new Error("boom"));
    const { container } = renderPage();
    fillRequiredFields(container);
    fireEvent.click(screen.getByRole("button", { name: "Demander des devis" }));

    await waitFor(() => {
      expect(toast.error).toHaveBeenCalledWith("Erreur lors de la demande de devis");
    });
  });
});
