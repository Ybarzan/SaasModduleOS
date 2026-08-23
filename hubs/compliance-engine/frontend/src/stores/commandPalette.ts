import { create } from 'zustand';

interface CommandPaletteState {
  isOpen: boolean;
  open: () => void;
  close: () => void;
  toggle: () => void;
}

/** État partagé pour que le raccourci clavier (écouté une fois, globalement)
 * et le bouton de la topbar puissent tous les deux ouvrir la palette sans
 * prop-drilling à travers AppLayout. */
export const useCommandPaletteStore = create<CommandPaletteState>((set) => ({
  isOpen: false,
  open: () => set({ isOpen: true }),
  close: () => set({ isOpen: false }),
  toggle: () => set((s) => ({ isOpen: !s.isOpen })),
}));
