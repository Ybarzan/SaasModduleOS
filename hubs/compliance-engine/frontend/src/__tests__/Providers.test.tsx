import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import toast from "react-hot-toast";
import Providers from "../pages/Providers";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    providers: { getAll: vi.fn(), health: vi.fn(), create: vi.fn(), delete: vi.fn(), test: vi.fn() },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

function mockDefaults() {
  vi.mocked(incokalkAPI.providers.getAll).mockResolvedValue({ data: [] } as never);
  vi.mocked(incokalkAPI.providers.health).mockResolvedValue({ data: [] } as never);
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <Providers />
    </QueryClientProvider>
  );
}

describe("Providers page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockDefaults();
  });

  it("shows all three provider cards", async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Shippo")).toBeInTheDocument();
    });
    expect(screen.getByText("DHL Express")).toBeInTheDocument();
    expect(screen.getByText("Rates Internes")).toBeInTheDocument();
    expect(screen.getByText("Toujours actif")).toBeInTheDocument();
  });

  it("requires an API key before connecting a provider", async () => {
    renderPage();
    await waitFor(() => screen.getByText("Shippo"));

    const connectButtons = screen.getAllByText("Connecter");
    fireEvent.click(connectButtons[0]);
    await waitFor(() => screen.getByText("Connecter Shippo"));

    fireEvent.click(screen.getByText("Sauvegarder"));
    expect(toast.error).toHaveBeenCalledWith("La clé API est requise");
    expect(incokalkAPI.providers.create).not.toHaveBeenCalled();
  });

  it("connects a provider with an API key", async () => {
    vi.mocked(incokalkAPI.providers.create).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("Shippo"));

    const connectButtons = screen.getAllByText("Connecter");
    fireEvent.click(connectButtons[0]);
    await waitFor(() => screen.getByText("Connecter Shippo"));

    fireEvent.change(screen.getByPlaceholderText("Entrez votre clé API"), {
      target: { value: "sk_test_12345" },
    });
    fireEvent.click(screen.getByText("Sauvegarder"));

    await waitFor(() => {
      expect(incokalkAPI.providers.create).toHaveBeenCalledWith(
        expect.objectContaining({ providerType: "SHIPPO", apiKey: "sk_test_12345" })
      );
    });
    expect(toast.success).toHaveBeenCalledWith("Fournisseur connecté avec succès");
  });

  it("shows the secret key field only for providers that need it", async () => {
    renderPage();
    await waitFor(() => screen.getByText("Shippo"));

    const connectButtons = screen.getAllByText("Connecter");
    // First card is Shippo (no secret), second is DHL (needs secret)
    fireEvent.click(connectButtons[1]);
    await waitFor(() => screen.getByText("Connecter DHL Express"));
    expect(screen.getByPlaceholderText("Clé secrète DHL")).toBeInTheDocument();
  });
});
