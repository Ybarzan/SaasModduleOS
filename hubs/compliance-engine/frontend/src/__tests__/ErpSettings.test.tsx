import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import ErpSettings from "../pages/ErpSettings";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    erp: {
      getAll: vi.fn(),
      health: vi.fn(),
      syncLogs: vi.fn(),
      create: vi.fn(),
      delete: vi.fn(),
      test: vi.fn(),
      sync: vi.fn(),
      products: vi.fn(),
      orders: vi.fn(),
      contacts: vi.fn(),
    },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const connectedConfig = {
  id: "e1",
  erpType: "ODOO",
  name: "Mon Odoo",
  apiEndpoint: "https://odoo.example.com",
  databaseName: "odoo_db",
  username: "admin",
  isActive: true,
  syncStatus: "IDLE",
  lastSyncAt: "2026-08-01T00:00:00Z",
  lastError: null,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-08-01T00:00:00Z",
};

const health = {
  erpType: "ODOO",
  name: "Mon Odoo",
  syncStatus: "IDLE",
  lastSyncAt: "2026-08-01T00:00:00Z",
  lastError: null,
};

function mockEmpty() {
  vi.mocked(incokalkAPI.erp.getAll).mockResolvedValue({ data: [] } as never);
  vi.mocked(incokalkAPI.erp.health).mockResolvedValue({ data: [] } as never);
  vi.mocked(incokalkAPI.erp.syncLogs).mockResolvedValue({ data: [] } as never);
}

function mockConnected() {
  vi.mocked(incokalkAPI.erp.getAll).mockResolvedValue({ data: [connectedConfig] } as never);
  vi.mocked(incokalkAPI.erp.health).mockResolvedValue({ data: [health] } as never);
  vi.mocked(incokalkAPI.erp.syncLogs).mockResolvedValue({ data: [] } as never);
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <ErpSettings />
    </QueryClientProvider>
  );
}

describe("ErpSettings page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("shows the empty state when no ERP is configured", async () => {
    mockEmpty();
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Aucune intégration ERP configurée")).toBeInTheDocument();
    });
    expect(screen.getAllByText("Non connecté").length).toBe(3);
  });

  it("connects an ERP provider", async () => {
    mockEmpty();
    vi.mocked(incokalkAPI.erp.create).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("Aucune intégration ERP configurée"));

    fireEvent.click(screen.getAllByRole("button", { name: "Connecter" })[0]);
    fireEvent.change(screen.getByPlaceholderText("Ma connexion Odoo"), {
      target: { value: "Odoo Prod" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Sauvegarder" }));

    await waitFor(() => {
      expect(incokalkAPI.erp.create).toHaveBeenCalledWith(
        expect.objectContaining({ erpType: "ODOO", name: "Odoo Prod" })
      );
    });
  });

  it("shows a connected ERP as active with its health status", async () => {
    mockConnected();
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Connecté")).toBeInTheDocument();
    });
    expect(screen.getByRole("button", { name: /Gérer/ })).toBeInTheDocument();
  });

  it("tests an existing ERP connection", async () => {
    mockConnected();
    vi.mocked(incokalkAPI.erp.test).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("Connecté"));

    fireEvent.click(screen.getByTitle("Tester la connexion"));
    await waitFor(() => {
      expect(incokalkAPI.erp.test).toHaveBeenCalledWith("e1");
    });
  });
});
