import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import Pagination from "../components/Pagination";

describe("Pagination", () => {
  const onPageChange = vi.fn();

  beforeEach(() => {
    onPageChange.mockClear();
  });

  it("renders nothing when totalPages <= 1", () => {
    const { container } = render(
      <Pagination page={0} totalPages={1} onPageChange={onPageChange} />
    );
    expect(container.innerHTML).toBe("");
  });

  it("renders page buttons", () => {
    render(<Pagination page={0} totalPages={5} onPageChange={onPageChange} />);
    expect(screen.getByText("1")).toBeInTheDocument();
    expect(screen.getByText("2")).toBeInTheDocument();
    expect(screen.getByText("3")).toBeInTheDocument();
  });

  it("calls onPageChange when clicking a page", () => {
    render(<Pagination page={0} totalPages={5} onPageChange={onPageChange} />);
    fireEvent.click(screen.getByText("3"));
    expect(onPageChange).toHaveBeenCalledWith(2);
  });

  it("disables previous button on first page", () => {
    render(<Pagination page={0} totalPages={5} onPageChange={onPageChange} />);
    const prevBtn = screen.getAllByRole("button")[0];
    expect(prevBtn).toBeDisabled();
  });

  it("disables next button on last page", () => {
    render(<Pagination page={4} totalPages={5} onPageChange={onPageChange} />);
    const buttons = screen.getAllByRole("button");
    const nextBtn = buttons[buttons.length - 1];
    expect(nextBtn).toBeDisabled();
  });

  it("shows ellipsis for many pages", () => {
    render(<Pagination page={5} totalPages={20} onPageChange={onPageChange} />);
    const ellipses = screen.getAllByText("...");
    expect(ellipses.length).toBeGreaterThanOrEqual(1);
  });

  it("highlights current page", () => {
    render(<Pagination page={2} totalPages={5} onPageChange={onPageChange} />);
    const currentPageBtn = screen.getByText("3");
    expect(currentPageBtn.className).toContain("bg-accent");
  });
});
