import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import toast from "react-hot-toast";
import Login from "../pages/Login";
import { incokalkAPI } from "../lib/api";
import { useAuthStore } from "../stores/auth";

const mockNavigate = vi.fn();
vi.mock("react-router-dom", async (importOriginal) => {
  const actual = await importOriginal<typeof import("react-router-dom")>();
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    auth: {
      login: vi.fn(),
    },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

function renderLogin() {
  const utils = render(
    <MemoryRouter>
      <Login />
    </MemoryRouter>
  );
  const emailInput = utils.container.querySelector('input[type="email"]') as HTMLInputElement;
  const passwordInput = utils.container.querySelector('input[type="password"]') as HTMLInputElement;
  return { ...utils, emailInput, passwordInput };
}

describe("Login page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    useAuthStore.setState({ token: null, refreshToken: null, user: null });
  });

  it("renders the login form", () => {
    renderLogin();
    expect(screen.getByText("Connexion")).toBeInTheDocument();
    expect(screen.getByText("Email")).toBeInTheDocument();
    expect(screen.getByText("Mot de passe")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Se connecter" })).toBeInTheDocument();
  });

  it("logs in, stores the session, and navigates to the dashboard on success", async () => {
    vi.mocked(incokalkAPI.auth.login).mockResolvedValue({
      data: {
        token: "tok",
        refreshToken: "refresh",
        userId: "u1",
        email: "jean@incokalk.com",
        role: "USER",
        plan: "FREE",
        fullName: "Jean Dupont",
      },
    } as never);

    const { emailInput, passwordInput } = renderLogin();
    fireEvent.change(emailInput, { target: { value: "jean@incokalk.com" } });
    fireEvent.change(passwordInput, { target: { value: "secret123" } });
    fireEvent.click(screen.getByRole("button", { name: "Se connecter" }));

    await waitFor(() => {
      expect(incokalkAPI.auth.login).toHaveBeenCalledWith("jean@incokalk.com", "secret123");
    });

    await waitFor(() => {
      expect(useAuthStore.getState().token).toBe("tok");
      expect(useAuthStore.getState().user?.firstName).toBe("Jean");
      expect(useAuthStore.getState().user?.lastName).toBe("Dupont");
    });

    expect(toast.success).toHaveBeenCalledWith("Connexion réussie !");
    expect(mockNavigate).toHaveBeenCalledWith("/dashboard");
  });

  it("shows the server error message and does not navigate on failure", async () => {
    vi.mocked(incokalkAPI.auth.login).mockRejectedValue({
      response: { data: { message: "Identifiants invalides" } },
    });

    const { emailInput, passwordInput } = renderLogin();
    fireEvent.change(emailInput, { target: { value: "jean@incokalk.com" } });
    fireEvent.change(passwordInput, { target: { value: "wrong" } });
    fireEvent.click(screen.getByRole("button", { name: "Se connecter" }));

    await waitFor(() => {
      expect(toast.error).toHaveBeenCalledWith("Identifiants invalides");
    });
    expect(mockNavigate).not.toHaveBeenCalled();
    expect(useAuthStore.getState().token).toBeNull();
  });

  it("falls back to a generic error message when the server sends none", async () => {
    vi.mocked(incokalkAPI.auth.login).mockRejectedValue({});

    const { emailInput, passwordInput } = renderLogin();
    fireEvent.change(emailInput, { target: { value: "jean@incokalk.com" } });
    fireEvent.change(passwordInput, { target: { value: "wrong" } });
    fireEvent.click(screen.getByRole("button", { name: "Se connecter" }));

    await waitFor(() => {
      expect(toast.error).toHaveBeenCalledWith("Erreur de connexion");
    });
  });
});
