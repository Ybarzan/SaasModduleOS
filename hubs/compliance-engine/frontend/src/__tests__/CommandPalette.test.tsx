import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import CommandPalette from "../components/CommandPalette";
import { useAuthStore } from "../stores/auth";
import { useCommandPaletteStore } from "../stores/commandPalette";

const mockNavigate = vi.fn();
vi.mock("react-router-dom", async (importOriginal) => {
  const actual = await importOriginal<typeof import("react-router-dom")>();
  return { ...actual, useNavigate: () => mockNavigate };
});

function renderPalette() {
  return render(
    <MemoryRouter>
      <CommandPalette />
    </MemoryRouter>
  );
}

describe("CommandPalette", () => {
  beforeEach(() => {
    mockNavigate.mockClear();
    useCommandPaletteStore.setState({ isOpen: false });
    useAuthStore.setState({
      token: "tok",
      refreshToken: "r",
      user: { id: "u1", email: "a@b.com", firstName: "A", lastName: "B", role: "OWNER", plan: "ENTERPRISE" },
    });
  });

  afterEach(() => {
    useAuthStore.setState({ token: null, refreshToken: null, user: null });
  });

  it("renders nothing when closed", () => {
    renderPalette();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("opens via Ctrl+K and shows default results", () => {
    renderPalette();
    fireEvent.keyDown(window, { key: "k", ctrlKey: true });
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getByText("Tableau de bord")).toBeInTheDocument();
  });

  it("filters results as the user types", () => {
    renderPalette();
    fireEvent.keyDown(window, { key: "k", ctrlKey: true });

    fireEvent.change(screen.getByPlaceholderText("Rechercher une page ou un outil..."), {
      target: { value: "webhook" },
    });
    expect(screen.getByText("Webhooks")).toBeInTheDocument();
    expect(screen.queryByText("Tableau de bord")).not.toBeInTheDocument();
  });

  it("shows a message when nothing matches", () => {
    renderPalette();
    fireEvent.keyDown(window, { key: "k", ctrlKey: true });

    fireEvent.change(screen.getByPlaceholderText("Rechercher une page ou un outil..."), {
      target: { value: "zzzznothingmatches" },
    });
    expect(screen.getByText("Aucun résultat")).toBeInTheDocument();
  });

  it("navigates to a result on click and closes", () => {
    renderPalette();
    fireEvent.keyDown(window, { key: "k", ctrlKey: true });

    fireEvent.change(screen.getByPlaceholderText("Rechercher une page ou un outil..."), {
      target: { value: "webhook" },
    });
    fireEvent.click(screen.getByText("Webhooks"));
    expect(mockNavigate).toHaveBeenCalledWith("/webhooks");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("navigates with arrow keys and Enter", () => {
    renderPalette();
    fireEvent.keyDown(window, { key: "k", ctrlKey: true });
    const input = screen.getByPlaceholderText("Rechercher une page ou un outil...");

    fireEvent.change(input, { target: { value: "webhook" } });
    fireEvent.keyDown(input, { key: "Enter" });
    expect(mockNavigate).toHaveBeenCalledWith("/webhooks");
  });

  it("closes on Escape", () => {
    renderPalette();
    fireEvent.keyDown(window, { key: "k", ctrlKey: true });
    const input = screen.getByPlaceholderText("Rechercher une page ou un outil...");

    fireEvent.keyDown(input, { key: "Escape" });
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("hides admin-only items from a USER role", () => {
    useAuthStore.setState({
      user: { id: "u1", email: "a@b.com", firstName: "A", lastName: "B", role: "USER", plan: "ENTERPRISE" },
    });
    renderPalette();
    fireEvent.keyDown(window, { key: "k", ctrlKey: true });

    fireEvent.change(screen.getByPlaceholderText("Rechercher une page ou un outil..."), {
      target: { value: "tarifs" },
    });
    expect(screen.queryByText("Gestion tarifs")).not.toBeInTheDocument();
  });

  it("routes a plan-locked item to /pricing instead of its real page", () => {
    useAuthStore.setState({
      user: { id: "u1", email: "a@b.com", firstName: "A", lastName: "B", role: "OWNER", plan: "FREE" },
    });
    renderPalette();
    fireEvent.keyDown(window, { key: "k", ctrlKey: true });

    fireEvent.change(screen.getByPlaceholderText("Rechercher une page ou un outil..."), {
      target: { value: "Landed Cost" },
    });
    fireEvent.click(screen.getByText("Landed Cost"));
    expect(mockNavigate).toHaveBeenCalledWith("/pricing");
  });
});
