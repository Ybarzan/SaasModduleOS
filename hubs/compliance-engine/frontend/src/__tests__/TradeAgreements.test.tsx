import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import TradeAgreements from "../pages/TradeAgreements";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: { tradeAgreements: { list: vi.fn() } },
}));

const agreements = [
  {
    id: "ag1",
    code: "EU-MA",
    name: "Accord UE-Maroc",
    partnerCountry: "MA",
    partnerName: "Maroc",
    description: "Accord de libre-échange UE-Maroc",
    type: "FTA",
    hsChaptersCovered: "01-05",
    originRules: "Cumul régional",
    validFrom: "2020-01-01T00:00:00Z",
    validTo: "2030-01-01T00:00:00Z",
    active: true,
  },
];

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <TradeAgreements />
    </QueryClientProvider>
  );
}

describe("TradeAgreements page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(incokalkAPI.tradeAgreements.list).mockResolvedValue({ data: agreements } as never);
  });

  it("lists agreements with type and country", async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Accord UE-Maroc")).toBeInTheDocument();
    });
    expect(screen.getByText("Maroc")).toBeInTheDocument();
    expect(screen.getAllByText("ALE").length).toBeGreaterThan(0);
  });

  it("shows the empty state when there are no agreements", async () => {
    vi.mocked(incokalkAPI.tradeAgreements.list).mockResolvedValue({ data: [] } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Aucun accord trouvé")).toBeInTheDocument();
    });
  });

  it("filters agreements by search text", async () => {
    renderPage();
    await waitFor(() => screen.getByText("Accord UE-Maroc"));

    fireEvent.change(screen.getByPlaceholderText("Rechercher par nom, code ou pays..."), {
      target: { value: "nomatch" },
    });
    expect(screen.getByText("Aucun accord trouvé")).toBeInTheDocument();
  });

  it("expands an agreement card to show its details", async () => {
    renderPage();
    await waitFor(() => screen.getByText("Accord UE-Maroc"));

    fireEvent.click(screen.getByText("Accord UE-Maroc"));
    await waitFor(() => {
      expect(screen.getByText("Cumul régional")).toBeInTheDocument();
    });
  });

  it("filters agreements by type", async () => {
    renderPage();
    await waitFor(() => screen.getByText("Accord UE-Maroc"));

    fireEvent.click(screen.getByText("APT")); // PTA filter, doesn't match FTA agreement
    expect(screen.getByText("Aucun accord trouvé")).toBeInTheDocument();

    fireEvent.click(screen.getByText("ALE")); // FTA filter matches
    expect(screen.getByText("Accord UE-Maroc")).toBeInTheDocument();
  });
});
