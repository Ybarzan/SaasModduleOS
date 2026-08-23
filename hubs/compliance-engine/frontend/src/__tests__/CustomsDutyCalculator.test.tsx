import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import toast from "react-hot-toast";
import CustomsDutyCalculator from "../pages/CustomsDutyCalculator";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    tradeAgreements: { list: vi.fn() },
    customs: { search: vi.fn(), getDuty: vi.fn() },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const dutyResult = {
  dutyRate: 5.5,
  dutyAmount: 660,
  savings: 0,
  mfnRate: 7.2,
  hsCode: "8471",
  origin: "CN",
  destination: "FR",
  isPrefential: false,
  cifValue: 12100,
};

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <CustomsDutyCalculator />
    </QueryClientProvider>
  );
}

describe("CustomsDutyCalculator page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(incokalkAPI.tradeAgreements.list).mockResolvedValue({ data: [] } as never);
  });

  it("shows a placeholder before any calculation", () => {
    renderPage();
    expect(screen.getByText("Remplissez le formulaire pour estimer les droits de douane")).toBeInTheDocument();
  });

  it("calculates duty for the default HS code and renders the result", async () => {
    vi.mocked(incokalkAPI.customs.getDuty).mockResolvedValue({ data: dutyResult } as never);
    renderPage();

    fireEvent.click(screen.getByRole("button", { name: /Calculer les droits de douane/ }));

    await waitFor(() => {
      expect(incokalkAPI.customs.getDuty).toHaveBeenCalledWith("8471", "CN", "FR", 10000, 2000, 100);
    });
    await waitFor(() => {
      expect(screen.getByText("5.5%")).toBeInTheDocument();
    });
    expect(screen.getByText("660 €")).toBeInTheDocument();
  });

  it("uses a manually entered HS code over the default when provided", async () => {
    vi.mocked(incokalkAPI.customs.getDuty).mockResolvedValue({ data: dutyResult } as never);
    renderPage();

    fireEvent.change(screen.getByPlaceholderText("ou saisissez un code SH manuellement..."), {
      target: { value: "6109" },
    });
    fireEvent.click(screen.getByRole("button", { name: /Calculer les droits de douane/ }));

    await waitFor(() => {
      expect(incokalkAPI.customs.getDuty).toHaveBeenCalledWith("6109", "CN", "FR", 10000, 2000, 100);
    });
  });

  it("shows an error toast when the calculation fails", async () => {
    vi.mocked(incokalkAPI.customs.getDuty).mockRejectedValue(new Error("boom"));
    renderPage();
    fireEvent.click(screen.getByRole("button", { name: /Calculer les droits de douane/ }));

    await waitFor(() => {
      expect(toast.error).toHaveBeenCalledWith("Erreur lors du calcul");
    });
  });
});
