import axios from "axios";
import { useAuthStore, type UserRole } from "../stores/auth";

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? "http://localhost:8091",
  withCredentials: true,
});

// Inject the bearer token on every request.
api.interceptors.request.use((config) => {
  try {
    const token = useAuthStore.getState().token;
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
  } catch {
    // token not available yet
  }
  return config;
});

// The Logistix backend issues a single long-lived JWT at login and exposes
// no /v1/auth/refresh endpoint — unlike fleet-hub, there is nothing to retry
// a 401 against. On a 401 outside the auth endpoints themselves, just drop
// the stale session and send the user back to /login.
api.interceptors.response.use(
  (r) => r,
  (err) => {
    const originalRequest = err.config;
    const url = originalRequest?.url || "";
    const isAuthEndpoint = url.startsWith("/v1/auth/login") || url.startsWith("/v1/auth/register");

    if (err.response?.status === 401 && !isAuthEndpoint) {
      useAuthStore.getState().logout();
      if (!window.location.pathname.startsWith("/login")) {
        window.location.href = "/login";
      }
    }

    return Promise.reject(err);
  }
);

// API Endpoints for Logistix — ported 1:1 from compliance-engine's
// `incokalkAPI.{warehouses,inventory,receivings}` so the REST contract keeps
// matching the backend being extracted in parallel. Everything outside the
// warehousing/réception domain is intentionally left out.
// /v1/auth/login répond à plat (voir com.logistix.dto.AuthResponse côté
// backend), jamais un objet `user` imbriqué.
export interface AuthLoginResponse {
  token: string;
  email: string;
  displayName: string;
  role: UserRole;
  companyId: string;
  companyName: string;
}

export const logistixAPI = {
  auth: {
    login: (email: string, password: string) =>
      api.post<AuthLoginResponse>("/v1/auth/login", { email, password }),
  },

  // Warehouses / Réception
  warehouses: {
    list: () => api.get("/v1/warehouses"),
    get: (id: string) => api.get(`/v1/warehouses/${id}`),
    create: (data: unknown) => api.post("/v1/warehouses", data),
    update: (id: string, data: unknown) => api.put(`/v1/warehouses/${id}`, data),
    delete: (id: string) => api.delete(`/v1/warehouses/${id}`),
  },

  // Inventaire / Stock
  inventory: {
    items: {
      list: (q?: string) => api.get("/v1/inventory/items", { params: q ? { q } : {} }),
      get: (id: string) => api.get(`/v1/inventory/items/${id}`),
      create: (data: unknown) => api.post("/v1/inventory/items", data),
      update: (id: string, data: unknown) => api.put(`/v1/inventory/items/${id}`, data),
      delete: (id: string) => api.delete(`/v1/inventory/items/${id}`),
    },
    barcodes: {
      list: (itemId: string) => api.get(`/v1/inventory/items/${itemId}/barcodes`),
      add: (itemId: string, data: { barcode: string; type?: string }) =>
        api.post(`/v1/inventory/items/${itemId}/barcodes`, data),
      remove: (itemId: string, barcodeId: string) =>
        api.delete(`/v1/inventory/items/${itemId}/barcodes/${barcodeId}`),
    },
    balances: (warehouseId?: string) =>
      api.get("/v1/inventory/balances", { params: warehouseId ? { warehouseId } : {} }),
    movements: (itemId: string) => api.get("/v1/inventory/movements", { params: { itemId } }),
    adjust: (data: { warehouseId: string; itemId: string; quantity: number; note?: string }) =>
      api.post("/v1/inventory/adjustments", data),
  },

  // Réception marchandises / Scan
  receivings: {
    list: (params?: { status?: string; warehouseId?: string; shipmentId?: string }) =>
      api.get("/v1/receivings", { params }),
    get: (id: string) => api.get(`/v1/receivings/${id}`),
    create: (data: unknown) => api.post("/v1/receivings", data),
    scan: (id: string, data: unknown) => api.post(`/v1/receivings/${id}/scan`, data),
    damage: (id: string, data: { itemId: string; quantity: number; notes?: string }) =>
      api.post(`/v1/receivings/${id}/damage`, data),
    complete: (id: string) => api.post(`/v1/receivings/${id}/complete`),
    cancel: (id: string) => api.post(`/v1/receivings/${id}/cancel`),
    discrepancies: () => api.get("/v1/receivings/discrepancies"),
    resolveDiscrepancy: (id: string, notes?: string) =>
      api.post(`/v1/receivings/discrepancies/${id}/resolve`, { notes }),
  },

  // Paramètres société (intégration fleet-hub)
  company: {
    fleetHubKeyStatus: () => api.get("/v1/company/fleethub-key"),
    setFleetHubKey: (apiKey: string) => api.put("/v1/company/fleethub-key", { apiKey }),
    clearFleetHubKey: () => api.delete("/v1/company/fleethub-key"),
  },

  // Partage opt-in d'un résumé d'activité vers le tableau de bord cross-hub de Praxio
  praxioIntegration: {
    settings: () => api.get("/v1/praxio-integration/settings"),
    enable: () => api.post("/v1/praxio-integration/enable"),
    disable: () => api.post("/v1/praxio-integration/disable"),
  },
};
