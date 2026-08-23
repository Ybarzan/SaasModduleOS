import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import NotFound from "../pages/NotFound";

describe("NotFound page", () => {
  it("renders the 404 message and quick links", () => {
    render(
      <MemoryRouter>
        <NotFound />
      </MemoryRouter>
    );
    expect(screen.getByText("404")).toBeInTheDocument();
    expect(screen.getByText("Cette page a pris une mauvaise route")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Accueil/ })).toHaveAttribute("href", "/");
    expect(screen.getByRole("link", { name: /Tarifs/ })).toHaveAttribute("href", "/pricing");
    expect(screen.getByRole("link", { name: /FAQ/ })).toHaveAttribute("href", "/faq");
    expect(screen.getByRole("link", { name: "Retour à l'accueil" })).toHaveAttribute("href", "/");
  });
});
