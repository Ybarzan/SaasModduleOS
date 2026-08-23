import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import toast from "react-hot-toast";
import Register from "../pages/Register";
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
      register: vi.fn(),
    },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

function renderRegister() {
  const utils = render(
    <MemoryRouter>
      <Register />
    </MemoryRouter>
  );
  const inputs = utils.container.querySelectorAll("input");
  return {
    ...utils,
    nameInput: inputs[0] as HTMLInputElement,
    emailInput: inputs[1] as HTMLInputElement,
    passwordInput: inputs[2] as HTMLInputElement,
    companyInput: inputs[3] as HTMLInputElement,
    referralCodeInput: inputs[4] as HTMLInputElement,
  };
}

function fillAndSubmit(fields: ReturnType<typeof renderRegister>) {
  fireEvent.change(fields.nameInput, { target: { value: "Jean Dupont" } });
  fireEvent.change(fields.emailInput, { target: { value: "jean@incokalk.com" } });
  fireEvent.change(fields.passwordInput, { target: { value: "secret123" } });
  fireEvent.click(screen.getByRole("button", { name: "S'inscrire" }));
}

describe("Register page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    useAuthStore.setState({ token: null, refreshToken: null, user: null });
  });

  it("renders the registration form", () => {
    renderRegister();
    expect(screen.getByText("Inscription")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "S'inscrire" })).toBeInTheDocument();
  });

  it("registers, logs the user in, and shows the welcome screen on success", async () => {
    vi.mocked(incokalkAPI.auth.register).mockResolvedValue({
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

    const fields = renderRegister();
    fillAndSubmit(fields);

    await waitFor(() => {
      expect(incokalkAPI.auth.register).toHaveBeenCalledWith({
        email: "jean@incokalk.com",
        password: "secret123",
        fullName: "Jean Dupont",
        company: "",
        referralCode: "",
      });
    });

    await waitFor(() => {
      expect(screen.getByText("Bienvenue, Jean !")).toBeInTheDocument();
    });
    expect(useAuthStore.getState().token).toBe("tok");
    expect(toast.success).toHaveBeenCalledWith("Inscription réussie ! Bienvenue sur IncoKalk.");
    expect(mockNavigate).not.toHaveBeenCalled();
  });

  it("navigates to the dashboard when clicking the welcome screen's CTA", async () => {
    vi.mocked(incokalkAPI.auth.register).mockResolvedValue({
      data: { token: "tok", refreshToken: "refresh", userId: "u1", email: "jean@incokalk.com" },
    } as never);

    const fields = renderRegister();
    fillAndSubmit(fields);

    await waitFor(() => screen.getByText("Continuer vers le tableau de bord"));
    fireEvent.click(screen.getByText("Continuer vers le tableau de bord"));
    expect(mockNavigate).toHaveBeenCalledWith("/dashboard");
  });

  it("pre-fills the referral code from the ?ref= URL param and shows the reward hint", () => {
    render(
      <MemoryRouter initialEntries={["/register?ref=abcd1234"]}>
        <Register />
      </MemoryRouter>
    );
    const inputs = screen.getAllByRole("textbox");
    const referralCodeInput = inputs[3] as HTMLInputElement;

    expect(referralCodeInput.value).toBe("ABCD1234");
    expect(screen.getByText("1 mois offert dès votre premier abonnement payant")).toBeInTheDocument();
  });

  it("shows the server error message (with details) and stays on the form", async () => {
    vi.mocked(incokalkAPI.auth.register).mockRejectedValue({
      response: { data: { message: "Validation échouée", details: { email: "Email déjà utilisé" } } },
    });

    const fields = renderRegister();
    fillAndSubmit(fields);

    await waitFor(() => {
      expect(screen.getByText("Validation échouée: Email déjà utilisé")).toBeInTheDocument();
    });
    expect(toast.error).toHaveBeenCalledWith("Validation échouée: Email déjà utilisé");
    expect(useAuthStore.getState().token).toBeNull();
  });
});
