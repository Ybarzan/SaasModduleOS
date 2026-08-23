import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import CustomsDuty from "../pages/CustomsDuty";
import { mobileApi } from "../lib/api";

vi.mock("../lib/api", () => ({
  mobileApi: { customs: { getDuty: vi.fn() } },
}));

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <CustomsDuty />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe("CustomsDuty (mobile)", () => {
  beforeEach(() => vi.clearAllMocks());

  it("shows a validation message and does not call the API when fields are missing", () => {
    renderPage();
    fireEvent.click(screen.getByRole("button", { name: "Calculer" }));
    expect(screen.getByText("Renseignez le code SH, le pays d’origine et de destination.")).toBeInTheDocument();
    expect(mobileApi.customs.getDuty).not.toHaveBeenCalled();
  });

  it("calculates duty and shows the result", async () => {
    vi.mocked(mobileApi.customs.getDuty).mockResolvedValue({ data: { dutyRate: 4.5, dutyAmount: 225 } } as never);
    renderPage();

    fireEvent.change(screen.getByPlaceholderText("Code SH (ex: 8471.30)"), { target: { value: "8471.30" } });
    fireEvent.change(screen.getByPlaceholderText("Origine (code pays)"), { target: { value: "CN" } });
    fireEvent.change(screen.getByPlaceholderText("Destination"), { target: { value: "FR" } });
    fireEvent.change(screen.getByPlaceholderText("Valeur des marchandises (€)"), { target: { value: "5000" } });
    fireEvent.click(screen.getByRole("button", { name: "Calculer" }));

    await waitFor(() => {
      expect(mobileApi.customs.getDuty).toHaveBeenCalledWith("8471.30", "CN", "FR", 5000);
    });
    await waitFor(() => {
      expect(screen.getByText("225.00 €")).toBeInTheDocument();
    });
    expect(screen.getByText("4.5%")).toBeInTheDocument();
  });
});
