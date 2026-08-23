import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import toast from "react-hot-toast";
import Warehouses from "../pages/Warehouses";
import { incokalkAPI } from "../lib/api";
import { useAuthStore } from "../stores/auth";

const mockNavigate = vi.fn();
vi.mock("react-router-dom", async (importOriginal) => {
  const actual = await importOriginal<typeof import("react-router-dom")>();
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    warehouses: { list: vi.fn(), create: vi.fn(), update: vi.fn(), delete: vi.fn() },
    inventory: { items: { list: vi.fn() }, balances: vi.fn() },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const warehouses = [
  { id: "w1", name: "Entrepôt Paris", code: "WH-01", city: "Paris", country: "France", active: true },
];

function mockDefaults() {
  vi.mocked(incokalkAPI.warehouses.list).mockResolvedValue({ data: warehouses } as never);
  vi.mocked(incokalkAPI.inventory.items.list).mockResolvedValue({ data: [] } as never);
  vi.mocked(incokalkAPI.inventory.balances).mockResolvedValue({ data: [] } as never);
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <Warehouses />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

function loginAsAdmin() {
  useAuthStore.setState({
    token: "tok",
    refreshToken: "r",
    user: { id: "u1", email: "a@b.com", firstName: "A", lastName: "B", role: "ADMIN" } as never,
  });
}

describe("Warehouses page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockDefaults();
  });

  afterEach(() => {
    useAuthStore.setState({ token: null, refreshToken: null, user: null });
  });

  it("lists warehouses with their status", async () => {
    loginAsAdmin();
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Entrepôt Paris")).toBeInTheDocument();
    });
    expect(screen.getByText("Actif")).toBeInTheDocument();
  });

  it("shows the empty state when there are no warehouses", async () => {
    loginAsAdmin();
    vi.mocked(incokalkAPI.warehouses.list).mockResolvedValue({ data: [] } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Aucun entrepôt. Créez votre premier site de réception.")).toBeInTheDocument();
    });
  });

  it("navigates to the warehouse detail page when a card is clicked", async () => {
    loginAsAdmin();
    renderPage();
    await waitFor(() => screen.getByText("Entrepôt Paris"));
    fireEvent.click(screen.getByText("Entrepôt Paris"));
    expect(mockNavigate).toHaveBeenCalledWith("/warehouses/w1");
  });

  it("hides admin actions for non-admins", async () => {
    useAuthStore.setState({ token: "tok", refreshToken: "r", user: { id: "u2", email: "a@b.com", firstName: "A", lastName: "B", role: "USER" } as never });
    renderPage();
    await waitFor(() => screen.getByText("Entrepôt Paris"));
    expect(screen.queryByText("Nouvel entrepôt")).not.toBeInTheDocument();
  });

  it("creates a new warehouse", async () => {
    loginAsAdmin();
    vi.mocked(incokalkAPI.warehouses.create).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("Entrepôt Paris"));

    fireEvent.click(screen.getByText("Nouvel entrepôt"));
    fireEvent.change(screen.getAllByRole("textbox")[0], { target: { value: "Entrepôt Lyon" } });
    fireEvent.click(screen.getByRole("button", { name: "Créer" }));

    await waitFor(() => {
      expect(incokalkAPI.warehouses.create).toHaveBeenCalledWith(
        expect.objectContaining({ name: "Entrepôt Lyon" })
      );
    });
    expect(toast.success).toHaveBeenCalledWith("Entrepôt créé");
  });

  it("deactivates a warehouse after confirmation", async () => {
    loginAsAdmin();
    vi.mocked(incokalkAPI.warehouses.delete).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("Entrepôt Paris"));

    fireEvent.click(screen.getByTitle("Désactiver"));
    fireEvent.click(screen.getByText("Confirmer"));

    await waitFor(() => {
      expect(incokalkAPI.warehouses.delete).toHaveBeenCalledWith("w1");
    });
    expect(toast.success).toHaveBeenCalledWith("Entrepôt désactivé");
  });
});
