import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import CargoInsurance from "../pages/CargoInsurance";
import { incokalkAPI } from "../lib/api";
import { useAuthStore } from "../stores/auth";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    insurance: { listQuotes: vi.fn(), saveQuote: vi.fn(), activatePolicy: vi.fn() },
    currency: { getRates: vi.fn() },
    logistics: { calculateInsurance: vi.fn() },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const calcResult = {
  premiumAmount: 1500,
  premiumRate: 0.003,
  goodsValue: 50000,
  coverageAmount: 55000,
  transportMode: "SEA",
  coverageType: "ICC A",
  note: null,
};

function mockDefaults() {
  vi.mocked(incokalkAPI.currency.getRates).mockResolvedValue({ data: { rates: { USD: 1.08 } } } as never);
  vi.mocked(incokalkAPI.insurance.listQuotes).mockResolvedValue({ data: [] } as never);
  vi.mocked(incokalkAPI.logistics.calculateInsurance).mockResolvedValue({ data: calcResult } as never);
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <CargoInsurance />
    </QueryClientProvider>
  );
}

function loginAsUser() {
  useAuthStore.setState({
    token: "tok",
    refreshToken: "r",
    user: { id: "u1", email: "a@b.com", firstName: "A", lastName: "B", role: "USER" } as never,
  });
}

describe("CargoInsurance page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockDefaults();
  });

  afterEach(() => {
    useAuthStore.setState({ token: null, refreshToken: null, user: null });
  });

  it("renders the EUR/USD market indicator", async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("1.0800")).toBeInTheDocument();
    });
  });

  it("calculates the insurance premium", async () => {
    renderPage();
    await waitFor(() => screen.getByText("1.0800"));

    fireEvent.click(screen.getByRole("button", { name: /Calculer la prime/ }));

    await waitFor(() => {
      expect(incokalkAPI.logistics.calculateInsurance).toHaveBeenCalledWith(
        expect.objectContaining({ goodsValue: 50000, transportMode: "SEA", goodsCategory: "STANDARD" })
      );
    });
    expect(screen.getByText("0.30%")).toBeInTheDocument();
  });

  it("saves a quote once authenticated and a result exists", async () => {
    loginAsUser();
    vi.mocked(incokalkAPI.insurance.saveQuote).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("1.0800"));

    fireEvent.click(screen.getByRole("button", { name: /Calculer la prime/ }));
    await waitFor(() => screen.getByText("0.30%"));

    fireEvent.click(screen.getByRole("button", { name: /Enregistrer le devis/ }));
    await waitFor(() => {
      expect(incokalkAPI.insurance.saveQuote).toHaveBeenCalledWith(
        expect.objectContaining({ goodsValue: 50000, transportMode: "SEA" })
      );
    });
  });
});
