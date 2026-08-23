import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import UseCases from "../pages/UseCases";

function renderPage() {
  return render(
    <MemoryRouter>
      <UseCases />
    </MemoryRouter>
  );
}

describe("UseCases page", () => {
  it("renders the page title", () => {
    renderPage();
    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("IncoKalk");
  });

  it("shows all 3 illustrative profiles", () => {
    renderPage();
    expect(screen.getByText(/Sophie — Responsable logistique/)).toBeInTheDocument();
    expect(screen.getByText(/Marc — Directeur supply chain/)).toBeInTheDocument();
    expect(screen.getByText(/Karim — Fondateur e-commerce/)).toBeInTheDocument();
  });

  it("explicitly discloses these are illustrative profiles, not real named customers", () => {
    renderPage();
    expect(screen.getByText(/pas des clients nommés/)).toBeInTheDocument();
    expect(screen.getByText(/pas un témoignage ni des métriques mesurées/)).toBeInTheDocument();
  });

  it("only references modules that actually exist in the product nav", () => {
    renderPage();
    // Spot-check a few real module labels pulled from navigation.ts / NAV_GROUPS
    expect(screen.getByText('Classification HS')).toBeInTheDocument();
    expect(screen.getByText('Multi-branche')).toBeInTheDocument();
    expect(screen.getByText('Landed Cost Calculator')).toBeInTheDocument();
  });

  it("links to the Hubs page and to registration", () => {
    renderPage();
    expect(screen.getByRole("link", { name: /Explorer les Hubs/ })).toHaveAttribute("href", "/hubs");
    const registerLinks = screen.getAllByRole("link", { name: /Essayer gratuitement/ });
    expect(registerLinks.some((link) => link.getAttribute("href") === "/register")).toBe(true);
  });
});
