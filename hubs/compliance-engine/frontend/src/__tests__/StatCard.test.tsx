import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { FileText } from "lucide-react";
import StatCard from "../components/StatCard";

describe("StatCard", () => {
  it("renders the label and value", () => {
    render(<StatCard label="Total" value={42} />);
    expect(screen.getByText("Total")).toBeInTheDocument();
    expect(screen.getByText("42")).toBeInTheDocument();
  });

  it("renders an icon when provided", () => {
    const { container } = render(<StatCard label="Total" value={42} icon={FileText} />);
    expect(container.querySelector("svg")).toBeInTheDocument();
  });

  it("renders no icon element when omitted", () => {
    const { container } = render(<StatCard label="Total" value={42} />);
    expect(container.querySelector("svg")).not.toBeInTheDocument();
  });

  it("accepts a ReactNode as value", () => {
    render(<StatCard label="Actifs" value={<span data-testid="custom-value">3</span>} />);
    expect(screen.getByTestId("custom-value")).toBeInTheDocument();
  });
});
