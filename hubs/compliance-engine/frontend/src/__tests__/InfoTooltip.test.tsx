import { describe, it, expect } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import InfoTooltip from "../components/InfoTooltip";

describe("InfoTooltip", () => {
  it("is hidden until hovered or focused", () => {
    render(<InfoTooltip text="Explication du champ" />);
    expect(screen.queryByRole("tooltip")).not.toBeInTheDocument();
  });

  it("shows the tooltip text on mouse hover and hides it on mouse leave", () => {
    render(<InfoTooltip text="Explication du champ" />);
    const wrapper = screen.getByRole("button", { name: "Explication du champ" }).parentElement!;

    fireEvent.mouseEnter(wrapper);
    expect(screen.getByRole("tooltip")).toHaveTextContent("Explication du champ");

    fireEvent.mouseLeave(wrapper);
    expect(screen.queryByRole("tooltip")).not.toBeInTheDocument();
  });

  it("shows the tooltip on keyboard focus and hides it on blur", () => {
    render(<InfoTooltip text="Explication du champ" />);
    const button = screen.getByRole("button", { name: "Explication du champ" });

    fireEvent.focus(button);
    expect(screen.getByRole("tooltip")).toBeInTheDocument();

    fireEvent.blur(button);
    expect(screen.queryByRole("tooltip")).not.toBeInTheDocument();
  });
});
