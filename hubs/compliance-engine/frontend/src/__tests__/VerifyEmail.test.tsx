import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import VerifyEmail from "../pages/VerifyEmail";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    auth: { verifyEmail: vi.fn() },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

function renderPage(initialEntry: string) {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <VerifyEmail />
    </MemoryRouter>
  );
}

describe("VerifyEmail page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("shows an invalid-link error when no token is present", () => {
    renderPage("/verify-email");
    expect(screen.getByText("Lien de vérification invalide")).toBeInTheDocument();
  });

  it("verifies the email successfully with a valid token", async () => {
    vi.mocked(incokalkAPI.auth.verifyEmail).mockResolvedValue({
      data: { message: "Email vérifié avec succès" },
    } as never);
    renderPage("/verify-email?token=abc123");

    await waitFor(() => {
      expect(screen.getByText("Email vérifié !")).toBeInTheDocument();
    });
    expect(incokalkAPI.auth.verifyEmail).toHaveBeenCalledWith("abc123");
    expect(screen.getByRole("link", { name: "Se connecter" })).toBeInTheDocument();
  });

  it("shows an error when verification fails", async () => {
    vi.mocked(incokalkAPI.auth.verifyEmail).mockRejectedValue({
      response: { data: { message: "Token expiré" } },
    });
    renderPage("/verify-email?token=expired");

    await waitFor(() => {
      expect(screen.getByText("Erreur de vérification")).toBeInTheDocument();
    });
    expect(screen.getByText("Token expiré")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Retour à la connexion" })).toBeInTheDocument();
  });
});
