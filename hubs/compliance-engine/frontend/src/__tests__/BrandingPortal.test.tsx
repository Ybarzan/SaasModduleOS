import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import toast from "react-hot-toast";
import BrandingPortal from "../pages/BrandingPortal";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    branding: { get: vi.fn(), update: vi.fn(), uploadLogo: vi.fn() },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const branding = {
  primaryColor: "#7c3aed",
  secondaryColor: "#f59e0b",
  portalTitle: "Suivi Atlas",
  welcomeMessage: "Bienvenue chez Atlas",
  footerText: "© Atlas",
  customDomain: "",
  customCssEnabled: false,
  customCss: "",
};

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <BrandingPortal />
    </QueryClientProvider>
  );
}

describe("BrandingPortal page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(incokalkAPI.branding.get).mockResolvedValue({ data: branding } as never);
  });

  it("loads and displays existing branding settings", async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByDisplayValue("Suivi Atlas")).toBeInTheDocument();
    });
    expect(screen.getByDisplayValue("Bienvenue chez Atlas")).toBeInTheDocument();
  });

  it("shows the live preview reflecting the portal title", async () => {
    renderPage();
    await waitFor(() => screen.getByDisplayValue("Suivi Atlas"));
    expect(screen.getAllByText("Suivi Atlas").length).toBeGreaterThan(0);
  });

  it("saves updated branding settings", async () => {
    vi.mocked(incokalkAPI.branding.update).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByDisplayValue("Suivi Atlas"));

    fireEvent.change(screen.getByDisplayValue("Suivi Atlas"), { target: { value: "Suivi International" } });
    fireEvent.click(screen.getByRole("button", { name: "Sauvegarder" }));

    await waitFor(() => {
      expect(incokalkAPI.branding.update).toHaveBeenCalledWith(
        expect.objectContaining({ portalTitle: "Suivi International" })
      );
    });
    expect(toast.success).toHaveBeenCalledWith("Configuration du portail sauvegardée");
  });

  it("resets unsaved changes", async () => {
    renderPage();
    await waitFor(() => screen.getByDisplayValue("Suivi Atlas"));

    fireEvent.change(screen.getByDisplayValue("Suivi Atlas"), { target: { value: "Draft title" } });
    fireEvent.click(screen.getByText("Annuler"));

    await waitFor(() => {
      expect(screen.getByDisplayValue("Suivi Atlas")).toBeInTheDocument();
    });
    expect(toast.success).toHaveBeenCalledWith("Modifications annulées");
  });
});
