import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import GroupagePage from "../pages/GroupagePage";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    groupages: {
      list: vi.fn(),
      stats: vi.fn(),
      detail: vi.fn(),
      create: vi.fn(),
      updateStatus: vi.fn(),
      delete: vi.fn(),
      addMember: vi.fn(),
      removeMember: vi.fn(),
    },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const groupages = [
  {
    id: "g1",
    reference: "GRP-001",
    name: "Groupage Casablanca",
    status: "PLANNED",
    transportMode: "SEA",
    carrierName: "Maersk",
    origin: "Casablanca",
    destination: "Marseille",
    capacityWeightKg: 1000,
    capacityVolumeM3: 10,
    bookedWeightKg: 200,
    bookedVolumeM3: 2,
    plannedDeparture: "2026-09-01",
    plannedArrival: null,
    createdAt: "2026-08-01T00:00:00Z",
  },
];

const stats = { total: 1, PLANNED: 1, FORMING: 0, BOOKED: 0, DEPARTED: 0, DELIVERED: 0, CANCELLED: 0 };

const detail = {
  ...groupages[0],
  memberCount: 1,
  weightUtilizationPct: 20,
  volumeUtilizationPct: 20,
  members: [
    { id: "m1", groupageId: "g1", shipmentOrderId: null, externalCompany: "ACME", reference: "REF-1", weightKg: 200, volumeM3: 2, createdAt: "2026-08-01T00:00:00Z" },
  ],
};

function mockDefaults() {
  vi.mocked(incokalkAPI.groupages.list).mockResolvedValue({ data: groupages } as never);
  vi.mocked(incokalkAPI.groupages.stats).mockResolvedValue({ data: stats } as never);
  vi.mocked(incokalkAPI.groupages.detail).mockResolvedValue({ data: detail } as never);
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <GroupagePage />
    </QueryClientProvider>
  );
}

describe("GroupagePage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockDefaults();
  });

  it("renders the groupage list with carrier and route info", async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Groupage Casablanca")).toBeInTheDocument();
    });
    expect(screen.getByText("Casablanca")).toBeInTheDocument();
    expect(screen.getByText(/Maersk/)).toBeInTheDocument();
  });

  it("shows the empty state when there are no groupages", async () => {
    vi.mocked(incokalkAPI.groupages.list).mockResolvedValue({ data: [] } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText(/Aucun groupage/)).toBeInTheDocument();
    });
  });

  it("creates a new groupage", async () => {
    vi.mocked(incokalkAPI.groupages.create).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("Groupage Casablanca"));

    fireEvent.click(screen.getByRole("button", { name: "Nouveau groupage" }));
    fireEvent.change(screen.getByPlaceholderText("Nom du groupage *"), {
      target: { value: "Groupage Test" },
    });
    fireEvent.click(screen.getByRole("button", { name: /Créer/ }));

    await waitFor(() => {
      expect(incokalkAPI.groupages.create).toHaveBeenCalledWith(
        expect.objectContaining({ name: "Groupage Test", transportMode: "ROAD" })
      );
    });
  });

  it("opens the groupage detail and lists its members", async () => {
    renderPage();
    await waitFor(() => screen.getByText("Groupage Casablanca"));

    fireEvent.click(screen.getByText("Détail & membres"));
    await waitFor(() => {
      expect(screen.getByText("REF-1")).toBeInTheDocument();
    });
  });

  it("adds a member to a groupage", async () => {
    vi.mocked(incokalkAPI.groupages.addMember).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("Groupage Casablanca"));

    fireEvent.click(screen.getByText("Détail & membres"));
    await waitFor(() => screen.getByText("REF-1"));

    fireEvent.change(screen.getByPlaceholderText("Poids (kg)"), { target: { value: "100" } });
    fireEvent.change(screen.getByPlaceholderText("Volume (m³)"), { target: { value: "1" } });
    fireEvent.click(screen.getByRole("button", { name: "Ajouter" }));

    await waitFor(() => {
      expect(incokalkAPI.groupages.addMember).toHaveBeenCalledWith(
        "g1",
        expect.objectContaining({ weightKg: 100, volumeM3: 1 })
      );
    });
  });
});
