import { describe, it, expect } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import VolumetricWeight from "../pages/VolumetricWeight";

function renderPage() {
  return render(
    <MemoryRouter>
      <VolumetricWeight />
    </MemoryRouter>
  );
}

describe("VolumetricWeight page", () => {
  it("computes the volumetric weight and chargeable weight for the default air mode", () => {
    renderPage();
    // 60x40x40cm / 6000 (air) = 16.0 kg volumetric vs 15.0 kg actual
    expect(screen.getByText("16.0")).toBeInTheDocument();
    expect(screen.getByText("16.0 kg")).toBeInTheDocument();
    expect(screen.getByText("0.0960 m³")).toBeInTheDocument();
    expect(screen.getByText(/est 6.7% plus élevé/)).toBeInTheDocument();
  });

  it("recomputes when switching to sea mode (÷1000)", () => {
    renderPage();
    fireEvent.click(screen.getByText("Maritime"));
    // 96000 / 1000 = 96.0 kg volumetric
    expect(screen.getByText("96.0")).toBeInTheDocument();
    expect(screen.getByText("Diviseur : 1000")).toBeInTheDocument();
  });

  it("uses the actual weight as chargeable when it exceeds the volumetric weight", () => {
    const { container } = renderPage();
    // Number inputs in DOM order: length, width, height, actual weight
    const numberInputs = container.querySelectorAll('input[type="number"]');
    fireEvent.change(numberInputs[3], { target: { value: "500" } });
    expect(screen.getByText("Le poids réel est utilisé pour la facturation")).toBeInTheDocument();
  });

  it("links to the CO2 calculator pre-filled with the chargeable weight", () => {
    renderPage();
    // Default: chargeable weight is 16.0 kg (volumetric) -> rounded to 16
    const link = screen.getByRole("link", { name: /Calculer l'impact CO₂/ });
    expect(link).toHaveAttribute("href", "/co2?weight=16");
  });
});
