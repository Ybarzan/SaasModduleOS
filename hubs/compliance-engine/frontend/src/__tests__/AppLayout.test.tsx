import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import AppLayout from "../layouts/AppLayout";
import { useAuthStore } from "../stores/auth";
import { useOnboardingStore } from "../stores/onboarding";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    auth: { logout: vi.fn().mockResolvedValue({}) },
    notifications: { unreadCount: vi.fn().mockResolvedValue({ data: 0 }) },
  },
}));

function renderLayout() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <AppLayout>
          <div>Page content</div>
        </AppLayout>
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe("AppLayout", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // jsdom n'implémente pas matchMedia -- ThemeToggle (monté par AppLayout, via
    // framer-motion's useReducedMotion et useMediaQuery) en a besoin, avec la forme
    // complète de MediaQueryList (addEventListener/removeEventListener inclus).
    window.matchMedia = vi.fn().mockReturnValue({
      matches: false,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      addListener: vi.fn(),
      removeListener: vi.fn(),
    });
    useAuthStore.setState({
      token: "tok",
      refreshToken: "r",
      user: { id: "u1", email: "a@b.com", firstName: "A", lastName: "B", role: "USER" },
    });
    useOnboardingStore.setState({ isOpen: false, hasSeenOnboarding: false });
  });

  afterEach(() => {
    useAuthStore.setState({ token: null, refreshToken: null, user: null });
  });

  it("renders its children", () => {
    renderLayout();
    expect(screen.getByText("Page content")).toBeInTheDocument();
  });

  it("auto-opens the onboarding modal on first mount when it hasn't been seen", () => {
    renderLayout();
    expect(screen.getByRole("dialog", { name: "Bienvenue sur IncoKalk" })).toBeInTheDocument();
  });

  it("does not auto-open onboarding when it was already seen", () => {
    useOnboardingStore.setState({ isOpen: false, hasSeenOnboarding: true });
    renderLayout();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("marks onboarding as seen once dismissed", () => {
    renderLayout();
    fireEvent.click(screen.getByRole("button", { name: "Passer" }));
    expect(useOnboardingStore.getState().hasSeenOnboarding).toBe(true);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("reopens onboarding from the Aide button even after it was seen", () => {
    useOnboardingStore.setState({ isOpen: false, hasSeenOnboarding: true });
    renderLayout();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();

    fireEvent.click(screen.getByTitle("Revoir le guide de démarrage"));
    expect(screen.getByRole("dialog", { name: "Bienvenue sur IncoKalk" })).toBeInTheDocument();
  });

  it("logs out and navigates to the home page", async () => {
    renderLayout();
    fireEvent.click(screen.getByTitle("Déconnexion"));
    expect(incokalkAPI.auth.logout).toHaveBeenCalled();
    expect(useAuthStore.getState().token).toBeNull();
  });
});
