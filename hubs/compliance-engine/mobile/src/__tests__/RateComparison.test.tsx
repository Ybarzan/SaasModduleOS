import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import RateComparison from "../pages/RateComparison";
import { mobileApi } from "../lib/api";

vi.mock("../lib/api", () => ({
  mobileApi: { shippingRates: { compare: vi.fn() } },
}));

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <RateComparison />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe("RateComparison (mobile)", () => {
  beforeEach(() => vi.clearAllMocks());

  it("disables submit until origin and destination are filled", () => {
    renderPage();
    expect(screen.getByRole("button", { name: "Comparer" })).toBeDisabled();
  });

  it("compares rates and sorts them cheapest first", async () => {
    vi.mocked(mobileApi.shippingRates.compare).mockResolvedValue({
      data: [
        { carrierName: "CMA CGM", estimatedCost: 1800, transitDaysAvg: 28 },
        { carrierName: "Maersk", estimatedCost: 1500, transitDaysAvg: 25 },
      ],
    } as never);
    renderPage();

    fireEvent.change(screen.getByPlaceholderText("Origine (pays)"), { target: { value: "China" } });
    fireEvent.change(screen.getByPlaceholderText("Destination"), { target: { value: "France" } });
    fireEvent.click(screen.getByRole("button", { name: "Comparer" }));

    await waitFor(() => {
      expect(mobileApi.shippingRates.compare).toHaveBeenCalledWith("China", "France", "SEA", undefined);
    });
    await waitFor(() => {
      expect(screen.getByText("Maersk")).toBeInTheDocument();
    });

    const cards = screen.getAllByText(/€$/);
    expect(cards[0]).toHaveTextContent("1500 €");
  });
});
