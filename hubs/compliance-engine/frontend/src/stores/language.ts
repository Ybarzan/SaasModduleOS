import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export type Language = 'fr' | 'en';

interface LanguageState {
  language: Language;
  setLanguage: (language: Language) => void;
  toggle: () => void;
}

/**
 * Pilote ciblé (pas une vraie infra i18n) : IncoKalk vise le commerce
 * international mais le site est 100% français, sans bascule de langue.
 * Ce store couvre volontairement seulement Home (hero) et Pricing -- pas
 * de react-i18next, pas de routes /en/*, pas de traduction du contenu
 * dynamique des plans (renvoyé en français par le backend).
 */
export const useLanguageStore = create<LanguageState>()(
  persist(
    (set, get) => ({
      language: 'fr',
      setLanguage: (language) => set({ language }),
      toggle: () => set({ language: get().language === 'fr' ? 'en' : 'fr' }),
    }),
    { name: 'incokalk-language' }
  )
);
