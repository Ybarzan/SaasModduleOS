import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import Developers from "../pages/Developers";

function renderPage() {
  return render(
    <MemoryRouter>
      <Developers />
    </MemoryRouter>
  );
}

describe("Developers page", () => {
  it("renders the page title", () => {
    renderPage();
    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("IncoKalk");
  });

  it("shows both authentication methods", () => {
    renderPage();
    expect(screen.getByText(/Authorization: Bearer/)).toBeInTheDocument();
    expect(screen.getByText(/X-API-Key:/)).toBeInTheDocument();
  });

  it("lists all 6 endpoint categories", () => {
    renderPage();
    ["Expéditions", "Entrepôt & Inventaire", "Douane & Conformité", "Finance", "Intégrations", "Analytics"].forEach(
      (title) => {
        expect(screen.getByText(title)).toBeInTheDocument();
      }
    );
  });

  it("links to the API keys page and to registration", () => {
    renderPage();
    expect(screen.getByRole("link", { name: /Accéder aux clés API/ })).toHaveAttribute("href", "/api-keys");
    // "Créer un compte gratuit" also appears in StickyMobileCta's default label — scope to the in-page CTA.
    const registerLinks = screen.getAllByRole("link", { name: /Créer un compte gratuit/ });
    expect(registerLinks.some((link) => link.getAttribute("href") === "/register")).toBe(true);
  });
});
