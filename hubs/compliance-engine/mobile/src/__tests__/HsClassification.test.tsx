import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import HsClassification from "../pages/HsClassification";
import { mobileApi } from "../lib/api";

vi.mock("../lib/api", () => ({
  mobileApi: { hsSuggestions: { suggest: vi.fn() } },
}));

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <HsClassification />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe("HsClassification (mobile)", () => {
  beforeEach(() => vi.clearAllMocks());

  it("disables the submit button until a description is entered", () => {
    renderPage();
    expect(screen.getByRole("button", { name: "Suggérer un code SH" })).toBeDisabled();
  });

  it("suggests HS codes and shows the top match first", async () => {
    vi.mocked(mobileApi.hsSuggestions.suggest).mockResolvedValue({
      data: {
        suggestedCode1: "6403.99", suggestedDescription1: "Chaussures en cuir", confidence1: 91,
        suggestedCode2: "6404.11", suggestedDescription2: "Chaussures de sport", confidence2: 60,
        suggestedCode3: "", suggestedDescription3: "", confidence3: 0,
      },
    } as never);
    renderPage();

    fireEvent.change(screen.getByPlaceholderText(/chaussures de sport/), { target: { value: "chaussures en cuir" } });
    fireEvent.click(screen.getByRole("button", { name: "Suggérer un code SH" }));

    await waitFor(() => {
      expect(mobileApi.hsSuggestions.suggest).toHaveBeenCalledWith({ productDescription: "chaussures en cuir" });
    });
    await waitFor(() => {
      expect(screen.getByText("6403.99")).toBeInTheDocument();
    });
    expect(screen.getByText("Meilleure suggestion · 91%")).toBeInTheDocument();
    expect(screen.getByText("6404.11")).toBeInTheDocument();
    expect(screen.getByText("Alternative 2 · 60%")).toBeInTheDocument();
    // Le 3e suggéré est vide dans le mock -- filtré, pas rendu comme une 3e carte.
    expect(screen.queryByText(/Alternative 3/)).not.toBeInTheDocument();
  });
});
