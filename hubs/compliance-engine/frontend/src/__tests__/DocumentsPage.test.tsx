import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import toast from "react-hot-toast";
import DocumentsPage from "../pages/DocumentsPage";
import { incokalkAPI } from "../lib/api";
import { useAuthStore } from "../stores/auth";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    shipments: { getAll: vi.fn() },
    export: {
      shippingLabelPdf: vi.fn(),
      cmrPdf: vi.fn(),
      dgdPdf: vi.fn(),
      certificateOfOriginPdf: vi.fn(),
    },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const shipments = [
  {
    id: "s1",
    orderNumber: "ORD-001",
    status: "IN_TRANSIT",
    shipperName: "IncoKalk SAS",
    shipperCountry: "FR",
    consigneeName: "Atlas Import",
    consigneeCountry: "MA",
  },
];

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  // jsdom doesn't implement blob URL APIs; the page calls these when downloading a generated PDF.
  URL.createObjectURL = vi.fn(() => "blob:mock");
  URL.revokeObjectURL = vi.fn();
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <DocumentsPage />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe("DocumentsPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(incokalkAPI.shipments.getAll).mockResolvedValue({ data: shipments } as never);
    useAuthStore.setState({ token: null, refreshToken: null, user: null });
  });

  it("lists shipments to pick from", async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("ORD-001")).toBeInTheDocument();
    });
  });

  it("disables document generation until a shipment is selected", async () => {
    renderPage();
    await waitFor(() => screen.getByText("ORD-001"));
    expect(screen.getByText("Étiquette d'expédition")).toBeInTheDocument();

    const card = screen.getByText("Étiquette d'expédition").closest("div")!;
    expect(card.querySelector("button")).toBeDisabled();
  });

  it("selects a shipment and generates a document", async () => {
    vi.mocked(incokalkAPI.export.shippingLabelPdf).mockResolvedValue({ data: new ArrayBuffer(8) } as never);
    renderPage();
    await waitFor(() => screen.getByText("ORD-001"));

    fireEvent.click(screen.getByText("ORD-001"));
    await waitFor(() => {
      expect(screen.getByText("Changer")).toBeInTheDocument();
    });

    const card = screen.getByText("Étiquette d'expédition").closest("div")!;
    fireEvent.click(card.querySelector("button")!);

    await waitFor(() => {
      expect(incokalkAPI.export.shippingLabelPdf).toHaveBeenCalledWith("s1");
    });
    expect(toast.success).toHaveBeenCalledWith("Étiquette d'expédition téléchargé");
  });

  it("searches shipments by order number", async () => {
    renderPage();
    await waitFor(() => screen.getByText("ORD-001"));

    fireEvent.change(screen.getByPlaceholderText(/Rechercher par n° de commande/), {
      target: { value: "nomatch" },
    });
    expect(screen.getByText("Aucune expédition ne correspond à cette recherche.")).toBeInTheDocument();
  });

  it("hides the linked document-parser and email-intake tools from a USER role", async () => {
    useAuthStore.setState({ user: { id: "u1", email: "a@b.com", firstName: "A", lastName: "B", role: "USER" } });
    renderPage();
    await waitFor(() => screen.getByText("ORD-001"));

    expect(screen.queryByText("Extraction de documents")).not.toBeInTheDocument();
    expect(screen.queryByText("Import Email")).not.toBeInTheDocument();
  });

  it("shows the document-parser link to a MANAGER but hides email-intake (admin-only)", async () => {
    useAuthStore.setState({ user: { id: "u1", email: "a@b.com", firstName: "A", lastName: "B", role: "MANAGER" } });
    renderPage();
    await waitFor(() => screen.getByText("ORD-001"));

    expect(screen.getByText("Extraction de documents")).toBeInTheDocument();
    expect(screen.queryByText("Import Email")).not.toBeInTheDocument();
  });

  it("shows both linked tools to an ADMIN", async () => {
    useAuthStore.setState({ user: { id: "u1", email: "a@b.com", firstName: "A", lastName: "B", role: "ADMIN" } });
    renderPage();
    await waitFor(() => screen.getByText("ORD-001"));

    expect(screen.getByText("Extraction de documents")).toBeInTheDocument();
    expect(screen.getByText("Import Email")).toBeInTheDocument();
  });
});
