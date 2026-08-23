import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import NotificationRules from "../pages/NotificationRules";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    notificationRules: { getPage: vi.fn(), create: vi.fn(), update: vi.fn(), delete: vi.fn(), test: vi.fn() },
    carriers: { getAll: vi.fn() },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const rule = {
  id: "r1",
  name: "Alerte transit",
  eventType: "SHIPMENT_STATUS_CHANGE",
  active: true,
  sendEmail: false,
  sendWebhook: false,
  sendInApp: true,
  emailRecipients: "",
  webhookUrl: "",
  webhookSecret: "",
  filterStatus: "",
  filterCarrierId: "",
};

function mockDefaults() {
  vi.mocked(incokalkAPI.notificationRules.getPage).mockResolvedValue({
    data: { content: [rule], totalPages: 1 },
  } as never);
  vi.mocked(incokalkAPI.carriers.getAll).mockResolvedValue({ data: [] } as never);
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <NotificationRules />
    </QueryClientProvider>
  );
}

describe("NotificationRules page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockDefaults();
  });

  it("renders the rules list", async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Alerte transit")).toBeInTheDocument();
    });
  });

  it("shows the empty state when there are no rules", async () => {
    vi.mocked(incokalkAPI.notificationRules.getPage).mockResolvedValue({
      data: { content: [], totalPages: 0 },
    } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Aucune règle de notification")).toBeInTheDocument();
    });
  });

  it("creates a new rule", async () => {
    vi.mocked(incokalkAPI.notificationRules.create).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("Alerte transit"));

    fireEvent.click(screen.getByRole("button", { name: "Nouvelle règle" }));
    fireEvent.change(screen.getByPlaceholderText("Ex: Alerte changement de statut"), {
      target: { value: "Alerte Test" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Créer" }));

    await waitFor(() => {
      expect(incokalkAPI.notificationRules.create).toHaveBeenCalledWith(
        expect.objectContaining({ name: "Alerte Test" })
      );
    });
  });

  it("sends a test notification for a rule", async () => {
    vi.mocked(incokalkAPI.notificationRules.test).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("Alerte transit"));

    fireEvent.click(screen.getByTitle("Tester"));
    await waitFor(() => {
      expect(incokalkAPI.notificationRules.test).toHaveBeenCalledWith({
        ruleId: "r1",
        eventType: "SHIPMENT_STATUS_CHANGE",
      });
    });
  });

  it("toggles a rule's active status", async () => {
    vi.mocked(incokalkAPI.notificationRules.update).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("Alerte transit"));

    fireEvent.click(screen.getByTitle("Désactiver"));
    await waitFor(() => {
      expect(incokalkAPI.notificationRules.update).toHaveBeenCalledWith(
        "r1",
        expect.objectContaining({ isActive: false })
      );
    });
  });

  it("shows quick templates when creating a new rule", async () => {
    renderPage();
    await waitFor(() => screen.getByText("Alerte transit"));

    fireEvent.click(screen.getByRole("button", { name: "Nouvelle règle" }));
    expect(screen.getByText("Modèles rapides (optionnel)")).toBeInTheDocument();
  });

  it("hides the template picker when editing an existing rule", async () => {
    renderPage();
    await waitFor(() => screen.getByText("Alerte transit"));

    fireEvent.click(screen.getByTitle("Modifier"));
    expect(screen.queryByText("Modèles rapides (optionnel)")).not.toBeInTheDocument();
  });

  it("pre-fills the form from a template and submits it", async () => {
    vi.mocked(incokalkAPI.notificationRules.create).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("Alerte transit"));

    fireEvent.click(screen.getByRole("button", { name: "Nouvelle règle" }));
    fireEvent.click(screen.getByRole("button", { name: /Livraison confirmée/ }));
    fireEvent.click(screen.getByRole("button", { name: "Créer" }));

    await waitFor(() => {
      expect(incokalkAPI.notificationRules.create).toHaveBeenCalledWith(
        expect.objectContaining({
          name: "Alerte livraison confirmée",
          eventType: "SHIPMENT_STATUS_CHANGE",
          filterStatus: "DELIVERED",
          sendInApp: true,
          sendEmail: true,
        })
      );
    });
  });
});
