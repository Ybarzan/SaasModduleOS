import { create } from 'zustand';
import { persist, createJSONStorage, type StateStorage } from 'zustand/middleware';
import { Preferences } from '@capacitor/preferences';

export type UserRole = 'OWNER' | 'ADMIN' | 'MANAGER' | 'USER';

export interface User {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  role?: UserRole;
  companyId?: string;
}

// Preferences (Keychain/SharedPreferences-backed en natif, IndexedDB en aperçu web)
// est asynchrone contrairement à localStorage -- zustand-persist le gère via
// createJSONStorage, mais on doit suivre nous-mêmes la fin de la réhydratation
// pour éviter un flash "non authentifié" le temps de la lecture initiale.
const capacitorStorage: StateStorage = {
  getItem: async (name) => (await Preferences.get({ key: name })).value,
  setItem: async (name, value) => {
    await Preferences.set({ key: name, value });
  },
  removeItem: async (name) => {
    await Preferences.remove({ key: name });
  },
};

interface AuthState {
  token: string | null;
  refreshToken: string | null;
  user: User | null;
  hasHydrated: boolean;
  login: (token: string, refreshToken: string, user: User) => void;
  logout: () => void;
  setTokens: (token: string, refreshToken: string) => void;
  isAuthenticated: () => boolean;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      token: null,
      refreshToken: null,
      user: null,
      hasHydrated: false,

      login: (token, refreshToken, user) => set({ token, refreshToken, user }),
      logout: () => set({ token: null, refreshToken: null, user: null }),
      setTokens: (token, refreshToken) => set({ token, refreshToken }),
      isAuthenticated: () => get().token !== null && get().user !== null,
    }),
    {
      name: 'incokalk-mobile-auth',
      storage: createJSONStorage(() => capacitorStorage),
      partialize: (state) => ({ token: state.token, refreshToken: state.refreshToken, user: state.user }),
      onRehydrateStorage: () => () => {
        useAuthStore.setState({ hasHydrated: true });
      },
    }
  )
);
