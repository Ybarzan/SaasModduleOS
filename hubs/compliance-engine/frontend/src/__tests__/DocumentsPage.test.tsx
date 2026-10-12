import { describe, it, expect, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import DocumentsPage from "../pages/DocumentsPage";
import { useAuthStore } from "../stores/auth";

function renderPage() {
  return render(
    <MemoryRouter>
      <DocumentsPage />
    </MemoryRouter>
  );
}

describe("DocumentsPage", () => {
  beforeEach(() => {
    useAuthStore.setState({ token: null, refreshToken: null, user: null });
  });

  it("renders the page header", () => {
    renderPage();
    expect(screen.getByText("Documents")).toBeInTheDocument();
  });

  it("hides the document-parser and email-intake tools from a USER role", () => {
    useAuthStore.setState({ user: { id: "u1", email: "a@b.com", firstName: "A", lastName: "B", role: "USER" } as never });
    renderPage();

    expect(screen.queryByText("Extraction de documents")).not.toBeInTheDocument();
    expect(screen.queryByText("Import Email")).not.toBeInTheDocument();
  });

  it("shows the document-parser link to a MANAGER but hides email-intake (admin-only)", () => {
    useAuthStore.setState({ user: { id: "u1", email: "a@b.com", firstName: "A", lastName: "B", role: "MANAGER" } as never });
    renderPage();

    expect(screen.getByText("Extraction de documents")).toBeInTheDocument();
    expect(screen.queryByText("Import Email")).not.toBeInTheDocument();
  });

  it("shows both linked tools to an ADMIN", () => {
    useAuthStore.setState({ user: { id: "u1", email: "a@b.com", firstName: "A", lastName: "B", role: "ADMIN" } as never });
    renderPage();

    expect(screen.getByText("Extraction de documents")).toBeInTheDocument();
    expect(screen.getByText("Import Email")).toBeInTheDocument();
  });
});
