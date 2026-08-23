import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import ScanReceiving from "../pages/ScanReceiving";
import { incokalkAPI } from "../lib/api";
import { offlineQueue } from "../lib/offlineQueue";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    receivings: {
      list: vi.fn(),
      scan: vi.fn(),
    },
  },
}));

vi.mock("../lib/offlineQueue", () => ({
  offlineQueue: {
    enqueue: vi.fn(),
    list: vi.fn(),
    count: vi.fn(),
    remove: vi.fn(),
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn(), __call: vi.fn() },
}));

// Le hook réel pilote une vraie caméra (zxing) -- on mocke pour capturer le
// callback onDetected et le déclencher manuellement depuis les tests, comme si
// un code-barres venait d'être lu.
let capturedOnDetected: ((result: { text: string }) => void) | null = null;
let mockScannerState: { isScanning: boolean; error: string | null } = { isScanning: true, error: null };
vi.mock("../hooks/useBarcodeScanner", () => ({
  useBarcodeScanner: vi.fn((opts: { onDetected: (r: { text: string }) => void }) => {
    capturedOnDetected = opts.onDetected;
    return mockScannerState;
  }),
}));

const orders = [
  { id: "o1", orderNumber: "RCT-0001", status: "DRAFT", warehouseId: "w1" },
  { id: "o2", orderNumber: "RCT-0002", status: "RECEIVING", warehouseId: "w1" },
  { id: "o3", orderNumber: "RCT-0003", status: "COMPLETED", warehouseId: "w1" },
];

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <ScanReceiving />
    </QueryClientProvider>
  );
}

async function selectOrder(orderId: string) {
  const select = screen.getByRole("combobox") as HTMLSelectElement;
  fireEvent.change(select, { target: { value: orderId } });
}

