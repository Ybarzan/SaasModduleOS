import { describe, it, expect, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import LanguageToggle from "../components/LanguageToggle";
import { useLanguageStore } from "../stores/language";

describe("LanguageToggle", () => {
  beforeEach(() => {
    useLanguageStore.setState({ language: "fr" });
  });

  it("shows EN when the current language is French", () => {
    render(<LanguageToggle />);
    expect(screen.getByText("EN")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Switch to English" })).toBeInTheDocument();
  });

  it("switches the store to English and shows FR after a click", () => {
    render(<LanguageToggle />);
    fireEvent.click(screen.getByRole("button"));

    expect(useLanguageStore.getState().language).toBe("en");
    expect(screen.getByText("FR")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Passer en français" })).toBeInTheDocument();
  });

  it("switches back to French on a second click", () => {
    render(<LanguageToggle />);
    fireEvent.click(screen.getByRole("button"));
    fireEvent.click(screen.getByRole("button"));

    expect(useLanguageStore.getState().language).toBe("fr");
  });
});
