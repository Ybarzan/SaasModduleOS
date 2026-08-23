import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import ClientDashboard from "../pages/ClientDashboard";
import { incokalkAPI } from "../lib/api";
import { useClientAuthStore } from "../stores/clientAuth";

vi.mock("../lib/api", () => ({
  incokalkAPI: { clientPortal: { shipments: vi.fn() } },
}));

const shipments = [
  {
    id: "s1",
    orderNumber: "ORD-100",
    status: "IN_TRANSIT",
    shipperCity: "Shanghai",
    shipperCountry: "CN",
    consigneeCity: "Paris",
    consigneeCountry: "FR",
    carrierName: "DHL",
    estimatedDeliveryDate: "2026-09-01T00:00:00Z",
  },
  {
    id: "s2",
    orderNumber: "ORD-101",
    status: "DELIVERED",
    shipperCity: "Rotterdam",
    consigneeCity: "Marseille",
  },
];

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <ClientDashboard />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe("ClientDashboard page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useClientAuthStore.setState({
      token: "ctok",
      client: { id: "c1", email: "client@acme.com", fullName: "Client Acme", companyId: "co1" },
    });
  });

  afterEach(() => {
    useClientAuthStore.setState({ token: null, client: null });
  });

  it("shows shipment counts and the client's name", async () => {
    vi.mocked(incokalkAPI.clientPortal.shipments).mockResolvedValue({ data: shipments } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("ORD-100")).toBeInTheDocument();
    });
    expect(screen.getByText("Client Acme")).toBeInTheDocument();
    expect(screen.getByText("2")).toBeInTheDocument(); // total
    expect(screen.getAllByText("En transit").length).toBeGreaterThan(0);
  });

  it("shows the empty state when there are no shared shipments", async () => {
    vi.mocked(incokalkAPI.clientPortal.shipments).mockResolvedValue({ data: [] } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Aucune expédition partagée pour le moment")).toBeInTheDocument();
    });
  });

  it("refetches shipments when clicking Actualiser", async () => {
    vi.mocked(incokalkAPI.clientPortal.shipments).mockResolvedValue({ data: shipments } as never);
    renderPage();
    await waitFor(() => screen.getByText("ORD-100"));

    fireEvent.click(screen.getByText("Actualiser"));
    await waitFor(() => {
      expect(incokalkAPI.clientPortal.shipments).toHaveBeenCalledTimes(2);
    });
  });

  it("logs the client out", async () => {
    vi.mocked(incokalkAPI.clientPortal.shipments).mockResolvedValue({ data: [] } as never);
    const originalLocation = window.location;
    // @ts-expect-error partial mock for navigation assertion
    delete window.location;
    // @ts-expect-error partial mock for navigation assertion
    window.location = { ...originalLocation, href: "" };

    renderPage();
    await waitFor(() => screen.getByText("Aucune expédition partagée pour le moment"));

    fireEvent.click(screen.getByText("Déconnexion"));
    expect(useClientAuthStore.getState().token).toBeNull();
    expect(window.location.href).toBe("/client/login");

    // @ts-expect-error partial mock for navigation assertion
    window.location = originalLocation;
  });
});
