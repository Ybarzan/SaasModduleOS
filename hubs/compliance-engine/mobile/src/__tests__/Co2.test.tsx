import { describe, it, expect } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import Co2 from "../pages/Co2";

function renderPage() {
  return render(
    <MemoryRouter>
      <Co2 />
    </MemoryRouter>
  );
}

describe("Co2 (mobile)", () => {
  it("computes emissions for all 3 transport modes from the default inputs", () => {
    renderPage();
    // 1000 kg x 10000 km x factor / 1000 -- maritime 0.016 -> 160 kg
    expect(screen.getByText("160 kg CO₂")).toBeInTheDocument();
  });

  it("recomputes when weight changes", () => {
    renderPage();
    const inputs = screen.getAllByRole("spinbutton");
    fireEvent.change(inputs[0], { target: { value: "500" } });
    // 500 x 10000 x 0.016 / 1000 = 80 kg
    expect(screen.getByText("80 kg CO₂")).toBeInTheDocument();
  });
});
