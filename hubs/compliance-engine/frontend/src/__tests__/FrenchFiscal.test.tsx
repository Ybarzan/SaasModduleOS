import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import FrenchFiscal from "../pages/FrenchFiscal";
import { api } from "../lib/api";

vi.mock("../lib/api", () => ({
  api: { get: vi.fn(), post: vi.fn() },
}));

const settings = {
  vat: { tvaRate: 20, vatNumber: "FR12345678901", intraEuScheme: "normal" },
  deb: { frequency: "monthly", threshold: 460000 },
  intrastat: { dispatchThreshold: 460000, arrivalThreshold: 460000, declarationType: "simplified" },
};

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <FrenchFiscal />
    </QueryClientProvider>
  );
}

describe("FrenchFiscal page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api.get).mockResolvedValue({ data: settings } as never);
  });

  it("loads and displays existing VAT settings", async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByDisplayValue("FR12345678901")).toBeInTheDocument();
    });
    expect(screen.getByDisplayValue("20")).toBeInTheDocument();
  });

  it("saves updated VAT settings", async () => {
    vi.mocked(api.post).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByDisplayValue("FR12345678901"));

    fireEvent.change(screen.getByDisplayValue("FR12345678901"), { target: { value: "FR98765432109" } });
    fireEvent.click(screen.getByText("Enregistrer TVA"));

    await waitFor(() => {
      expect(api.post).toHaveBeenCalledWith(
        "/v1/fiscal/french-settings",
        expect.objectContaining({ section: "vat", data: expect.objectContaining({ vatNumber: "FR98765432109" }) })
      );
    });
  });

  it("saves DEB settings independently", async () => {
    vi.mocked(api.post).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByDisplayValue("FR12345678901"));

    fireEvent.click(screen.getByText("Enregistrer DEB"));
    await waitFor(() => {
      expect(api.post).toHaveBeenCalledWith(
        "/v1/fiscal/french-settings",
        expect.objectContaining({ section: "deb" })
      );
    });
  });
});
