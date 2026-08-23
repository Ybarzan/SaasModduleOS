import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor, within } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import toast from "react-hot-toast";
import ShippingRates from "../pages/ShippingRates";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    carriers: { getAll: vi.fn() },
    shippingRates: { getPage: vi.fn(), create: vi.fn(), update: vi.fn(), toggle: vi.fn(), delete: vi.fn() },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const rates = [
  {
    id: "r1",
    carrierId: "c1",
    carrierName: "DHL",
    carrierCode: "DHL",
    name: "TARIF STANDARD FR->DE",
    originCountry: "France",
    destinationCountry: "Allemagne",
    transportMode: "ROAD",
    baseRate: 120,
    currency: "EUR",
    ratePerKg: 1.5,
    ratePerCbm: 0,
    transitDaysMin: 2,
    transitDaysMax: 4,
    active: true,
  },
];

function mockDefaults() {
  vi.mocked(incokalkAPI.carriers.getAll).mockResolvedValue({ data: [{ id: "c1", name: "DHL", code: "DHL" }] } as never);
  vi.mocked(incokalkAPI.shippingRates.getPage).mockResolvedValue({ data: rates } as never);
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <ShippingRates />
    </QueryClientProvider>
  );
}

describe("ShippingRates page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockDefaults();
  });

  it("lists rates with route and pricing", async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("TARIF STANDARD FR->DE")).toBeInTheDocument();
    });
    expect(screen.getByText("120.00 €")).toBeInTheDocument();
  });

  it("shows the empty state when there are no rates", async () => {
    vi.mocked(incokalkAPI.shippingRates.getPage).mockResolvedValue({ data: [] } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Aucun tarif configuré")).toBeInTheDocument();
    });
  });

  it("filters rates by search text", async () => {
    renderPage();
    await waitFor(() => screen.getByText("TARIF STANDARD FR->DE"));

    fireEvent.change(screen.getByPlaceholderText("Rechercher par nom, transporteur, origine, destination..."), {
      target: { value: "nomatch" },
    });
    expect(screen.getByText("Aucun résultat")).toBeInTheDocument();
  });

  it("expands a rate's details when clicked", async () => {
    renderPage();
    await waitFor(() => screen.getByText("TARIF STANDARD FR->DE"));
    fireEvent.click(screen.getByText("TARIF STANDARD FR->DE"));
    expect(screen.getByText("Prix au m³ :")).toBeInTheDocument();
  });

  it("creates a new rate once all required fields are filled", async () => {
    // Every field checked by the JS validation (carrier/name/origin/destination/baseRate)
    // also carries a native HTML `required`/`min`, so an empty submit never reaches the JS
    // handler in a real browser either — exercise the actual reachable path instead.
    vi.mocked(incokalkAPI.shippingRates.create).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("TARIF STANDARD FR->DE"));
    fireEvent.click(screen.getByText("Ajouter un tarif"));

    const modal = screen.getByText("Nouveau tarif").closest("div.bg-surface") as HTMLElement;
    const selects = within(modal).getAllByRole("combobox");
    fireEvent.change(selects[0], { target: { value: "c1" } }); // Transporteur
    fireEvent.change(within(modal).getByPlaceholderText("Ex: TARIF STANDARD FR→DE"), {
      target: { value: "TARIF EXPRESS FR->IT" },
    });
    fireEvent.change(selects[1], { target: { value: "France" } }); // Origine
    fireEvent.change(selects[2], { target: { value: "Italie" } }); // Destination
    const baseRateInput = within(modal).getAllByRole("spinbutton")[0];
    fireEvent.change(baseRateInput, { target: { value: "80" } });
    fireEvent.click(within(modal).getByRole("button", { name: "Créer" }));

    await waitFor(() => {
      expect(incokalkAPI.shippingRates.create).toHaveBeenCalledWith(
        expect.objectContaining({ carrierId: "c1", name: "TARIF EXPRESS FR->IT", baseRate: 80 })
      );
    });
    expect(toast.success).toHaveBeenCalledWith("Tarif créé avec succès");
  });

  it("toggles a rate's active status", async () => {
    vi.mocked(incokalkAPI.shippingRates.toggle).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("TARIF STANDARD FR->DE"));

    fireEvent.click(screen.getByTitle("Désactiver"));
    await waitFor(() => {
      expect(incokalkAPI.shippingRates.toggle).toHaveBeenCalledWith("r1");
    });
    expect(toast.success).toHaveBeenCalledWith("Statut mis à jour");
  });

  it("deletes a rate after confirmation", async () => {
    vi.mocked(incokalkAPI.shippingRates.delete).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("TARIF STANDARD FR->DE"));

    fireEvent.click(screen.getByTitle("Supprimer"));
    await waitFor(() => screen.getByText("Confirmer la suppression"));
    const confirmButtons = screen.getAllByRole("button", { name: "Supprimer" });
    fireEvent.click(confirmButtons[confirmButtons.length - 1]);

    await waitFor(() => {
      expect(incokalkAPI.shippingRates.delete).toHaveBeenCalledWith("r1");
    });
    expect(toast.success).toHaveBeenCalledWith("Tarif supprimé");
  });
});
