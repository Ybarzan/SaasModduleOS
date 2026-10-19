import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import Login from "../pages/Login";
import { logistixAPI } from "../lib/api";
import { useAuthStore } from "../stores/auth";

const mockNavigate = vi.fn();
vi.mock("react-router-dom", async (importOriginal) => {
  const actual = await importOriginal<typeof import("react-router-dom")>();
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock("../lib/api", () => ({
  logistixAPI: {
    auth: { login: vi.fn() },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { error: vi.fn() },
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

describe("Login", () => {
  beforeEach(() => {
    mockNavigate.mockClear();
    useAuthStore.setState({ token: null, user: null });
  });

  it("logs in from the flat AuthResponse payload and survives a page reload", async () => {
    vi.mocked(logistixAPI.auth.login).mockResolvedValue({
      data: {
        token: "jwt-abc",
        email: "ops@acme.fr",
        displayName: "Ops Acme",
        role: "ADMIN",
        companyId: "company-1",
        companyName: "Acme",
      },
    } as never);
    const { emailInput, passwordInput } = renderLogin();

    fireEvent.change(emailInput, { target: { value: "ops@acme.fr" } });
    fireEvent.change(passwordInput, { target: { value: "secret" } });
    fireEvent.click(screen.getByRole("button", { name: "Se connecter" }));

    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith("/warehouses", { replace: true }));

    // La régression couverte ici : `data.user` n'existe jamais dans la vraie
    // réponse backend (AuthResponse est plate) -- si Login.tsx se remettait à
    // passer `data.user` à `login()`, `user` serait `undefined`, ce que
    // `JSON.stringify` (zustand persist) supprimerait silencieusement du
    // blob localStorage, cassant la session au prochain rechargement complet.
    const user = useAuthStore.getState().user;
    expect(user).not.toBeUndefined();
    expect(user).toMatchObject({
      id: "ops@acme.fr",
      email: "ops@acme.fr",
      firstName: "Ops",
      lastName: "Acme",
      role: "ADMIN",
      companyId: "company-1",
      company: "Acme",
    });
    expect(JSON.parse(JSON.stringify(user))).toMatchObject({ email: "ops@acme.fr" });
  });

  it("shows an error and does not navigate when the credentials are rejected", async () => {
    vi.mocked(logistixAPI.auth.login).mockRejectedValue({
      response: { data: { message: "Identifiants invalides" } },
    });
    const { emailInput, passwordInput } = renderLogin();

    fireEvent.change(emailInput, { target: { value: "ops@acme.fr" } });
    fireEvent.change(passwordInput, { target: { value: "wrong" } });
    fireEvent.click(screen.getByRole("button", { name: "Se connecter" }));

    await waitFor(() => expect(logistixAPI.auth.login).toHaveBeenCalledWith("ops@acme.fr", "wrong"));
    expect(mockNavigate).not.toHaveBeenCalled();
    expect(useAuthStore.getState().token).toBeNull();
  });
});
