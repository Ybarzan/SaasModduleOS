import { create } from "zustand";
import { persist } from "zustand/middleware";

export type UserRole = 'OWNER' | 'ADMIN' | 'MANAGER' | 'USER';

/** Miroir de Company.Plan / User.Plan côté backend (BillingService.getPlans). */
export type PlanId = 'FREE' | 'STARTER' | 'PRO' | 'ENTERPRISE';

export interface User {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  role?: UserRole;
  plan?: PlanId;
  company?: string;
  companyId?: string;
}

export const ROLE_HIERARCHY: Record<UserRole, number> = {
  OWNER: 0,
  ADMIN: 1,
  MANAGER: 2,
  USER: 3,
};

/** Plus haut = plus d'accès (inverse de ROLE_HIERARCHY, attention en comparant les deux). */
export const PLAN_HIERARCHY: Record<PlanId, number> = {
  FREE: 0,
  STARTER: 1,
  PRO: 2,
  ENTERPRISE: 3,
};

export function hasMinimumPlan(currentPlan: PlanId | undefined, requiredPlan: PlanId): boolean {
  if (!currentPlan) return false;
  return PLAN_HIERARCHY[currentPlan] >= PLAN_HIERARCHY[requiredPlan];
}

interface AuthState {
  token: string | null;
  refreshToken: string | null;
  user: User | null;
  login: (token: string, refreshToken: string, user: User) => void;
  logout: () => void;
  setTokens: (token: string, refreshToken: string) => void;
  setUser: (user: User) => void;
  isAuthenticated: () => boolean;
  isAdmin: () => boolean;
  hasRole: (role: UserRole) => boolean;
  hasMinimumRole: (role: UserRole) => boolean;
  canEdit: () => boolean;
  canManageTeam: () => boolean;
  canManageShipments: () => boolean;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      token: null,
      refreshToken: null,
      user: null,

      login: (token: string, refreshToken: string, user: User) => {
        set({ token, refreshToken, user });
      },

      logout: () => {
        set({ token: null, refreshToken: null, user: null });
      },

      setTokens: (token: string, refreshToken: string) => {
        set({ token, refreshToken });
      },

      setUser: (user: User) => {
        set({ user });
      },

      isAuthenticated: () => {
        return get().token !== null && get().user !== null;
      },

      isAdmin: () => {
        const role = get().user?.role;
        return role === 'OWNER' || role === 'ADMIN';
      },

      hasRole: (role: UserRole) => get().user?.role === role,

      hasMinimumRole: (role: UserRole) => {
        const currentRole = get().user?.role;
        if (!currentRole) return false;
        const currentLevel = ROLE_HIERARCHY[currentRole];
        const requiredLevel = ROLE_HIERARCHY[role];
        if (currentLevel === undefined || requiredLevel === undefined) return false;
        return currentLevel <= requiredLevel;
      },

      canEdit: () => {
        const role = get().user?.role;
        return role === 'OWNER' || role === 'ADMIN' || role === 'MANAGER' || role === 'USER';
      },

      canManageTeam: () => {
        const role = get().user?.role;
        return role === 'OWNER' || role === 'ADMIN' || role === 'MANAGER';
      },

      canManageShipments: () => {
        const role = get().user?.role;
        return role === 'OWNER' || role === 'ADMIN' || role === 'MANAGER' || role === 'USER';
      },
    }),
    {
      name: "incokalk-auth",
    }
  )
);
