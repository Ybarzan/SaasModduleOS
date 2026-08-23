import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import Clients from "../pages/Clients";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    clients: { list: vi.fn(), stats: vi.fn(), create: vi.fn(), update: vi.fn(), delete: vi.fn(), resetPassword: vi.fn() },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const clients = [
  { id: "c1", email: "client@test.com", fullName: "Jean Client", phone: "0600000000", active: true, createdAt: "2026-08-01T00:00:00Z" },
];

const stats = { totalClients: 5, activeClients: 3 };

function mockDefaults() {
  vi.mocked(incokalkAPI.clients.list).mockResolvedValue({ data: clients } as never);
  vi.mocked(incokalkAPI.clients.stats).mockResolvedValue({ data: stats } as never);
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <Clients />
    </QueryClientProvider>
  );
}

describe("Clients page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockDefaults();
  });

  it("renders the client list and stats", async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Jean Client")).toBeInTheDocument();
    });
    expect(screen.getByText("5")).toBeInTheDocument();
    expect(screen.getByText("3")).toBeInTheDocument();
  });

  it("shows the empty state when there are no clients", async () => {
    vi.mocked(incokalkAPI.clients.list).mockResolvedValue({ data: [] } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Aucun client")).toBeInTheDocument();
    });
  });

  it("creates a new client", async () => {
    vi.mocked(incokalkAPI.clients.create).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("Jean Client"));

    fireEvent.click(screen.getByRole("button", { name: "Nouveau client" }));
    fireEvent.change(screen.getByPlaceholderText("Nom complet"), { target: { value: "Marie Dupont" } });
    fireEvent.change(screen.getByPlaceholderText("Email"), { target: { value: "marie@test.com" } });
    fireEvent.change(screen.getByPlaceholderText("Mot de passe (min. 8 car.)"), { target: { value: "password1" } });
    fireEvent.click(screen.getByRole("button", { name: "Créer le client" }));

    await waitFor(() => {
      expect(incokalkAPI.clients.create).toHaveBeenCalledWith(
        expect.objectContaining({ fullName: "Marie Dupont", email: "marie@test.com", password: "password1" })
      );
    });
  });

  it("toggles a client's active status", async () => {
    vi.mocked(incokalkAPI.clients.update).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("Jean Client"));

    fireEvent.click(screen.getByTitle("Désactiver"));
    await waitFor(() => {
      expect(incokalkAPI.clients.update).toHaveBeenCalledWith("c1", { active: false });
    });
  });

  it("resets a client's password", async () => {
    vi.mocked(incokalkAPI.clients.resetPassword).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("Jean Client"));

    fireEvent.click(screen.getByTitle("Réinitialiser mot de passe"));
    fireEvent.change(screen.getByPlaceholderText("Nouveau mot de passe (min. 8 car.)"), {
      target: { value: "newpass123" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Réinitialiser" }));

    await waitFor(() => {
      expect(incokalkAPI.clients.resetPassword).toHaveBeenCalledWith("c1", "newpass123");
    });
  });
});
