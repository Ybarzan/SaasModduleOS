import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import TaricData from "../pages/TaricData";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: { customs: { search: vi.fn() } },
}));

const results = [
  {
    id: "t1",
    hsCode: "8471.30",
    description: "Machines automatiques de traitement de l'information",
    dutyRate: "0%",
    thirdCountryDuty: "2.5%",
    origin: "Erga omnes",
    measures: "Aucune",
    chapter: "84-85",
  },
];

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <TaricData />
    </QueryClientProvider>
  );
}

describe("TaricData page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(incokalkAPI.customs.search).mockResolvedValue({ data: results } as never);
  });

  it("lists TARIC lines matching the search", async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("8471.30")).toBeInTheDocument();
    });
    expect(screen.getByText("Machines automatiques de traitement de l'information")).toBeInTheDocument();
  });

  it("shows the empty state when there are no results", async () => {
    vi.mocked(incokalkAPI.customs.search).mockResolvedValue({ data: [] } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Aucune donnée TARIC trouvée")).toBeInTheDocument();
    });
  });

  it("searches by keyword", async () => {
    renderPage();
    await waitFor(() => screen.getByText("8471.30"));

    fireEvent.change(screen.getByPlaceholderText("Code SH ou mot-clé..."), {
      target: { value: "ordinateur" },
    });

    await waitFor(() => {
      expect(incokalkAPI.customs.search).toHaveBeenCalledWith("ordinateur", "EU");
    });
  });

  it("expands a TARIC line to show full duty details", async () => {
    renderPage();
    await waitFor(() => screen.getByText("8471.30"));

    fireEvent.click(screen.getByText("Machines automatiques de traitement de l'information"));
    await waitFor(() => {
      expect(screen.getByText("Droit pays tiers")).toBeInTheDocument();
    });
    expect(screen.getByText("2.5%")).toBeInTheDocument();
  });
});
