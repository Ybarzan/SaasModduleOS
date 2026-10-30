import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import toast from "react-hot-toast";
import CustomsInvoices from "../pages/CustomsInvoices";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    customsInvoices: { list: vi.fn(), get: vi.fn(), getByShipment: vi.fn(), generate: vi.fn() },
    shipments: { search: vi.fn() },
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
    vi.mocked(incokalkAPI.shipments.search).mockResolvedValue({ data: [] } as never);
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

  it("asks for a shipment ID as a plain text field in the generate modal", async () => {
    vi.mocked(incokalkAPI.customsInvoices.list).mockResolvedValue({ data: [invoice] } as never);
    renderPage();
    await waitFor(() => screen.getByText("CD-20260824-1234"));

    fireEvent.click(screen.getByRole("button", { name: /Générer une facture/ }));

    const input = screen.getByPlaceholderText(/UUID de l'expédition/) as HTMLInputElement;
    expect(input).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Générer" })).toBeDisabled();
  });

  it("generates an invoice for a manually entered shipment ID", async () => {
    vi.mocked(incokalkAPI.customsInvoices.list).mockResolvedValue({ data: [] } as never);
    vi.mocked(incokalkAPI.customsInvoices.generate).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("Aucune facture douanière"));

    fireEvent.click(screen.getByRole("button", { name: /Générer une facture/ }));
    const input = screen.getByPlaceholderText(/UUID de l'expédition/);
    fireEvent.change(input, { target: { value: "s2" } });
    fireEvent.click(screen.getByRole("button", { name: "Générer" }));

    await waitFor(() => {
      expect(incokalkAPI.customsInvoices.generate).toHaveBeenCalledWith("s2");
    });
    expect(toast.success).toHaveBeenCalledWith("Facture douanière générée");
  });

  it("shows an error toast when generation fails", async () => {
    vi.mocked(incokalkAPI.customsInvoices.list).mockResolvedValue({ data: [] } as never);
    vi.mocked(incokalkAPI.customsInvoices.generate).mockRejectedValue({
      response: { data: { message: "Expédition non trouvée" } },
    } as never);
    renderPage();
    await waitFor(() => screen.getByText("Aucune facture douanière"));

    fireEvent.click(screen.getByRole("button", { name: /Générer une facture/ }));
    const input = screen.getByPlaceholderText(/UUID de l'expédition/);
    fireEvent.change(input, { target: { value: "s2" } });
    fireEvent.click(screen.getByRole("button", { name: "Générer" }));

    await waitFor(() => {
      expect(toast.error).toHaveBeenCalledWith("Expédition non trouvée");
    });
  });

  it("searches shipments live and fills the field with the picked one's real UUID", async () => {
    vi.mocked(incokalkAPI.customsInvoices.list).mockResolvedValue({ data: [] } as never);
    vi.mocked(incokalkAPI.customsInvoices.generate).mockResolvedValue({} as never);
    vi.mocked(incokalkAPI.shipments.search).mockResolvedValue({
      data: [
        {
          id: "f47ac10b-58cc-4372-a567-0e02b2c3d479",
          orderNumber: "SHP-2026-042",
          consigneeCity: "Bruxelles",
          consigneeCountry: "BE",
        },
      ],
    } as never);
    renderPage();
    await waitFor(() => screen.getByText("Aucune facture douanière"));

    fireEvent.click(screen.getByRole("button", { name: /Générer une facture/ }));
    const input = screen.getByPlaceholderText(/UUID de l'expédition/);
    fireEvent.change(input, { target: { value: "acme" } });

    await waitFor(() => {
      expect(incokalkAPI.shipments.search).toHaveBeenCalledWith("acme");
    });
    await waitFor(() => screen.getByText("SHP-2026-042"));

    fireEvent.click(screen.getByText("SHP-2026-042"));
    expect((screen.getByPlaceholderText(/UUID de l'expédition/) as HTMLInputElement).value)
      .toBe("f47ac10b-58cc-4372-a567-0e02b2c3d479");

    fireEvent.click(screen.getByRole("button", { name: "Générer" }));
    await waitFor(() => {
      expect(incokalkAPI.customsInvoices.generate).toHaveBeenCalledWith("f47ac10b-58cc-4372-a567-0e02b2c3d479");
    });
  });

  it("does not query search for a value that already looks like a full UUID", async () => {
    vi.mocked(incokalkAPI.customsInvoices.list).mockResolvedValue({ data: [] } as never);
    renderPage();
    await waitFor(() => screen.getByText("Aucune facture douanière"));

    fireEvent.click(screen.getByRole("button", { name: /Générer une facture/ }));
    const input = screen.getByPlaceholderText(/UUID de l'expédition/);
    fireEvent.change(input, { target: { value: "f47ac10b-58cc-4372-a567-0e02b2c3d479" } });

    await new Promise((r) => setTimeout(r, 450));
    expect(incokalkAPI.shipments.search).not.toHaveBeenCalled();
  });
});
