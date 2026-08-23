import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import toast from "react-hot-toast";
import PaymentTerms from "../pages/PaymentTerms";
import { incokalkAPI } from "../lib/api";
import { useAuthStore } from "../stores/auth";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    paymentTerms: { list: vi.fn(), create: vi.fn(), update: vi.fn(), delete: vi.fn() },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const terms = [
  { id: "t1", name: "30 jours net", code: "NET30", daysUntilDue: 30, earlyPaymentDiscountPercent: 2, earlyPaymentDiscountDays: 10, default: true },
  { id: "t2", name: "Comptant", code: "COD", daysUntilDue: 0, earlyPaymentDiscountPercent: 0, earlyPaymentDiscountDays: 0, default: false },
];

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <PaymentTerms />
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

describe("PaymentTerms page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(incokalkAPI.paymentTerms.list).mockResolvedValue({ data: terms } as never);
  });

  afterEach(() => {
    useAuthStore.setState({ token: null, refreshToken: null, user: null });
  });

  it("lists payment terms with the default badge", async () => {
    loginAsAdmin();
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("30 jours net")).toBeInTheDocument();
    });
    expect(screen.getByText("Par défaut")).toBeInTheDocument();
    expect(screen.getByText("Comptant")).toBeInTheDocument();
  });

  it("shows the empty state when there are no terms", async () => {
    loginAsAdmin();
    vi.mocked(incokalkAPI.paymentTerms.list).mockResolvedValue({ data: [] } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Aucune condition de paiement")).toBeInTheDocument();
    });
  });

  it("hides admin actions for non-admins", async () => {
    useAuthStore.setState({ token: "tok", refreshToken: "r", user: { id: "u2", email: "a@b.com", firstName: "A", lastName: "B", role: "USER" } as never });
    renderPage();
    await waitFor(() => screen.getByText("30 jours net"));
    expect(screen.queryByText("Nouveau terme")).not.toBeInTheDocument();
  });

  it("creates a new payment term", async () => {
    loginAsAdmin();
    vi.mocked(incokalkAPI.paymentTerms.create).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("30 jours net"));

    fireEvent.click(screen.getByText("Nouveau terme"));
    fireEvent.change(screen.getByPlaceholderText("30 jours net"), { target: { value: "60 jours net" } });
    fireEvent.change(screen.getByPlaceholderText("NET30"), { target: { value: "NET60" } });
    fireEvent.click(screen.getByRole("button", { name: "Créer" }));

    await waitFor(() => {
      expect(incokalkAPI.paymentTerms.create).toHaveBeenCalledWith(
        expect.objectContaining({ name: "60 jours net", code: "NET60" })
      );
    });
    expect(toast.success).toHaveBeenCalledWith("Condition créée");
  });

  it("sets a term as default", async () => {
    loginAsAdmin();
    vi.mocked(incokalkAPI.paymentTerms.update).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("Comptant"));

    fireEvent.click(screen.getByTitle("Définir par défaut"));
    await waitFor(() => {
      expect(incokalkAPI.paymentTerms.update).toHaveBeenCalledWith(
        "t2",
        expect.objectContaining({ isDefault: true })
      );
    });
    expect(toast.success).toHaveBeenCalledWith("Condition définie par défaut");
  });

  it("deletes a term after confirmation", async () => {
    loginAsAdmin();
    vi.mocked(incokalkAPI.paymentTerms.delete).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("30 jours net"));

    const deleteButtons = screen.getAllByTitle("Supprimer");
    fireEvent.click(deleteButtons[0]);
    fireEvent.click(screen.getByText("Confirmer"));

    await waitFor(() => {
      expect(incokalkAPI.paymentTerms.delete).toHaveBeenCalledWith("t1");
    });
    expect(toast.success).toHaveBeenCalledWith("Condition de paiement supprimée");
  });
});
