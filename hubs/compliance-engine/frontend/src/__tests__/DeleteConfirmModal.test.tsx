import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import DeleteConfirmModal from "../components/DeleteConfirmModal";

describe("DeleteConfirmModal", () => {
  it("renders nothing when closed", () => {
    render(
      <DeleteConfirmModal open={false} onClose={vi.fn()} onConfirm={vi.fn()} message="Supprimer ?" />
    );
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("shows the message and default title/confirm label", () => {
    render(
      <DeleteConfirmModal open onClose={vi.fn()} onConfirm={vi.fn()} message="Supprimer cet élément ?" />
    );
    expect(screen.getByText("Confirmer la suppression")).toBeInTheDocument();
    expect(screen.getByText("Supprimer cet élément ?")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Supprimer" })).toBeInTheDocument();
  });

  it("supports a custom title and confirm label", () => {
    render(
      <DeleteConfirmModal
        open
        onClose={vi.fn()}
        onConfirm={vi.fn()}
        message="..."
        title="Révoquer le lien"
        confirmLabel="Révoquer"
      />
    );
    expect(screen.getByText("Révoquer le lien")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Révoquer" })).toBeInTheDocument();
  });

  it("calls onConfirm when confirming and onClose when cancelling", () => {
    const onConfirm = vi.fn();
    const onClose = vi.fn();
    render(<DeleteConfirmModal open onClose={onClose} onConfirm={onConfirm} message="Supprimer ?" />);

    fireEvent.click(screen.getByRole("button", { name: "Annuler" }));
    expect(onClose).toHaveBeenCalled();

    fireEvent.click(screen.getByRole("button", { name: "Supprimer" }));
    expect(onConfirm).toHaveBeenCalled();
  });

  it("disables the confirm button while pending", () => {
    render(
      <DeleteConfirmModal open onClose={vi.fn()} onConfirm={vi.fn()} message="Supprimer ?" isPending />
    );
    expect(screen.getByRole("button", { name: "Supprimer" })).toBeDisabled();
  });
});