describe("ScanReceiving page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    capturedOnDetected = null;
    mockScannerState = { isScanning: true, error: null };
    Object.defineProperty(navigator, "onLine", { value: true, configurable: true });
    vi.mocked(incokalkAPI.receivings.list).mockResolvedValue({ data: orders } as never);
    vi.mocked(offlineQueue.count).mockResolvedValue(0);
    vi.mocked(offlineQueue.list).mockResolvedValue([]);
  });

  it("lists only DRAFT/RECEIVING orders, excluding COMPLETED", async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("RCT-0001 (DRAFT)")).toBeInTheDocument();
    });
    expect(screen.getByText("RCT-0002 (RECEIVING)")).toBeInTheDocument();
    expect(screen.queryByText(/RCT-0003/)).not.toBeInTheDocument();
  });

  it("switches between camera and manual entry modes", async () => {
    renderPage();
    await waitFor(() => screen.getByText("RCT-0001 (DRAFT)"));

    expect(screen.getByText("Sélectionnez un bon de réception pour activer le scanner")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /Manuel/ }));
    expect(screen.getByPlaceholderText("Scanner ou saisir puis Entrée")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: /Caméra/ }));
    expect(screen.queryByPlaceholderText("Scanner ou saisir puis Entrée")).not.toBeInTheDocument();
  });

  it("blocks a scan when no receiving order is selected", async () => {
    const toast = (await import("react-hot-toast")).default;
    renderPage();
    await waitFor(() => screen.getByText("RCT-0001 (DRAFT)"));
    fireEvent.click(screen.getByRole("button", { name: /Manuel/ }));

    fireEvent.click(screen.getByRole("button", { name: /Scanner/ }));

    expect(toast.error).toHaveBeenCalledWith("Sélectionnez d’abord un bon de réception");
    expect(incokalkAPI.receivings.scan).not.toHaveBeenCalled();
  });

  it("submits a manual scan with the entered detail fields", async () => {
    vi.mocked(incokalkAPI.receivings.scan).mockResolvedValue({ data: {} } as never);
    renderPage();
    await waitFor(() => screen.getByText("RCT-0001 (DRAFT)"));
    await selectOrder("o1");
    fireEvent.click(screen.getByRole("button", { name: /Manuel/ }));

    fireEvent.change(screen.getByPlaceholderText("Scanner ou saisir puis Entrée"), {
      target: { value: "EAN123456" },
    });
    fireEvent.click(screen.getByRole("button", { name: /Scanner/ }));

    await waitFor(() => {
      expect(incokalkAPI.receivings.scan).toHaveBeenCalledWith("o1", {
        barcode: "EAN123456",
        quantity: 1,
        lotNumber: undefined,
        expiryDate: undefined,
        serialNumber: undefined,
      });
    });
    expect(await screen.findByText("EAN123456")).toBeInTheDocument();
  });

  it("submits a manual scan on pressing Enter in the barcode field", async () => {
    vi.mocked(incokalkAPI.receivings.scan).mockResolvedValue({ data: {} } as never);
    renderPage();
    await waitFor(() => screen.getByText("RCT-0001 (DRAFT)"));
    await selectOrder("o1");
    fireEvent.click(screen.getByRole("button", { name: /Manuel/ }));

    const input = screen.getByPlaceholderText("Scanner ou saisir puis Entrée");
    fireEvent.change(input, { target: { value: "EAN999" } });
    fireEvent.keyDown(input, { key: "Enter" });

    await waitFor(() => {
      expect(incokalkAPI.receivings.scan).toHaveBeenCalledWith("o1", expect.objectContaining({ barcode: "EAN999" }));
    });
  });

  it("triggers a scan when the camera hook detects a barcode", async () => {
    vi.mocked(incokalkAPI.receivings.scan).mockResolvedValue({ data: {} } as never);
    renderPage();
    await waitFor(() => screen.getByText("RCT-0001 (DRAFT)"));
    await selectOrder("o1");

    expect(capturedOnDetected).not.toBeNull();
    capturedOnDetected!({ text: "CAM-SCANNED-CODE" });

    await waitFor(() => {
      expect(incokalkAPI.receivings.scan).toHaveBeenCalledWith("o1", expect.objectContaining({ barcode: "CAM-SCANNED-CODE" }));
    });
  });

  it("shows the camera error with a fallback to manual entry", async () => {
    mockScannerState = { isScanning: false, error: "Accès caméra refusé. Autorisez la caméra dans votre navigateur." };
    renderPage();
    await waitFor(() => screen.getByText("RCT-0001 (DRAFT)"));

    expect(screen.getByText(/Accès caméra refusé/)).toBeInTheDocument();
    fireEvent.click(screen.getByText("Passer en saisie manuelle"));
    expect(screen.getByPlaceholderText("Scanner ou saisir puis Entrée")).toBeInTheDocument();
  });

  it("queues the scan offline instead of calling the API when the browser is offline", async () => {
    Object.defineProperty(navigator, "onLine", { value: false, configurable: true });
    vi.mocked(offlineQueue.enqueue).mockResolvedValue(1);
    vi.mocked(offlineQueue.count).mockResolvedValue(1);
    const toast = (await import("react-hot-toast")).default;

    renderPage();
    await waitFor(() => screen.getByText("RCT-0001 (DRAFT)"));
    await selectOrder("o1");
    fireEvent.click(screen.getByRole("button", { name: /Manuel/ }));

    fireEvent.change(screen.getByPlaceholderText("Scanner ou saisir puis Entrée"), {
      target: { value: "OFFLINE-CODE" },
    });
    fireEvent.click(screen.getByRole("button", { name: /Scanner/ }));

    await waitFor(() => {
      expect(offlineQueue.enqueue).toHaveBeenCalledWith("o1", expect.objectContaining({ barcode: "OFFLINE-CODE" }));
    });
    expect(incokalkAPI.receivings.scan).not.toHaveBeenCalled();
    expect(toast.success).toHaveBeenCalledWith("Hors ligne — scan mis en file d’attente");
    expect(await screen.findByText(/1 scan\(s\) en attente/)).toBeInTheDocument();
  });

  it("shows the pending offline count and syncs the queue when back online", async () => {
    vi.mocked(offlineQueue.count).mockResolvedValue(2);
    vi.mocked(offlineQueue.list).mockResolvedValue([
      { id: 1, orderId: "o1", payload: { barcode: "A" }, createdAt: 1 },
      { id: 2, orderId: "o1", payload: { barcode: "B" }, createdAt: 2 },
    ]);
    vi.mocked(incokalkAPI.receivings.scan).mockResolvedValue({ data: {} } as never);
    const toast = (await import("react-hot-toast")).default;

    renderPage();
    expect(await screen.findByText(/2 scan\(s\) en attente/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: /Synchroniser/ }));

    await waitFor(() => {
      expect(incokalkAPI.receivings.scan).toHaveBeenCalledTimes(2);
    });
    expect(offlineQueue.remove).toHaveBeenCalledWith(1);
    expect(offlineQueue.remove).toHaveBeenCalledWith(2);
    expect(toast.success).toHaveBeenCalledWith("2 scan(s) synchronisé(s)");
  });

  it("disables sync when there is nothing pending", async () => {
    renderPage();
    await waitFor(() => screen.getByText("RCT-0001 (DRAFT)"));
    expect(screen.getByRole("button", { name: /Synchroniser/ })).toBeDisabled();
  });
});
