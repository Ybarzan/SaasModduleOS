import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import toast from "react-hot-toast";
import Receiving from "../pages/Receiving";
import { logistixAPI } from "../lib/api";
import { useAuthStore } from "../stores/auth";

// Pas de `shipments.getAll()` ici (contrairement à compliance-engine) : le hub
// Transport n'existe pas encore, donc le champ "ID Expédition" du formulaire
// de création est une saisie manuelle, sans source de données à mocker.
vi.mock("../lib/api", () => ({
  logistixAPI: {
    warehouses: { list: vi.fn() },
    inventory: { items: { list: vi.fn() } },
    receivings: {
      list: vi.fn(),
      get: vi.fn(),
      discrepancies: vi.fn(),
      create: vi.fn(),
      scan: vi.fn(),
      damage: vi.fn(),
      complete: vi.fn(),
      cancel: vi.fn(),
      resolveDiscrepancy: vi.fn(),
    },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const warehouses = [{ id: "w1", name: "Entrepôt Paris", code: "WH-01" }];
const items = [{ id: "i1", name: "Carton A4", sku: "SKU-1" }];
const orders = [
  { id: "o1", orderNumber: "REC-001", warehouseId: "w1", status: "RECEIVING", reference: "PO-9", createdAt: "2026-08-01T00:00:00Z" },
];
const orderDetail = {
  order: orders[0],
  lines: [{ id: "l1", itemId: "i1", quantityExpected: 100, quantityReceived: 40, quantityDamaged: 0, unit: "PCS" }],
  scans: [],
  discrepancies: [],
  totalExpected: 100,
  totalReceived: 40,
  openDiscrepancyCount: 0,
  remaining: 60,
};

function mockDefaults() {
  vi.mocked(logistixAPI.warehouses.list).mockResolvedValue({ data: warehouses } as never);
  vi.mocked(logistixAPI.inventory.items.list).mockResolvedValue({ data: items } as never);
  vi.mocked(logistixAPI.receivings.list).mockResolvedValue({ data: orders } as never);
  vi.mocked(logistixAPI.receivings.discrepancies).mockResolvedValue({ data: [] } as never);
  vi.mocked(logistixAPI.receivings.get).mockResolvedValue({ data: orderDetail } as never);
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <Receiving />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

function loginAsManager() {
  useAuthStore.setState({
    token: "tok",
    user: { id: "u1", email: "a@b.com", role: "MANAGER" },
  });
}

describe("Receiving page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockDefaults();
  });

  afterEach(() => {
    useAuthStore.setState({ token: null, user: null });
  });

  it("lists receiving orders", async () => {
    loginAsManager();
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("REC-001")).toBeInTheDocument();
    });
  });

  it("shows a placeholder before an order is selected", async () => {
    loginAsManager();
    renderPage();
    await waitFor(() => screen.getByText("REC-001"));
    expect(screen.getByText("Sélectionnez un bon de réception pour voir son détail.")).toBeInTheDocument();
  });

  it("shows order detail with expected/received totals once selected", async () => {
    loginAsManager();
    renderPage();
    await waitFor(() => screen.getByText("REC-001"));
    fireEvent.click(screen.getByText("REC-001"));

    await waitFor(() => {
      expect(logistixAPI.receivings.get).toHaveBeenCalledWith("o1");
    });
    await waitFor(() => {
      expect(screen.getByText("Carton A4")).toBeInTheDocument();
    });
  });

  it("creates a new receiving order with a manually entered shipment id", async () => {
    loginAsManager();
    vi.mocked(logistixAPI.receivings.create).mockResolvedValue({ data: { id: "o2" } } as never);
    renderPage();
    await waitFor(() => screen.getByText("REC-001"));

    fireEvent.click(screen.getByText("Nouveau bon"));
    const modal = screen.getByText("Nouveau bon de réception").closest("div.relative") as HTMLElement;
    fireEvent.change(within(modal).getAllByRole("combobox")[0], { target: { value: "w1" } });
    fireEvent.change(within(modal).getByPlaceholderText("UUID de l'expédition (saisie manuelle)"), {
      target: { value: "ship-123" },
    });
    fireEvent.click(within(modal).getByRole("button", { name: "Créer" }));

    await waitFor(() => {
      expect(logistixAPI.receivings.create).toHaveBeenCalledWith(
        expect.objectContaining({ warehouseId: "w1", shipmentId: "ship-123" })
      );
    });
    expect(toast.success).toHaveBeenCalledWith("Bon de réception créé");
  });

  it("completes a receiving order", async () => {
    loginAsManager();
    vi.mocked(logistixAPI.receivings.complete).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("REC-001"));
    fireEvent.click(screen.getByText("REC-001"));
    await waitFor(() => screen.getByText("Clôturer"));

    fireEvent.click(screen.getByText("Clôturer"));
    await waitFor(() => {
      expect(logistixAPI.receivings.complete).toHaveBeenCalledWith("o1");
    });
    expect(toast.success).toHaveBeenCalledWith("Bon de réception clôturé");
  });

  it("hides the create button for regular users", async () => {
    useAuthStore.setState({ token: "tok", user: { id: "u2", email: "a@b.com", role: "USER" } });
    renderPage();
    await waitFor(() => screen.getByText("REC-001"));
    expect(screen.queryByText("Nouveau bon")).not.toBeInTheDocument();
  });
});
