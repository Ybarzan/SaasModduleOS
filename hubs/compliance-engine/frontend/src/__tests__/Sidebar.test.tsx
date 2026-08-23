import { describe, it, expect, beforeEach, afterEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import Sidebar from "../components/Sidebar";
import { useAuthStore } from "../stores/auth";

const baseUser = {
  id: "u1",
  email: "test@incokalk.com",
  firstName: "Test",
  lastName: "User",
};

function renderSidebar(initialPath: string, role: "USER" | "ADMIN") {
  useAuthStore.setState({
    token: "fake-token",
    refreshToken: "fake-refresh",
    user: { ...baseUser, role },
  });
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Sidebar />
    </MemoryRouter>
  );
}

describe("Sidebar", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  afterEach(() => {
    useAuthStore.setState({ token: null, refreshToken: null, user: null });
  });

  it("always shows the standalone Tableau de bord and Calculateur Incoterms links", () => {
    renderSidebar("/dashboard", "USER");
    expect(screen.getByText("Tableau de bord")).toBeInTheDocument();
    expect(screen.getByText("Calculateur Incoterms")).toBeInTheDocument();
  });

  it("hides admin-only items from a USER role", () => {
    renderSidebar("/dashboard", "USER");
    // "Gestion tarifs" (adminOnly) must never render for a USER, regardless of group state.
    expect(screen.queryByText("Gestion tarifs")).not.toBeInTheDocument();
  });

  it("auto-expands the group containing the active route and shows admin items for ADMIN", () => {
    renderSidebar("/shipping-rates", "ADMIN");
    expect(screen.getByText("Gestion tarifs")).toBeInTheDocument();
  });

  it("persists the active group's expanded state to localStorage", () => {
    renderSidebar("/shipping-rates", "ADMIN");
    const stored = JSON.parse(localStorage.getItem("incokalk-sidebar-groups") || "{}");
    expect(stored.transport).toBe(true);
  });
});
