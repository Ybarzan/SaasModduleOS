import { describe, it, expect, vi, afterEach } from "vitest";
import { render, screen, act, fireEvent } from "@testing-library/react";
import HeroShowcase from "../components/HeroShowcase";

// jsdom n'implémente pas matchMedia -- framer-motion's useReducedMotion en a besoin
// (même pattern que AppLayout.test.tsx).
function stubMatchMedia(reduceMotion: boolean) {
  window.matchMedia = vi.fn().mockReturnValue({
    matches: reduceMotion,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
  });
}

// L'index de la scène active pilote directement la classe active du point de
// navigation (état React) -- vérifier ça plutôt que le contenu de la scène
// évite de dépendre de la fin réelle de l'animation d'AnimatePresence (pilotée
// par requestAnimationFrame, non simulé par les timers Vitest).
function activeDotIndex(dots: HTMLElement[]): number {
  return dots.findIndex((d) => d.className.includes("w-6"));
}

describe("HeroShowcase", () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it("shows the first scene (Sophie) on mount", () => {
    stubMatchMedia(false);
    render(<HeroShowcase />);
    expect(screen.getByText("Sophie")).toBeInTheDocument();
    expect(screen.getByText("Jongle entre 4 portails transporteurs")).toBeInTheDocument();
  });

  it("shows the real trial and referral claims as floating overlays", () => {
    stubMatchMedia(false);
    render(<HeroShowcase />);
    expect(screen.getByText("Essai 14 jours, sans engagement")).toBeInTheDocument();
    expect(screen.getByText("1 mois offert en parrainant")).toBeInTheDocument();
  });

  it("marks the clicked dot as active", () => {
    stubMatchMedia(false);
    render(<HeroShowcase />);
    const dots = screen.getAllByRole("button", { name: /Scène \d/ });
    expect(activeDotIndex(dots)).toBe(0);

    act(() => {
      dots[3].click();
    });

    expect(activeDotIndex(dots)).toBe(3);
  });

  it("auto-advances the active dot after the interval", () => {
    stubMatchMedia(false);
    vi.useFakeTimers();
    render(<HeroShowcase />);
    const dots = screen.getAllByRole("button", { name: /Scène \d/ });
    expect(activeDotIndex(dots)).toBe(0);

    act(() => {
      vi.advanceTimersByTime(4500);
    });

    expect(activeDotIndex(dots)).toBe(1);
  });

  it("pauses auto-advance while hovered", () => {
    stubMatchMedia(false);
    vi.useFakeTimers();
    const { container } = render(<HeroShowcase />);
    const dots = screen.getAllByRole("button", { name: /Scène \d/ });
    const card = container.querySelector(".bg-bg.rounded-none") as HTMLElement;

    act(() => {
      fireEvent.mouseEnter(card);
    });
    act(() => {
      vi.advanceTimersByTime(10000);
    });

    expect(activeDotIndex(dots)).toBe(0);
  });
});

// Le bypass prefers-reduced-motion n'est volontairement pas testé ici : framer-motion
// met en cache la lecture de matchMedia au niveau du module (motion-dom's
// hasReducedMotionListener), donc un seul état est observable par fichier de test --
// même limite que PageReveal.tsx (aucun test), qui utilise le même pattern
// useReducedMotion(). Le code du bypass (useCountUp(target, skipAnimation) avec
// useState(skipAnimation ? target : 0)) reste couvert par la relecture + le typecheck.
