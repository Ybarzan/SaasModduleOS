import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import EmailIntake from "../pages/EmailIntake";
import { mobileApi } from "../lib/api";
import { useAuthStore } from "../stores/auth";

vi.mock("../lib/api", () => ({
  mobileApi: { emailIntake: { logs: vi.fn() } },
}));

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <EmailIntake />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe("EmailIntake (mobile)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.setState({ token: "tok", refreshToken: "r", user: { id: "u1", email: "a@b.com", firstName: "A", lastName: "B", role: "ADMIN" }, hasHydrated: true });
  });

  it("restricts the page to admins/owners, unlike other MANAGER-gated screens", () => {
    useAuthStore.setState({ user: { id: "u1", email: "a@b.com", firstName: "A", lastName: "B", role: "MANAGER" } });
    renderPage();
    expect(screen.getByText("Réservé aux administrateurs et propriétaires.")).toBeInTheDocument();
    expect(mobileApi.emailIntake.logs).not.toHaveBeenCalled();
  });

  it("lists sync logs with processed/error counts", async () => {
    vi.mocked(mobileApi.emailIntake.logs).mockResolvedValue({
      data: [{ id: "l1", status: "SUCCESS", message: "ok", processedCount: 3, errorCount: 0, startedAt: "2026-08-22T10:00:00Z" }],
    } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("3 document(s) traité(s)")).toBeInTheDocument();
    });
    expect(screen.getByText("SUCCESS")).toBeInTheDocument();
  });

  it("shows the empty state when there is no recent sync", async () => {
    vi.mocked(mobileApi.emailIntake.logs).mockResolvedValue({ data: [] } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Aucune synchronisation récente.")).toBeInTheDocument();
    });
  });
});
