import { describe, it, expect, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import OnboardingModal from "../components/OnboardingModal";
import { useOnboardingStore } from "../stores/onboarding";

describe("OnboardingModal", () => {
  beforeEach(() => {
    useOnboardingStore.setState({ isOpen: false, hasSeenOnboarding: false });
  });

  it("renders nothing when closed", () => {
    render(<OnboardingModal />);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("shows the first slide when open", () => {
    useOnboardingStore.setState({ isOpen: true });
    render(<OnboardingModal />);
    expect(screen.getByText("Bienvenue sur IncoKalk")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Suivant" })).toBeInTheDocument();
  });

  it("advances through all slides and ends with Terminer", () => {
    useOnboardingStore.setState({ isOpen: true });
    render(<OnboardingModal />);

    fireEvent.click(screen.getByRole("button", { name: "Suivant" }));
    expect(screen.getByText("7 Hubs métier")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Suivant" }));
    expect(screen.getByText("Recherche rapide")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Suivant" }));
    expect(screen.getByText("Par où commencer")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Terminer" })).toBeInTheDocument();
  });

  it("closes and marks onboarding as seen when finishing the last slide", () => {
    useOnboardingStore.setState({ isOpen: true });
    render(<OnboardingModal />);

    fireEvent.click(screen.getByRole("button", { name: "Suivant" }));
    fireEvent.click(screen.getByRole("button", { name: "Suivant" }));
    fireEvent.click(screen.getByRole("button", { name: "Suivant" }));
    fireEvent.click(screen.getByRole("button", { name: "Terminer" }));

    expect(useOnboardingStore.getState().isOpen).toBe(false);
    expect(useOnboardingStore.getState().hasSeenOnboarding).toBe(true);
  });

  it("closes and marks onboarding as seen when skipped from the first slide", () => {
    useOnboardingStore.setState({ isOpen: true });
    render(<OnboardingModal />);

    fireEvent.click(screen.getByRole("button", { name: "Passer" }));

    expect(useOnboardingStore.getState().isOpen).toBe(false);
    expect(useOnboardingStore.getState().hasSeenOnboarding).toBe(true);
  });
});
