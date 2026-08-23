import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import Eori from "../pages/Eori";
import { mobileApi } from "../lib/api";
import { useAuthStore } from "../stores/auth";

vi.mock("../lib/api", () => ({
  mobileApi: { eori: { list: vi.fn(), validate: vi.fn() } },
}));

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <Eori />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe("Eori (mobile)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.setState({ token: "tok", refreshToken: "r", user: { id: "u1", email: "a@b.com", firstName: "A", lastName: "B", role: "MANAGER" }, hasHydrated: true });
  });

  it("restricts the page to managers/admins/owners", () => {
    useAuthStore.setState({ user: { id: "u1", email: "a@b.com", firstName: "A", lastName: "B", role: "USER" } });
    renderPage();
    expect(screen.getByText("Réservé aux managers, administrateurs et propriétaires.")).toBeInTheDocument();
    expect(mobileApi.eori.list).not.toHaveBeenCalled();
  });

  it("lists registered EORI numbers and flags the default one", async () => {
    vi.mocked(mobileApi.eori.list).mockResolvedValue({
      data: [{ id: "e1", eori: "FR12345678901", holderName: "IncoKalk SAS", isDefault: true }],
    } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("FR12345678901")).toBeInTheDocument();
    });
    expect(screen.getByText("Par défaut")).toBeInTheDocument();
  });

  it("validates an EORI number and shows the result", async () => {
    vi.mocked(mobileApi.eori.list).mockResolvedValue({ data: [] } as never);
    vi.mocked(mobileApi.eori.validate).mockResolvedValue({ data: { valid: true } } as never);
    renderPage();

    fireEvent.change(screen.getByPlaceholderText("FR12345678901"), { target: { value: "fr98765432100" } });
    fireEvent.click(screen.getByRole("button", { name: "Vérifier" }));

    await waitFor(() => {
      expect(mobileApi.eori.validate).toHaveBeenCalledWith("FR98765432100");
    });
    await waitFor(() => {
      expect(screen.getByText("EORI valide")).toBeInTheDocument();
    });
  });
});
