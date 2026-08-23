import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import IncotermsGuide from "../pages/IncotermsGuide";

function renderPage() {
  return render(
    <MemoryRouter>
      <IncotermsGuide />
    </MemoryRouter>
  );
}

const ALL_11_CODES = ["EXW", "FCA", "CPT", "CIP", "DAP", "DPU", "DDP", "FAS", "FOB", "CFR", "CIF"];

describe("IncotermsGuide page", () => {
  it("renders the page title", () => {
    renderPage();
    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("Incoterms 2020");
  });

  it("lists all 11 Incoterms 2020 rule codes", () => {
    renderPage();
    ALL_11_CODES.forEach((code) => {
      expect(screen.getByText(code)).toBeInTheDocument();
    });
  });

  it("groups the 4 sea-only rules under their own section", () => {
    renderPage();
    expect(
      screen.getByText(/Maritime et voies navigables intérieures uniquement/)
    ).toBeInTheDocument();
  });

  it("links to the Incoterms calculator", () => {
    renderPage();
    const link = screen.getByRole("link", { name: /Essayer le calculateur/ });
    expect(link).toHaveAttribute("href", "/simulation");
  });

  it("cross-links to the misclassification cost calculator", () => {
    renderPage();
    const link = screen.getByRole("link", { name: /coût d'une erreur de classification/ });
    expect(link).toHaveAttribute("href", "/cout-erreur-douane");
  });
});
