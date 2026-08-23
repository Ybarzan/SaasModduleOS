import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import ScanReceiving from "../pages/ScanReceiving";
import { mobileApi } from "../lib/api";
import { offlineQueue } from "../lib/offlineQueue";
import { Camera } from "@capacitor/camera";

vi.mock("../lib/api", () => ({
  mobileApi: {
    receivings: { list: vi.fn(), scan: vi.fn() },
  },
}));

vi.mock("../lib/offlineQueue", () => ({
  offlineQueue: { enqueue: vi.fn(), list: vi.fn(), count: vi.fn(), remove: vi.fn() },
}));

vi.mock("@capacitor/camera", () => ({
  Camera: { getPhoto: vi.fn() },
  CameraResultType: { Uri: "uri" },
  CameraSource: { Camera: "camera" },
}));

const decodeFromImageUrl = vi.fn();
vi.mock("@zxing/library", () => ({
  // vi.fn() ne peut pas être invoquée avec `new` si son implémentation est une
  // arrow function (pas un constructeur JS valide) -- il faut `function () {}`.
  BrowserMultiFormatReader: vi.fn().mockImplementation(function () {
    return { decodeFromImageUrl };
  }),
}));

const orders = [
  { id: "o1", orderNumber: "RCT-0001", status: "RECEIVING" },
  { id: "o2", orderNumber: "RCT-0002", status: "COMPLETED" },
];

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <ScanReceiving />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

async function selectOrder() {
  await waitFor(() => screen.getByText("RCT-0001 (RECEIVING)"));
  fireEvent.change(screen.getByRole("combobox"), { target: { value: "o1" } });
}

describe("ScanReceiving (mobile)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    Object.defineProperty(navigator, "onLine", { value: true, configurable: true });
    vi.mocked(mobileApi.receivings.list).mockResolvedValue({ data: orders } as never);
    vi.mocked(offlineQueue.count).mockResolvedValue(0);
  });

  it("lists only DRAFT/RECEIVING orders, excluding COMPLETED", async () => {
    renderPage();
    await waitFor(() => screen.getByText("RCT-0001 (RECEIVING)"));
    expect(screen.queryByText(/RCT-0002/)).not.toBeInTheDocument();
  });

  it("disables the photo capture button until an order is selected", async () => {
    renderPage();
    await waitFor(() => screen.getByText("RCT-0001 (RECEIVING)"));
    expect(screen.getByRole("button", { name: /Photographier/ })).toBeDisabled();
  });

  it("decodes a barcode from a captured photo and submits the scan", async () => {
    vi.mocked(Camera.getPhoto).mockResolvedValue({ webPath: "blob:mock-photo" } as never);
    decodeFromImageUrl.mockResolvedValue({ getText: () => "EAN-CAPTURED" });
    vi.mocked(mobileApi.receivings.scan).mockResolvedValue({ data: {} } as never);

    renderPage();
    await selectOrder();
    fireEvent.click(screen.getByRole("button", { name: /Photographier/ }));

    await waitFor(() => {
      expect(mobileApi.receivings.scan).toHaveBeenCalledWith("o1", expect.objectContaining({ barcode: "EAN-CAPTURED" }));
    });
    expect(await screen.findByText(/EAN-CAPTURED/)).toBeInTheDocument();
  });

  it("shows a decode error and lets the user fall back to manual entry", async () => {
    vi.mocked(Camera.getPhoto).mockResolvedValue({ webPath: "blob:mock-photo" } as never);
    decodeFromImageUrl.mockRejectedValue(new Error("not found"));

    renderPage();
    await selectOrder();
    fireEvent.click(screen.getByRole("button", { name: /Photographier/ }));

    await waitFor(() => {
      expect(screen.getByText(/Aucun code-barres détecté/)).toBeInTheDocument();
    });
    expect(mobileApi.receivings.scan).not.toHaveBeenCalled();

    fireEvent.change(screen.getByPlaceholderText("Code-barres"), { target: { value: "MANUAL-CODE" } });
    fireEvent.click(screen.getByRole("button", { name: "Enregistrer le scan" }));
    await waitFor(() => {
      expect(mobileApi.receivings.scan).toHaveBeenCalledWith("o1", expect.objectContaining({ barcode: "MANUAL-CODE" }));
    });
  });

  it("queues the scan offline instead of calling the API when offline", async () => {
    Object.defineProperty(navigator, "onLine", { value: false, configurable: true });
    vi.mocked(offlineQueue.enqueue).mockResolvedValue(1);
    vi.mocked(offlineQueue.count).mockResolvedValue(1);

    renderPage();
    await selectOrder();

    fireEvent.change(screen.getByPlaceholderText("Code-barres"), { target: { value: "OFFLINE-CODE" } });
    fireEvent.click(screen.getByRole("button", { name: "Enregistrer le scan" }));

    await waitFor(() => {
      expect(offlineQueue.enqueue).toHaveBeenCalledWith("o1", expect.objectContaining({ barcode: "OFFLINE-CODE" }));
    });
    expect(mobileApi.receivings.scan).not.toHaveBeenCalled();
    expect(await screen.findByText(/1 scan\(s\) en attente/)).toBeInTheDocument();
  });

  it("syncs queued scans when tapped", async () => {
    vi.mocked(offlineQueue.count).mockResolvedValue(1);
    vi.mocked(offlineQueue.list).mockResolvedValue([{ id: 1, orderId: "o1", payload: { barcode: "Q1" }, createdAt: 1 }]);
    vi.mocked(mobileApi.receivings.scan).mockResolvedValue({ data: {} } as never);

    renderPage();
    const syncButton = await screen.findByText(/1 scan\(s\) en attente/);
    fireEvent.click(syncButton);

    await waitFor(() => {
      expect(mobileApi.receivings.scan).toHaveBeenCalledWith("o1", { barcode: "Q1" });
    });
    expect(offlineQueue.remove).toHaveBeenCalledWith(1);
  });
});
