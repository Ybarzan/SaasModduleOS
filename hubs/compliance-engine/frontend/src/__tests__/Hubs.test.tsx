import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import Hubs from "../pages/Hubs";
import { NAV_GROUPS } from "../config/navigation";

function renderPage() {
  return render(
    <MemoryRouter>
      <Hubs />
    </MemoryRouter>
  );
}

describe("Hubs page", () => {
  it("renders the page title", () => {
    renderPage();
    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("Hubs métier");
  });

  it("lists all Hub groups from the real product navigation", () => {
    renderPage();
    NAV_GROUPS.forEach((group) => {
      expect(screen.getByText(group.label)).toBeInTheDocument();
    });
  });

  it("shows a plan badge derived from the actual gating in navigation.ts", () => {
    renderPage();
    // Import-Export spans STARTER through ENTERPRISE (/change, Finance & Trésorerie's
    // sole survivor in compliance-engine, is ENTERPRISE-gated)
    expect(screen.getByText("Du plan Starter au plan Suite")).toBeInTheDocument();
    // Platform hub items carry no requiredPlan (never sold alone)
    expect(screen.getByText("Inclus dans tous les plans")).toBeInTheDocument();
    // Documents & Automatisation spans STARTER through PRO (/orchestration-suggestions is PRO-gated)
    expect(screen.getByText("Du plan Starter au plan Croissance")).toBeInTheDocument();
  });

  it("links to pricing and registration", () => {
    renderPage();
    expect(screen.getByRole("link", { name: /Voir les plans et tarifs/ })).toHaveAttribute("href", "/pricing");
    expect(screen.getByRole("link", { name: /Essayer gratuitement/ })).toHaveAttribute("href", "/register");
  });
});
