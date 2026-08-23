import { create } from "zustand";
import { persist } from "zustand/middleware";

export interface ClientUser {
  id: string;
  email: string;
  fullName: string;
  phone?: string;
  companyId: string;
}

interface ClientAuthState {
  token: string | null;
  client: ClientUser | null;
  login: (token: string, client: ClientUser) => void;
  logout: () => void;
  isAuthenticated: () => boolean;
}

export const useClientAuthStore = create<ClientAuthState>()(
  persist(
    (set, get) => ({
      token: null,
      client: null,

      login: (token: string, client: ClientUser) => {
        set({ token, client });
      },

      logout: () => {
        set({ token: null, client: null });
      },

      isAuthenticated: () => {
        return get().token !== null && get().client !== null;
      },
    }),
    {
      name: "incokalk-client-auth",
    }
  )
);
