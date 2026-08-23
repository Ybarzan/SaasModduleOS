import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import toast from "react-hot-toast";
import ResetPassword from "../pages/ResetPassword";
import { incokalkAPI } from "../lib/api";

const mockNavigate = vi.fn();
vi.mock("react-router-dom", async (importOriginal) => {
  const actual = await importOriginal<typeof import("react-router-dom")>();
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    auth: {
      resetPassword: vi.fn(),
    },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

function renderPage(path = "/reset-password?token=abc123") {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <ResetPassword />
    </MemoryRouter>
  );
}

function passwordInputs(container: HTMLElement) {
  return Array.from(container.querySelectorAll('input[type="password"]')) as HTMLInputElement[];
}

describe("ResetPassword page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("redirects to /login when no token is present in the URL", () => {
    renderPage("/reset-password");
    expect(toast.error).toHaveBeenCalledWith("Lien de réinitialisation invalide");
    expect(mockNavigate).toHaveBeenCalledWith("/login");
  });

  it("renders the form when a token is present", () => {
    renderPage();
    expect(screen.getByRole("heading", { name: "Nouveau mot de passe" })).toBeInTheDocument();
    expect(mockNavigate).not.toHaveBeenCalled();
  });

  it("rejects mismatched passwords without calling the API", async () => {
    const { container } = renderPage();
    const [pw, confirm] = passwordInputs(container);
    fireEvent.change(pw, { target: { value: "secret123" } });
    fireEvent.change(confirm, { target: { value: "different1" } });
    fireEvent.click(screen.getByRole("button", { name: "Réinitialiser le mot de passe" }));

    await waitFor(() => {
      expect(toast.error).toHaveBeenCalledWith("Les mots de passe ne correspondent pas");
    });
    expect(incokalkAPI.auth.resetPassword).not.toHaveBeenCalled();
  });

  it("rejects passwords shorter than 8 characters", async () => {
    const { container } = renderPage();
    const [pw, confirm] = passwordInputs(container);
    fireEvent.change(pw, { target: { value: "short" } });
    fireEvent.change(confirm, { target: { value: "short" } });
    fireEvent.click(screen.getByRole("button", { name: "Réinitialiser le mot de passe" }));

    await waitFor(() => {
      expect(toast.error).toHaveBeenCalledWith("Le mot de passe doit contenir au moins 8 caractères");
    });
    expect(incokalkAPI.auth.resetPassword).not.toHaveBeenCalled();
  });

  it("resets the password and shows the success screen", async () => {
    vi.mocked(incokalkAPI.auth.resetPassword).mockResolvedValue({} as never);

    const { container } = renderPage();
    const [pw, confirm] = passwordInputs(container);
    fireEvent.change(pw, { target: { value: "secret123" } });
    fireEvent.change(confirm, { target: { value: "secret123" } });
    fireEvent.click(screen.getByRole("button", { name: "Réinitialiser le mot de passe" }));

    await waitFor(() => {
      expect(incokalkAPI.auth.resetPassword).toHaveBeenCalledWith("abc123", "secret123");
    });
    await waitFor(() => {
      expect(screen.getByText("Mot de passe réinitialisé")).toBeInTheDocument();
    });
    expect(toast.success).toHaveBeenCalledWith("Mot de passe réinitialisé avec succès");
  });

  it("shows the server error message when the reset fails", async () => {
    vi.mocked(incokalkAPI.auth.resetPassword).mockRejectedValue({
      response: { data: { message: "Lien expiré" } },
    });

    const { container } = renderPage();
    const [pw, confirm] = passwordInputs(container);
    fireEvent.change(pw, { target: { value: "secret123" } });
    fireEvent.change(confirm, { target: { value: "secret123" } });
    fireEvent.click(screen.getByRole("button", { name: "Réinitialiser le mot de passe" }));

    await waitFor(() => {
      expect(toast.error).toHaveBeenCalledWith("Lien expiré");
    });
  });
});
