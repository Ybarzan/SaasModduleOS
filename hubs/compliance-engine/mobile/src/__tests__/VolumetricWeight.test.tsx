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

describe("VolumetricWeight (mobile)", () => {
  it("computes volumetric and chargeable weight from the default inputs (air, /6000)", () => {
    renderPage();
    // 50 x 40 x 30 = 60000 cm3 / 6000 = 10 kg, actual weight also 10 -> both read 10.00 kg
    expect(screen.getAllByText("10.00 kg")).toHaveLength(2);
  });

  it("switches divisor when transport mode changes to maritime", () => {
    renderPage();
    fireEvent.change(screen.getByRole("combobox"), { target: { value: "sea" } });
    // 60000 / 1000 = 60 kg volumetric, actual 10 -> chargeable = max(10, 60) = 60, both read 60.00 kg
    expect(screen.getAllByText("60.00 kg")).toHaveLength(2);
  });
});
