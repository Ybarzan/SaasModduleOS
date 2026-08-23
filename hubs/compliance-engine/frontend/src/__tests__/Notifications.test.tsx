import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import toast from "react-hot-toast";
import Notifications from "../pages/Notifications";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    notifications: { getPage: vi.fn(), markRead: vi.fn(), markAllRead: vi.fn(), archive: vi.fn(), delete: vi.fn() },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const notifications = [
  {
    id: "n1",
    title: "Expédition en transit",
    message: "Votre colis est en route",
    eventType: "SHIPMENT_STATUS_CHANGE",
    channel: "IN_APP",
    status: "UNREAD",
    sentAt: "2026-08-19T10:00:00Z",
  },
  {
    id: "n2",
    title: "Devis reçu",
    message: "Un nouveau devis est disponible",
    eventType: "QUOTE_RECEIVED",
    channel: "EMAIL",
    status: "READ",
    sentAt: "2026-08-18T10:00:00Z",
  },
];

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <Notifications />
    </QueryClientProvider>
  );
}

describe("Notifications page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(incokalkAPI.notifications.getPage).mockResolvedValue({ data: notifications } as never);
  });

  it("lists notifications and shows the unread count", async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Expédition en transit")).toBeInTheDocument();
    });
    expect(screen.getByText("1 non lu(s)")).toBeInTheDocument();
    expect(screen.getByText("Devis reçu")).toBeInTheDocument();
  });

  it("filters notifications by tab", async () => {
    renderPage();
    await waitFor(() => screen.getByText("Expédition en transit"));

    fireEvent.click(screen.getByRole("button", { name: "Non lus" }));
    expect(screen.getByText("Expédition en transit")).toBeInTheDocument();
    expect(screen.queryByText("Devis reçu")).not.toBeInTheDocument();
  });

  it("marks a single notification as read", async () => {
    vi.mocked(incokalkAPI.notifications.markRead).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("Expédition en transit"));

    fireEvent.click(screen.getByTitle("Marquer comme lu"));
    await waitFor(() => {
      expect(incokalkAPI.notifications.markRead).toHaveBeenCalledWith(["n1"]);
    });
    expect(toast.success).toHaveBeenCalledWith("Notification marquée comme lue");
  });

  it("marks all notifications as read", async () => {
    vi.mocked(incokalkAPI.notifications.markAllRead).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("Expédition en transit"));

    fireEvent.click(screen.getByText("Tout marquer lu"));
    await waitFor(() => {
      expect(incokalkAPI.notifications.markAllRead).toHaveBeenCalled();
    });
    expect(toast.success).toHaveBeenCalledWith("Toutes les notifications marquées comme lues");
  });

  it("archives a notification", async () => {
    vi.mocked(incokalkAPI.notifications.archive).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("Expédition en transit"));

    const archiveButtons = screen.getAllByTitle("Archiver");
    fireEvent.click(archiveButtons[0]);
    await waitFor(() => {
      expect(incokalkAPI.notifications.archive).toHaveBeenCalledWith("n1");
    });
    expect(toast.success).toHaveBeenCalledWith("Notification archivée");
  });

  it("deletes a notification after confirmation", async () => {
    vi.mocked(incokalkAPI.notifications.delete).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("Expédition en transit"));

    const deleteButtons = screen.getAllByTitle("Supprimer");
    fireEvent.click(deleteButtons[0]);
    await waitFor(() => screen.getByText("Confirmer la suppression"));
    const confirmButtons = screen.getAllByRole("button", { name: "Supprimer" });
    fireEvent.click(confirmButtons[confirmButtons.length - 1]);

    await waitFor(() => {
      expect(incokalkAPI.notifications.delete).toHaveBeenCalledWith("n1");
    });
    expect(toast.success).toHaveBeenCalledWith("Notification supprimée");
  });

  it("shows the empty state when there are no notifications", async () => {
    vi.mocked(incokalkAPI.notifications.getPage).mockResolvedValue({ data: [] } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Aucune notification")).toBeInTheDocument();
    });
  });
});
