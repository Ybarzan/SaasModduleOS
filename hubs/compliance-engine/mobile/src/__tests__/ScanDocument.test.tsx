import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import ScanDocument from "../pages/ScanDocument";
import { mobileApi } from "../lib/api";
import { Camera } from "@capacitor/camera";

vi.mock("../lib/api", () => ({
  mobileApi: {
    documentParser: { parseImage: vi.fn() },
  },
}));

vi.mock("@capacitor/camera", () => ({
  Camera: { getPhoto: vi.fn() },
  CameraResultType: { Uri: "uri" },
  CameraSource: { Prompt: "prompt" },
}));

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <ScanDocument />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe("ScanDocument (mobile)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ blob: () => Promise.resolve(new Blob(["x"])) }));
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("does nothing when the camera/gallery picker is cancelled", async () => {
    vi.mocked(Camera.getPhoto).mockResolvedValue({ webPath: undefined } as never);
    renderPage();

    fireEvent.click(screen.getByRole("button", { name: /Prendre une photo/ }));
    await waitFor(() => expect(Camera.getPhoto).toHaveBeenCalled());
    expect(mobileApi.documentParser.parseImage).not.toHaveBeenCalled();
  });

  it("captures a photo, parses it, and shows the extracted fields", async () => {
    vi.mocked(Camera.getPhoto).mockResolvedValue({ webPath: "blob:mock-photo" } as never);
    vi.mocked(mobileApi.documentParser.parseImage).mockResolvedValue({
      data: {
        id: "doc-1",
        documentType: "COMMERCIAL_INVOICE",
        confidence: 0.92,
        status: "PARSED",
        parsedData: { invoiceNumber: "INV-042", totalAmount: "1 200,00 EUR" },
      },
    } as never);

    renderPage();
    fireEvent.click(screen.getByRole("button", { name: /Prendre une photo/ }));

    await waitFor(() => {
      expect(mobileApi.documentParser.parseImage).toHaveBeenCalledWith(
        expect.any(Blob),
        "scan.jpg",
        "COMMERCIAL_INVOICE",
      );
    });

    await waitFor(() => {
      expect(screen.getByText("Document analysé")).toBeInTheDocument();
    });
    expect(screen.getByText("92% confiance")).toBeInTheDocument();
    expect(screen.getByText("INV-042")).toBeInTheDocument();
  });

  it("shows an error message when parsing fails", async () => {
    vi.mocked(Camera.getPhoto).mockResolvedValue({ webPath: "blob:mock-photo" } as never);
    vi.mocked(mobileApi.documentParser.parseImage).mockRejectedValue(new Error("parse failed"));

    renderPage();
    fireEvent.click(screen.getByRole("button", { name: /Prendre une photo/ }));

    await waitFor(() => {
      expect(screen.getByText("Impossible d'analyser ce document.")).toBeInTheDocument();
    });
  });

  it("resets the capture and lets the user rescan", async () => {
    vi.mocked(Camera.getPhoto).mockResolvedValue({ webPath: "blob:mock-photo" } as never);
    vi.mocked(mobileApi.documentParser.parseImage).mockResolvedValue({
      data: { id: "doc-1", documentType: "COMMERCIAL_INVOICE", status: "PARSED", parsedData: {} },
    } as never);

    renderPage();
    fireEvent.click(screen.getByRole("button", { name: /Prendre une photo/ }));
    await waitFor(() => screen.getByRole("button", { name: /Rescanner/ }));

    fireEvent.click(screen.getByRole("button", { name: /Rescanner/ }));
    expect(screen.getByRole("button", { name: /Prendre une photo/ })).toBeInTheDocument();
  });
});
