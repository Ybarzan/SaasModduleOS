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

const carrier = { id: "c1", name: "DHL Express" };

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

  it("shows an automation badge on a rule with an actionType", async () => {
    vi.mocked(incokalkAPI.notificationRules.getPage).mockResolvedValue({
      data: { content: [{ ...rule, actionType: "SUGGEST_ERP_ORDER_ADJUSTMENT", maxBudgetAmount: 500 }], totalPages: 1 },
    } as never);
    renderPage();

    await waitFor(() => {
      expect(screen.getByText("Ajustement commande ERP")).toBeInTheDocument();
    });
    expect(screen.getByText(/budget max 500/)).toBeInTheDocument();
  });

  it("reveals governance fields when an action type is selected", async () => {
    vi.mocked(incokalkAPI.carriers.getAll).mockResolvedValue({ data: [carrier] } as never);
    renderPage();
    await waitFor(() => screen.getByText("Alerte transit"));

    fireEvent.click(screen.getByRole("button", { name: "Nouvelle règle" }));
    expect(screen.queryByText("Budget maximum (€, optionnel)")).not.toBeInTheDocument();

    fireEvent.change(screen.getByDisplayValue("Aucune (notification seule)"), {
      target: { value: "SUGGEST_ERP_ORDER_ADJUSTMENT" },
    });

    expect(screen.getByText("Budget maximum (€, optionnel)")).toBeInTheDocument();
    expect(screen.getByLabelText("DHL Express")).toBeInTheDocument();
  });

  it("creates a rule with an action type, budget and allowed carrier", async () => {
    vi.mocked(incokalkAPI.carriers.getAll).mockResolvedValue({ data: [carrier] } as never);
    vi.mocked(incokalkAPI.notificationRules.create).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("Alerte transit"));

    fireEvent.click(screen.getByRole("button", { name: "Nouvelle règle" }));
    fireEvent.change(screen.getByPlaceholderText("Ex: Alerte changement de statut"), {
      target: { value: "Ajustement auto" },
    });
    fireEvent.change(screen.getByDisplayValue("Aucune (notification seule)"), {
      target: { value: "SUGGEST_ERP_ORDER_ADJUSTMENT" },
    });
    fireEvent.change(screen.getByPlaceholderText("Aucune limite"), { target: { value: "1000" } });
    fireEvent.click(screen.getByLabelText("DHL Express"));
    fireEvent.click(screen.getByRole("button", { name: "Créer" }));

    await waitFor(() => {
      expect(incokalkAPI.notificationRules.create).toHaveBeenCalledWith(
        expect.objectContaining({
          name: "Ajustement auto",
          actionType: "SUGGEST_ERP_ORDER_ADJUSTMENT",
          maxBudgetAmount: 1000,
          allowedCarrierIds: "c1",
        })
      );
    });
  });

  it("blocks submission when the budget is negative", async () => {
    renderPage();
    await waitFor(() => screen.getByText("Alerte transit"));

    fireEvent.click(screen.getByRole("button", { name: "Nouvelle règle" }));
    fireEvent.change(screen.getByPlaceholderText("Ex: Alerte changement de statut"), {
      target: { value: "Règle invalide" },
    });
    fireEvent.change(screen.getByDisplayValue("Aucune (notification seule)"), {
      target: { value: "SUGGEST_ERP_ORDER_ADJUSTMENT" },
    });
    fireEvent.change(screen.getByPlaceholderText("Aucune limite"), { target: { value: "-50" } });
    fireEvent.click(screen.getByRole("button", { name: "Créer" }));

    expect(incokalkAPI.notificationRules.create).not.toHaveBeenCalled();
  });

  it("switches to advanced condition mode and hides the simple filters", async () => {
    renderPage();
    await waitFor(() => screen.getByText("Alerte transit"));

    fireEvent.click(screen.getByRole("button", { name: "Nouvelle règle" }));
    expect(screen.getByText("Filtrer par statut")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Conditions avancées (ET/OU)" }));

    expect(screen.queryByText("Filtrer par statut")).not.toBeInTheDocument();
    expect(screen.getByText("Combiner avec :")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("Valeur")).toBeInTheDocument();
  });

  it("creates a rule with a composed condition, clearing the legacy flat filters", async () => {
    vi.mocked(incokalkAPI.notificationRules.create).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("Alerte transit"));

    fireEvent.click(screen.getByRole("button", { name: "Nouvelle règle" }));
    fireEvent.change(screen.getByPlaceholderText("Ex: Alerte changement de statut"), {
      target: { value: "Règle composée" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Conditions avancées (ET/OU)" }));
    fireEvent.change(screen.getByPlaceholderText("Valeur"), { target: { value: "BOOKED" } });
    fireEvent.click(screen.getByRole("button", { name: "Créer" }));

    await waitFor(() => {
      expect(incokalkAPI.notificationRules.create).toHaveBeenCalledWith(
        expect.objectContaining({
          name: "Règle composée",
          filterStatus: undefined,
          filterCarrierId: undefined,
          filterDataSource: undefined,
        })
      );
    });
    const payload = vi.mocked(incokalkAPI.notificationRules.create).mock.calls[0][0] as { conditionJson: string };
    expect(JSON.parse(payload.conditionJson)).toEqual({
      type: "AND",
      children: [{ type: "LEAF", field: "newStatus", operator: "EQ", value: "BOOKED" }],
    });
  });

  it("blocks submission when a composed condition is left incomplete", async () => {
    renderPage();
    await waitFor(() => screen.getByText("Alerte transit"));

    fireEvent.click(screen.getByRole("button", { name: "Nouvelle règle" }));
    fireEvent.change(screen.getByPlaceholderText("Ex: Alerte changement de statut"), {
      target: { value: "Règle incomplète" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Conditions avancées (ET/OU)" }));
    // La valeur de la condition par défaut reste vide.
    fireEvent.click(screen.getByRole("button", { name: "Créer" }));

    expect(incokalkAPI.notificationRules.create).not.toHaveBeenCalled();
  });

  it("adds a nested group and toggles it to OR", async () => {
    renderPage();
    await waitFor(() => screen.getByText("Alerte transit"));

    fireEvent.click(screen.getByRole("button", { name: "Nouvelle règle" }));
    fireEvent.click(screen.getByRole("button", { name: "Conditions avancées (ET/OU)" }));
    fireEvent.click(screen.getByRole("button", { name: "Groupe imbriqué" }));

    // Deux sélecteurs ET/OU existent maintenant (racine + groupe imbriqué) : deux boutons "OU".
    const orButtons = screen.getAllByRole("button", { name: "OU" });
    expect(orButtons).toHaveLength(2);
    fireEvent.click(orButtons[1]);

    // Deux lignes de condition existent désormais (racine + groupe imbriqué).
    expect(screen.getAllByPlaceholderText("Valeur")).toHaveLength(2);
  });

  it("pre-fills the condition editor and switches to advanced mode when editing a rule with conditionJson", async () => {
    const composedRule = {
      ...rule,
      id: "r2",
      name: "Règle avec condition",
      conditionJson: JSON.stringify({
        type: "OR",
        children: [{ type: "LEAF", field: "newStatus", operator: "EQ", value: "DELIVERED" }],
      }),
    };
    vi.mocked(incokalkAPI.notificationRules.getPage).mockResolvedValue({
      data: { content: [composedRule], totalPages: 1 },
    } as never);
    renderPage();
    await waitFor(() => screen.getByText("Règle avec condition"));

    // La carte affiche la description de la condition composée plutôt que les filtres plats.
    expect(screen.getByText(/Nouveau statut est égal à "DELIVERED"/)).toBeInTheDocument();

    fireEvent.click(screen.getByTitle("Modifier"));
    expect(screen.getByRole("button", { name: "Conditions avancées (ET/OU)" })).toHaveClass("bg-surface");
    expect(screen.getByDisplayValue("DELIVERED")).toBeInTheDocument();
  });
});
