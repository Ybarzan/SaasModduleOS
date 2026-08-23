import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor, within } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import toast from "react-hot-toast";
import ClientInvoicing from "../pages/ClientInvoicing";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    clientInvoices: {
      list: vi.fn(),
      stats: vi.fn(),
      create: vi.fn(),
      updateStatus: vi.fn(),
      recordPayment: vi.fn(),
      delete: vi.fn(),
    },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const invoices = [
  {
    id: "i1",
    invoiceNumber: "INV-2026-001",
    invoiceDate: "2026-08-01T00:00:00Z",
    dueDate: "2026-09-01T00:00:00Z",
    status: "DRAFT",
    clientName: "Atlas Import Export",
    clientEmail: "compta@atlas.test",
    subtotal: 1000,
    vatAmount: 200,
    totalAmount: 1200,
    amountPaid: 0,
    balanceDue: 1200,
    currency: "EUR",
  },
];
const stats = { total: 1, sent: 0, paid: 0, overdue: 0, totalRevenue: 1200, totalPaid: 0, totalOutstanding: 1200 };

function mockDefaults() {
  vi.mocked(incokalkAPI.clientInvoices.list).mockResolvedValue({ data: invoices } as never);
  vi.mocked(incokalkAPI.clientInvoices.stats).mockResolvedValue({ data: stats } as never);
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <ClientInvoicing />
    </QueryClientProvider>
  );
}

describe("ClientInvoicing page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockDefaults();
  });

  it("lists invoices with their status and balance", async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("INV-2026-001")).toBeInTheDocument();
    });
    expect(screen.getByText("Atlas Import Export")).toBeInTheDocument();
    expect(screen.getByText("Brouillon")).toBeInTheDocument();
  });

  it("shows the empty state when there are no invoices", async () => {
    vi.mocked(incokalkAPI.clientInvoices.list).mockResolvedValue({ data: [] } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Aucune facture")).toBeInTheDocument();
    });
  });

  it("sends a draft invoice", async () => {
    vi.mocked(incokalkAPI.clientInvoices.updateStatus).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("INV-2026-001"));

    fireEvent.click(screen.getByTitle("Envoyer"));
    await waitFor(() => {
      expect(incokalkAPI.clientInvoices.updateStatus).toHaveBeenCalledWith("i1", { status: "SENT" });
    });
    expect(toast.success).toHaveBeenCalledWith("Statut mis à jour");
  });

  it("deletes a draft invoice after confirmation", async () => {
    vi.mocked(incokalkAPI.clientInvoices.delete).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("INV-2026-001"));

    fireEvent.click(screen.getByTitle("Supprimer"));
    fireEvent.click(screen.getByText("Oui"));

    await waitFor(() => {
      expect(incokalkAPI.clientInvoices.delete).toHaveBeenCalledWith("i1");
    });
    expect(toast.success).toHaveBeenCalledWith("Facture supprimée");
  });

  it("creates a new invoice", async () => {
    vi.mocked(incokalkAPI.clientInvoices.create).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("INV-2026-001"));
    fireEvent.click(screen.getByText("Nouvelle facture"));

    const modal = screen.getByText("Nouvelle facture", { selector: "h3" }).closest("div.relative") as HTMLElement;
    const textInputs = within(modal).getAllByRole("textbox");
    const dateInputs = modal.querySelectorAll('input[type="date"]');

    fireEvent.change(textInputs[0], { target: { value: "INV-2026-002" } }); // N° facture
    fireEvent.change(dateInputs[0], { target: { value: "2026-08-20" } }); // Date facture
    fireEvent.change(dateInputs[1], { target: { value: "2026-09-20" } }); // Date échéance
    fireEvent.change(textInputs[1], { target: { value: "New Client SARL" } }); // Nom client

    const emailInput = modal.querySelector('input[type="email"]')!;
    fireEvent.change(emailInput, { target: { value: "contact@newclient.test" } });

    const numberInputs = modal.querySelectorAll('input[type="number"]');
    fireEvent.change(numberInputs[0], { target: { value: "1000" } }); // Sous-total
    fireEvent.change(numberInputs[2], { target: { value: "1200" } }); // Montant total

    fireEvent.click(within(modal).getByRole("button", { name: /Créer/ }));

    await waitFor(() => {
      expect(incokalkAPI.clientInvoices.create).toHaveBeenCalledWith(
        expect.objectContaining({ invoiceNumber: "INV-2026-002", clientName: "New Client SARL" })
      );
    });
    expect(toast.success).toHaveBeenCalledWith("Facture créée avec succès");
  });
});
