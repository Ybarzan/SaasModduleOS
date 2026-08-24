import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import toast from "react-hot-toast";
import CustomsInvoices from "../pages/CustomsInvoices";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    customsInvoices: { list: vi.fn(), get: vi.fn(), getByShipment: vi.fn(), generate: vi.fn() },
    shipments: { getAll: vi.fn() },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const invoice = {
  id: "inv1",
  companyId: "c1",
  shipmentId: "s1",
  invoiceNumber: "CD-20260824-1234",
  invoiceDate: "2026-08-24T00:00:00Z",
  shipperName: "Acme SARL",
  shipperCountry: "FR",
  consigneeName: "Globex GmbH",
  consigneeCountry: "DE",
  eoriNumber: "FR123456789",
  currency: "EUR",
  totalGoodsValue: 1000,
  totalWeightKg: 50,
  totalPackages: 2,
  incotermCode: "CIF",
  totalDuty: 25,
  totalVat: 205,
  totalAmount: 1230,
  status: "DRAFT",
  items: [
    {
      id: "item1",
      lineNumber: 1,
      name: "Widget",
      hsCode: "8471.30.00",
      quantity: 10,
      unit: "PCS",
      unitPrice: 100,
      totalValue: 1000,
      countryOfOrigin: "CN",
      dutyRate: 2.5,
      dutyType: "AD",
      isPreferential: false,
      dutyAmount: 25,
      vatRate: 20,
      vatAmount: 205,
    },
  ],
  createdAt: "2026-08-24T00:00:00Z",
};

const shipment = { id: "s2", orderNumber: "CMD-002", consigneeName: "New Corp", consigneeCity: "Berlin" };

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <CustomsInvoices />
    </QueryClientProvider>
  );
}

describe("CustomsInvoices page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("lists invoices with their number, recipient and total", async () => {
    vi.mocked(incokalkAPI.customsInvoices.list).mockResolvedValue({ data: [invoice] } as never);
    renderPage();

    await waitFor(() => {
      expect(screen.getByText("CD-20260824-1234")).toBeInTheDocument();
    });
    expect(screen.getByText("Globex GmbH")).toBeInTheDocument();
    expect(screen.getByText(/1\s?230,00\s?EUR/)).toBeInTheDocument();
  });

  it("shows the empty state when there are no invoices", async () => {
    vi.mocked(incokalkAPI.customsInvoices.list).mockResolvedValue({ data: [] } as never);
    renderPage();

    await waitFor(() => {
      expect(screen.getByText("Aucune facture douanière")).toBeInTheDocument();
    });
  });

  it("expands an invoice to show its line items and totals", async () => {
    vi.mocked(incokalkAPI.customsInvoices.list).mockResolvedValue({ data: [invoice] } as never);
    renderPage();
    await waitFor(() => screen.getByText("CD-20260824-1234"));

    fireEvent.click(screen.getByText("CD-20260824-1234"));

    await waitFor(() => {
      expect(screen.getByText("Widget")).toBeInTheDocument();
    });
    expect(screen.getByText("8471.30.00")).toBeInTheDocument();
    expect(screen.getByText(/FR123456789/)).toBeInTheDocument();
  });

  it("lists shipments without an existing invoice in the generate modal", async () => {
    vi.mocked(incokalkAPI.customsInvoices.list).mockResolvedValue({ data: [invoice] } as never);
    vi.mocked(incokalkAPI.shipments.getAll).mockResolvedValue({ data: [shipment] } as never);
    renderPage();
    await waitFor(() => screen.getByText("CD-20260824-1234"));

    fireEvent.click(screen.getByRole("button", { name: /Générer une facture/ }));

    await waitFor(() => {
      expect(screen.getByText(/CMD-002/)).toBeInTheDocument();
    });
  });

  it("generates an invoice for the selected shipment", async () => {
    vi.mocked(incokalkAPI.customsInvoices.list).mockResolvedValue({ data: [] } as never);
    vi.mocked(incokalkAPI.shipments.getAll).mockResolvedValue({ data: [shipment] } as never);
    vi.mocked(incokalkAPI.customsInvoices.generate).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("Aucune facture douanière"));

    fireEvent.click(screen.getByRole("button", { name: /Générer une facture/ }));
    await waitFor(() => screen.getByText(/CMD-002/));

    fireEvent.change(screen.getByDisplayValue("Sélectionner une expédition"), { target: { value: "s2" } });
    fireEvent.click(screen.getByRole("button", { name: "Générer" }));

    await waitFor(() => {
      expect(incokalkAPI.customsInvoices.generate).toHaveBeenCalledWith("s2");
    });
    expect(toast.success).toHaveBeenCalledWith("Facture douanière générée");
  });

  it("shows an error toast when generation fails", async () => {
    vi.mocked(incokalkAPI.customsInvoices.list).mockResolvedValue({ data: [] } as never);
    vi.mocked(incokalkAPI.shipments.getAll).mockResolvedValue({ data: [shipment] } as never);
    vi.mocked(incokalkAPI.customsInvoices.generate).mockRejectedValue({
      response: { data: { message: "Expédition non trouvée" } },
    } as never);
    renderPage();
    await waitFor(() => screen.getByText("Aucune facture douanière"));

    fireEvent.click(screen.getByRole("button", { name: /Générer une facture/ }));
    await waitFor(() => screen.getByText(/CMD-002/));
    fireEvent.change(screen.getByDisplayValue("Sélectionner une expédition"), { target: { value: "s2" } });
    fireEvent.click(screen.getByRole("button", { name: "Générer" }));

    await waitFor(() => {
      expect(toast.error).toHaveBeenCalledWith("Expédition non trouvée");
    });
  });
});
