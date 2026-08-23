import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import toast from "react-hot-toast";
import HsClassification from "../pages/HsClassification";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    hsSuggestions: { history: vi.fn(), suggest: vi.fn(), suggestFromImage: vi.fn(), confirm: vi.fn() },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const history = [
  {
    id: "h1",
    productDescription: "Chaussures de sport en cuir",
    suggestedCode1: "6403.99",
    suggestedDescription1: "Chaussures, dessus cuir",
    confidence1: 87,
    suggestedCode2: "",
    suggestedDescription2: "",
    confidence2: 0,
    suggestedCode3: "",
    suggestedDescription3: "",
    confidence3: 0,
    userSelection: "6403.99",
    createdAt: "2026-08-01T00:00:00Z",
  },
];

const suggestionResult = {
  id: "h2",
  productDescription: "Smartphone",
  suggestedCode1: "8517.13",
  suggestedDescription1: "Téléphones intelligents",
  confidence1: 92,
  suggestedCode2: "8517.62",
  suggestedDescription2: "Appareils de télécommunication",
  confidence2: 40,
  suggestedCode3: "8471.30",
  suggestedDescription3: "Machines de traitement de données",
  confidence3: 15,
  userSelection: null,
  createdAt: "2026-08-20T00:00:00Z",
};

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <HsClassification />
    </QueryClientProvider>
  );
}

describe("HsClassification page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(incokalkAPI.hsSuggestions.history).mockResolvedValue({ data: history } as never);
  });

  it("lists classification history", async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Chaussures de sport en cuir")).toBeInTheDocument();
    });
    expect(screen.getAllByText("6403.99").length).toBeGreaterThan(0);
  });

  it("shows the empty state when there is no history", async () => {
    vi.mocked(incokalkAPI.hsSuggestions.history).mockResolvedValue({ data: [] } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Aucune classification trouvée")).toBeInTheDocument();
    });
  });

  it("classifies a product description and shows ranked suggestions", async () => {
    vi.mocked(incokalkAPI.hsSuggestions.suggest).mockResolvedValue({ data: suggestionResult } as never);
    renderPage();
    await waitFor(() => screen.getByText("Chaussures de sport en cuir"));

    fireEvent.change(screen.getByPlaceholderText(/Décrivez votre produit/), {
      target: { value: "Smartphone dernier modèle" },
    });
    fireEvent.click(screen.getByRole("button", { name: /Classifier/ }));

    await waitFor(() => {
      expect(incokalkAPI.hsSuggestions.suggest).toHaveBeenCalledWith({ productDescription: "Smartphone dernier modèle" });
    });
    await waitFor(() => {
      expect(screen.getByText("Meilleure suggestion")).toBeInTheDocument();
    });
    expect(screen.getByText("8517.13")).toBeInTheDocument();
    expect(screen.getByText("92%")).toBeInTheDocument();
  });

  it("confirms a suggested code", async () => {
    vi.mocked(incokalkAPI.hsSuggestions.suggest).mockResolvedValue({ data: suggestionResult } as never);
    vi.mocked(incokalkAPI.hsSuggestions.confirm).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("Chaussures de sport en cuir"));

    fireEvent.change(screen.getByPlaceholderText(/Décrivez votre produit/), {
      target: { value: "Smartphone" },
    });
    fireEvent.click(screen.getByRole("button", { name: /Classifier/ }));
    await waitFor(() => screen.getByText("Meilleure suggestion"));

    const selectButtons = screen.getAllByRole("button", { name: "Sélectionner" });
    fireEvent.click(selectButtons[0]);

    await waitFor(() => {
      expect(incokalkAPI.hsSuggestions.confirm).toHaveBeenCalledWith("h2", { selectedCode: "8517.13" });
    });
    expect(toast.success).toHaveBeenCalledWith("Code HS confirmé");
  });

  it("switches to image mode and requires a file before submitting", async () => {
    renderPage();
    await waitFor(() => screen.getByText("Chaussures de sport en cuir"));

    fireEvent.click(screen.getByText("Image / Document"));
    expect(screen.getByText("Cliquez pour sélectionner une image ou un PDF")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Classifier/ })).toBeDisabled();
  });
});
