import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import toast from "react-hot-toast";
import WarehouseDetail from "../pages/WarehouseDetail";
import { incokalkAPI } from "../lib/api";
import { useAuthStore } from "../stores/auth";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    warehouses: { get: vi.fn() },
    inventory: { items: { list: vi.fn() }, balances: vi.fn(), movements: vi.fn(), adjust: vi.fn() },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const warehouse = { id: "w1", name: "Entrepôt Paris", code: "WH-01", city: "Paris", country: "France", active: true };
const items = [{ id: "i1", name: "Carton A4", sku: "SKU-1", unit: "PCS", unitPrice: 5, category: "Emballage" }];
const balances = [
  { id: "b1", warehouseId: "w1", itemId: "i1", quantityOnHand: 100, quantityReserved: 10, quantityInTransit: 0 },
];

function mockDefaults() {
  vi.mocked(incokalkAPI.warehouses.get).mockResolvedValue({ data: warehouse } as never);
  vi.mocked(incokalkAPI.inventory.items.list).mockResolvedValue({ data: items } as never);
  vi.mocked(incokalkAPI.inventory.balances).mockResolvedValue({ data: balances } as never);
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={["/warehouses/w1"]}>
        <Routes>
          <Route path="/warehouses/:id" element={<WarehouseDetail />} />
        </Routes>
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

describe("WarehouseDetail page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockDefaults();
  });

  afterEach(() => {
    useAuthStore.setState({ token: null, refreshToken: null, user: null });
  });

  it("shows warehouse name and computed stats", async () => {
    loginAsManager();
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Entrepôt Paris")).toBeInTheDocument();
    });
    expect(screen.getAllByText("1").length).toBeGreaterThan(0); // itemCount / categoryCount
    expect(screen.getByText("100")).toBeInTheDocument(); // unitsTotal
  });

  it("shows a not-found message for a missing warehouse", async () => {
    vi.mocked(incokalkAPI.warehouses.get).mockResolvedValue({ data: undefined } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Entrepôt introuvable.")).toBeInTheDocument();
    });
  });

  it("switches between map and list views", async () => {
    loginAsManager();
    renderPage();
    await waitFor(() => screen.getByText("Entrepôt Paris"));

    fireEvent.click(screen.getByText("Vue liste"));
    await waitFor(() => {
      expect(screen.getByText("Emballage")).toBeInTheDocument();
    });
  });

  it("adjusts stock from the list view", async () => {
    loginAsManager();
    vi.mocked(incokalkAPI.inventory.adjust).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("Entrepôt Paris"));
    fireEvent.click(screen.getByText("Vue liste"));
    await waitFor(() => screen.getByTitle("Ajuster le stock"));

    fireEvent.click(screen.getByTitle("Ajuster le stock"));
    fireEvent.change(screen.getByRole("spinbutton"), { target: { value: "-10" } });
    fireEvent.click(screen.getByRole("button", { name: "Ajuster" }));

    await waitFor(() => {
      expect(incokalkAPI.inventory.adjust).toHaveBeenCalledWith(
        expect.objectContaining({ warehouseId: "w1", itemId: "i1", quantity: -10 })
      );
    });
    expect(toast.success).toHaveBeenCalledWith("Stock ajusté");
  });

  it("hides adjustment actions for regular users", async () => {
    useAuthStore.setState({ token: "tok", refreshToken: "r", user: { id: "u2", email: "a@b.com", firstName: "A", lastName: "B", role: "USER" } as never });
    renderPage();
    await waitFor(() => screen.getByText("Entrepôt Paris"));
    fireEvent.click(screen.getByText("Vue liste"));
    await waitFor(() => screen.getByText("Emballage"));
    expect(screen.queryByTitle("Ajuster le stock")).not.toBeInTheDocument();
  });
});
