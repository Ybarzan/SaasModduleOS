import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import toast from "react-hot-toast";
import SharedLinks from "../pages/SharedLinks";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    sharedLinks: { list: vi.fn(), stats: vi.fn(), create: vi.fn(), revoke: vi.fn() },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const links = [
  {
    id: "l1",
    label: "Suivi client Atlas",
    orderNumber: "ORD-100",
    accessCount: 5,
    expiresAt: null,
    active: true,
    createdAt: "2026-08-01T00:00:00Z",
    url: "/track/abc123",
  },
];
const stats = { totalLinks: 1, activeLinks: 1, totalAccesses: 5 };

function mockDefaults() {
  vi.mocked(incokalkAPI.sharedLinks.list).mockResolvedValue({ data: links } as never);
  vi.mocked(incokalkAPI.sharedLinks.stats).mockResolvedValue({ data: stats } as never);
  Object.defineProperty(navigator, "clipboard", {
    value: { writeText: vi.fn().mockResolvedValue(undefined) },
    configurable: true,
  });
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <SharedLinks />
    </QueryClientProvider>
  );
}

describe("SharedLinks page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockDefaults();
  });

  it("lists shared links and stats", async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Suivi client Atlas")).toBeInTheDocument();
    });
    expect(screen.getByText("ORD-100")).toBeInTheDocument();
    expect(screen.getByText("Actif")).toBeInTheDocument();
  });

  it("shows the empty state when there are no links", async () => {
    vi.mocked(incokalkAPI.sharedLinks.list).mockResolvedValue({ data: [] } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Aucun lien partagé")).toBeInTheDocument();
    });
  });

  it("disables link creation until a shipment id is entered", async () => {
    renderPage();
    await waitFor(() => screen.getByText("Suivi client Atlas"));
    fireEvent.click(screen.getByText("Nouveau lien"));
    expect(screen.getByRole("button", { name: /Créer le lien/ })).toBeDisabled();
    expect(incokalkAPI.sharedLinks.create).not.toHaveBeenCalled();
  });

  it("creates a new shared link and copies the URL", async () => {
    vi.mocked(incokalkAPI.sharedLinks.create).mockResolvedValue({ data: { url: "/track/xyz789" } } as never);
    renderPage();
    await waitFor(() => screen.getByText("Suivi client Atlas"));
    fireEvent.click(screen.getByText("Nouveau lien"));

    fireEvent.change(screen.getByPlaceholderText("ID de l'expédition (UUID)"), {
      target: { value: "ship-uuid-1" },
    });
    fireEvent.click(screen.getByRole("button", { name: /Créer le lien/ }));

    await waitFor(() => {
      expect(incokalkAPI.sharedLinks.create).toHaveBeenCalledWith(
        expect.objectContaining({ shipmentId: "ship-uuid-1" })
      );
    });
    expect(toast.success).toHaveBeenCalledWith("Lien créé");
  });

  it("revokes an active link", async () => {
    vi.mocked(incokalkAPI.sharedLinks.revoke).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("Suivi client Atlas"));

    fireEvent.click(screen.getByTitle("Révoquer"));
    await waitFor(() => {
      expect(incokalkAPI.sharedLinks.revoke).toHaveBeenCalledWith("l1");
    });
    expect(toast.success).toHaveBeenCalledWith("Lien révoqué");
  });
});
