import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import MultiBranch from "../pages/MultiBranch";
import { incokalkAPI } from "../lib/api";
import { useAuthStore } from "../stores/auth";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    branches: { list: vi.fn(), parent: vi.fn(), consolidatedReport: vi.fn(), transfers: vi.fn(), add: vi.fn(), remove: vi.fn(), createTransfer: vi.fn() },
  },
}));

const parent = { id: "p1", branchName: "IncoKalk SAS", companyCode: "HQ", status: "ACTIVE", companyId: "c1", parentCompanyId: "", createdAt: "2026-01-01T00:00:00Z" };
const branches = [
  { id: "b1", branchName: "Filiale Maroc", companyCode: "MA-01", status: "ACTIVE", companyId: "c2", parentCompanyId: "c1", createdAt: "2026-08-01T00:00:00Z" },
];

function mockDefaults() {
  vi.mocked(incokalkAPI.branches.parent).mockResolvedValue({ data: parent } as never);
  vi.mocked(incokalkAPI.branches.list).mockResolvedValue({ data: branches } as never);
  vi.mocked(incokalkAPI.branches.transfers).mockResolvedValue({ data: [] } as never);
}

function loginAsEditor() {
  useAuthStore.setState({
    token: "tok",
    refreshToken: "r",
    user: { id: "u1", email: "a@b.com", firstName: "A", lastName: "B", role: "OWNER" } as never,
  });
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MultiBranch />
    </QueryClientProvider>
  );
}

describe("MultiBranch page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockDefaults();
  });

  afterEach(() => {
    useAuthStore.setState({ token: null, refreshToken: null, user: null });
  });

  it("renders the parent company and branches list", async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("IncoKalk SAS")).toBeInTheDocument();
    });
    expect(screen.getByText("Filiale Maroc")).toBeInTheDocument();
  });

  it("shows the empty state when there are no branches", async () => {
    vi.mocked(incokalkAPI.branches.list).mockResolvedValue({ data: [] } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Aucune filiale")).toBeInTheDocument();
    });
  });

  it("hides the add-branch form for users without edit rights", async () => {
    renderPage();
    await waitFor(() => screen.getByText("Filiale Maroc"));
    expect(screen.queryByPlaceholderText("Nom de la filiale")).not.toBeInTheDocument();
  });

  it("adds a branch when the user can edit", async () => {
    loginAsEditor();
    vi.mocked(incokalkAPI.branches.add).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("Filiale Maroc"));

    fireEvent.change(screen.getByPlaceholderText("Nom de la filiale"), { target: { value: "Filiale Tunisie" } });
    fireEvent.click(screen.getByRole("button", { name: "Ajouter" }));

    await waitFor(() => {
      expect(incokalkAPI.branches.add).toHaveBeenCalledWith(
        expect.objectContaining({ branchName: "Filiale Tunisie", parentCompanyId: "c1" })
      );
    });
  });

  it("generates a consolidated report", async () => {
    vi.mocked(incokalkAPI.branches.consolidatedReport).mockResolvedValue({
      data: { totalRevenue: 100000, totalCost: 60000, totalMargin: 40000, branchCount: 2, period: "2026" },
    } as never);
    renderPage();
    await waitFor(() => screen.getByText("Filiale Maroc"));

    fireEvent.click(screen.getByRole("button", { name: "Générer" }));
    await waitFor(() => {
      expect(screen.getByText("2")).toBeInTheDocument();
    });
  });
});
