import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import Compliance from "../pages/Compliance";
import { api } from "../lib/api";

vi.mock("../lib/api", () => ({
  api: { get: vi.fn() },
}));

const stats = { declarationsPending: 3, dpsAlerts: 1, sanctionsMatches: 0, expiringEori: 2 };
const alerts = [
  { id: "a1", type: "DPS", title: "Correspondance possible", description: "Client à vérifier", severity: "medium", date: "2026-08-10T00:00:00Z" },
];

function mockDefaults() {
  vi.mocked(api.get).mockImplementation((url: string) => {
    if (url === "/v1/compliance/stats") return Promise.resolve({ data: stats });
    if (url === "/v1/compliance/alerts") return Promise.resolve({ data: alerts });
    return Promise.reject(new Error("unexpected url " + url));
  });
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <Compliance />
    </QueryClientProvider>
  );
}

describe("Compliance page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockDefaults();
  });

  it("renders the stat cards from the compliance stats endpoint", async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Déclarations en attente")).toBeInTheDocument();
    });
    expect(screen.getAllByText("3").length).toBeGreaterThan(0);
    expect(screen.getByText("Alertes DPS")).toBeInTheDocument();
  });

  it("lists recent alerts with their severity", async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Correspondance possible")).toBeInTheDocument();
    });
    expect(screen.getByText("Client à vérifier")).toBeInTheDocument();
    expect(screen.getByText("medium")).toBeInTheDocument();
  });

  it("shows a clean-state message when there are no alerts", async () => {
    vi.mocked(api.get).mockImplementation((url: string) => {
      if (url === "/v1/compliance/stats") return Promise.resolve({ data: stats });
      if (url === "/v1/compliance/alerts") return Promise.resolve({ data: [] });
      return Promise.reject(new Error("unexpected url " + url));
    });
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Aucune alerte récente")).toBeInTheDocument();
    });
  });
});
