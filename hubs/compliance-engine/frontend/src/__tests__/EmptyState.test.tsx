import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { Bell } from "lucide-react";
import EmptyState from "../components/EmptyState";

describe("EmptyState", () => {
  it("renders the title", () => {
    render(<EmptyState icon={Bell} title="Aucune règle" />);
    expect(screen.getByText("Aucune règle")).toBeInTheDocument();
  });

  it("renders the description only when provided", () => {
    const { rerender } = render(<EmptyState icon={Bell} title="Aucune règle" />);
    expect(screen.queryByText("Détails")).not.toBeInTheDocument();

    rerender(<EmptyState icon={Bell} title="Aucune règle" description="Détails" />);
    expect(screen.getByText("Détails")).toBeInTheDocument();
  });

  it("renders the action button and fires its onClick", () => {
    const onClick = vi.fn();
    render(<EmptyState icon={Bell} title="Aucune règle" action={{ label: "Créer", onClick }} />);

    fireEvent.click(screen.getByRole("button", { name: /Créer/ }));
    expect(onClick).toHaveBeenCalled();
  });

  it("renders no button when action is omitted", () => {
    render(<EmptyState icon={Bell} title="Aucune règle" />);
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });
});
