import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import Logistics from "../pages/Logistics";
import { incokalkAPI } from "../lib/api";
import toast from "react-hot-toast";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    logistics: { calculatePackaging: vi.fn(), calculateTrucking: vi.fn() },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const packagingResult = {
  totalBoxes: 2,
  utilizationPercent: 75,
  boxes: [{ boxRef: "BOX-1", lengthCm: 30, widthCm: 20, heightCm: 15, totalWeightKg: 5, utilizationPercent: 75 }],
  unpackedItems: [],
  totalWeightKg: 5,
  totalVolumeM3: 0.5,
};

const truckingResult = {
  estimatedPallets: 1,
  totalWeightKg: 5,
  totalVolumeM3: 0.5,
  options: [
    { mode: "SEA", label: "Maritime", costEur: 500, transitDays: 20, co2Kg: 50, description: "Transport maritime", costPerPallet: 500, recommended: true },
  ],
};

function renderPage() {
  return render(
    <MemoryRouter>
      <Logistics />
    </MemoryRouter>
  );
}

function addItem(sku: string, container: HTMLElement) {
  fireEvent.change(screen.getByPlaceholderText("SKU"), { target: { value: sku } });
  fireEvent.click(container.querySelector("button.bg-accent.text-white.px-3.py-2.rounded-none.hover\\:bg-accent-strong.flex-shrink-0")!);
}

describe("Logistics page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("adds an item to the packaging list", () => {
    const { container } = renderPage();
    addItem("BOX-1", container);
    expect(screen.getByText("BOX-1")).toBeInTheDocument();
  });

  it("calculates packaging and trucking after adding an item", async () => {
    vi.mocked(incokalkAPI.logistics.calculatePackaging).mockResolvedValue({ data: packagingResult } as never);
    vi.mocked(incokalkAPI.logistics.calculateTrucking).mockResolvedValue({ data: truckingResult } as never);
    const { container } = renderPage();
    addItem("BOX-1", container);

    fireEvent.click(screen.getByRole("button", { name: /Calculer le packaging/ }));

    await waitFor(() => {
      expect(screen.getByText("Maritime")).toBeInTheDocument();
    });
    expect(screen.getByText("Recommandé")).toBeInTheDocument();
  });

  it("shows an error toast when packaging calculation fails", async () => {
    vi.mocked(incokalkAPI.logistics.calculatePackaging).mockRejectedValue(new Error("fail"));
    const { container } = renderPage();
    addItem("BOX-1", container);

    fireEvent.click(screen.getByRole("button", { name: /Calculer le packaging/ }));

    await waitFor(() => {
      expect(toast.error).toHaveBeenCalledWith("Erreur calcul packaging");
    });
  });
});
