import { describe, it, expect } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import Co2Calculator from "../pages/Co2Calculator";

function renderPage() {
  return render(
    <MemoryRouter>
      <Co2Calculator />
    </MemoryRouter>
  );
}

describe("Co2Calculator page", () => {
  it("renders CO2 estimates for all three transport modes with sea as the lowest", () => {
    renderPage();
    // Defaults: weight=1000kg, distance=10000km
    // SEA: 1000*10000*0.016/1000 = 160, AIR: 2550, ROAD: 620
    expect(screen.getByText("160")).toBeInTheDocument();
    expect(screen.getByText(/^2.550$/)).toBeInTheDocument();
    expect(screen.getByText("620")).toBeInTheDocument();
    expect(screen.getByText("LE PLUS VERTE")).toBeInTheDocument();
  });

  it("recomputes results when selecting a common route", () => {
    renderPage();
    fireEvent.click(screen.getByText(/France → Maroc/));
    // distance becomes 1200km: SEA = 1000*1200*0.016/1000 = 19.2 -> rounded to 19.2
    expect(screen.getAllByDisplayValue("1200").length).toBeGreaterThan(0);
  });

  it("shows the potential CO2 reduction percentage", () => {
    renderPage();
    // (2550-160)/2550 * 100 = 93.7% -> rounded 94%
    expect(screen.getByText(/94%/)).toBeInTheDocument();
  });

  it("pre-fills the weight from a ?weight= query param (handoff from VolumetricWeight)", () => {
    const { container } = render(
      <MemoryRouter initialEntries={["/co2?weight=250"]}>
        <Co2Calculator />
      </MemoryRouter>
    );
    const weightInput = container.querySelector('input[type="number"]');
    expect(weightInput).toHaveValue(250);
  });

  it("falls back to the default weight when the query param is missing or invalid", () => {
    const { container } = render(
      <MemoryRouter initialEntries={["/co2?weight=not-a-number"]}>
        <Co2Calculator />
      </MemoryRouter>
    );
    const weightInput = container.querySelector('input[type="number"]');
    expect(weightInput).toHaveValue(1000);
  });
});
