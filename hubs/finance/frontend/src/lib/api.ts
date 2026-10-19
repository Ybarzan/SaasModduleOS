import axios from "axios";
import { useAuthStore, type UserRole } from "../stores/auth";

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? "http://localhost:8093",
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

// The Finance backend issues a single long-lived JWT at login and exposes no
// /v1/auth/refresh endpoint — unlike fleet-hub, there is nothing to retry a
// 401 against. On a 401 outside the auth endpoints themselves, just drop the
// stale session and send the user back to /login.
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

// API Endpoints for Finance & Trésorerie — ported 1:1 from compliance-engine's
// `incokalkAPI.{clientInvoices,paymentTerms,finance,financials,fintech,
// carbonOffsets,csrd}` so the REST contract keeps matching the backend being
// extracted in parallel. Tout ce qui est hors de ce périmètre (devises,
// transport, douane...) est volontairement laissé de côté -- voir
// CurrencyService/CurrencyExchangeService, qui restent côté compliance-engine.
// La réponse de /v1/auth/login est à plat (token/email/displayName/role/
// companyId/companyName) -- jamais un objet `user` imbriqué, voir
// com.finance.dto.AuthResponse côté backend.
export interface AuthLoginResponse {
  token: string;
  email: string;
  displayName: string;
  role: UserRole;
  companyId: string;
  companyName: string;
}

export const financeAPI = {
  auth: {
    login: (email: string, password: string) =>
      api.post<AuthLoginResponse>("/v1/auth/login", { email, password }),
  },

  // Facturation clients / AR
  clientInvoices: {
    list: () => api.get("/v1/client-invoices"),
    getById: (id: string) => api.get(`/v1/client-invoices/${id}`),
    create: (data: unknown) => api.post("/v1/client-invoices", data),
    update: (id: string, data: unknown) => api.put(`/v1/client-invoices/${id}`, data),
    updateStatus: (id: string, data: { status: string }) => api.put(`/v1/client-invoices/${id}/status`, data),
    recordPayment: (id: string, data: { amount: number; reference: string }) =>
      api.post(`/v1/client-invoices/${id}/payment`, data),
    delete: (id: string) => api.delete(`/v1/client-invoices/${id}`),
    stats: () => api.get("/v1/client-invoices/stats"),
    overdue: () => api.get("/v1/client-invoices/overdue"),
    dashboard: () => api.get("/v1/client-invoices/dashboard"),
  },

  // Conditions de paiement
  paymentTerms: {
    list: () => api.get("/v1/payment-terms"),
    getDefault: () => api.get("/v1/payment-terms/default"),
    create: (data: unknown) => api.post("/v1/payment-terms", data),
    update: (id: string, data: unknown) => api.put(`/v1/payment-terms/${id}`, data),
    delete: (id: string) => api.delete(`/v1/payment-terms/${id}`),
    seed: () => api.post("/v1/payment-terms/seed"),
  },

  // Supply Chain Finance (affacturage)
  finance: {
    request: (data: { invoiceId: string; amount: number }) => api.post("/v1/finance/request", data),
    approve: (id: string) => api.post(`/v1/finance/${id}/approve`),
    fund: (id: string) => api.post(`/v1/finance/${id}/fund`),
    repay: (id: string) => api.post(`/v1/finance/${id}/repay`),
    history: () => api.get("/v1/finance/history"),
    stats: () => api.get("/v1/finance/stats"),
    earlyDiscount: (invoiceId: string, amount: number) =>
      api.get(`/v1/finance/early-payment-discount?invoiceId=${invoiceId}&amount=${amount}`),
  },

  // Reporting financier et P&L
  financials: {
    listShipments: () => api.get("/v1/financials/shipments"),
    getShipment: (id: string) => api.get(`/v1/financials/shipments/${id}`),
    createShipment: (data: unknown) => api.post("/v1/financials/shipments", data),
    updateShipment: (id: string, data: unknown) => api.put(`/v1/financials/shipments/${id}`, data),
    deleteShipment: (id: string) => api.delete(`/v1/financials/shipments/${id}`),
    dashboard: () => api.get("/v1/financials/dashboard"),
    byCarrier: () => api.get("/v1/financials/by-carrier"),
    byLane: () => api.get("/v1/financials/by-lane"),
    topLanes: (limit = 10) => api.get(`/v1/financials/top-lanes?limit=${limit}`),
    topCarriers: (limit = 10) => api.get(`/v1/financials/top-carriers?limit=${limit}`),
  },

  // Fintech - intégrations bancaires (Qonto, Spendesk)
  fintech: {
    listConnections: () => api.get("/v1/fintech/connections"),
    createConnection: (data: { provider: string; name: string; apiKey?: string; apiSecret?: string }) =>
      api.post("/v1/fintech/connections", data),
    updateConnection: (id: string, data: { name?: string; apiKey?: string; apiSecret?: string; active?: boolean }) =>
      api.put(`/v1/fintech/connections/${id}`, data),
    deleteConnection: (id: string) => api.delete(`/v1/fintech/connections/${id}`),
    testConnection: (id: string) => api.post(`/v1/fintech/connections/${id}/test`),
    syncConnection: (id: string) => api.post(`/v1/fintech/connections/${id}/sync`),
    fetchData: (id: string) => api.get(`/v1/fintech/connections/${id}/data`),
  },

  // Crédits carbone et offset
  carbonOffsets: {
    list: () => api.get("/v1/carbon-offsets"),
    getById: (id: string) => api.get(`/v1/carbon-offsets/${id}`),
    create: (data: unknown) => api.post("/v1/carbon-offsets", data),
    update: (id: string, data: unknown) => api.put(`/v1/carbon-offsets/${id}`, data),
    delete: (id: string) => api.delete(`/v1/carbon-offsets/${id}`),
    stats: () => api.get("/v1/carbon-offsets/stats"),
    dashboard: () => api.get("/v1/carbon-offsets/dashboard"),
  },

  // CSRD / EU Taxonomy
  csrd: {
    report: () => api.get("/v1/carbon-offsets/csrd-report"),
  },

  // Partage opt-in d'agrégats Finance vers Praxio (tableau de bord cross-hub)
  // et compliance-engine (FinanceIntegrationClient) -- même clé pour les deux.
  internalIntegration: {
    settings: () => api.get("/v1/internal-integration/settings"),
    enable: () => api.post("/v1/internal-integration/enable"),
    disable: () => api.post("/v1/internal-integration/disable"),
  },
};
