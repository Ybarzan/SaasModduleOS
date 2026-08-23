import { describe, it, expect } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import Faq from "../pages/Faq";

function renderPage() {
  return render(
    <MemoryRouter>
      <Faq />
    </MemoryRouter>
  );
}

describe("Faq page", () => {
  it("renders all questions collapsed by default", () => {
    renderPage();
    expect(screen.getByText("Qu'est-ce qu'IncoKalk ?")).toBeInTheDocument();
    expect(
      screen.queryByText(/logiciel de calcul de coûts logistiques internationaux/)
    ).not.toBeInTheDocument();
  });

  it("expands an answer when its question is clicked, and collapses it again", () => {
    renderPage();
    const question = screen.getByText("Qu'est-ce qu'IncoKalk ?");
    const button = question.closest("button")!;

    fireEvent.click(button);
    expect(button).toHaveAttribute("aria-expanded", "true");
    expect(
      screen.getByText(/logiciel de calcul de coûts logistiques internationaux/)
    ).toBeInTheDocument();

    fireEvent.click(button);
    expect(button).toHaveAttribute("aria-expanded", "false");
    expect(
      screen.queryByText(/logiciel de calcul de coûts logistiques internationaux/)
    ).not.toBeInTheDocument();
  });
});
