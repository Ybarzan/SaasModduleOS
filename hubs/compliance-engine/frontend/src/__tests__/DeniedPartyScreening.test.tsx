import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import toast from "react-hot-toast";
import DeniedPartyScreening from "../pages/DeniedPartyScreening";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    dps: { stats: vi.fn(), history: vi.fn(), sanctionedEntities: vi.fn(), alerts: vi.fn(), screen: vi.fn() },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const stats = { total: 10, CLEAR: 8, MATCH: 1, POSSIBLE_MATCH: 1, BLOCKED: 0 };
const history = [
  {
    id: "c1",
    checkedName: "Acme Corp",
    checkType: "MANUAL",
    result: "CLEAR",
    matchedListName: null,
    matchedEntryId: null,
    matchedEntryDetails: null,
    riskLevel: "NONE",
    countryCode: "FR",
    notes: null,
    checkedByUserId: "u1",
    createdAt: "2026-08-01T00:00:00Z",
  },
];

function mockDefaults() {
  vi.mocked(incokalkAPI.dps.stats).mockResolvedValue({ data: stats } as never);
  vi.mocked(incokalkAPI.dps.history).mockResolvedValue({ data: history } as never);
  vi.mocked(incokalkAPI.dps.sanctionedEntities).mockResolvedValue({ data: [] } as never);
  vi.mocked(incokalkAPI.dps.alerts).mockResolvedValue({ data: [] } as never);
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <DeniedPartyScreening />
    </QueryClientProvider>
  );
}

describe("DeniedPartyScreening page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockDefaults();
  });

  it("renders stats and screening history", async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Acme Corp")).toBeInTheDocument();
    });
    expect(screen.getByText("10")).toBeInTheDocument();
  });

  it("shows the empty state when there is no history", async () => {
    vi.mocked(incokalkAPI.dps.history).mockResolvedValue({ data: [] } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Aucun screening effectué")).toBeInTheDocument();
    });
  });

  it("screens a name and displays the result", async () => {
    vi.mocked(incokalkAPI.dps.screen).mockResolvedValue({
      data: {
        id: "c2",
        checkedName: "Suspicious LLC",
        result: "MATCH",
        matchedListName: "OFAC SDN",
        matchedEntryDetails: null,
        riskLevel: "HIGH",
      },
    } as never);
    renderPage();
    await waitFor(() => screen.getByText("Acme Corp"));

    fireEvent.change(screen.getByPlaceholderText("Nom de l'entité ou de la personne"), {
      target: { value: "Suspicious LLC" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Vérifier" }));

    await waitFor(() => {
      expect(incokalkAPI.dps.screen).toHaveBeenCalledWith({ name: "Suspicious LLC", countryCode: undefined });
    });
    await waitFor(() => {
      expect(screen.getByText("CORRESPONDANCE TROUVÉE")).toBeInTheDocument();
    });
    expect(screen.getByText("OFAC SDN")).toBeInTheDocument();
  });

  it("shows an error toast when screening fails", async () => {
    vi.mocked(incokalkAPI.dps.screen).mockRejectedValue({
      response: { data: { message: "Service indisponible" } },
    });
    renderPage();
    await waitFor(() => screen.getByText("Acme Corp"));

    fireEvent.change(screen.getByPlaceholderText("Nom de l'entité ou de la personne"), {
      target: { value: "X" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Vérifier" }));

    await waitFor(() => {
      expect(toast.error).toHaveBeenCalledWith("Service indisponible");
    });
  });

  it("switches to the sanctioned-entities tab", async () => {
    vi.mocked(incokalkAPI.dps.sanctionedEntities).mockResolvedValue({
      data: [{ id: "s1", name: "Blocked Co", type: "COMPANY", country: "KP", reason: "Sanctions", programme: "UN", source: "OFAC" }],
    } as never);
    renderPage();
    await waitFor(() => screen.getByText("Acme Corp"));

    fireEvent.click(screen.getByText("Entités sanctionnées"));
    await waitFor(() => {
      expect(screen.getByText("Blocked Co")).toBeInTheDocument();
    });
  });
});
