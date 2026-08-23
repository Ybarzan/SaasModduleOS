import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import Academy from "../pages/Academy";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    academy: {
      dashboard: vi.fn(),
      modules: vi.fn(),
      module: vi.fn(),
      enroll: vi.fn(),
      submitQuiz: vi.fn(),
      certificate: vi.fn(),
    },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const dashboardStats = { totalModules: 5, enrolled: 2, completed: 1, passRate: 80 };
const modules = [
  {
    id: "m1",
    title: "Incoterms 2020",
    description: "Comprendre les 11 Incoterms",
    difficulty: "BEGINNER",
    category: "Bases",
    durationHours: 2,
    status: "NOT_STARTED",
  },
];
const moduleDetail = {
  ...modules[0],
  status: "IN_PROGRESS",
  progress: 50,
  quiz: [{ id: "q1", question: "Quel Incoterm transfère le risque au départ ?", options: ["FOB", "CIF"] }],
};

function mockDefaults() {
  vi.mocked(incokalkAPI.academy.dashboard).mockResolvedValue({ data: dashboardStats } as never);
  vi.mocked(incokalkAPI.academy.modules).mockResolvedValue({ data: modules } as never);
  vi.mocked(incokalkAPI.academy.module).mockResolvedValue({ data: moduleDetail } as never);
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <Academy />
    </QueryClientProvider>
  );
}

describe("Academy page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockDefaults();
  });

  it("renders dashboard stats and the modules list", async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Incoterms 2020")).toBeInTheDocument();
    });
    expect(screen.getByText("80%")).toBeInTheDocument();
  });

  it("shows the empty state when there are no modules", async () => {
    vi.mocked(incokalkAPI.academy.modules).mockResolvedValue({ data: [] } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Aucun module disponible")).toBeInTheDocument();
    });
  });

  it("enrolls in a module", async () => {
    vi.mocked(incokalkAPI.academy.enroll).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("Incoterms 2020"));

    fireEvent.click(screen.getByText("S'inscrire"));
    await waitFor(() => {
      expect(incokalkAPI.academy.enroll).toHaveBeenCalledWith("m1");
    });
  });

  it("opens a module and submits the quiz", async () => {
    vi.mocked(incokalkAPI.academy.submitQuiz).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("Incoterms 2020"));

    fireEvent.click(screen.getByText("Incoterms 2020"));
    await waitFor(() => {
      expect(screen.getByText("Quiz")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText("FOB"));
    fireEvent.click(screen.getByRole("button", { name: /Soumettre le quiz/ }));

    await waitFor(() => {
      expect(incokalkAPI.academy.submitQuiz).toHaveBeenCalledWith("m1", { answers: { q1: "FOB" } });
    });
  });
});
