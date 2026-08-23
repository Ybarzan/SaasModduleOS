import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import Receivings from "../pages/Receivings";
import { mobileApi } from "../lib/api";

vi.mock("../lib/api", () => ({
  mobileApi: { receivings: { list: vi.fn() } },
}));

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <Receivings />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe("Receivings (mobile)", () => {
  beforeEach(() => vi.clearAllMocks());

  it("lists receiving orders with status", async () => {
    vi.mocked(mobileApi.receivings.list).mockResolvedValue({
      data: [{ id: "r1", orderNumber: "RCT-0001", status: "RECEIVING" }],
    } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("RCT-0001")).toBeInTheDocument();
    });
    expect(screen.getByText("En réception")).toBeInTheDocument();
  });

  it("shows the empty state when there are no receiving orders", async () => {
    vi.mocked(mobileApi.receivings.list).mockResolvedValue({ data: [] } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Aucun bon de réception.")).toBeInTheDocument();
    });
  });
});
