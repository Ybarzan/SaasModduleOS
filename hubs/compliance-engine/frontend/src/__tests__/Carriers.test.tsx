import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import toast from "react-hot-toast";
import Carriers from "../pages/Carriers";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    carriers: { getPage: vi.fn(), create: vi.fn(), update: vi.fn(), toggle: vi.fn(), delete: vi.fn() },
    export: { csv: { carriers: vi.fn() } },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const carriers = [
  { id: "c1", name: "DHL Express", code: "DHL", transportModes: "AIR,ROAD", active: true, country: "DE" },
  { id: "c2", name: "Maersk Line", code: "MAERSK", transportModes: "SEA", active: false, country: "DK" },
];

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <Carriers />
    </QueryClientProvider>
  );
}

describe("Carriers page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(incokalkAPI.carriers.getPage).mockResolvedValue({ data: carriers } as never);
  });

  it("shows a loading state then lists carriers", async () => {
    renderPage();
    expect(screen.getByText("Chargement des transporteurs...")).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByText("DHL Express")).toBeInTheDocument();
    });
    expect(screen.getByText("Maersk Line")).toBeInTheDocument();
  });

  it("shows an error state when the fetch fails", async () => {
    vi.mocked(incokalkAPI.carriers.getPage).mockRejectedValue(new Error("boom"));
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Erreur lors du chargement des transporteurs")).toBeInTheDocument();
    });
  });

  it("shows the empty state when there are no carriers", async () => {
    vi.mocked(incokalkAPI.carriers.getPage).mockResolvedValue({ data: [] } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Aucun transporteur")).toBeInTheDocument();
    });
  });

  it("validates required fields before creating a carrier", async () => {
    renderPage();
    await waitFor(() => screen.getByText("DHL Express"));
    fireEvent.click(screen.getByText("Ajouter un transporteur"));
    fireEvent.click(screen.getByRole("button", { name: "Créer" }));
    expect(toast.error).toHaveBeenCalledWith("Le nom et le code sont obligatoires");
    expect(incokalkAPI.carriers.create).not.toHaveBeenCalled();
  });

  it("requires at least one transport mode", async () => {
    renderPage();
    await waitFor(() => screen.getByText("DHL Express"));
    fireEvent.click(screen.getByText("Ajouter un transporteur"));
    fireEvent.change(screen.getByPlaceholderText("Nom du transporteur"), { target: { value: "UPS" } });
    fireEvent.change(screen.getByPlaceholderText("Ex: DHL, FedEx..."), { target: { value: "UPS" } });
    fireEvent.click(screen.getByRole("button", { name: "Créer" }));
    expect(toast.error).toHaveBeenCalledWith("Sélectionnez au moins un mode de transport");
  });

  it("creates a carrier with the selected transport modes", async () => {
    vi.mocked(incokalkAPI.carriers.create).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("DHL Express"));
    fireEvent.click(screen.getByText("Ajouter un transporteur"));
    fireEvent.change(screen.getByPlaceholderText("Nom du transporteur"), { target: { value: "UPS" } });
    fireEvent.change(screen.getByPlaceholderText("Ex: DHL, FedEx..."), { target: { value: "UPS" } });
    fireEvent.click(screen.getByRole("button", { name: "Routier" }));
    fireEvent.click(screen.getByRole("button", { name: "Créer" }));

    await waitFor(() => {
      expect(incokalkAPI.carriers.create).toHaveBeenCalledWith(
        expect.objectContaining({ name: "UPS", code: "UPS", transportModes: "ROAD" })
      );
    });
    expect(toast.success).toHaveBeenCalledWith("Transporteur créé avec succès");
  });

  it("toggles a carrier's active status", async () => {
    vi.mocked(incokalkAPI.carriers.toggle).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("DHL Express"));
    fireEvent.click(screen.getByTitle("Désactiver"));
    await waitFor(() => {
      expect(incokalkAPI.carriers.toggle).toHaveBeenCalledWith("c1");
    });
    expect(toast.success).toHaveBeenCalledWith("Statut mis à jour");
  });

  it("deletes a carrier after confirmation", async () => {
    vi.mocked(incokalkAPI.carriers.delete).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("DHL Express"));
    const deleteButtons = screen.getAllByTitle("Supprimer");
    fireEvent.click(deleteButtons[0]);
    await waitFor(() => screen.getByText("Confirmer la suppression"));
    const confirmButtons = screen.getAllByRole("button", { name: "Supprimer" });
    fireEvent.click(confirmButtons[confirmButtons.length - 1]);

    await waitFor(() => {
      expect(incokalkAPI.carriers.delete).toHaveBeenCalledWith("c1");
    });
    expect(toast.success).toHaveBeenCalledWith("Transporteur supprimé");
  });
});
