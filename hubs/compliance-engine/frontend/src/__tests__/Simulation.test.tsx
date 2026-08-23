import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import Simulation from "../pages/Simulation";
import { incokalkAPI } from "../lib/api";
import { useAuthStore } from "../stores/auth";

const mockNavigate = vi.fn();
vi.mock("react-router-dom", async (importOriginal) => {
  const actual = await importOriginal<typeof import("react-router-dom")>();
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock("../lib/api", () => ({
  incokalkAPI: { incoterms: { getAll: vi.fn() } },
}));

const incoterms = [
  { id: "1", code: "EXW", fullName: "Ex Works", mode: "any", buyerRiskScore: 5 },
  { id: "2", code: "FOB", fullName: "Free On Board", mode: "sea_only", buyerRiskScore: 3 },
];

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <Simulation />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe("Simulation page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.setState({ token: null, refreshToken: null, user: null });
  });

  it("shows a loading state while incoterms are being fetched", () => {
    vi.mocked(incokalkAPI.incoterms.getAll).mockReturnValue(new Promise(() => {}) as never);
    renderPage();
    expect(screen.getByText("Chargement des Incoterms...")).toBeInTheDocument();
  });

  it("shows an error state when the fetch fails", async () => {
    vi.mocked(incokalkAPI.incoterms.getAll).mockRejectedValue(new Error("network error"));
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Erreur lors du chargement des Incoterms")).toBeInTheDocument();
    });
  });

  it("prompts anonymous users to log in to save simulations", async () => {
    vi.mocked(incokalkAPI.incoterms.getAll).mockResolvedValue({ data: incoterms } as never);
    renderPage();
    await waitFor(() => screen.getByText("EXW"));
    expect(screen.getByText("Connectez-vous pour sauvegarder vos simulations")).toBeInTheDocument();
  });

  it("does not show the login prompt for authenticated users", async () => {
    useAuthStore.setState({ token: "tok", refreshToken: "r", user: { id: "u1", email: "a@b.com", firstName: "A", lastName: "B", role: "USER" } as never });
    vi.mocked(incokalkAPI.incoterms.getAll).mockResolvedValue({ data: incoterms } as never);
    renderPage();
    await waitFor(() => screen.getByText("EXW"));
    expect(screen.queryByText("Connectez-vous pour sauvegarder vos simulations")).not.toBeInTheDocument();
  });

  it("navigates to the calculator with the selected incoterm when a mode is clicked", async () => {
    vi.mocked(incokalkAPI.incoterms.getAll).mockResolvedValue({ data: incoterms } as never);
    renderPage();
    await waitFor(() => screen.getByText("EXW"));

    const cards = screen.getAllByText("Calculer les coûts");
    fireEvent.click(cards[0]);
    expect(mockNavigate).toHaveBeenCalledWith("/calculator", {
      state: { incotermId: "1", transportMode: "SEA" },
    });
  });
});
