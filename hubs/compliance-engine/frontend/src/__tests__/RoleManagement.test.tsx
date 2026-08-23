import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import toast from "react-hot-toast";
import RoleManagement from "../pages/RoleManagement";
import { incokalkAPI } from "../lib/api";
import { useAuthStore } from "../stores/auth";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    roles: { list: vi.fn(), create: vi.fn(), update: vi.fn(), delete: vi.fn() },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const roles = [
  { id: "r1", name: "Douane", description: "Gère la conformité douanière", userCount: 2, permissions: ["shipments:view"], isSystem: false },
];

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <RoleManagement />
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

describe("RoleManagement page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(incokalkAPI.roles.list).mockResolvedValue({ data: roles } as never);
  });

  afterEach(() => {
    useAuthStore.setState({ token: null, refreshToken: null, user: null });
  });

  it("lists roles with their user count", async () => {
    loginAsAdmin();
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Douane")).toBeInTheDocument();
    });
    expect(screen.getByText("2")).toBeInTheDocument();
  });

  it("shows the empty state when there are no roles", async () => {
    loginAsAdmin();
    vi.mocked(incokalkAPI.roles.list).mockResolvedValue({ data: [] } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Aucun rôle défini")).toBeInTheDocument();
    });
  });

  it("expands a role to show its permissions", async () => {
    loginAsAdmin();
    renderPage();
    await waitFor(() => screen.getByText("Douane"));

    fireEvent.click(screen.getByText("Permissions"));
    expect(screen.getByText("Réduire")).toBeInTheDocument();
    expect(screen.getByText("1/4 permissions")).toBeInTheDocument();
  });

  it("creates a new role with selected permissions", async () => {
    loginAsAdmin();
    vi.mocked(incokalkAPI.roles.create).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("Douane"));

    fireEvent.click(screen.getByText("Nouveau rôle"));
    fireEvent.change(screen.getByPlaceholderText("Ex: Manager"), { target: { value: "Entrepôt" } });
    fireEvent.change(screen.getByPlaceholderText("Description du rôle"), { target: { value: "Gère le stock" } });
    fireEvent.click(screen.getByRole("button", { name: "Créer le rôle" }));

    await waitFor(() => {
      expect(incokalkAPI.roles.create).toHaveBeenCalledWith(
        expect.objectContaining({ name: "Entrepôt", description: "Gère le stock", permissions: [] })
      );
    });
    expect(toast.success).toHaveBeenCalledWith("Rôle créé");
  });

  it("deletes a non-system role after confirmation", async () => {
    loginAsAdmin();
    vi.mocked(incokalkAPI.roles.delete).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("Douane"));

    fireEvent.click(screen.getByTitle("Supprimer"));
    fireEvent.click(screen.getByText("Confirmer"));

    await waitFor(() => {
      expect(incokalkAPI.roles.delete).toHaveBeenCalledWith("r1");
    });
    expect(toast.success).toHaveBeenCalledWith("Rôle supprimé");
  });

  it("hides the new-role button for non-admins", async () => {
    useAuthStore.setState({ token: "tok", refreshToken: "r", user: { id: "u2", email: "a@b.com", firstName: "A", lastName: "B", role: "USER" } as never });
    renderPage();
    await waitFor(() => screen.getByText("Douane"));
    expect(screen.queryByText("Nouveau rôle")).not.toBeInTheDocument();
  });
});
