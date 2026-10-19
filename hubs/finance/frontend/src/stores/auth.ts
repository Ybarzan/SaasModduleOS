import { create } from "zustand";
import { persist } from "zustand/middleware";

export type UserRole = 'ADMIN' | 'MANAGER' | 'USER';

export interface User {
  id: string;
  email: string;
  firstName?: string;
  lastName?: string;
  role?: UserRole;
  company?: string;
  companyId?: string;
}

export const ROLE_HIERARCHY: Record<UserRole, number> = {
  ADMIN: 0,
  MANAGER: 1,
  USER: 2,
};

interface AuthState {
  token: string | null;
  user: User | null;
  login: (token: string, user: User) => void;
  logout: () => void;
  isAuthenticated: () => boolean;
  isAdmin: () => boolean;
  hasMinimumRole: (role: UserRole) => boolean;
  canEdit: () => boolean;
}

// Store Zustand autonome pour Finance -- pas de partage de code avec
// compliance-engine (voir docs/07-... pour le choix d'extraction). Trimmé à
// exactement ce dont les pages portées ont besoin : token/user pour
// l'intercepteur axios de lib/api.ts, isAdmin()/canEdit() pour les contrôles
// d'accès UI -- même principe que hubs/logistix/src/stores/auth.ts.
export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      token: null,
      user: null,

      login: (token: string, user: User) => {
        set({ token, user });
      },

      logout: () => {
        set({ token: null, user: null });
      },

      isAuthenticated: () => {
        return get().token !== null && get().user !== null;
      },

      isAdmin: () => {
        return get().user?.role === 'ADMIN';
      },

      hasMinimumRole: (role: UserRole) => {
        const currentRole = get().user?.role;
        if (!currentRole) return false;
        const currentLevel = ROLE_HIERARCHY[currentRole];
        const requiredLevel = ROLE_HIERARCHY[role];
        if (currentLevel === undefined || requiredLevel === undefined) return false;
        return currentLevel <= requiredLevel;
      },

      // ADMIN/MANAGER/USER couvrent tous les rôles Finance (voir Role.java) --
      // conservé comme méthode distincte plutôt que isAuthenticated() pour
      // rester cohérent avec les points d'appel portés de compliance-engine.
      canEdit: () => {
        const role = get().user?.role;
        return role === 'ADMIN' || role === 'MANAGER' || role === 'USER';
      },
    }),
    {
      name: "finance-auth",
    }
  )
);
