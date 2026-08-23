import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import Eur1Certificates from "../pages/Eur1Certificates";
import { api } from "../lib/api";

vi.mock("../lib/api", () => ({
  api: { get: vi.fn(), post: vi.fn() },
}));

const certificates = [
  {
    id: "c1",
    certificateNumber: "EUR1-000123",
    agreementCode: "EU-MA",
    originCountry: "FR",
    importerName: "Client Maroc SARL",
    exporterName: "IncoKalk SAS",
    hsCode: "8471.30",
    status: "ISSUED",
    issueDate: "2026-08-01T00:00:00Z",
  },
];
const agreements = [{ code: "EU-MA", name: "Accord UE-Maroc" }];

function mockDefaults() {
  vi.mocked(api.get).mockImplementation((url: string) => {
    if (url === "/v1/eur1") return Promise.resolve({ data: certificates });
    if (url === "/v1/trade-agreements") return Promise.resolve({ data: agreements });
    return Promise.reject(new Error("unexpected url " + url));
  });
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <Eur1Certificates />
    </QueryClientProvider>
  );
}

describe("Eur1Certificates page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockDefaults();
  });

  it("lists certificates with their status", async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("EUR1-000123")).toBeInTheDocument();
    });
    expect(screen.getByText("Émis")).toBeInTheDocument();
    expect(screen.getByText("IncoKalk SAS")).toBeInTheDocument();
  });

  it("shows the empty state when there are no certificates", async () => {
    vi.mocked(api.get).mockImplementation((url: string) => {
      if (url === "/v1/eur1") return Promise.resolve({ data: [] });
      if (url === "/v1/trade-agreements") return Promise.resolve({ data: agreements });
      return Promise.reject(new Error("unexpected url " + url));
    });
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Aucun certificat EUR.1")).toBeInTheDocument();
    });
  });

  it("disables creation until the required fields are filled", async () => {
    renderPage();
    await waitFor(() => screen.getByText("EUR1-000123"));
    fireEvent.click(screen.getByText("Nouveau certificat"));
    expect(screen.getByRole("button", { name: "Créer le certificat" })).toBeDisabled();
  });

  it("creates a new certificate once required fields are filled", async () => {
    vi.mocked(api.post).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("EUR1-000123"));
    fireEvent.click(screen.getByText("Nouveau certificat"));

    fireEvent.change(screen.getByDisplayValue("Sélectionner"), { target: { value: "EU-MA" } });
    fireEvent.change(screen.getByPlaceholderText("FR"), { target: { value: "FR" } });
    fireEvent.change(screen.getByPlaceholderText("8471.30.00"), { target: { value: "8471.30" } });
    const textInputs = document.querySelectorAll('input[type="text"]');
    // Exportateur then Importateur, per form order.
    fireEvent.change(textInputs[1], { target: { value: "IncoKalk SAS" } });
    fireEvent.change(textInputs[2], { target: { value: "Client Maroc SARL" } });

    fireEvent.click(screen.getByRole("button", { name: "Créer le certificat" }));

    await waitFor(() => {
      expect(api.post).toHaveBeenCalledWith(
        "/v1/eur1",
        expect.objectContaining({ agreementCode: "EU-MA", originCountry: "FR", hsCode: "8471.30" })
      );
    });
  });
});
