import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import toast from "react-hot-toast";
import ApiKeys from "../pages/ApiKeys";
import { api } from "../lib/api";
import { useAuthStore } from "../stores/auth";

vi.mock("../lib/api", () => ({
  api: { get: vi.fn(), post: vi.fn(), delete: vi.fn() },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const keys = [
  { id: "k1", name: "Production", keyPrefix: "ik_live_", plan: "PRO", dailyLimit: 500, active: true, createdAt: "2026-01-01T00:00:00Z" },
];

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <ApiKeys />
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

describe("ApiKeys page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api.get).mockResolvedValue({ data: keys } as never);
    Object.defineProperty(navigator, "clipboard", {
      value: { writeText: vi.fn() },
      configurable: true,
    });
  });

  afterEach(() => {
    useAuthStore.setState({ token: null, refreshToken: null, user: null });
  });

  it("lists existing API keys", async () => {
    loginAsAdmin();
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Production")).toBeInTheDocument();
    });
    expect(screen.getByText("Actif")).toBeInTheDocument();
  });

  it("shows the empty state when there are no keys", async () => {
    loginAsAdmin();
    vi.mocked(api.get).mockResolvedValue({ data: [] } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Aucune clé API")).toBeInTheDocument();
    });
  });

  it("hides the generate button for non-admins", async () => {
    useAuthStore.setState({ token: "tok", refreshToken: "r", user: { id: "u2", email: "a@b.com", firstName: "A", lastName: "B", role: "USER" } as never });
    renderPage();
    await waitFor(() => screen.getByText("Production"));
    expect(screen.queryByText("Generate New Key")).not.toBeInTheDocument();
  });

  it("generates a new key and shows it once for copying", async () => {
    loginAsAdmin();
    vi.mocked(api.post).mockResolvedValue({ data: { key: "ik_live_secretvalue" } } as never);
    renderPage();
    await waitFor(() => screen.getByText("Production"));

    fireEvent.click(screen.getByText("Generate New Key"));
    fireEvent.change(screen.getByPlaceholderText("Production, Développement, ..."), {
      target: { value: "CI pipeline" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Générer" }));

    await waitFor(() => {
      expect(api.post).toHaveBeenCalledWith("/v1/api-keys", { name: "CI pipeline" });
    });
    await waitFor(() => {
      expect(screen.getByText("ik_live_secretvalue")).toBeInTheDocument();
    });
    expect(toast.success).toHaveBeenCalledWith("Clé API générée avec succès");
  });

  it("requires a name before generating a key", async () => {
    loginAsAdmin();
    renderPage();
    await waitFor(() => screen.getByText("Production"));
    fireEvent.click(screen.getByText("Generate New Key"));
    fireEvent.click(screen.getByRole("button", { name: "Générer" }));
    expect(toast.error).toHaveBeenCalledWith("Le nom de la clé est requis");
    expect(api.post).not.toHaveBeenCalled();
  });

  it("revokes a key after a two-step confirmation", async () => {
    loginAsAdmin();
    vi.mocked(api.delete).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("Production"));

    fireEvent.click(screen.getByTitle("Révoquer"));
    fireEvent.click(screen.getByText("Oui"));

    await waitFor(() => {
      expect(api.delete).toHaveBeenCalledWith("/v1/api-keys/k1");
    });
    expect(toast.success).toHaveBeenCalledWith("Clé API révoquée");
  });
});
