import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import toast from "react-hot-toast";
import ApprovalWorkflows from "../pages/ApprovalWorkflows";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    approvals: {
      listWorkflows: vi.fn(),
      listRequests: vi.fn(),
      pendingApprovals: vi.fn(),
      myRequests: vi.fn(),
      stats: vi.fn(),
      getHistory: vi.fn(),
      createWorkflow: vi.fn(),
      updateWorkflow: vi.fn(),
      deleteWorkflow: vi.fn(),
      approve: vi.fn(),
      reject: vi.fn(),
      cancel: vi.fn(),
    },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const workflows = [
  {
    id: "wf1",
    name: "Approbation devis > 10k",
    description: "Pour les gros devis",
    entityType: "QUOTE",
    thresholdAmount: 10000,
    steps: [{ name: "Validation manager", approverRole: "MANAGER", required: true }],
    active: true,
    createdAt: "2026-08-01T00:00:00Z",
  },
];

function mockDefaults() {
  vi.mocked(incokalkAPI.approvals.listWorkflows).mockResolvedValue({ data: workflows } as never);
  vi.mocked(incokalkAPI.approvals.stats).mockResolvedValue({
    data: { total: 1, pending: 0, approved: 0, rejected: 0 },
  } as never);
  vi.mocked(incokalkAPI.approvals.listRequests).mockResolvedValue({ data: [] } as never);
  vi.mocked(incokalkAPI.approvals.pendingApprovals).mockResolvedValue({ data: [] } as never);
  vi.mocked(incokalkAPI.approvals.myRequests).mockResolvedValue({ data: [] } as never);
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <ApprovalWorkflows />
    </QueryClientProvider>
  );
}

describe("ApprovalWorkflows page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockDefaults();
  });

  it("lists workflows with their step count and threshold", async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Approbation devis > 10k")).toBeInTheDocument();
    });
    expect(screen.getByText("1 étape")).toBeInTheDocument();
    expect(screen.getByText(/Seuil : 10.000/)).toBeInTheDocument();
  });

  it("shows the empty state when there are no workflows", async () => {
    vi.mocked(incokalkAPI.approvals.listWorkflows).mockResolvedValue({ data: [] } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Aucun workflow configuré")).toBeInTheDocument();
    });
  });

  it("requires every step to have a name before creating a workflow", async () => {
    renderPage();
    await waitFor(() => screen.getByText("Approbation devis > 10k"));
    fireEvent.click(screen.getByText("Nouveau workflow"));
    // The name field has native HTML `required`, so it's the step name (no such
    // attribute, validated in JS) that's reachable here without a real name being blocked client-side.
    fireEvent.change(screen.getByPlaceholderText("Approbation devis > 10k€"), {
      target: { value: "Workflow sans étape nommée" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Créer le workflow" }));
    expect(toast.error).toHaveBeenCalledWith("Chaque étape doit avoir un nom");
    expect(incokalkAPI.approvals.createWorkflow).not.toHaveBeenCalled();
  });

  it("creates a new workflow", async () => {
    vi.mocked(incokalkAPI.approvals.createWorkflow).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("Approbation devis > 10k"));
    fireEvent.click(screen.getByText("Nouveau workflow"));
    fireEvent.change(screen.getByPlaceholderText("Approbation devis > 10k€"), {
      target: { value: "Validation entrepôt" },
    });
    fireEvent.change(screen.getByPlaceholderText("Étape 1"), { target: { value: "Vérif stock" } });
    fireEvent.click(screen.getByRole("button", { name: "Créer le workflow" }));

    await waitFor(() => {
      expect(incokalkAPI.approvals.createWorkflow).toHaveBeenCalledWith(
        expect.objectContaining({ name: "Validation entrepôt" })
      );
    });
    expect(toast.success).toHaveBeenCalledWith("Workflow créé avec succès");
  });

  it("toggles a workflow's active status", async () => {
    vi.mocked(incokalkAPI.approvals.updateWorkflow).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("Approbation devis > 10k"));

    fireEvent.click(screen.getByText("Désactiver"));
    await waitFor(() => {
      expect(incokalkAPI.approvals.updateWorkflow).toHaveBeenCalledWith("wf1", { active: false });
    });
  });

  it("switches to the demandes tab and lists approval requests", async () => {
    vi.mocked(incokalkAPI.approvals.listRequests).mockResolvedValue({
      data: [
        {
          id: "req1",
          reference: "REQ-001",
          entityType: "QUOTE",
          amount: 15000,
          status: "PENDING",
          requestedBy: "u1",
          requestedByName: "Jean Dupont",
          createdAt: "2026-08-10T00:00:00Z",
          workflowId: "wf1",
          currentStep: 0,
        },
      ],
    } as never);

    renderPage();
    await waitFor(() => screen.getByText("Approbation devis > 10k"));
    fireEvent.click(screen.getByText("Demandes"));

    await waitFor(() => {
      expect(screen.getByText("REQ-001")).toBeInTheDocument();
    });
    expect(screen.getByText("Jean Dupont")).toBeInTheDocument();
  });

  it("approves a pending request", async () => {
    vi.mocked(incokalkAPI.approvals.listRequests).mockResolvedValue({
      data: [
        {
          id: "req1",
          reference: "REQ-001",
          entityType: "QUOTE",
          amount: 15000,
          status: "PENDING",
          requestedBy: "u1",
          createdAt: "2026-08-10T00:00:00Z",
          workflowId: "wf1",
          currentStep: 0,
        },
      ],
    } as never);
    vi.mocked(incokalkAPI.approvals.approve).mockResolvedValue({} as never);

    renderPage();
    await waitFor(() => screen.getByText("Approbation devis > 10k"));
    fireEvent.click(screen.getByText("Demandes"));
    await waitFor(() => screen.getByText("REQ-001"));

    fireEvent.click(screen.getByText("Approuver"));
    await waitFor(() => {
      expect(incokalkAPI.approvals.approve).toHaveBeenCalledWith("req1", { notes: "" });
    });
    expect(toast.success).toHaveBeenCalledWith("Demande approuvée");
  });
});
