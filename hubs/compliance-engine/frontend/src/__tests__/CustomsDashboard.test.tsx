import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import CustomsDashboard from "../pages/CustomsDashboard";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    customs: { getDuty: vi.fn(), getTariffInfo: vi.fn(), getVat: vi.fn(), getVatRates: vi.fn() },
    tradeAgreements: { list: vi.fn() },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const dutyResult = {
  dutyAmount: 1200,
  dutyRate: 12,
  dutyType: "AD_VALOREM",
  isPrefential: false,
  agreementCode: "",
  agreementName: "",
  mfnRate: 15,
  savings: 0,
  notes: "",
};

const tariffResult = {
  hsCode: "8471",
  origin: "CN",
  destination: "FR",
  mfnRate: 12,
  appliedRate: 12,
  isPrefential: false,
  agreement: "",
  savings: 0,
  notes: "",
  availableAgreements: [],
};

const vatResult = {
  vatAmount: 2000,
  vatRate: 20,
  vatType: "STANDARD",
  regime: "IMPORT",
  reverseCharge: false,
  isExempt: false,
  notes: "",
};

function mockDefaults() {
  vi.mocked(incokalkAPI.tradeAgreements.list).mockResolvedValue({ data: [] } as never);
  vi.mocked(incokalkAPI.customs.getDuty).mockResolvedValue({ data: dutyResult } as never);
  vi.mocked(incokalkAPI.customs.getTariffInfo).mockResolvedValue({ data: tariffResult } as never);
  vi.mocked(incokalkAPI.customs.getVat).mockResolvedValue({ data: vatResult } as never);
  vi.mocked(incokalkAPI.customs.getVatRates).mockResolvedValue({ data: { FR: 20, DE: 19 } } as never);
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <CustomsDashboard />
    </QueryClientProvider>
  );
}

describe("CustomsDashboard page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockDefaults();
  });

  it("calculates customs duty", async () => {
    renderPage();

    fireEvent.change(screen.getByPlaceholderText("Ex: 8471, 9401..."), { target: { value: "8471" } });
    fireEvent.click(screen.getByRole("button", { name: /^Calculer$/ }));

    await waitFor(() => {
      expect(incokalkAPI.customs.getDuty).toHaveBeenCalledWith("8471", "CN", "FR", 10000, 2000, 100);
    });
    expect(screen.getByText("12.0%")).toBeInTheDocument();
  });

  it("switches to the VAT tab and calculates VAT", async () => {
    renderPage();

    fireEvent.click(screen.getByRole("button", { name: "Simulateur TVA" }));
    fireEvent.click(screen.getByRole("button", { name: /Calculer la TVA/ }));

    await waitFor(() => {
      expect(incokalkAPI.customs.getVat).toHaveBeenCalledWith("CN", "FR", 10000, 2000, 100, "FOB", true);
    });
    expect(screen.getByText("20.0%")).toBeInTheDocument();
  });

  it("shows VAT rates by country", async () => {
    renderPage();

    fireEvent.click(screen.getByRole("button", { name: "Taux TVA par pays" }));

    await waitFor(() => {
      expect(screen.getByText("France")).toBeInTheDocument();
    });
    expect(screen.getByText("20%")).toBeInTheDocument();
  });
});
