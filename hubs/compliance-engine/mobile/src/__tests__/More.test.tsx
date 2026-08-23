import { describe, it, expect, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import More from "../pages/More";
import { useAuthStore } from "../stores/auth";

function renderPage() {
  return render(
    <MemoryRouter>
      <More />
    </MemoryRouter>
  );
}

describe("More (mobile)", () => {
  beforeEach(() => {
    useAuthStore.setState({ token: "tok", refreshToken: "r", user: { id: "u1", email: "a@b.com", firstName: "A", lastName: "B", role: "USER" }, hasHydrated: true });
  });

  it("shows items with no role requirement to a plain USER", () => {
    renderPage();
    expect(screen.getByText("Ship Tracker")).toBeInTheDocument();
    expect(screen.getByText("Bons de réception")).toBeInTheDocument();
  });

  it("hides manager-restricted items from a plain USER", () => {
    renderPage();
    expect(screen.queryByText("Prédictions ETA")).not.toBeInTheDocument();
    expect(screen.queryByText("Screening parties")).not.toBeInTheDocument();
  });

  it("hides admin-restricted items from a MANAGER", () => {
    useAuthStore.setState({ user: { id: "u1", email: "a@b.com", firstName: "A", lastName: "B", role: "MANAGER" } });
    renderPage();
    expect(screen.getByText("Prédictions ETA")).toBeInTheDocument();
    expect(screen.queryByText("Suivi email entrant")).not.toBeInTheDocument();
  });

  it("shows every item to an OWNER", () => {
    useAuthStore.setState({ user: { id: "u1", email: "a@b.com", firstName: "A", lastName: "B", role: "OWNER" } });
    renderPage();
    expect(screen.getByText("Prédictions ETA")).toBeInTheDocument();
    expect(screen.getByText("Screening parties")).toBeInTheDocument();
    expect(screen.getByText("Suivi email entrant")).toBeInTheDocument();
  });

  it("links Scanner réception to /scan-receiving", () => {
    renderPage();
    expect(screen.getByText("Scanner réception").closest("a")).toHaveAttribute("href", "/scan-receiving");
  });
});
