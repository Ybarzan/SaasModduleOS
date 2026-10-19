import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, within, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import toast from "react-hot-toast";
import CompanySettings from "../pages/CompanySettings";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    branding: { get: vi.fn(), update: vi.fn(), uploadLogo: vi.fn() },
    companies: {
      me: vi.fn(),
      fleetHubKeyStatus: vi.fn(),
      setFleetHubKey: vi.fn(),
      clearFleetHubKey: vi.fn(),
      financeKeyStatus: vi.fn(),
      setFinanceKey: vi.fn(),
      clearFinanceKey: vi.fn(),
    },
  },
}));

// Les deux cartes d'intégration (Fleet Hub, Finance) partagent le même texte
// générique ("Enregistrer", "Clé configurée", "Effacer la clé") -- chaque test
// doit donc chercher dans le conteneur de SA carte, repéré par son titre.
function cardFor(heading: string) {
  const el = screen.getByRole("heading", { name: heading }).closest(".bg-surface");
  if (!el) throw new Error(`Carte "${heading}" introuvable`);
  return within(el as HTMLElement);
}

// Les deux cartes partagent la même clé de requête ("companies-me"), résolue
// après le premier rendu ; tant que leur propre requête de statut de clé n'a
// pas démarré (gated par `enabled: !!me?.id`), un fireEvent.change ou .click
// peut retomber au milieu d'un ré-rendu React et être silencieusement perdu.
// Attendre que les deux requêtes de statut aient démarré avant d'interagir
// élimine cette fenêtre de course.
async function settleIntegrationCards() {
  await waitFor(() => expect(incokalkAPI.companies.fleetHubKeyStatus).toHaveBeenCalled());
  await waitFor(() => expect(incokalkAPI.companies.financeKeyStatus).toHaveBeenCalled());
}

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
    vi.mocked(incokalkAPI.companies.me).mockResolvedValue({ data: { id: "company-1" } } as never);
    vi.mocked(incokalkAPI.companies.fleetHubKeyStatus).mockResolvedValue({
      data: { configured: false },
    } as never);
    vi.mocked(incokalkAPI.companies.financeKeyStatus).mockResolvedValue({
      data: { configured: false },
    } as never);
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

  describe("Fleet Hub integration card", () => {
    it("shows an input to save the key when none is configured", async () => {
      renderPage();
      await waitFor(() =>
        expect(screen.getByPlaceholderText(/Clé d'intégration fleet-hub/)).toBeInTheDocument()
      );
    });

    it("saves the key and shows the configured state", async () => {
      vi.mocked(incokalkAPI.companies.setFleetHubKey).mockResolvedValue({
        data: { configured: true },
      } as never);
      renderPage();
      // Attendre la fin du chargement de la page (branding), puis que les deux
      // requêtes de statut de clé aient démarré, avant d'interagir -- voir
      // settleIntegrationCards ci-dessus.
      await screen.findByDisplayValue("IncoKalk SAS");
      await settleIntegrationCards();
      const card = cardFor("Intégration Fleet Hub");
      fireEvent.change(card.getByPlaceholderText(/Clé d'intégration fleet-hub/), { target: { value: "abc123def456" } });
      fireEvent.click(card.getByRole("button", { name: "Enregistrer" }));

      await waitFor(() => {
        expect(incokalkAPI.companies.setFleetHubKey).toHaveBeenCalledWith("company-1", "abc123def456");
      });
      expect(toast.success).toHaveBeenCalledWith("Clé fleet-hub enregistrée");
    });

    it("shows the configured state and lets the admin clear the key", async () => {
      vi.mocked(incokalkAPI.companies.fleetHubKeyStatus).mockResolvedValue({
        data: { configured: true },
      } as never);
      vi.mocked(incokalkAPI.companies.clearFleetHubKey).mockResolvedValue({
        data: { configured: false },
      } as never);
      renderPage();

      await waitFor(() => expect(cardFor("Intégration Fleet Hub").getByText("Clé configurée")).toBeInTheDocument());
      fireEvent.click(cardFor("Intégration Fleet Hub").getByRole("button", { name: "Effacer la clé" }));

      await waitFor(() => {
        expect(incokalkAPI.companies.clearFleetHubKey).toHaveBeenCalledWith("company-1");
      });
      expect(toast.success).toHaveBeenCalledWith("Clé fleet-hub effacée");
    });
  });

  describe("Finance integration card", () => {
    it("shows an input to save the key when none is configured", async () => {
      renderPage();
      await waitFor(() =>
        expect(screen.getByPlaceholderText(/Clé d'intégration Finance/)).toBeInTheDocument()
      );
    });

    it("saves the key and shows the configured state", async () => {
      vi.mocked(incokalkAPI.companies.setFinanceKey).mockResolvedValue({
        data: { configured: true },
      } as never);
      renderPage();
      await screen.findByDisplayValue("IncoKalk SAS");
      await settleIntegrationCards();
      const card = cardFor("Intégration Finance & Trésorerie");
      fireEvent.change(card.getByPlaceholderText(/Clé d'intégration Finance/), { target: { value: "fed654cba321" } });
      fireEvent.click(card.getByRole("button", { name: "Enregistrer" }));

      await waitFor(() => {
        expect(incokalkAPI.companies.setFinanceKey).toHaveBeenCalledWith("company-1", "fed654cba321");
      });
      expect(toast.success).toHaveBeenCalledWith("Clé Finance enregistrée");
    });

    it("shows the configured state and lets the admin clear the key", async () => {
      vi.mocked(incokalkAPI.companies.financeKeyStatus).mockResolvedValue({
        data: { configured: true },
      } as never);
      vi.mocked(incokalkAPI.companies.clearFinanceKey).mockResolvedValue({
        data: { configured: false },
      } as never);
      renderPage();

      await waitFor(() => expect(cardFor("Intégration Finance & Trésorerie").getByText("Clé configurée")).toBeInTheDocument());
      fireEvent.click(cardFor("Intégration Finance & Trésorerie").getByRole("button", { name: "Effacer la clé" }));

      await waitFor(() => {
        expect(incokalkAPI.companies.clearFinanceKey).toHaveBeenCalledWith("company-1");
      });
      expect(toast.success).toHaveBeenCalledWith("Clé Finance effacée");
    });
  });
});
