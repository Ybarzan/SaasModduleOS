import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import NotificationRules from "../pages/NotificationRules";
import { mobileApi } from "../lib/api";
import { useAuthStore } from "../stores/auth";

vi.mock("../lib/api", () => ({
  mobileApi: { notificationRules: { list: vi.fn() } },
}));

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <NotificationRules />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe("NotificationRules (mobile)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.setState({ token: "tok", refreshToken: "r", user: { id: "u1", email: "a@b.com", firstName: "A", lastName: "B", role: "MANAGER" }, hasHydrated: true });
  });

  it("restricts the page to managers/admins/owners", () => {
    useAuthStore.setState({ user: { id: "u1", email: "a@b.com", firstName: "A", lastName: "B", role: "USER" } });
    renderPage();
    expect(screen.getByText("Réservé aux managers, administrateurs et propriétaires.")).toBeInTheDocument();
    expect(mobileApi.notificationRules.list).not.toHaveBeenCalled();
  });

  it("lists rules with their event type", async () => {
    vi.mocked(mobileApi.notificationRules.list).mockResolvedValue({
      data: [{ id: "n1", name: "Alerte livraison", eventType: "SHIPMENT_STATUS_CHANGE", active: true }],
    } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Alerte livraison")).toBeInTheDocument();
    });
    expect(screen.getByText("Changement de statut expédition")).toBeInTheDocument();
  });
});
