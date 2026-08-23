import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import CargoInsurance from "../pages/CargoInsurance";
import { mobileApi } from "../lib/api";

vi.mock("../lib/api", () => ({
  mobileApi: { insurance: { listQuotes: vi.fn() } },
}));

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <CargoInsurance />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe("CargoInsurance (mobile)", () => {
  beforeEach(() => vi.clearAllMocks());

  it("lists quotes and shows the policy number when issued", async () => {
    vi.mocked(mobileApi.insurance.listQuotes).mockResolvedValue({
      data: [{ id: "q1", goodsValue: 50000, premiumAmount: 375.5, policyNumber: "POL-2026-042" }],
    } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("50000 € couverts")).toBeInTheDocument();
    });
    expect(screen.getByText("Prime 375.50 €")).toBeInTheDocument();
    expect(screen.getByText("POL-2026-042")).toBeInTheDocument();
  });

  it("shows the empty state when there is no quote or policy", async () => {
    vi.mocked(mobileApi.insurance.listQuotes).mockResolvedValue({ data: [] } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Aucune police ou devis d'assurance.")).toBeInTheDocument();
    });
  });
});
