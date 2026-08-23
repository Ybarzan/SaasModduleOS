import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import TradeAgreements from "../pages/TradeAgreements";
import { mobileApi } from "../lib/api";

vi.mock("../lib/api", () => ({
  mobileApi: { tradeAgreements: { list: vi.fn() } },
}));

const agreements = [
  { id: "a1", code: "CETA", name: "Accord Canada-UE", partnerCountry: "CA", partnerName: "Canada", active: true },
  { id: "a2", code: "EU-JP", name: "Accord Japon-UE", partnerCountry: "JP", partnerName: "Japon", active: false },
];

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <TradeAgreements />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe("TradeAgreements (mobile)", () => {
  beforeEach(() => vi.clearAllMocks());

  it("lists agreements and flags inactive ones", async () => {
    vi.mocked(mobileApi.tradeAgreements.list).mockResolvedValue({ data: agreements } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("CETA")).toBeInTheDocument();
    });
    expect(screen.getByText("EU-JP")).toBeInTheDocument();
    expect(screen.getByText("Inactif")).toBeInTheDocument();
  });

  it("filters by search query", async () => {
    vi.mocked(mobileApi.tradeAgreements.list).mockResolvedValue({ data: agreements } as never);
    renderPage();
    await waitFor(() => screen.getByText("CETA"));

    fireEvent.change(screen.getByPlaceholderText(/Rechercher un accord/), { target: { value: "Japon" } });
    expect(screen.queryByText("CETA")).not.toBeInTheDocument();
    expect(screen.getByText("EU-JP")).toBeInTheDocument();
  });
});
