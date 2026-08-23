import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import toast from "react-hot-toast";
import CompanySettings from "../pages/CompanySettings";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    branding: { get: vi.fn(), update: vi.fn(), uploadLogo: vi.fn() },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const company = {
  name: "IncoKalk SAS",
  legalName: "IncoKalk Société par Actions Simplifiée",
  siret: "123456789",
  vatNumber: "FR12345678901",
  address: "1 rue de Paris",
  city: "Paris",
  postalCode: "75001",
  country: "France",
  phone: "+33100000000",
  email: "contact@incokalk.com",
  website: "https://incokalk.com",
};

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <CompanySettings />
    </QueryClientProvider>
  );
}

describe("CompanySettings page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(incokalkAPI.branding.get).mockResolvedValue({ data: company } as never);
  });

  it("loads and displays existing company settings", async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByDisplayValue("IncoKalk SAS")).toBeInTheDocument();
    });
    expect(screen.getByDisplayValue("contact@incokalk.com")).toBeInTheDocument();
  });

  it("disables save until a field is changed", async () => {
    renderPage();
    await waitFor(() => screen.getByDisplayValue("IncoKalk SAS"));
    const saveButtons = screen.getAllByRole("button", { name: "Sauvegarder" });
    expect(saveButtons[0]).toBeDisabled();
  });

  it("saves the updated settings", async () => {
    vi.mocked(incokalkAPI.branding.update).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByDisplayValue("IncoKalk SAS"));

    fireEvent.change(screen.getByDisplayValue("IncoKalk SAS"), { target: { value: "IncoKalk International" } });
    const saveButtons = screen.getAllByRole("button", { name: "Sauvegarder" });
    fireEvent.click(saveButtons[0]);

    await waitFor(() => {
      expect(incokalkAPI.branding.update).toHaveBeenCalledWith(
        expect.objectContaining({ name: "IncoKalk International" })
      );
    });
    expect(toast.success).toHaveBeenCalledWith("Paramètres entreprise sauvegardés");
  });

  it("shows the server error message when saving fails", async () => {
    vi.mocked(incokalkAPI.branding.update).mockRejectedValue({
      response: { data: { message: "SIRET invalide" } },
    });
    renderPage();
    await waitFor(() => screen.getByDisplayValue("IncoKalk SAS"));

    fireEvent.change(screen.getByDisplayValue("IncoKalk SAS"), { target: { value: "X" } });
    const saveButtons = screen.getAllByRole("button", { name: "Sauvegarder" });
    fireEvent.click(saveButtons[0]);

    await waitFor(() => {
      expect(toast.error).toHaveBeenCalledWith("SIRET invalide");
    });
  });
});
