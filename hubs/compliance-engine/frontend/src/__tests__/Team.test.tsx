import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import toast from "react-hot-toast";
import Team from "../pages/Team";
import { incokalkAPI } from "../lib/api";
import { useAuthStore } from "../stores/auth";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    team: { list: vi.fn(), stats: vi.fn(), invite: vi.fn(), update: vi.fn(), remove: vi.fn() },
    roles: { list: vi.fn() },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const members = [
  { id: "u1", email: "admin@incokalk.com", fullName: "Admin User", role: "ADMIN", createdAt: "2026-01-01T00:00:00Z" },
  { id: "u2", email: "member@incokalk.com", fullName: "Member User", role: "USER", createdAt: "2026-02-01T00:00:00Z" },
];
const stats = { total: 2, admins: 1, members: 1, viewers: 0 };

function mockDefaults() {
  vi.mocked(incokalkAPI.team.list).mockResolvedValue({ data: members } as never);
  vi.mocked(incokalkAPI.team.stats).mockResolvedValue({ data: stats } as never);
  vi.mocked(incokalkAPI.roles.list).mockResolvedValue({ data: [] } as never);
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <Team />
    </QueryClientProvider>
  );
}

function loginAs(role: "ADMIN" | "USER", id = "u1") {
  useAuthStore.setState({
    token: "tok",
    refreshToken: "r",
    user: { id, email: "me@incokalk.com", firstName: "Me", lastName: "User", role } as never,
  });
}

describe("Team page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockDefaults();
  });

  afterEach(() => {
    useAuthStore.setState({ token: null, refreshToken: null, user: null });
  });

  it("renders team members and stats", async () => {
    loginAs("ADMIN");
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Admin User")).toBeInTheDocument();
    });
    expect(screen.getByText("Member User")).toBeInTheDocument();
  });

  it("hides the invite button and shows a notice for non-admins", async () => {
    loginAs("USER", "u2");
    renderPage();
    await waitFor(() => screen.getByText("Member User"));
    expect(screen.queryByText("Inviter un membre")).not.toBeInTheDocument();
    expect(screen.getByText("Seuls les administrateurs peuvent gérer l'équipe")).toBeInTheDocument();
  });

  it("invites a new member from the modal", async () => {
    loginAs("ADMIN");
    vi.mocked(incokalkAPI.team.invite).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("Admin User"));

    fireEvent.click(screen.getByText("Inviter un membre"));
    fireEvent.change(screen.getByPlaceholderText("membre@entreprise.com"), { target: { value: "new@incokalk.com" } });
    fireEvent.change(screen.getByPlaceholderText("Jean Dupont"), { target: { value: "New Member" } });
    fireEvent.click(screen.getByRole("button", { name: "Envoyer l'invitation" }));

    await waitFor(() => {
      expect(incokalkAPI.team.invite).toHaveBeenCalledWith({
        email: "new@incokalk.com",
        fullName: "New Member",
        role: "USER",
      });
    });
    expect(toast.success).toHaveBeenCalledWith("Invitation envoyée avec succès");
  });

  it("shows the server error message when invite fails", async () => {
    loginAs("ADMIN");
    vi.mocked(incokalkAPI.team.invite).mockRejectedValue({
      response: { data: { message: "Email déjà invité" } },
    });
    renderPage();
    await waitFor(() => screen.getByText("Admin User"));

    fireEvent.click(screen.getByText("Inviter un membre"));
    fireEvent.change(screen.getByPlaceholderText("membre@entreprise.com"), { target: { value: "new@incokalk.com" } });
    fireEvent.change(screen.getByPlaceholderText("Jean Dupont"), { target: { value: "New Member" } });
    fireEvent.click(screen.getByRole("button", { name: "Envoyer l'invitation" }));

    await waitFor(() => {
      expect(toast.error).toHaveBeenCalledWith("Email déjà invité");
    });
  });

  it("removes a member after a two-step confirmation", async () => {
    loginAs("ADMIN");
    vi.mocked(incokalkAPI.team.remove).mockResolvedValue({} as never);
    renderPage();
    await waitFor(() => screen.getByText("Member User"));

    fireEvent.click(screen.getByTitle("Retirer de l'équipe"));
    fireEvent.click(screen.getByText("Oui"));

    await waitFor(() => {
      expect(incokalkAPI.team.remove).toHaveBeenCalledWith("u2");
    });
    expect(toast.success).toHaveBeenCalledWith("Membre retiré de l'équipe");
  });
});
