import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import toast from "react-hot-toast";
import InventoryItems from "../pages/InventoryItems";
import { incokalkAPI } from "../lib/api";
import { useAuthStore } from "../stores/auth";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    inventory: {
      items: { list: vi.fn(), create: vi.fn(), update: vi.fn(), delete: vi.fn() },
      barcodes: { add: vi.fn() },
    },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const items = [
  { id: "i1", name: "Carton A4", sku: "SKU-1", hsCode: "48191000", originCountry: "CN", unit: "PCS", active: true },
];

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <InventoryItems />
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

describe("InventoryItems page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(incokalkAPI.inventory.items.list).mockResolvedValue({ data: items } as never);
  });

  afterEach(() => {
    useAuthStore.setState({ token: null, refreshToken: null, user: null });
  });

  it("lists inventory items with their HS code and origin", async () => {
    loginAsManager();
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Carton A4")).toBeInTheDocument();
    });
    expect(screen.getByText("48191000")).toBeInTheDocument();
  });

  it("shows the empty state when there are no items", async () => {
    loginAsManager();
    vi.mocked(incokalkAPI.inventory.items.list).mockResolvedValue({ data: [] } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Aucun article trouvé")).toBeInTheDocument();
    });
  });

  it("searches items by triggering a new query", async () => {
    loginAsManager();
    renderPage();
    await waitFor(() => screen.getByText("Carton A4"));

    fireEvent.change(screen.getByPlaceholderText("Rechercher par nom, SKU ou code..."), {
      target: { value: "carton" },
    });
    await waitFor(() => {
      expect(incokalkAPI.inventory.items.list).toHaveBeenCalledWith("carton");
    });
  });

  it("creates a new item", async () => {
    loginAsManager();
    vi.mocked(incokalkAPI.inventory.items.create).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("Carton A4"));

    fireEvent.click(screen.getByText("Nouvel article"));
    const modal = screen.getByText("Nouvel article", { selector: "h3" }).closest("div.relative") as HTMLElement;
    fireEvent.change(within(modal).getAllByRole("textbox")[0], { target: { value: "Palette bois" } });
    fireEvent.click(screen.getByRole("button", { name: "Créer" }));

    await waitFor(() => {
      expect(incokalkAPI.inventory.items.create).toHaveBeenCalledWith(
        expect.objectContaining({ name: "Palette bois" })
      );
    });
    expect(toast.success).toHaveBeenCalledWith("Article créé");
  });

  it("associates a barcode with an item", async () => {
    loginAsManager();
    vi.mocked(incokalkAPI.inventory.barcodes.add).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("Carton A4"));

    fireEvent.click(screen.getByTitle("Associer un code-barres"));
    fireEvent.change(screen.getByPlaceholderText("3760123456789"), { target: { value: "1234567890123" } });
    fireEvent.click(screen.getByRole("button", { name: "Associer" }));

    await waitFor(() => {
      expect(incokalkAPI.inventory.barcodes.add).toHaveBeenCalledWith("i1", {
        barcode: "1234567890123",
        type: "EAN13",
      });
    });
    expect(toast.success).toHaveBeenCalledWith("Code-barres associé");
  });

  it("deactivates an item after confirmation", async () => {
    loginAsManager();
    vi.mocked(incokalkAPI.inventory.items.delete).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("Carton A4"));

    fireEvent.click(screen.getByTitle("Désactiver"));
    fireEvent.click(screen.getByText("Confirmer"));

    await waitFor(() => {
      expect(incokalkAPI.inventory.items.delete).toHaveBeenCalledWith("i1");
    });
    expect(toast.success).toHaveBeenCalledWith("Article désactivé");
  });

  it("hides management actions for regular users", async () => {
    useAuthStore.setState({ token: "tok", refreshToken: "r", user: { id: "u2", email: "a@b.com", firstName: "A", lastName: "B", role: "USER" } as never });
    renderPage();
    await waitFor(() => screen.getByText("Carton A4"));
    expect(screen.queryByText("Nouvel article")).not.toBeInTheDocument();
  });
});
