import { describe, it, expect } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import MisclassificationCostCalculator from "../pages/MisclassificationCostCalculator";

function renderPage() {
  const utils = render(
    <MemoryRouter>
      <MisclassificationCostCalculator />
    </MemoryRouter>
  );
  const inputs = utils.container.querySelectorAll("input");
  return {
    ...utils,
    shipmentsInput: inputs[0] as HTMLInputElement,
    avgValueInput: inputs[1] as HTMLInputElement,
    dutyGapInput: inputs[2] as HTMLInputElement,
  };
}

describe("MisclassificationCostCalculator page", () => {
  it("renders the page title", () => {
    renderPage();
    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("erreur de classification douanière");
  });

  it("computes the estimated monthly and annual cost from the default inputs", () => {
    // 20 shipments/month x 8000EUR x 3% = 4800 EUR/month, x12 = 57600 EUR/year
    renderPage();
    expect(screen.getByText("4 800 €")).toBeInTheDocument();
    expect(screen.getByText("57 600 €")).toBeInTheDocument();
  });

  it("recomputes when inputs change", () => {
    const { shipmentsInput, avgValueInput, dutyGapInput } = renderPage();

    fireEvent.change(shipmentsInput, { target: { value: "10" } });
    fireEvent.change(avgValueInput, { target: { value: "5000" } });
    fireEvent.change(dutyGapInput, { target: { value: "2" } });

    // 10 x 5000 x 2% = 1000 EUR/month, x12 = 12000 EUR/year
    expect(screen.getByText("1 000 €")).toBeInTheDocument();
    expect(screen.getByText("12 000 €")).toBeInTheDocument();
  });

  it("never fabricates an industry-average duty gap — the field starts editable, not locked to a claimed stat", () => {
    const { dutyGapInput } = renderPage();
    expect(dutyGapInput).not.toHaveAttribute("readonly");
    expect(dutyGapInput).not.toBeDisabled();
  });

  it("links to the simulator and to registration", () => {
    renderPage();
    expect(screen.getByRole("link", { name: /Essayer le calculateur/ })).toHaveAttribute("href", "/simulation");
    const registerLinks = screen.getAllByRole("link", { name: /Créer un compte gratuit/ });
    expect(registerLinks.some((link) => link.getAttribute("href") === "/register")).toBe(true);
  });
});
