import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import toast from "react-hot-toast";
import DeclarationsPage from "../pages/DeclarationsPage";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    declarations: {
      dau: { getPage: vi.fn(), stats: vi.fn(), create: vi.fn(), updateStatus: vi.fn(), delete: vi.fn() },
      debp: { list: vi.fn(), create: vi.fn(), updateStatus: vi.fn(), delete: vi.fn() },
      ics2: { list: vi.fn(), stats: vi.fn(), create: vi.fn(), updateStatus: vi.fn(), delete: vi.fn() },
      exportd: { list: vi.fn(), stats: vi.fn(), create: vi.fn(), updateStatus: vi.fn(), delete: vi.fn() },
    },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const daus = [
  {
    id: "d1",
    declarationNumber: "DAU-2026-001",
    declarationType: "DAU_IMPORT",
    status: "DRAFT",
    customsOffice: "FR001000",
    customsRegime: "40",
    declaredValue: 15000,
    currency: "EUR",
    originCountry: "CN",
    destinationCountry: "FR",
    hsCode: "8471.30",
    goodsDescription: "Ordinateurs portables",
    netWeight: 500,
    grossWeight: 550,
    packages: 20,
    createdAt: "2026-08-01T00:00:00Z",
  },
];
const dauStats = { total: 1, draft: 1, submitted: 0, cleared: 0, rejected: 0 };

function mockDefaults() {
  vi.mocked(incokalkAPI.declarations.dau.getPage).mockResolvedValue({ data: daus } as never);
  vi.mocked(incokalkAPI.declarations.dau.stats).mockResolvedValue({ data: dauStats } as never);
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <DeclarationsPage />
    </QueryClientProvider>
  );
}

describe("DeclarationsPage (DAU tab)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockDefaults();
  });

  it("lists DAU declarations with stats", async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("DAU-2026-001")).toBeInTheDocument();
    });
    expect(screen.getByText("Brouillon")).toBeInTheDocument();
    expect(screen.getAllByText("1").length).toBeGreaterThan(0); // total/draft stats
  });

  it("shows the empty state when there are no DAU declarations", async () => {
    vi.mocked(incokalkAPI.declarations.dau.getPage).mockResolvedValue({ data: [] } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Aucune déclaration DAU")).toBeInTheDocument();
    });
  });

  it("opens the detail modal when clicking a row", async () => {
    renderPage();
    await waitFor(() => screen.getByText("DAU-2026-001"));

    fireEvent.click(screen.getByText("DAU-2026-001"));
    await waitFor(() => {
      expect(screen.getByText("Ordinateurs portables")).toBeInTheDocument();
    });
  });

  it("advances a draft declaration's status", async () => {
    vi.mocked(incokalkAPI.declarations.dau.updateStatus).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("DAU-2026-001"));

    fireEvent.click(screen.getByText("Soumettre"));
    await waitFor(() => {
      expect(incokalkAPI.declarations.dau.updateStatus).toHaveBeenCalledWith("d1", { status: "SUBMITTED" });
    });
  });

  it("creates a new DAU declaration", async () => {
    vi.mocked(incokalkAPI.declarations.dau.create).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("DAU-2026-001"));

    fireEvent.click(screen.getByText("Nouvelle déclaration"));
    await waitFor(() => screen.getByText("Type de déclaration"));

    fireEvent.change(screen.getByPlaceholderText("Ex: Paris-CDG"), { target: { value: "Paris-CDG" } });
    fireEvent.change(screen.getByPlaceholderText("Ex: 4000"), { target: { value: "4000" } });
    fireEvent.change(screen.getAllByPlaceholderText("0.00")[0], { target: { value: "15000" } });
    fireEvent.change(screen.getByPlaceholderText("Code pays (ex: CN)"), { target: { value: "CN" } });
    fireEvent.change(screen.getByPlaceholderText("Code pays (ex: FR)"), { target: { value: "FR" } });
    fireEvent.change(screen.getByPlaceholderText("Ex: 8471.30"), { target: { value: "8471.30" } });
    fireEvent.change(screen.getByPlaceholderText("Description détaillée..."), { target: { value: "Ordinateurs" } });

    fireEvent.click(screen.getByRole("button", { name: "Créer la déclaration" }));

    await waitFor(() => {
      expect(incokalkAPI.declarations.dau.create).toHaveBeenCalledWith(
        expect.objectContaining({ customsOffice: "Paris-CDG", originCountry: "CN", destinationCountry: "FR" })
      );
    });
    expect(toast.success).toHaveBeenCalledWith("Déclaration DAU créée");
  });

  it("switches to the ICS2 tab and shows its empty state", async () => {
    vi.mocked(incokalkAPI.declarations.ics2.list).mockResolvedValue({ data: [] } as never);
    vi.mocked(incokalkAPI.declarations.ics2.stats).mockResolvedValue({
      data: { total: 0, draft: 0, submitted: 0, cleared: 0, rejected: 0 },
    } as never);
    renderPage();
    await waitFor(() => screen.getByText("DAU-2026-001"));

    fireEvent.click(screen.getByText("ICS2"));
    await waitFor(() => {
      expect(screen.getByText("Aucune déclaration ICS2")).toBeInTheDocument();
    });
  });
});
