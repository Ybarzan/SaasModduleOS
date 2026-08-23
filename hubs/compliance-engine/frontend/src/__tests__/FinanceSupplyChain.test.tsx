import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import FinanceSupplyChain from "../pages/FinanceSupplyChain";
import { incokalkAPI } from "../lib/api";
import { useAuthStore } from "../stores/auth";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    finance: {
      stats: vi.fn(),
      history: vi.fn(),
      earlyDiscount: vi.fn(),
      request: vi.fn(),
      approve: vi.fn(),
      fund: vi.fn(),
      repay: vi.fn(),
    },
  },
}));

const stats = { totalFinanced: 50000, pendingRequests: 2, fundedAmount: 30000, avgFeePercent: 2.5 };
const requests = [
  { id: "f1", invoiceReference: "INV-100", amount: 10000, fee: 250, feePercent: 2.5, status: "PENDING", createdAt: "2026-08-01T00:00:00Z", updatedAt: "2026-08-01T00:00:00Z" },
];

function mockDefaults() {
  vi.mocked(incokalkAPI.finance.stats).mockResolvedValue({ data: stats } as never);
  vi.mocked(incokalkAPI.finance.history).mockResolvedValue({ data: requests } as never);
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <FinanceSupplyChain />
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

describe("FinanceSupplyChain page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockDefaults();
  });

  afterEach(() => {
    useAuthStore.setState({ token: null, refreshToken: null, user: null });
  });

  it("renders stats and the financing request list", async () => {
    loginAsUser();
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("INV-100")).toBeInTheDocument();
    });
    expect(screen.getByText("2")).toBeInTheDocument(); // pendingRequests stat
  });

  it("shows the empty state when there are no requests", async () => {
    loginAsUser();
    vi.mocked(incokalkAPI.finance.history).mockResolvedValue({ data: [] } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Aucune demande de financement")).toBeInTheDocument();
    });
  });

  it("hides the new-request button for users without edit rights", async () => {
    useAuthStore.setState({ token: null, refreshToken: null, user: null });
    renderPage();
    await waitFor(() => screen.getByText("INV-100"));
    expect(screen.queryByText("Nouvelle demande")).not.toBeInTheDocument();
  });

  it("submits a new financing request", async () => {
    loginAsUser();
    vi.mocked(incokalkAPI.finance.request).mockResolvedValue({ data: {} } as never);
    renderPage();
    await waitFor(() => screen.getByText("INV-100"));

    fireEvent.click(screen.getByText("Nouvelle demande"));
    fireEvent.change(screen.getAllByPlaceholderText("INV-001")[0], { target: { value: "INV-200" } });
    fireEvent.change(screen.getAllByPlaceholderText("10000")[0], { target: { value: "5000" } });
    fireEvent.click(screen.getByRole("button", { name: "Soumettre" }));

    await waitFor(() => {
      expect(incokalkAPI.finance.request).toHaveBeenCalledWith({ invoiceId: "INV-200", amount: 5000 });
    });
  });

  it("approves a pending request", async () => {
    loginAsUser();
    vi.mocked(incokalkAPI.finance.approve).mockResolvedValue({ data: {} } as never);
    renderPage();
    await waitFor(() => screen.getByText("INV-100"));

    fireEvent.click(screen.getByText("Approuver"));
    await waitFor(() => {
      expect(incokalkAPI.finance.approve).toHaveBeenCalledWith("f1");
    });
  });
});
