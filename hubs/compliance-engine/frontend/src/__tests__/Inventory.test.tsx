import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import toast from "react-hot-toast";
import Inventory from "../pages/Inventory";
import { incokalkAPI } from "../lib/api";
import { useAuthStore } from "../stores/auth";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    warehouses: { list: vi.fn() },
    inventory: { items: { list: vi.fn() }, balances: vi.fn(), movements: vi.fn(), adjust: vi.fn() },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const warehouses = [{ id: "w1", name: "Entrepôt Paris", code: "WH-01" }];
const items = [{ id: "i1", name: "Carton A4", sku: "SKU-1", unit: "PCS" }];
const balances = [
  { id: "b1", warehouseId: "w1", itemId: "i1", quantityOnHand: 100, quantityReserved: 10, quantityInTransit: 5 },
];

function mockDefaults() {
  vi.mocked(incokalkAPI.warehouses.list).mockResolvedValue({ data: warehouses } as never);
  vi.mocked(incokalkAPI.inventory.items.list).mockResolvedValue({ data: items } as never);
  vi.mocked(incokalkAPI.inventory.balances).mockResolvedValue({ data: balances } as never);
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <Inventory />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

function loginAsManager() {
  useAuthStore.setState({
    token: "tok",
    refreshToken: "r",
    user: { id: "u1", email: "a@b.com", firstName: "A", lastName: "B", role: "MANAGER" } as never,
  });
}

describe("Inventory page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockDefaults();
  });

  afterEach(() => {
    useAuthStore.setState({ token: null, refreshToken: null, user: null });
  });

  it("lists stock balances with item and warehouse names", async () => {
    loginAsManager();
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Carton A4")).toBeInTheDocument();
    });
    expect(screen.getByText("Entrepôt Paris")).toBeInTheDocument();
    expect(screen.getByText("100")).toBeInTheDocument();
  });

  it("filters balances by search text", async () => {
    loginAsManager();
    renderPage();
    await waitFor(() => screen.getByText("Carton A4"));

    fireEvent.change(screen.getByPlaceholderText("Rechercher article / SKU..."), {
      target: { value: "nomatch" },
    });
    expect(screen.getByText("Aucun article ne correspond à cette recherche.")).toBeInTheDocument();
  });

  it("hides adjustment actions for regular users", async () => {
    useAuthStore.setState({ token: "tok", refreshToken: "r", user: { id: "u2", email: "a@b.com", firstName: "A", lastName: "B", role: "USER" } as never });
    renderPage();
    await waitFor(() => screen.getByText("Carton A4"));
    expect(screen.queryByTitle("Ajuster le stock")).not.toBeInTheDocument();
  });

  it("adjusts stock for an item", async () => {
    loginAsManager();
    vi.mocked(incokalkAPI.inventory.adjust).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("Carton A4"));

    fireEvent.click(screen.getByTitle("Ajuster le stock"));
    fireEvent.change(screen.getByRole("spinbutton"), { target: { value: "-5" } });
    fireEvent.click(screen.getByRole("button", { name: "Ajuster" }));

    await waitFor(() => {
      expect(incokalkAPI.inventory.adjust).toHaveBeenCalledWith(
        expect.objectContaining({ warehouseId: "w1", itemId: "i1", quantity: -5 })
      );
    });
    expect(toast.success).toHaveBeenCalledWith("Stock ajusté");
  });

  it("shows movement history for an item", async () => {
    loginAsManager();
    vi.mocked(incokalkAPI.inventory.movements).mockResolvedValue({
      data: [{ id: "m1", itemId: "i1", warehouseId: "w1", quantity: 50, type: "RECEIPT", createdAt: "2026-08-01T00:00:00Z" }],
    } as never);
    renderPage();
    await waitFor(() => screen.getByText("Carton A4"));

    fireEvent.click(screen.getByTitle("Mouvements"));
    await waitFor(() => {
      expect(incokalkAPI.inventory.movements).toHaveBeenCalledWith("i1");
    });
    await waitFor(() => {
      expect(screen.getByText("Réception")).toBeInTheDocument();
    });
    expect(screen.getByText("+50 PCS")).toBeInTheDocument();
  });
});
