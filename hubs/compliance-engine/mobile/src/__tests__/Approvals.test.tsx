import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import Approvals from "../pages/Approvals";
import { mobileApi } from "../lib/api";
import { useAuthStore } from "../stores/auth";

vi.mock("../lib/api", () => ({
  mobileApi: {
    approvals: {
      pending: vi.fn(),
      approve: vi.fn(),
      reject: vi.fn(),
    },
  },
}));

const request = {
  id: "req-1",
  entityType: "QUOTE",
  entityReference: "Q-2026-042",
  status: "PENDING" as const,
  requestedAt: "2026-08-20T10:00:00Z",
  amount: 1200,
  currency: "EUR",
  currentStep: 1,
  totalSteps: 2,
};

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <Approvals />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe("Approvals (mobile)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.setState({ token: "tok", refreshToken: "r", user: { id: "u1", email: "a@b.com", firstName: "A", lastName: "B", role: "MANAGER" }, hasHydrated: true });
  });

  it("restricts the page to managers/admins/owners", async () => {
    useAuthStore.setState({ user: { id: "u1", email: "a@b.com", firstName: "A", lastName: "B", role: "USER" } });
    renderPage();
    expect(screen.getByText("Réservé aux managers, administrateurs et propriétaires.")).toBeInTheDocument();
    expect(mobileApi.approvals.pending).not.toHaveBeenCalled();
  });

  it("shows the empty state when there is nothing pending", async () => {
    vi.mocked(mobileApi.approvals.pending).mockResolvedValue({ data: [] } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Aucune approbation en attente.")).toBeInTheDocument();
    });
  });

  it("lists a pending request with amount and step", async () => {
    vi.mocked(mobileApi.approvals.pending).mockResolvedValue({ data: [request] } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Q-2026-042")).toBeInTheDocument();
    });
    expect(screen.getByText("Devis")).toBeInTheDocument();
    expect(screen.getByText("Étape 1/2")).toBeInTheDocument();
  });

  it("approves a request", async () => {
    vi.mocked(mobileApi.approvals.pending).mockResolvedValue({ data: [request] } as never);
    vi.mocked(mobileApi.approvals.approve).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("Q-2026-042"));

    fireEvent.click(screen.getByRole("button", { name: /Approuver/ }));
    await waitFor(() => {
      expect(mobileApi.approvals.approve).toHaveBeenCalledWith("req-1");
    });
  });

  it("rejects a request", async () => {
    vi.mocked(mobileApi.approvals.pending).mockResolvedValue({ data: [request] } as never);
    vi.mocked(mobileApi.approvals.reject).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("Q-2026-042"));

    fireEvent.click(screen.getByRole("button", { name: /Rejeter/ }));
    await waitFor(() => {
      expect(mobileApi.approvals.reject).toHaveBeenCalledWith("req-1");
    });
  });
});
