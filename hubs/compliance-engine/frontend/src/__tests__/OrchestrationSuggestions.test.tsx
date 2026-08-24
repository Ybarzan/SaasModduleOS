import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import toast from "react-hot-toast";
import OrchestrationSuggestions from "../pages/OrchestrationSuggestions";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    orchestrationSuggestions: {
      list: vi.fn(),
      get: vi.fn(),
      approve: vi.fn(),
      reject: vi.fn(),
    },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const pendingSuggestion = {
  id: "s1",
  ruleName: "ETA dégradé",
  shipmentId: "ship-1",
  actionType: "SUGGEST_ERP_ORDER_ADJUSTMENT",
  status: "PENDING_APPROVAL",
  contextJson: '{"orderNumber":"CMD-001"}',
  createdAt: "2026-08-24T10:00:00Z",
  decidedAt: null,
  decidedByUserId: null,
  decisionNote: null,
  executionResult: null,
};

const executedSuggestion = {
  ...pendingSuggestion,
  id: "s2",
  status: "EXECUTED",
  decidedAt: "2026-08-24T11:00:00Z",
  decisionNote: "Montant raisonnable",
  executionResult: "Synchronisé vers odoo (Odoo prod)",
};

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <OrchestrationSuggestions />
    </QueryClientProvider>
  );
}

describe("OrchestrationSuggestions page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("lists pending suggestions by default with their rule name and action label", async () => {
    vi.mocked(incokalkAPI.orchestrationSuggestions.list).mockResolvedValue({ data: [pendingSuggestion] } as never);
    renderPage();

    await waitFor(() => {
      expect(screen.getByText("ETA dégradé")).toBeInTheDocument();
    });
    expect(screen.getByText("Ajustement commande ERP")).toBeInTheDocument();
    expect(screen.getByText("En attente (1)")).toBeInTheDocument();
  });

  it("shows the empty state when there are no pending suggestions", async () => {
    vi.mocked(incokalkAPI.orchestrationSuggestions.list).mockResolvedValue({ data: [] } as never);
    renderPage();

    await waitFor(() => {
      expect(screen.getByText("Aucune suggestion en attente")).toBeInTheDocument();
    });
  });

  it("switches to the history tab and shows decided suggestions with their execution result", async () => {
    vi.mocked(incokalkAPI.orchestrationSuggestions.list).mockResolvedValue({
      data: [pendingSuggestion, executedSuggestion],
    } as never);
    renderPage();

    await waitFor(() => screen.getByText("ETA dégradé"));
    fireEvent.click(screen.getByText("Historique"));

    await waitFor(() => {
      expect(screen.getAllByText("ETA dégradé")).toHaveLength(1);
    });
    // Expand the row to reveal the execution result
    fireEvent.click(screen.getByText("ETA dégradé"));
    await waitFor(() => {
      expect(screen.getByText(/Synchronisé vers odoo/)).toBeInTheDocument();
    });
    expect(screen.getByText(/Montant raisonnable/)).toBeInTheDocument();
  });

  it("approves a pending suggestion with an optional note", async () => {
    vi.mocked(incokalkAPI.orchestrationSuggestions.list).mockResolvedValue({ data: [pendingSuggestion] } as never);
    vi.mocked(incokalkAPI.orchestrationSuggestions.approve).mockResolvedValue({} as never);
    renderPage();

    await waitFor(() => screen.getByText("ETA dégradé"));
    fireEvent.click(screen.getByText("Approuver"));

    await waitFor(() => screen.getByText("Approuver la suggestion"));
    fireEvent.change(screen.getByPlaceholderText("Motif de la décision..."), {
      target: { value: "Montant raisonnable" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Confirmer l'approbation" }));

    await waitFor(() => {
      expect(incokalkAPI.orchestrationSuggestions.approve).toHaveBeenCalledWith("s1", "Montant raisonnable");
    });
    expect(toast.success).toHaveBeenCalledWith("Suggestion approuvée");
  });

  it("rejects a pending suggestion", async () => {
    vi.mocked(incokalkAPI.orchestrationSuggestions.list).mockResolvedValue({ data: [pendingSuggestion] } as never);
    vi.mocked(incokalkAPI.orchestrationSuggestions.reject).mockResolvedValue({} as never);
    renderPage();

    await waitFor(() => screen.getByText("ETA dégradé"));
    fireEvent.click(screen.getByText("Rejeter"));

    await waitFor(() => screen.getByText("Rejeter la suggestion"));
    fireEvent.click(screen.getByRole("button", { name: "Confirmer le rejet" }));

    await waitFor(() => {
      expect(incokalkAPI.orchestrationSuggestions.reject).toHaveBeenCalledWith("s1", undefined);
    });
    expect(toast.success).toHaveBeenCalledWith("Suggestion rejetée");
  });

  it("shows an error toast when the decision call fails", async () => {
    vi.mocked(incokalkAPI.orchestrationSuggestions.list).mockResolvedValue({ data: [pendingSuggestion] } as never);
    vi.mocked(incokalkAPI.orchestrationSuggestions.approve).mockRejectedValue({
      response: { data: { message: "Décision impossible : la suggestion est déjà EXECUTED" } },
    } as never);
    renderPage();

    await waitFor(() => screen.getByText("ETA dégradé"));
    fireEvent.click(screen.getByText("Approuver"));
    await waitFor(() => screen.getByText("Approuver la suggestion"));
    fireEvent.click(screen.getByRole("button", { name: "Confirmer l'approbation" }));

    await waitFor(() => {
      expect(toast.error).toHaveBeenCalledWith("Décision impossible : la suggestion est déjà EXECUTED");
    });
  });
});
