import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import toast from "react-hot-toast";
import Settings from "../pages/Settings";
import { financeAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  financeAPI: {
    internalIntegration: {
      settings: vi.fn(),
      enable: vi.fn(),
      disable: vi.fn(),
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

  it("shows an activate button when the share is not enabled", async () => {
    vi.mocked(financeAPI.internalIntegration.settings).mockResolvedValue({
      data: { internalIntegrationEnabled: false, internalIntegrationApiKey: null },
    } as never);
    renderPage();

    expect(await screen.findByRole("button", { name: "Activer le partage" })).toBeInTheDocument();
  });

  it("enabling the share shows the generated key once", async () => {
    vi.mocked(financeAPI.internalIntegration.settings).mockResolvedValue({
      data: { internalIntegrationEnabled: false, internalIntegrationApiKey: null },
    } as never);
    vi.mocked(financeAPI.internalIntegration.enable).mockResolvedValue({
      data: { internalIntegrationEnabled: true, internalIntegrationApiKey: "deadbeef00112233" },
    } as never);
    renderPage();

    fireEvent.click(await screen.findByRole("button", { name: "Activer le partage" }));

    expect(await screen.findByText("deadbeef00112233")).toBeInTheDocument();
  });

  it("shows the enabled state and lets the admin disable the share", async () => {
    vi.mocked(financeAPI.internalIntegration.settings).mockResolvedValue({
      data: { internalIntegrationEnabled: true, internalIntegrationApiKey: null },
    } as never);
    vi.mocked(financeAPI.internalIntegration.disable).mockResolvedValue({
      data: { internalIntegrationEnabled: false, internalIntegrationApiKey: null },
    } as never);
    renderPage();

    expect(await screen.findByText("Partage activé")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Désactiver le partage" }));

    await waitFor(() => {
      expect(financeAPI.internalIntegration.disable).toHaveBeenCalled();
    });
    expect(toast.success).toHaveBeenCalledWith("Partage désactivé");
  });
});
