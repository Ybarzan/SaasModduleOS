import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import AuditLog from "../pages/AuditLog";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    audit: { getStats: vi.fn(), getAll: vi.fn(), getByEntity: vi.fn() },
  },
}));

const stats = { total: 42, byEntity: { SHIPMENT: 20, CARRIER: 10 } };
const logsPage = {
  content: [
    { id: "l1", action: "SHIPMENT_CREATED", entityType: "SHIPMENT", entityId: "s1", userName: "Jean Dupont", createdAt: "2026-08-01T00:00:00Z" },
  ],
  totalElements: 1,
  totalPages: 1,
};

function mockDefaults() {
  vi.mocked(incokalkAPI.audit.getStats).mockResolvedValue({ data: stats } as never);
  vi.mocked(incokalkAPI.audit.getAll).mockResolvedValue({ data: logsPage } as never);
  vi.mocked(incokalkAPI.audit.getByEntity).mockResolvedValue({ data: { content: [], totalElements: 0, totalPages: 0 } } as never);
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <AuditLog />
    </QueryClientProvider>
  );
}

describe("AuditLog page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockDefaults();
  });

  it("renders stats and the activity log", async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("42")).toBeInTheDocument();
    });
    expect(incokalkAPI.audit.getAll).toHaveBeenCalledWith(0, 20);
  });

  it("switches to the entity filter tabs", async () => {
    renderPage();
    await waitFor(() => screen.getByText("42"));

    fireEvent.click(screen.getByRole("button", { name: "Transporteurs" }));
    await waitFor(() => {
      expect(incokalkAPI.audit.getByEntity).toHaveBeenCalledWith("CARRIER", 0, 20);
    });
  });
});
