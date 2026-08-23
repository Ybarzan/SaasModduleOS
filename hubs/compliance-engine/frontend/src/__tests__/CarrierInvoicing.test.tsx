import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import toast from "react-hot-toast";
import CarrierInvoicing from "../pages/CarrierInvoicing";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    carrierInvoices: {
      getPage: vi.fn(),
      stats: vi.fn(),
      create: vi.fn(),
      updateStatus: vi.fn(),
      delete: vi.fn(),
      dispute: vi.fn(),
    },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const invoices = [
  {
    id: "ci1",
    invoiceNumber: "CI-2026-001",
    invoiceDate: "2026-08-01T00:00:00Z",
    dueDate: "2026-09-01T00:00:00Z",
    status: "RECEIVED",
    carrierName: "DHL",
    carrierReference: "REF-1",
    totalAmount: 500,
    currency: "USD",
    totalAmountEur: 460,
    variance: 20,
    variancePercent: 4.3,
  },
];
const stats = { total: 1, pending: 1, approved: 0, totalAmountEur: 460 };

function mockDefaults() {
  vi.mocked(incokalkAPI.carrierInvoices.getPage).mockResolvedValue({ data: invoices } as never);
  vi.mocked(incokalkAPI.carrierInvoices.stats).mockResolvedValue({ data: stats } as never);
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <CarrierInvoicing />
    </QueryClientProvider>
  );
}

describe("CarrierInvoicing page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockDefaults();
  });

  it("lists carrier invoices with status and variance", async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("CI-2026-001")).toBeInTheDocument();
    });
    expect(screen.getByText("DHL")).toBeInTheDocument();
    expect(screen.getByText("Reçue")).toBeInTheDocument();
    expect(screen.getByText("+4.3%")).toBeInTheDocument();
  });

  it("shows the empty state when there are no invoices", async () => {
    vi.mocked(incokalkAPI.carrierInvoices.getPage).mockResolvedValue({ data: [] } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText(/Aucune facture/)).toBeInTheDocument();
    });
  });

  it("submits a received invoice for review", async () => {
    vi.mocked(incokalkAPI.carrierInvoices.updateStatus).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("CI-2026-001"));

    fireEvent.click(screen.getByTitle("Soumettre"));
    await waitFor(() => {
      expect(incokalkAPI.carrierInvoices.updateStatus).toHaveBeenCalledWith("ci1", { status: "UNDER_REVIEW" });
    });
  });

  it("deletes a received invoice after confirmation", async () => {
    vi.mocked(incokalkAPI.carrierInvoices.delete).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("CI-2026-001"));

    fireEvent.click(screen.getByTitle("Supprimer"));
    fireEvent.click(screen.getByText("Oui"));

    await waitFor(() => {
      expect(incokalkAPI.carrierInvoices.delete).toHaveBeenCalledWith("ci1");
    });
    expect(toast.success).toHaveBeenCalledWith("Facture supprimée");
  });

  it("opens the invoice detail view", async () => {
    renderPage();
    await waitFor(() => screen.getByText("CI-2026-001"));

    fireEvent.click(screen.getByTitle("Voir les détails"));
    await waitFor(() => {
      expect(screen.getByText("REF-1")).toBeInTheDocument();
    });
  });
});
