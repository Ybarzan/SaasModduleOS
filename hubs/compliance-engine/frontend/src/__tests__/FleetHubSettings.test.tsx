import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import toast from "react-hot-toast";
import FleetHubSettings from "../pages/FleetHubSettings";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    fleetHub: {
      getAll: vi.fn(),
      create: vi.fn(),
      update: vi.fn(),
      delete: vi.fn(),
      test: vi.fn(),
      vehicles: vi.fn(),
    },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const config = {
  id: "cfg1",
  name: "Flotte principale",
  baseUrl: "https://fleethub.example.com",
  username: "integration@acme.io",
  isActive: true,
  createdAt: "2026-08-24T10:00:00Z",
};

const vehicle = {
  truckId: 1,
  registration: "AB-123-CD",
  brand: "Volvo",
  model: "FH16",
  driverName: "Jean Dupont",
  latitude: 48.85,
  longitude: 2.35,
  speedKph: 72,
  status: "ROULAGE",
  lastGpsUpdate: "2026-08-24T09:30:00Z",
};

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <FleetHubSettings />
    </QueryClientProvider>
  );
}

describe("FleetHubSettings page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(incokalkAPI.fleetHub.getAll).mockResolvedValue({ data: [config] } as never);
  });

  it("lists configurations with their active status", async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Flotte principale")).toBeInTheDocument();
    });
    expect(screen.getByText("[ACTIF]")).toBeInTheDocument();
  });

  it("shows the empty state when there are no configurations", async () => {
    vi.mocked(incokalkAPI.fleetHub.getAll).mockResolvedValue({ data: [] } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Aucune configuration fleet-hub")).toBeInTheDocument();
    });
  });

  it("shows the last error when present", async () => {
    vi.mocked(incokalkAPI.fleetHub.getAll).mockResolvedValue({
      data: [{ ...config, lastError: "Connexion refusée" }],
    } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Connexion refusée")).toBeInTheDocument();
    });
  });

  it("requires a password when creating a new configuration", async () => {
    renderPage();
    await waitFor(() => screen.getByText("Flotte principale"));

    fireEvent.click(screen.getByRole("button", { name: /Nouvelle configuration/ }));
    fireEvent.change(screen.getByPlaceholderText("Flotte principale"), { target: { value: "Flotte secondaire" } });
    fireEvent.change(screen.getByPlaceholderText("https://fleethub.example.com"), { target: { value: "https://fh2.example.com" } });
    fireEvent.change(screen.getByPlaceholderText("integration@acme.io"), { target: { value: "svc@acme.io" } });
    fireEvent.click(screen.getByRole("button", { name: "Créer" }));

    expect(toast.error).toHaveBeenCalledWith("Le mot de passe est obligatoire à la création");
    expect(incokalkAPI.fleetHub.create).not.toHaveBeenCalled();
  });

  it("creates a new configuration", async () => {
    vi.mocked(incokalkAPI.fleetHub.create).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("Flotte principale"));

    fireEvent.click(screen.getByRole("button", { name: /Nouvelle configuration/ }));
    fireEvent.change(screen.getByPlaceholderText("Flotte principale"), { target: { value: "Flotte secondaire" } });
    fireEvent.change(screen.getByPlaceholderText("https://fleethub.example.com"), { target: { value: "https://fh2.example.com" } });
    fireEvent.change(screen.getByPlaceholderText("integration@acme.io"), { target: { value: "svc@acme.io" } });
    fireEvent.change(screen.getByPlaceholderText("Compte de service sans 2FA activée"), { target: { value: "secret" } });
    fireEvent.click(screen.getByRole("button", { name: "Créer" }));

    await waitFor(() => {
      expect(incokalkAPI.fleetHub.create).toHaveBeenCalledWith(
        expect.objectContaining({ name: "Flotte secondaire", baseUrl: "https://fh2.example.com", username: "svc@acme.io", password: "secret" })
      );
    });
    expect(toast.success).toHaveBeenCalledWith("Configuration créée");
  });

  it("updates a configuration without requiring the password again", async () => {
    vi.mocked(incokalkAPI.fleetHub.update).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("Flotte principale"));

    fireEvent.click(screen.getByTitle("Modifier"));
    fireEvent.change(screen.getByPlaceholderText("Flotte principale"), { target: { value: "Flotte renommée" } });
    fireEvent.click(screen.getByRole("button", { name: "Mettre à jour" }));

    await waitFor(() => {
      expect(incokalkAPI.fleetHub.update).toHaveBeenCalledWith(
        "cfg1", expect.objectContaining({ name: "Flotte renommée", password: "" })
      );
    });
  });

  it("deletes a configuration after confirmation", async () => {
    vi.mocked(incokalkAPI.fleetHub.delete).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("Flotte principale"));

    fireEvent.click(screen.getByTitle("Supprimer"));
    fireEvent.click(screen.getByRole("button", { name: "Confirmer la suppression" }));

    await waitFor(() => {
      expect(incokalkAPI.fleetHub.delete).toHaveBeenCalledWith("cfg1");
    });
    expect(toast.success).toHaveBeenCalledWith("Configuration supprimée");
  });

  it("tests the connection and shows success", async () => {
    vi.mocked(incokalkAPI.fleetHub.test).mockResolvedValue({ data: { success: true } } as never);
    renderPage();
    await waitFor(() => screen.getByText("Flotte principale"));

    fireEvent.click(screen.getByTitle("Tester la connexion"));

    await waitFor(() => {
      expect(toast.success).toHaveBeenCalledWith("Connexion réussie");
    });
  });

  it("tests the connection and shows failure", async () => {
    vi.mocked(incokalkAPI.fleetHub.test).mockResolvedValue({ data: { success: false } } as never);
    renderPage();
    await waitFor(() => screen.getByText("Flotte principale"));

    fireEvent.click(screen.getByTitle("Tester la connexion"));

    await waitFor(() => {
      expect(toast.error).toHaveBeenCalledWith("Échec de la connexion — voir le détail sur la configuration");
    });
  });

  it("expands a configuration to show its vehicles", async () => {
    vi.mocked(incokalkAPI.fleetHub.vehicles).mockResolvedValue({ data: [vehicle] } as never);
    renderPage();
    await waitFor(() => screen.getByText("Flotte principale"));

    fireEvent.click(screen.getByText("Flotte principale"));

    await waitFor(() => {
      expect(incokalkAPI.fleetHub.vehicles).toHaveBeenCalledWith("cfg1");
    });
    await waitFor(() => {
      expect(screen.getByText("AB-123-CD")).toBeInTheDocument();
    });
    expect(screen.getByText("Jean Dupont")).toBeInTheDocument();
  });

  it("shows an empty vehicles message when the fleet has no GPS position available", async () => {
    vi.mocked(incokalkAPI.fleetHub.vehicles).mockResolvedValue({ data: [] } as never);
    renderPage();
    await waitFor(() => screen.getByText("Flotte principale"));

    fireEvent.click(screen.getByText("Flotte principale"));

    await waitFor(() => {
      expect(screen.getByText("Aucun véhicule avec position GPS disponible")).toBeInTheDocument();
    });
  });
});
