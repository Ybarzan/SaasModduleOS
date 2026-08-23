import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import toast from "react-hot-toast";
import EoriSettings from "../pages/EoriSettings";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    eori: { getPage: vi.fn(), create: vi.fn(), setDefault: vi.fn(), delete: vi.fn(), validate: vi.fn() },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const eoris = [
  { id: "e1", eori: "FR12345678901", holderName: "IncoKalk SAS", holderAddress: "1 rue de Paris", holderCountry: "FR", type: "COMPANY", isDefault: true, isValid: true, createdAt: "2026-08-01T00:00:00Z" },
];

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <EoriSettings />
    </QueryClientProvider>
  );
}

describe("EoriSettings page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(incokalkAPI.eori.getPage).mockResolvedValue({ data: eoris } as never);
  });

  it("lists EORI numbers with the default badge", async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getAllByText("FR12345678901").length).toBeGreaterThan(0);
    });
    expect(screen.getByText("Par défaut")).toBeInTheDocument();
    expect(screen.getByText("Validé")).toBeInTheDocument();
  });

  it("shows the empty state when there are no EORI numbers", async () => {
    vi.mocked(incokalkAPI.eori.getPage).mockResolvedValue({ data: [] } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Aucun EORI configuré")).toBeInTheDocument();
    });
  });

  it("rejects an invalid EORI format before submitting", async () => {
    renderPage();
    await waitFor(() => screen.getAllByText("FR12345678901").length > 0);
    fireEvent.click(screen.getByText("Ajouter un EORI"));

    fireEvent.change(screen.getByPlaceholderText("FR12345678901"), { target: { value: "invalid" } });
    expect(screen.getByText(/Format invalide/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Ajouter" })).toBeDisabled();
  });

  it("creates a new EORI once validated by the API", async () => {
    vi.mocked(incokalkAPI.eori.validate).mockResolvedValue({ data: { valid: true } } as never);
    vi.mocked(incokalkAPI.eori.create).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getAllByText("FR12345678901").length > 0);
    fireEvent.click(screen.getByText("Ajouter un EORI"));

    fireEvent.change(screen.getByPlaceholderText("FR12345678901"), { target: { value: "DE98765432109" } });
    fireEvent.change(screen.getByPlaceholderText("Entreprise Exemple SAS"), { target: { value: "Acme GmbH" } });
    fireEvent.click(screen.getByRole("button", { name: "Ajouter" }));

    await waitFor(() => {
      expect(incokalkAPI.eori.validate).toHaveBeenCalledWith("DE98765432109");
    });
    await waitFor(() => {
      expect(incokalkAPI.eori.create).toHaveBeenCalledWith(
        expect.objectContaining({ eori: "DE98765432109", holderName: "Acme GmbH" })
      );
    });
    expect(toast.success).toHaveBeenCalledWith("EORI ajouté avec succès");
  });

  it("rejects an EORI the customs API says is invalid", async () => {
    vi.mocked(incokalkAPI.eori.validate).mockResolvedValue({ data: { valid: false } } as never);
    renderPage();
    await waitFor(() => screen.getAllByText("FR12345678901").length > 0);
    fireEvent.click(screen.getByText("Ajouter un EORI"));

    fireEvent.change(screen.getByPlaceholderText("FR12345678901"), { target: { value: "DE98765432109" } });
    fireEvent.change(screen.getByPlaceholderText("Entreprise Exemple SAS"), { target: { value: "Acme GmbH" } });
    fireEvent.click(screen.getByRole("button", { name: "Ajouter" }));

    await waitFor(() => {
      expect(toast.error).toHaveBeenCalledWith("EORI invalide selon la validation douanière");
    });
    expect(incokalkAPI.eori.create).not.toHaveBeenCalled();
  });

  it("deletes an EORI after confirmation", async () => {
    vi.mocked(incokalkAPI.eori.delete).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getAllByText("FR12345678901").length > 0);

    fireEvent.click(screen.getByTitle("Supprimer"));
    fireEvent.click(screen.getByText("Oui"));

    await waitFor(() => {
      expect(incokalkAPI.eori.delete).toHaveBeenCalledWith("e1");
    });
    expect(toast.success).toHaveBeenCalledWith("EORI supprimé");
  });
});
