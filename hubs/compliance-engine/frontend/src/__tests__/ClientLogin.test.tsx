import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import toast from "react-hot-toast";
import ClientLogin from "../pages/ClientLogin";
import { incokalkAPI } from "../lib/api";
import { useClientAuthStore } from "../stores/clientAuth";

const mockNavigate = vi.fn();
vi.mock("react-router-dom", async (importOriginal) => {
  const actual = await importOriginal<typeof import("react-router-dom")>();
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock("../lib/api", () => ({
  incokalkAPI: { clientAuth: { login: vi.fn() } },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

function renderPage() {
  const utils = render(
    <MemoryRouter>
      <ClientLogin />
    </MemoryRouter>
  );
  const emailInput = utils.container.querySelector('input[type="email"]') as HTMLInputElement;
  const passwordInput = utils.container.querySelector('input[type="password"]') as HTMLInputElement;
  return { ...utils, emailInput, passwordInput };
}

describe("ClientLogin page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    useClientAuthStore.setState({ token: null, client: null });
  });

  it("renders the client login form", () => {
    renderPage();
    expect(screen.getByText("Espace Client")).toBeInTheDocument();
  });

  it("toggles password visibility", () => {
    const { passwordInput } = renderPage();
    expect(passwordInput).toHaveAttribute("type", "password");
    fireEvent.click(screen.getByRole("button", { name: "" }));
    expect(passwordInput).toHaveAttribute("type", "text");
  });

  it("logs the client in and navigates to the client dashboard", async () => {
    vi.mocked(incokalkAPI.clientAuth.login).mockResolvedValue({
      data: { token: "ctok", clientId: "c1", email: "client@acme.com", fullName: "Client Acme", companyId: "co1" },
    } as never);

    const { emailInput, passwordInput } = renderPage();
    fireEvent.change(emailInput, { target: { value: "client@acme.com" } });
    fireEvent.change(passwordInput, { target: { value: "secret123" } });
    fireEvent.click(screen.getByRole("button", { name: "Se connecter" }));

    await waitFor(() => {
      expect(incokalkAPI.clientAuth.login).toHaveBeenCalledWith("client@acme.com", "secret123");
    });
    await waitFor(() => {
      expect(useClientAuthStore.getState().token).toBe("ctok");
    });
    expect(toast.success).toHaveBeenCalledWith("Bienvenue !");
    expect(mockNavigate).toHaveBeenCalledWith("/client/dashboard");
  });

  it("shows the server error message on failed login", async () => {
    vi.mocked(incokalkAPI.clientAuth.login).mockRejectedValue({
      response: { data: { message: "Identifiants invalides" } },
    });

    const { emailInput, passwordInput } = renderPage();
    fireEvent.change(emailInput, { target: { value: "client@acme.com" } });
    fireEvent.change(passwordInput, { target: { value: "wrong" } });
    fireEvent.click(screen.getByRole("button", { name: "Se connecter" }));

    await waitFor(() => {
      expect(toast.error).toHaveBeenCalledWith("Identifiants invalides");
    });
    expect(mockNavigate).not.toHaveBeenCalled();
  });
});
