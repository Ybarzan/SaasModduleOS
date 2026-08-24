import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import toast from "react-hot-toast";
import Shipments from "../pages/Shipments";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    shipments: { getPage: vi.fn(), create: vi.fn(), updateStatus: vi.fn(), delete: vi.fn() },
    carriers: { getAll: vi.fn() },
    shippingRates: { getAll: vi.fn() },
    fleetHub: { allVehicles: vi.fn() },
    export: { shippingLabelPdf: vi.fn(), cmrPdf: vi.fn(), dgdPdf: vi.fn(), certificateOfOriginPdf: vi.fn(), csv: { shipments: vi.fn() } },
    receivings: { list: vi.fn() },
    sharedLinks: { create: vi.fn() },
  },
}));

vi.mock("react-hot-toast", () => {
  const fn = vi.fn();
  return { default: Object.assign(fn, { success: vi.fn(), error: vi.fn() }) };
});

const shipments = [
  {
    id: "s1",
    orderNumber: "ORD-001",
    status: "DRAFT",
    shipperCity: "Paris",
    consigneeCity: "Berlin",
    carrierName: "DHL",
    finalCost: null,
    quotedCost: 250,
    createdAt: "2026-08-01T00:00:00Z",
  },
];

function mockDefaults() {
  vi.mocked(incokalkAPI.shipments.getPage).mockResolvedValue({ data: shipments } as never);
  vi.mocked(incokalkAPI.carriers.getAll).mockResolvedValue({ data: [] } as never);
  vi.mocked(incokalkAPI.shippingRates.getAll).mockResolvedValue({ data: [] } as never);
  vi.mocked(incokalkAPI.fleetHub.allVehicles).mockResolvedValue({ data: [] } as never);
  Object.defineProperty(navigator, "clipboard", {
    value: { writeText: vi.fn().mockResolvedValue(undefined) },
    configurable: true,
  });
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <Shipments />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe("Shipments page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockDefaults();
  });

  it("shows a loading state then lists shipments with computed stats", async () => {
    renderPage();
    expect(screen.getByText("Chargement des expéditions...")).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByText("ORD-001")).toBeInTheDocument();
    });
    expect(screen.getByText("250.00 €")).toBeInTheDocument();
  });

  it("shows an error state when the fetch fails", async () => {
    vi.mocked(incokalkAPI.shipments.getPage).mockRejectedValue(new Error("boom"));
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Erreur lors du chargement des expéditions")).toBeInTheDocument();
    });
  });

  it("shows the empty state when there are no shipments", async () => {
    vi.mocked(incokalkAPI.shipments.getPage).mockResolvedValue({ data: [] } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Aucune expédition")).toBeInTheDocument();
    });
  });

  it("requires shipper and consignee names before creating a shipment", async () => {
    renderPage();
    await waitFor(() => screen.getByText("ORD-001"));
    fireEvent.click(screen.getByText("Nouvelle expédition"));
    fireEvent.click(screen.getByRole("button", { name: "Créer l'expédition" }));
    expect(toast.error).toHaveBeenCalledWith("Le nom de l'expéditeur et du destinataire sont obligatoires");
    expect(incokalkAPI.shipments.create).not.toHaveBeenCalled();
  });

  it("creates a shipment when the required fields are filled", async () => {
    vi.mocked(incokalkAPI.shipments.create).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("ORD-001"));
    fireEvent.click(screen.getByText("Nouvelle expédition"));

    const nameInputs = screen.getAllByPlaceholderText("Nom / Société");
    fireEvent.change(nameInputs[0], { target: { value: "Acme SARL" } });
    fireEvent.change(nameInputs[1], { target: { value: "Client GmbH" } });

    fireEvent.click(screen.getByRole("button", { name: "Créer l'expédition" }));

    await waitFor(() => {
      expect(incokalkAPI.shipments.create).toHaveBeenCalledWith(
        expect.objectContaining({ shipperName: "Acme SARL", consigneeName: "Client GmbH" })
      );
    });
    expect(toast.success).toHaveBeenCalledWith("Expédition créée");
  });

  it("shows a fleet-hub truck picker and includes the selection when creating a shipment", async () => {
    vi.mocked(incokalkAPI.fleetHub.allVehicles).mockResolvedValue({
      data: [{ truckId: 1, registration: "AB-123-CD", driverName: "Jean Dupont", latitude: 0, longitude: 0, speedKph: 0, status: "ROULAGE" }],
    } as never);
    vi.mocked(incokalkAPI.shipments.create).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("ORD-001"));
    fireEvent.click(screen.getByText("Nouvelle expédition"));

    await waitFor(() => {
      expect(screen.getByText("Camion de la flotte propre (optionnel)")).toBeInTheDocument();
    });

    const nameInputs = screen.getAllByPlaceholderText("Nom / Société");
    fireEvent.change(nameInputs[0], { target: { value: "Acme SARL" } });
    fireEvent.change(nameInputs[1], { target: { value: "Client GmbH" } });
    fireEvent.change(screen.getByDisplayValue("Aucun camion assigné"), { target: { value: "AB-123-CD" } });
    fireEvent.click(screen.getByRole("button", { name: "Créer l'expédition" }));

    await waitFor(() => {
      expect(incokalkAPI.shipments.create).toHaveBeenCalledWith(
        expect.objectContaining({ fleetHubTruckRegistration: "AB-123-CD" })
      );
    });
  });

  it("hides the fleet-hub truck picker when no vehicle is available", async () => {
    renderPage();
    await waitFor(() => screen.getByText("ORD-001"));
    fireEvent.click(screen.getByText("Nouvelle expédition"));

    expect(screen.queryByText("Camion de la flotte propre (optionnel)")).not.toBeInTheDocument();
  });

  it("advances a draft shipment's status to booked", async () => {
    vi.mocked(incokalkAPI.shipments.updateStatus).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("ORD-001"));

    fireEvent.click(screen.getByTitle("Réserver"));
    await waitFor(() => {
      expect(incokalkAPI.shipments.updateStatus).toHaveBeenCalledWith("s1", { status: "BOOKED" });
    });
    expect(toast.success).toHaveBeenCalledWith("Statut mis à jour");
  });

  it("deletes a shipment after confirmation", async () => {
    vi.mocked(incokalkAPI.shipments.delete).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("ORD-001"));

    fireEvent.click(screen.getByTitle("Supprimer"));
    await waitFor(() => screen.getByText("Confirmer la suppression"));
    const confirmButtons = screen.getAllByRole("button", { name: "Supprimer" });
    fireEvent.click(confirmButtons[confirmButtons.length - 1]);

    await waitFor(() => {
      expect(incokalkAPI.shipments.delete).toHaveBeenCalledWith("s1");
    });
    expect(toast.success).toHaveBeenCalledWith("Expédition supprimée");
  });

  it("generates and copies a client tracking link from the shipment's detail panel", async () => {
    vi.mocked(incokalkAPI.sharedLinks.create).mockResolvedValue({ data: { url: "/s/abc123" } } as never);
    renderPage();
    await waitFor(() => screen.getByText("ORD-001"));

    fireEvent.click(screen.getByTitle("Détails"));
    await waitFor(() => screen.getByText("Portail client"));

    fireEvent.click(screen.getByText("Générer un lien de suivi client"));

    await waitFor(() => {
      expect(incokalkAPI.sharedLinks.create).toHaveBeenCalledWith({ shipmentId: "s1" });
    });
    await waitFor(() => {
      expect(screen.getByDisplayValue(`${window.location.origin}/s/abc123`)).toBeInTheDocument();
    });
    expect(navigator.clipboard.writeText).toHaveBeenCalledWith(`${window.location.origin}/s/abc123`);
    expect(toast.success).toHaveBeenCalledWith("Lien client copié !");
  });
});
