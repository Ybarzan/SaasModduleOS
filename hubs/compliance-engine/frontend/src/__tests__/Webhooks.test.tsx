import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import toast from "react-hot-toast";
import Webhooks from "../pages/Webhooks";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    notificationRules: { getAll: vi.fn(), create: vi.fn(), delete: vi.fn(), test: vi.fn() },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const webhooks = [
  {
    id: "w1",
    url: "https://example.com/hook",
    events: ["shipment.created", "shipment.delivered"],
    status: "ACTIVE",
    secret: "shh",
    createdAt: "2026-08-01T00:00:00Z",
  },
];

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <Webhooks />
    </QueryClientProvider>
  );
}

describe("Webhooks page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(incokalkAPI.notificationRules.getAll).mockResolvedValue({ data: webhooks } as never);
  });

  it("lists configured webhooks with their events and status", async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("https://example.com/hook")).toBeInTheDocument();
    });
    expect(screen.getByText("Expédition créée")).toBeInTheDocument();
    expect(screen.getByText("Actif")).toBeInTheDocument();
  });

  it("shows the empty state when there are no webhooks", async () => {
    vi.mocked(incokalkAPI.notificationRules.getAll).mockResolvedValue({ data: [] } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Aucun webhook configuré")).toBeInTheDocument();
    });
  });

  it("requires a URL and at least one event before creating a webhook", async () => {
    renderPage();
    await waitFor(() => screen.getByText("https://example.com/hook"));
    fireEvent.click(screen.getByText("Ajouter un webhook"));
    fireEvent.click(screen.getByRole("button", { name: "Créer le webhook" }));
    expect(toast.error).toHaveBeenCalledWith("L'URL du webhook est requise");
    expect(incokalkAPI.notificationRules.create).not.toHaveBeenCalled();
  });

  it("creates a webhook with the selected events", async () => {
    vi.mocked(incokalkAPI.notificationRules.create).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("https://example.com/hook"));
    fireEvent.click(screen.getByText("Ajouter un webhook"));
    fireEvent.change(screen.getByPlaceholderText("https://votre-serveur.com/webhook"), {
      target: { value: "https://myapp.test/hook" },
    });
    fireEvent.click(screen.getByText("Devis créé"));
    fireEvent.click(screen.getByRole("button", { name: "Créer le webhook" }));

    await waitFor(() => {
      expect(incokalkAPI.notificationRules.create).toHaveBeenCalledWith(
        expect.objectContaining({ webhookUrl: "https://myapp.test/hook", eventType: "quote.created" })
      );
    });
    expect(toast.success).toHaveBeenCalledWith("Webhook créé avec succès");
  });

  it("tests an existing webhook", async () => {
    vi.mocked(incokalkAPI.notificationRules.test).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("https://example.com/hook"));

    fireEvent.click(screen.getByTitle("Tester le webhook"));
    await waitFor(() => {
      expect(incokalkAPI.notificationRules.test).toHaveBeenCalledWith({
        webhookUrl: "https://example.com/hook",
        webhookSecret: "shh",
      });
    });
    expect(toast.success).toHaveBeenCalledWith("Test webhook envoyé avec succès");
  });

  it("deletes a webhook after confirmation", async () => {
    vi.mocked(incokalkAPI.notificationRules.delete).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("https://example.com/hook"));

    fireEvent.click(screen.getByTitle("Supprimer"));
    fireEvent.click(screen.getByText("Oui"));

    await waitFor(() => {
      expect(incokalkAPI.notificationRules.delete).toHaveBeenCalledWith("w1");
    });
    expect(toast.success).toHaveBeenCalledWith("Webhook supprimé");
  });
});
