import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import Dps from "../pages/Dps";
import { mobileApi } from "../lib/api";
import { useAuthStore } from "../stores/auth";

vi.mock("../lib/api", () => ({
  mobileApi: { dps: { screen: vi.fn() } },
}));

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <Dps />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe("Dps (mobile)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.setState({ token: "tok", refreshToken: "r", user: { id: "u1", email: "a@b.com", firstName: "A", lastName: "B", role: "MANAGER" }, hasHydrated: true });
  });

  it("restricts the page to managers/admins/owners", () => {
    useAuthStore.setState({ user: { id: "u1", email: "a@b.com", firstName: "A", lastName: "B", role: "USER" } });
    renderPage();
    expect(screen.getByText("Réservé aux managers, administrateurs et propriétaires.")).toBeInTheDocument();
  });

  it("screens a party and shows a clear result", async () => {
    vi.mocked(mobileApi.dps.screen).mockResolvedValue({
      data: { riskLevel: "NONE", matchedListName: null, matchedEntryDetails: null },
    } as never);
    renderPage();

    fireEvent.change(screen.getByPlaceholderText("Nom de la partie / entreprise"), { target: { value: "Acme Corp" } });
    fireEvent.click(screen.getByRole("button", { name: "Vérifier" }));

    await waitFor(() => {
      expect(mobileApi.dps.screen).toHaveBeenCalledWith({ name: "Acme Corp", countryCode: undefined });
    });
    await waitFor(() => {
      expect(screen.getByText("Aucun risque")).toBeInTheDocument();
    });
  });

  it("shows a high-risk match with the matched list", async () => {
    vi.mocked(mobileApi.dps.screen).mockResolvedValue({
      data: { riskLevel: "HIGH", matchedListName: "OFAC SDN", matchedEntryDetails: "..." },
    } as never);
    renderPage();

    fireEvent.change(screen.getByPlaceholderText("Nom de la partie / entreprise"), { target: { value: "Suspect Ltd" } });
    fireEvent.click(screen.getByRole("button", { name: "Vérifier" }));

    await waitFor(() => {
      expect(screen.getByText("Risque élevé")).toBeInTheDocument();
    });
    expect(screen.getByText("Liste : OFAC SDN")).toBeInTheDocument();
  });
});
