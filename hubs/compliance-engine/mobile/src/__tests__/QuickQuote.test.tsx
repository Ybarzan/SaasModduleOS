import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import QuickQuote from "../pages/QuickQuote";
import { mobileApi } from "../lib/api";

vi.mock("../lib/api", () => ({
  mobileApi: {
    quickQuote: vi.fn(),
  },
}));

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <QuickQuote />
    </QueryClientProvider>
  );
}

describe("QuickQuote (mobile)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("shows a visible message and does not call the API when required fields are missing", () => {
    renderPage();
    fireEvent.click(screen.getByRole("button", { name: "Calculer" }));
    expect(screen.getByText("Renseignez l'origine et la destination.")).toBeInTheDocument();
    expect(mobileApi.quickQuote).not.toHaveBeenCalled();
  });

  it("shows a visible message and does not call the API when weight is zero or negative", () => {
    renderPage();
    fireEvent.change(screen.getByPlaceholderText("Origine (ville ou pays)"), { target: { value: "Paris" } });
    fireEvent.change(screen.getByPlaceholderText("Destination"), { target: { value: "Lyon" } });
    fireEvent.change(screen.getByPlaceholderText("Poids (kg)"), { target: { value: "0" } });
    fireEvent.click(screen.getByRole("button", { name: "Calculer" }));
    expect(screen.getByText("Indiquez un poids supérieur à 0.")).toBeInTheDocument();
    expect(mobileApi.quickQuote).not.toHaveBeenCalled();
  });

  it("clears the validation message once the user corrects the field", () => {
    renderPage();
    fireEvent.click(screen.getByRole("button", { name: "Calculer" }));
    expect(screen.getByText("Renseignez l'origine et la destination.")).toBeInTheDocument();

    fireEvent.change(screen.getByPlaceholderText("Origine (ville ou pays)"), { target: { value: "Paris" } });
    expect(screen.queryByText("Renseignez l'origine et la destination.")).not.toBeInTheDocument();
  });

  it("submits a valid quote request and displays the breakdown", async () => {
    vi.mocked(mobileApi.quickQuote).mockResolvedValue({
      data: {
        origin: "Paris",
        destination: "Lyon",
        weight_kg: 12,
        incoterm: "FOB",
        currency: "EUR",
        estimated_total: 150.5,
        breakdown: { base_freight: 100, fuel_surcharge: 30, security_surcharge: 10, handling_fee: 5, documentation_fee: 5.5 },
      },
    } as never);

    renderPage();
    fireEvent.change(screen.getByPlaceholderText("Origine (ville ou pays)"), { target: { value: "Paris" } });
    fireEvent.change(screen.getByPlaceholderText("Destination"), { target: { value: "Lyon" } });
    fireEvent.change(screen.getByPlaceholderText("Poids (kg)"), { target: { value: "12" } });
    fireEvent.click(screen.getByRole("button", { name: "Calculer" }));

    await waitFor(() => {
      expect(mobileApi.quickQuote).toHaveBeenCalledWith({
        origin: "Paris",
        destination: "Lyon",
        weight: 12,
        incoterm: "FOB",
      });
    });

    await waitFor(() => {
      expect(screen.getByText("150.50 EUR")).toBeInTheDocument();
    });
    expect(screen.getByText("Fret de base")).toBeInTheDocument();
  });

  it("shows an error message when the quote request fails", async () => {
    vi.mocked(mobileApi.quickQuote).mockRejectedValue(new Error("network error"));

    renderPage();
    fireEvent.change(screen.getByPlaceholderText("Origine (ville ou pays)"), { target: { value: "Paris" } });
    fireEvent.change(screen.getByPlaceholderText("Destination"), { target: { value: "Lyon" } });
    fireEvent.change(screen.getByPlaceholderText("Poids (kg)"), { target: { value: "5" } });
    fireEvent.click(screen.getByRole("button", { name: "Calculer" }));

    await waitFor(() => {
      expect(screen.getByText("Impossible de calculer le devis.")).toBeInTheDocument();
    });
  });
});
