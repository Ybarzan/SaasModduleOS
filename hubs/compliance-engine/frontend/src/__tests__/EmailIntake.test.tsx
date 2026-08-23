import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import EmailIntake from "../pages/EmailIntake";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    emailIntake: {
      list: vi.fn(),
      logs: vi.fn(),
      create: vi.fn(),
      update: vi.fn(),
      remove: vi.fn(),
      test: vi.fn(),
    },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const intakes = [
  {
    id: "e1",
    email: "ops@incokalk.com",
    imapHost: "imap.incokalk.com",
    imapPort: 993,
    username: "ops@incokalk.com",
    protocol: "IMAP",
    sslEnabled: true,
    folder: "INBOX",
    autoImport: true,
    deleteAfterImport: false,
    targetDocumentType: "INVOICE",
    isActive: true,
    lastCheckAt: "2026-08-01T00:00:00Z",
    lastError: undefined,
    createdAt: "2026-08-01T00:00:00Z",
    updatedAt: "2026-08-01T00:00:00Z",
  },
];

const logs = [
  {
    id: "l1",
    emailIntakeId: "e1",
    status: "SUCCESS",
    message: "3 documents importés",
    processedCount: 3,
    errorCount: 0,
    startedAt: "2026-08-01T00:00:00Z",
    completedAt: "2026-08-01T00:05:00Z",
  },
];

function mockDefaults() {
  vi.mocked(incokalkAPI.emailIntake.list).mockResolvedValue({ data: intakes } as never);
  vi.mocked(incokalkAPI.emailIntake.logs).mockResolvedValue({ data: logs } as never);
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <EmailIntake />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe("EmailIntake page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockDefaults();
  });

  it("renders configured mailboxes and recent sync logs", async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("ops@incokalk.com")).toBeInTheDocument();
    });
    expect(screen.getByText("3 documents importés")).toBeInTheDocument();
  });

  it("shows the empty state when no mailbox is configured", async () => {
    vi.mocked(incokalkAPI.emailIntake.list).mockResolvedValue({ data: [] } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Aucune boîte email configurée")).toBeInTheDocument();
    });
  });

  it("creates a new mailbox", async () => {
    vi.mocked(incokalkAPI.emailIntake.create).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("ops@incokalk.com"));

    fireEvent.click(screen.getByRole("button", { name: /Ajouter une boîte/ }));
    const emailLikeFields = screen.getAllByPlaceholderText("incoming@exemple.com");
    fireEvent.change(emailLikeFields[0], { target: { value: "sync@incokalk.com" } });
    fireEvent.change(screen.getByPlaceholderText("imap.exemple.com"), {
      target: { value: "imap.test.com" },
    });
    fireEvent.change(emailLikeFields[1], { target: { value: "sync@incokalk.com" } });
    fireEvent.click(screen.getByRole("button", { name: "Sauvegarder" }));

    await waitFor(() => {
      expect(incokalkAPI.emailIntake.create).toHaveBeenCalledWith(
        expect.objectContaining({
          email: "sync@incokalk.com",
          imapHost: "imap.test.com",
          username: "sync@incokalk.com",
        })
      );
    });
  });

  it("tests a mailbox connection", async () => {
    vi.mocked(incokalkAPI.emailIntake.test).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("ops@incokalk.com"));

    fireEvent.click(screen.getByRole("button", { name: /Tester/ }));
    await waitFor(() => {
      expect(incokalkAPI.emailIntake.test).toHaveBeenCalledWith("e1");
    });
  });

  it("deletes a mailbox after confirmation", async () => {
    vi.mocked(incokalkAPI.emailIntake.remove).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("ops@incokalk.com"));

    fireEvent.click(screen.getByTitle("Supprimer"));
    await waitFor(() => screen.getByText("Confirmer la suppression"));

    const dialog = screen.getByText("Confirmer la suppression").closest("div")!.parentElement!;
    fireEvent.click(within(dialog).getByRole("button", { name: "Supprimer" }));

    await waitFor(() => {
      expect(incokalkAPI.emailIntake.remove).toHaveBeenCalledWith("e1");
    });
  });

  it("links to the related document tools", async () => {
    renderPage();
    await waitFor(() => screen.getByText("ops@incokalk.com"));

    expect(screen.getByText("Extraction de documents")).toBeInTheDocument();
    expect(screen.getByText("Génération de documents")).toBeInTheDocument();
  });
});
