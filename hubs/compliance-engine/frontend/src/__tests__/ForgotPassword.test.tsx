import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import toast from "react-hot-toast";
import ForgotPassword from "../pages/ForgotPassword";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    auth: {
      forgotPassword: vi.fn(),
    },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

function renderPage() {
  return render(
    <MemoryRouter>
      <ForgotPassword />
    </MemoryRouter>
  );
}

describe("ForgotPassword page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders the request form", () => {
    renderPage();
    expect(screen.getByText("Mot de passe oublié")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("vous@entreprise.com")).toBeInTheDocument();
  });

  it("sends the reset email and shows the confirmation screen", async () => {
    vi.mocked(incokalkAPI.auth.forgotPassword).mockResolvedValue({} as never);

    renderPage();
    fireEvent.change(screen.getByPlaceholderText("vous@entreprise.com"), {
      target: { value: "jean@incokalk.com" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Envoyer le lien" }));

    await waitFor(() => {
      expect(incokalkAPI.auth.forgotPassword).toHaveBeenCalledWith("jean@incokalk.com");
    });
    await waitFor(() => {
      expect(screen.getByText("Email envoyé")).toBeInTheDocument();
    });
    expect(screen.getByText("jean@incokalk.com")).toBeInTheDocument();
    expect(toast.success).toHaveBeenCalledWith("Email de réinitialisation envoyé");
  });

  it("shows an error toast and stays on the form when the request fails", async () => {
    vi.mocked(incokalkAPI.auth.forgotPassword).mockRejectedValue({
      response: { data: { message: "Trop de tentatives" } },
    });

    renderPage();
    fireEvent.change(screen.getByPlaceholderText("vous@entreprise.com"), {
      target: { value: "jean@incokalk.com" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Envoyer le lien" }));

    await waitFor(() => {
      expect(toast.error).toHaveBeenCalledWith("Trop de tentatives");
    });
    expect(screen.queryByText("Email envoyé")).not.toBeInTheDocument();
  });
});
