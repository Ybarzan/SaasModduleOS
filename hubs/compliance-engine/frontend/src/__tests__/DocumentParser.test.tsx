import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import toast from "react-hot-toast";
import DocumentParser from "../pages/DocumentParser";
import { incokalkAPI } from "../lib/api";
import { useAuthStore } from "../stores/auth";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    documentParser: { stats: vi.fn(), history: vi.fn(), parseText: vi.fn(), parsePdf: vi.fn() },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const stats = { total: 5, parsed: 3, verified: 1, rejected: 1 };
const history = [
  {
    id: "d1",
    documentType: "COMMERCIAL_INVOICE",
    originalFilename: "invoice-001.pdf",
    rawText: null,
    parsedData: { seller: "ACME Corp", total: "15000" },
    confidence: 82,
    status: "PARSED",
    createdAt: "2026-08-01T00:00:00Z",
  },
];

function mockDefaults() {
  vi.mocked(incokalkAPI.documentParser.stats).mockResolvedValue({ data: stats } as never);
  vi.mocked(incokalkAPI.documentParser.history).mockResolvedValue({ data: history } as never);
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <DocumentParser />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe("DocumentParser page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockDefaults();
    useAuthStore.setState({ token: null, refreshToken: null, user: null });
  });

  it("renders stats and parsing history", async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("invoice-001.pdf")).toBeInTheDocument();
    });
    expect(screen.getByText("5")).toBeInTheDocument();
    expect(screen.getByText("82%")).toBeInTheDocument();
  });

  it("shows the empty state when there is no history", async () => {
    vi.mocked(incokalkAPI.documentParser.history).mockResolvedValue({ data: [] } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Aucun document parsé")).toBeInTheDocument();
    });
  });

  it("disables text parsing until text is entered", async () => {
    renderPage();
    await waitFor(() => screen.getByText("invoice-001.pdf"));
    expect(screen.getByRole("button", { name: /Parser le texte/ })).toBeDisabled();
  });

  it("parses raw text", async () => {
    vi.mocked(incokalkAPI.documentParser.parseText).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("invoice-001.pdf"));

    fireEvent.change(screen.getByPlaceholderText(/Collez le texte extrait/), {
      target: { value: "Seller: ACME\nTotal: 5000 EUR" },
    });
    fireEvent.click(screen.getByRole("button", { name: /Parser le texte/ }));

    await waitFor(() => {
      expect(incokalkAPI.documentParser.parseText).toHaveBeenCalledWith({
        text: "Seller: ACME\nTotal: 5000 EUR",
        documentType: "COMMERCIAL_INVOICE",
      });
    });
    expect(toast.success).toHaveBeenCalledWith("Document parsé avec succès");
  });

  it("switches the target document type", async () => {
    vi.mocked(incokalkAPI.documentParser.parseText).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("invoice-001.pdf"));

    fireEvent.click(screen.getByText("Bill of Lading"));
    fireEvent.change(screen.getByPlaceholderText(/Collez le texte extrait/), {
      target: { value: "Vessel: MSC Gaia" },
    });
    fireEvent.click(screen.getByRole("button", { name: /Parser le texte/ }));

    await waitFor(() => {
      expect(incokalkAPI.documentParser.parseText).toHaveBeenCalledWith(
        expect.objectContaining({ documentType: "BILL_OF_LADING" })
      );
    });
  });

  it("links to document generation but hides email-intake from a plain MANAGER", async () => {
    useAuthStore.setState({ user: { id: "u1", email: "a@b.com", firstName: "A", lastName: "B", role: "MANAGER" } });
    renderPage();
    await waitFor(() => screen.getByText("invoice-001.pdf"));

    expect(screen.getByText("Génération de documents")).toBeInTheDocument();
    expect(screen.queryByText("Import Email")).not.toBeInTheDocument();
  });

  it("shows the email-intake link to an ADMIN", async () => {
    useAuthStore.setState({ user: { id: "u1", email: "a@b.com", firstName: "A", lastName: "B", role: "ADMIN" } });
    renderPage();
    await waitFor(() => screen.getByText("invoice-001.pdf"));

    expect(screen.getByText("Import Email")).toBeInTheDocument();
  });
});
