import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import ECommerce from "../pages/ECommerce";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    ecommerce: {
      list: vi.fn(),
      syncLog: vi.fn(),
      create: vi.fn(),
      update: vi.fn(),
      remove: vi.fn(),
      sync: vi.fn(),
    },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const integrations = [
  {
    id: "i1",
    platform: "SHOPIFY",
    storeUrl: "https://myshop.myshopify.com",
    apiKey: "k",
    apiSecret: "s",
    webhookSecret: "w",
    syncFrequencyMin: 60,
    active: true,
    lastSyncAt: "2026-08-01T00:00:00Z",
    createdAt: "2026-01-01T00:00:00Z",
  },
];

function mockDefaults() {
  vi.mocked(incokalkAPI.ecommerce.list).mockResolvedValue({ data: integrations } as never);
  vi.mocked(incokalkAPI.ecommerce.syncLog).mockResolvedValue({ data: [] } as never);
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <ECommerce />
    </QueryClientProvider>
  );
}

describe("ECommerce page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockDefaults();
  });

  it("renders the integrations list", async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("https://myshop.myshopify.com")).toBeInTheDocument();
    });
    expect(screen.getByText("Shopify")).toBeInTheDocument();
  });

  it("shows the empty state when there are no integrations", async () => {
    vi.mocked(incokalkAPI.ecommerce.list).mockResolvedValue({ data: [] } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Aucune intégration")).toBeInTheDocument();
    });
  });

  it("creates a new integration", async () => {
    vi.mocked(incokalkAPI.ecommerce.create).mockResolvedValue({} as never);
    const { container } = renderPage();
    await waitFor(() => screen.getByText("https://myshop.myshopify.com"));

    fireEvent.click(screen.getByRole("button", { name: "Ajouter une intégration" }));
    fireEvent.change(screen.getByPlaceholderText("https://maboutique.myshopify.com"), {
      target: { value: "https://newshop.myshopify.com" },
    });
    fireEvent.change(container.querySelector('input[type="text"]')!, { target: { value: "apikey123" } });
    fireEvent.change(container.querySelector('input[type="password"]')!, { target: { value: "secret123" } });
    fireEvent.click(screen.getByRole("button", { name: "Ajouter" }));

    await waitFor(() => {
      expect(incokalkAPI.ecommerce.create).toHaveBeenCalledWith(
        expect.objectContaining({ storeUrl: "https://newshop.myshopify.com", apiKey: "apikey123", apiSecret: "secret123" })
      );
    });
  });

  it("triggers a manual sync", async () => {
    vi.mocked(incokalkAPI.ecommerce.sync).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("https://myshop.myshopify.com"));

    fireEvent.click(screen.getByRole("button", { name: /Sync/ }));
    await waitFor(() => {
      expect(incokalkAPI.ecommerce.sync).toHaveBeenCalledWith("i1");
    });
  });

  it("deletes an integration after confirmation", async () => {
    vi.mocked(incokalkAPI.ecommerce.remove).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("https://myshop.myshopify.com"));

    fireEvent.click(screen.getByTitle("Supprimer"));
    fireEvent.click(screen.getByText("Oui"));

    await waitFor(() => {
      expect(incokalkAPI.ecommerce.remove).toHaveBeenCalledWith("i1");
    });
  });
});
