import axios from "axios";
import { useAuthStore, type User } from "../stores/auth";

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

// 401 handling with a single refresh-and-retry, modeled on fleet-hub's
// services/api.js interceptor shape (refresh call + `_retry` guard + queueing
// concurrent requests while the refresh is in flight), adapted to this app's
// bearer-token-in-header style instead of fleet-hub's cookie-only session:
// the refresh endpoint is expected to rely on an httpOnly refresh cookie set
// at login time and to hand back a fresh { token, user } pair, so no
// refreshToken needs to live in the Zustand store.
let isRefreshing = false;
let failedQueue: Array<{ resolve: (value: unknown) => void; reject: (reason?: unknown) => void }> = [];

const processQueue = (error: unknown, token: string | null = null) => {
  failedQueue.forEach((prom) => {
    if (error) prom.reject(error);
    else prom.resolve(token);
  });
  failedQueue = [];
};

api.interceptors.response.use(
  (r) => r,
  async (err) => {
    const originalRequest = err.config;
    const url = originalRequest?.url || "";
    const isAuthEndpoint = url.startsWith("/v1/auth/login") || url.startsWith("/v1/auth/refresh");

    if (err.response?.status === 401 && !originalRequest?._retry && !isAuthEndpoint) {
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        }).then((token) => {
          originalRequest.headers.Authorization = `Bearer ${token}`;
          return api(originalRequest);
        });
      }

      originalRequest._retry = true;
      isRefreshing = true;

      try {
        const { data } = await axios.post<{ token: string; user: User }>(
          `${api.defaults.baseURL}/v1/auth/refresh`,
          {},
          { withCredentials: true },
        );
        useAuthStore.getState().login(data.token, data.user);
        processQueue(null, data.token);
        originalRequest.headers.Authorization = `Bearer ${data.token}`;
        return api(originalRequest);
      } catch (refreshError) {
        processQueue(refreshError, null);
        useAuthStore.getState().logout();
        if (!window.location.pathname.startsWith("/login")) {
          window.location.href = "/login";
        }
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }

    return Promise.reject(err);
  }
);

// API Endpoints for Logistix — ported 1:1 from compliance-engine's
// `incokalkAPI.{warehouses,inventory,receivings}` so the REST contract keeps
// matching the backend being extracted in parallel. Everything outside the
// warehousing/réception domain is intentionally left out.
export const logistixAPI = {
  auth: {
    login: (email: string, password: string) =>
      api.post<{ token: string; user: User }>("/v1/auth/login", { email, password }),
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
};
