import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import toast from "react-hot-toast";
import CurrencyExchange from "../pages/CurrencyExchange";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    currency: { getRates: vi.fn(), convert: vi.fn() },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

describe("CurrencyExchange page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("loads reference rates on mount and displays them", async () => {
    vi.mocked(incokalkAPI.currency.getRates).mockResolvedValue({
      data: { rates: { USD: 1.08, GBP: 0.85 }, supported: ["USD", "GBP"] },
    } as never);

    render(<CurrencyExchange />);
    await waitFor(() => {
      expect(incokalkAPI.currency.getRates).toHaveBeenCalledWith("EUR");
    });
    await waitFor(() => {
      expect(screen.getAllByText("1.0800").length).toBeGreaterThan(0);
    });
  });

  it("shows an error toast when the reference rates fail to load", async () => {
    vi.mocked(incokalkAPI.currency.getRates).mockRejectedValue(new Error("boom"));
    render(<CurrencyExchange />);
    await waitFor(() => {
      expect(toast.error).toHaveBeenCalledWith("Erreur lors du chargement des taux");
    });
  });

  it("converts an amount between two different currencies", async () => {
    vi.mocked(incokalkAPI.currency.getRates).mockResolvedValue({ data: { rates: {}, supported: [] } } as never);
    vi.mocked(incokalkAPI.currency.convert).mockResolvedValue({
      data: { convertedAmount: 10800, rate: 1.08 },
    } as never);

    render(<CurrencyExchange />);
    fireEvent.click(screen.getByRole("button", { name: "Convertir" }));

    await waitFor(() => {
      expect(incokalkAPI.currency.convert).toHaveBeenCalledWith(10000, "EUR", "USD");
    });
    await waitFor(() => {
      expect(screen.getByText(/^10.800,00 USD$/)).toBeInTheDocument();
    });
  });

  it("converts locally without an API call when from and to currencies match", async () => {
    vi.mocked(incokalkAPI.currency.getRates).mockResolvedValue({ data: { rates: {}, supported: [] } } as never);
    render(<CurrencyExchange />);
    await waitFor(() => expect(incokalkAPI.currency.getRates).toHaveBeenCalled());

    const selects = document.querySelectorAll("select");
    fireEvent.change(selects[1], { target: { value: "EUR" } });
    fireEvent.click(screen.getByRole("button", { name: "Convertir" }));

    expect(incokalkAPI.currency.convert).not.toHaveBeenCalled();
    expect(screen.getByText(/^10.000,00 EUR$/)).toBeInTheDocument();
  });

  it("swaps the from/to currencies", async () => {
    vi.mocked(incokalkAPI.currency.getRates).mockResolvedValue({ data: { rates: {}, supported: [] } } as never);
    const { container } = render(<CurrencyExchange />);
    await waitFor(() => expect(incokalkAPI.currency.getRates).toHaveBeenCalled());

    const selects = container.querySelectorAll("select");
    expect((selects[0] as HTMLSelectElement).value).toBe("EUR");
    expect((selects[1] as HTMLSelectElement).value).toBe("USD");

    fireEvent.click(container.querySelector("button.rounded-full")!);
    expect((selects[0] as HTMLSelectElement).value).toBe("USD");
    expect((selects[1] as HTMLSelectElement).value).toBe("EUR");
  });
});
