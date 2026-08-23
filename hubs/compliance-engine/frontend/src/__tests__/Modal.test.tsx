import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import Modal from "../components/Modal";

function Harness({ open, onClose }: { open: boolean; onClose: () => void }) {
  return (
    <>
      <button>Trigger</button>
      <Modal open={open} onClose={onClose} ariaLabel="Test modal">
        <div className="p-6">
          <button>First</button>
          <input placeholder="middle field" />
          <button>Last</button>
        </div>
      </Modal>
    </>
  );
}

describe("Modal", () => {
  it("renders nothing when closed", () => {
    render(<Harness open={false} onClose={vi.fn()} />);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("renders into document.body via a portal when open", () => {
    render(<Harness open onClose={vi.fn()} />);
    const dialog = screen.getByRole("dialog", { name: "Test modal" });
    expect(dialog).toBeInTheDocument();
    expect(dialog.closest("body")).toBe(document.body);
  });

  it("focuses the first focusable element on open", () => {
    render(<Harness open onClose={vi.fn()} />);
    expect(document.activeElement).toHaveTextContent("First");
  });

  it("calls onClose on Escape", () => {
    const onClose = vi.fn();
    render(<Harness open onClose={onClose} />);
    fireEvent.keyDown(document, { key: "Escape" });
    expect(onClose).toHaveBeenCalled();
  });

  it("calls onClose on backdrop click but not on dialog content click", () => {
    const onClose = vi.fn();
    render(<Harness open onClose={onClose} />);
    fireEvent.click(screen.getByRole("dialog"));
    expect(onClose).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole("dialog").parentElement!);
    expect(onClose).toHaveBeenCalled();
  });

  it("traps Tab focus within the dialog, wrapping from last to first", () => {
    render(<Harness open onClose={vi.fn()} />);
    const last = screen.getByRole("button", { name: "Last" });
    last.focus();
    fireEvent.keyDown(document, { key: "Tab" });
    expect(document.activeElement).toHaveTextContent("First");
  });

  it("traps Shift+Tab focus, wrapping from first to last", () => {
    render(<Harness open onClose={vi.fn()} />);
    const first = screen.getByRole("button", { name: "First" });
    first.focus();
    fireEvent.keyDown(document, { key: "Tab", shiftKey: true });
    expect(document.activeElement).toHaveTextContent("Last");
  });

  it("restores focus to the trigger element on close", () => {
    const { rerender } = render(<Harness open={false} onClose={vi.fn()} />);
    const trigger = screen.getByText("Trigger");
    trigger.focus();
    expect(document.activeElement).toBe(trigger);

    rerender(<Harness open onClose={vi.fn()} />);
    expect(document.activeElement).not.toBe(trigger);

    rerender(<Harness open={false} onClose={vi.fn()} />);
    expect(document.activeElement).toBe(trigger);
  });
});
