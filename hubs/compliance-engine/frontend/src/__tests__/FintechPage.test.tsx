import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import FintechPage from "../pages/FintechPage";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    fintech: {
      listConnections: vi.fn(),
      fetchData: vi.fn(),
      createConnection: vi.fn(),
      testConnection: vi.fn(),
      syncConnection: vi.fn(),
      deleteConnection: vi.fn(),
      updateConnection: vi.fn(),
    },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const connections = [
  {
    id: "f1",
    provider: "QONTO",
    name: "Compte pro Qonto",
    apiKey: "",
    apiSecret: "",
    active: true,
    lastSyncAt: "2026-08-01T00:00:00Z",
    createdAt: "2026-01-01T00:00:00Z",
  },
];

function mockDefaults() {
  vi.mocked(incokalkAPI.fintech.listConnections).mockResolvedValue({ data: connections } as never);
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <FintechPage />
    </QueryClientProvider>
  );
}

describe("FintechPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockDefaults();
  });

  it("renders the fintech connections list", async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Compte pro Qonto")).toBeInTheDocument();
    });
    expect(screen.getByText("Qonto")).toBeInTheDocument();
  });

  it("shows the empty state when there are no connections", async () => {
    vi.mocked(incokalkAPI.fintech.listConnections).mockResolvedValue({ data: [] } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText(/Aucune connexion fintech/)).toBeInTheDocument();
    });
  });

  it("creates a new fintech connection", async () => {
    vi.mocked(incokalkAPI.fintech.createConnection).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("Compte pro Qonto"));

    fireEvent.click(screen.getByRole("button", { name: "Connecter un fournisseur" }));
    fireEvent.change(screen.getByPlaceholderText("Nom de la connexion *"), {
      target: { value: "Spendesk Ops" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Connecter" }));

    await waitFor(() => {
      expect(incokalkAPI.fintech.createConnection).toHaveBeenCalledWith(
        expect.objectContaining({ name: "Spendesk Ops", provider: "QONTO" })
      );
    });
  });

  it("tests a fintech connection", async () => {
    vi.mocked(incokalkAPI.fintech.testConnection).mockResolvedValue({
      data: { ok: true, message: "Connexion réussie" },
    } as never);
    renderPage();
    await waitFor(() => screen.getByText("Compte pro Qonto"));

    fireEvent.click(screen.getByRole("button", { name: /Tester/ }));
    await waitFor(() => {
      expect(incokalkAPI.fintech.testConnection).toHaveBeenCalledWith("f1");
    });
    expect(screen.getByText("Connexion réussie")).toBeInTheDocument();
  });

  it("deletes a connection after confirmation", async () => {
    vi.spyOn(window, "confirm").mockReturnValue(true);
    vi.mocked(incokalkAPI.fintech.deleteConnection).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("Compte pro Qonto"));

    fireEvent.click(screen.getByTitle("Supprimer"));
    await waitFor(() => {
      expect(incokalkAPI.fintech.deleteConnection).toHaveBeenCalledWith("f1");
    });
  });
});
