import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface OnboardingState {
  hasSeenOnboarding: boolean;
  isOpen: boolean;
  open: () => void;
  close: () => void;
}

/** Seul `hasSeenOnboarding` est persisté (voir `partialize`) : `isOpen` ne doit
 * pas survivre à un rechargement de page, sinon fermer l'onglet pendant que la
 * modale est ouverte la rouvrirait automatiquement à la prochaine visite même
 * pour un utilisateur qui l'a déjà vue. */
export const useOnboardingStore = create<OnboardingState>()(
  persist(
    (set) => ({
      hasSeenOnboarding: false,
      isOpen: false,
      open: () => set({ isOpen: true }),
      close: () => set({ isOpen: false, hasSeenOnboarding: true }),
    }),
    {
      name: 'incokalk-onboarding',
      partialize: (state) => ({ hasSeenOnboarding: state.hasSeenOnboarding }),
    }
  )
);
