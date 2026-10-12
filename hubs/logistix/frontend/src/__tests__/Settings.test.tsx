import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import toast from "react-hot-toast";
import Settings from "../pages/Settings";
import { logistixAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  logistixAPI: {
    company: {
      fleetHubKeyStatus: vi.fn(),
      setFleetHubKey: vi.fn(),
      clearFleetHubKey: vi.fn(),
    },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <Settings />
    </QueryClientProvider>
  );
}

describe("Settings page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("shows an input to save the key when none is configured", async () => {
    vi.mocked(logistixAPI.company.fleetHubKeyStatus).mockResolvedValue({
      data: { configured: false },
    } as never);
    renderPage();

    expect(
      await screen.findByPlaceholderText(/Clé d'intégration fleet-hub/)
    ).toBeInTheDocument();
  });

  it("saves the key and shows the configured state", async () => {
    vi.mocked(logistixAPI.company.fleetHubKeyStatus).mockResolvedValue({
      data: { configured: false },
    } as never);
    vi.mocked(logistixAPI.company.setFleetHubKey).mockResolvedValue({
      data: { configured: true },
    } as never);
    renderPage();

    const input = await screen.findByPlaceholderText(/Clé d'intégration fleet-hub/);
    fireEvent.change(input, { target: { value: "abc123def456" } });
    fireEvent.click(await screen.findByRole("button", { name: "Enregistrer" }));

    await waitFor(() => {
      expect(logistixAPI.company.setFleetHubKey).toHaveBeenCalledWith("abc123def456");
    });
    expect(toast.success).toHaveBeenCalledWith("Clé fleet-hub enregistrée");
  });

  it("shows the configured state and lets the admin clear the key", async () => {
    vi.mocked(logistixAPI.company.fleetHubKeyStatus).mockResolvedValue({
      data: { configured: true },
    } as never);
    vi.mocked(logistixAPI.company.clearFleetHubKey).mockResolvedValue({
      data: { configured: false },
    } as never);
    renderPage();

    expect(await screen.findByText("Clé configurée")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Effacer la clé" }));

    await waitFor(() => {
      expect(logistixAPI.company.clearFleetHubKey).toHaveBeenCalled();
    });
    expect(toast.success).toHaveBeenCalledWith("Clé fleet-hub effacée");
  });
});
