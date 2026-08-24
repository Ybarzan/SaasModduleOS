import axios from "axios";
import { useAuthStore } from "../stores/auth";
import { useClientAuthStore } from "../stores/clientAuth";
import type { QuoteRequest } from "../types";

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || "/api",
});

let isRefreshing = false;
let failedQueue: Array<{ resolve: (value: unknown) => void; reject: (reason?: unknown) => void }> = [];

const processQueue = (error: unknown, token: string | null = null) => {
  failedQueue.forEach((prom) => {
    if (error) prom.reject(error);
    else prom.resolve(token);
  });
  failedQueue = [];
};

// Inject token on every request (prefer client token for /v1/client/* routes)
api.interceptors.request.use((config) => {
  try {
    const url = config.url || "";
    if (url.startsWith("/v1/client")) {
      const clientToken = useClientAuthStore.getState().token;
      if (clientToken) {
        config.headers.Authorization = `Bearer ${clientToken}`;
        return config;
      }
    }
    const token = useAuthStore.getState().token;
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
  } catch {
    // token not available yet
  }
  return config;
});

// Handle 401 with refresh token retry + redirect to login on final failure
api.interceptors.response.use(
  (r) => r,
  async (err) => {
    const originalRequest = err.config;

    if ((err.response?.status === 401 || err.response?.status === 403) && !originalRequest._retry) {
      const refreshToken = useAuthStore.getState().refreshToken;
      const url = originalRequest?.url || "";

      if (url.startsWith("/v1/client")) {
        useClientAuthStore.getState().logout();
        window.location.href = "/client/login";
        return Promise.reject(err);
      }

      if (!refreshToken) {
        useAuthStore.getState().logout();
        if (!window.location.pathname.startsWith('/login') && !window.location.pathname.startsWith('/register')) {
          window.location.href = "/login";
        }
        return Promise.reject(err);
      }

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
        const { data } = await axios.post("/api/v1/auth/refresh", { refreshToken });
        const newToken = data.token;
        const newRefreshToken = data.refreshToken;
        useAuthStore.getState().setTokens(newToken, newRefreshToken);
        processQueue(null, newToken);
        originalRequest.headers.Authorization = `Bearer ${newToken}`;
        return api(originalRequest);
      } catch (refreshError) {
        processQueue(refreshError, null);
        useAuthStore.getState().logout();
        if (!window.location.pathname.startsWith('/login') && !window.location.pathname.startsWith('/register')) {
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

// API Endpoints for IncoKalk
export const incokalkAPI = {
  // Auth
  auth: {
    login: (email: string, password: string) =>
      api.post("/v1/auth/login", { email, password }),
    register: (data: { email: string; password: string; fullName: string; company: string; referralCode?: string }) =>
      api.post("/v1/auth/register", data),
    logout: () => api.post("/v1/auth/logout"),
    me: () => api.get("/v1/auth/me"),
    forgotPassword: (email: string) =>
      api.post("/v1/auth/forgot-password", { email }),
    resetPassword: (token: string, newPassword: string) =>
      api.post("/v1/auth/reset-password", { token, newPassword }),
    verifyEmail: (token: string) =>
      api.get(`/v1/auth/verify-email?token=${token}`),
  },

  // Company
  companies: {
    me: () => api.get("/v1/companies/me"),
  },

  // Incoterms
  incoterms: {
    getAll: () => api.get("/incoterms"),
    getByCode: (code: string) => api.get(`/incoterms/${code}`),
  },

  // Simulations
  simulation: {
    calculate: (data: unknown) =>
      api.post("/v1/simulate", data),
    getHistory: (page?: number, size?: number) => {
      const params: Record<string, number> = {};
      if (page !== undefined) params.page = page;
      if (size !== undefined) params.size = size;
      return api.get("/v1/simulate/simulations", { params });
    },
    getSimulation: (id: string) =>
      api.get(`/v1/simulate/simulations/${id}`),
    delete: (id: string) =>
      api.delete(`/v1/simulate/simulations/${id}`),
  },

  // Logistics
  logistics: {
    calculatePackaging: (data: unknown) =>
      api.post("/v1/logistics/packaging", data),
    calculateTrucking: (data: unknown) =>
      api.post("/v1/logistics/trucking", data),
    calculateCustomsDuty: (data: unknown) =>
      api.post("/v1/logistics/customs-duty", data),
    calculateInsurance: (data: unknown) =>
      api.post("/v1/logistics/insurance", data),
    optimizeRoute: (data: unknown) =>
      api.post("/v1/logistics/route-optimization", data),
  },

  // Insurance cargo (P4) - devis et polices
  insurance: {
    listQuotes: () => api.get('/v1/insurance/quotes'),
    saveQuote: (data: {
      goodsValue: number;
      weightKg: number;
      transportMode?: string;
      originCountry?: string;
      destinationCountry?: string;
      goodsCategory?: string;
      currency?: string;
    }) => api.post('/v1/insurance/quotes', data),
    activatePolicy: (id: string) => api.post(`/v1/insurance/quotes/${id}/policy`),
  },

  // Fintech - intégrations bancaires (Qonto, Spendesk)
  fintech: {
    listConnections: () => api.get('/v1/fintech/connections'),
    createConnection: (data: {
      provider: string;
      name: string;
      apiKey?: string;
      apiSecret?: string;
    }) => api.post('/v1/fintech/connections', data),
    updateConnection: (id: string, data: {
      name?: string;
      apiKey?: string;
      apiSecret?: string;
      active?: boolean;
    }) => api.put(`/v1/fintech/connections/${id}`, data),
    deleteConnection: (id: string) => api.delete(`/v1/fintech/connections/${id}`),
    testConnection: (id: string) => api.post(`/v1/fintech/connections/${id}/test`),
    syncConnection: (id: string) => api.post(`/v1/fintech/connections/${id}/sync`),
    fetchData: (id: string) => api.get(`/v1/fintech/connections/${id}/data`),
  },

  // Groupages - consolidation multi-exportateurs / co-loading
  groupages: {
    list: () => api.get('/v1/groupages'),
    create: (data: {
      name: string;
      transportMode?: string;
      carrierName?: string;
      origin?: string;
      destination?: string;
      capacityWeightKg?: number;
      capacityVolumeM3?: number;
      plannedDeparture?: string;
      plannedArrival?: string;
    }) => api.post('/v1/groupages', data),
    update: (id: string, data: {
      name?: string;
      transportMode?: string;
      carrierName?: string;
      origin?: string;
      destination?: string;
      capacityWeightKg?: number;
      capacityVolumeM3?: number;
      plannedDeparture?: string;
      plannedArrival?: string;
    }) => api.put(`/v1/groupages/${id}`, data),
    delete: (id: string) => api.delete(`/v1/groupages/${id}`),
    detail: (id: string) => api.get(`/v1/groupages/${id}`),
    updateStatus: (id: string, status: string) =>
      api.post(`/v1/groupages/${id}/status`, { status }),
    addMember: (id: string, data: {
      shipmentOrderId?: string;
      externalCompany?: string;
      reference?: string;
      weightKg: number;
      volumeM3: number;
    }) => api.post(`/v1/groupages/${id}/members`, data),
    removeMember: (id: string, memberId: string) =>
      api.delete(`/v1/groupages/${id}/members/${memberId}`),
    stats: () => api.get('/v1/groupages/stats'),
  },

  // Currency
  currency: {
    list: () => api.get("/v1/currencies"),
    getRate: (from: string, to: string) =>
      api.get(`/v1/currencies/rate?from=${from}&to=${to}`),
    getRates: (base: string = 'EUR') =>
      api.get(`/v1/currencies/rates?base=${base}`),
    convert: (amount: number, from: string, to: string) =>
      api.get(`/v1/currencies/convert?amount=${amount}&from=${from}&to=${to}`),
  },

  // User
  user: {
    getProfile: () =>
      api.get("/v1/auth/me"),
    updateProfile: (data: unknown) =>
      api.put("/v1/auth/me", data),
  },

  // Carriers
  carriers: {
    getAll: () => api.get('/v1/carriers'),
    getPage: (page: number, size: number) => api.get(`/v1/carriers?page=${page}&size=${size}`),
    create: (data: unknown) => api.post('/v1/carriers', data),
    update: (id: string, data: unknown) => api.put(`/v1/carriers/${id}`, data),
    delete: (id: string) => api.delete(`/v1/carriers/${id}`),
    toggle: (id: string) => api.patch(`/v1/carriers/${id}/toggle`),
  },

  // Carrier Bookings (P2)
  carrierBookings: {
    list: (page?: number, size?: number) => {
      const params = page !== undefined ? `?page=${page}&size=${size}` : '';
      return api.get(`/v1/carrier-bookings${params}`);
    },
    get: (id: string) => api.get(`/v1/carrier-bookings/${id}`),
    create: (data: unknown) => api.post('/v1/carrier-bookings', data),
    submit: (id: string) => api.post(`/v1/carrier-bookings/${id}/submit`),
    cancel: (id: string) => api.post(`/v1/carrier-bookings/${id}/cancel`),
    listByShipment: (shipmentId: string) => api.get(`/v1/carrier-bookings/shipment/${shipmentId}`),
    listByCarrier: (carrierId: string) => api.get(`/v1/carrier-bookings/carrier/${carrierId}`),
    stats: () => api.get('/v1/carrier-bookings/stats'),
  },

  // Shipping Rates
  shippingRates: {
    getAll: () => api.get('/v1/shipping-rates'),
    getPage: (page: number, size: number) => api.get(`/v1/shipping-rates?page=${page}&size=${size}`),
    getByCarrier: (carrierId: string) => api.get(`/v1/shipping-rates/carrier/${carrierId}`),
    create: (data: unknown) => api.post('/v1/shipping-rates', data),
    update: (id: string, data: unknown) => api.put(`/v1/shipping-rates/${id}`, data),
    delete: (id: string) => api.delete(`/v1/shipping-rates/${id}`),
    toggle: (id: string) => api.patch(`/v1/shipping-rates/${id}/toggle`),
    compare: (originCountry: string, destinationCountry: string, transportMode: string, weightKg?: number) =>
      api.get(`/v1/shipping-rates/compare?originCountry=${originCountry}&destinationCountry=${destinationCountry}&transportMode=${transportMode}${weightKg ? `&weightKg=${weightKg}` : ''}`),
  },

  // Quotes
  quotes: {
    get: (data: unknown) => api.post('/v1/quotes', data),
  },

   // Shipments
   shipments: {
     getAll: () => api.get('/v1/shipments'),
     getPage: (page: number, size: number) => api.get(`/v1/shipments?page=${page}&size=${size}`),
     create: (data: unknown) => api.post('/v1/shipments', data),
     get: (id: string) => api.get(`/v1/shipments/${id}`),
     updateStatus: (id: string, data: unknown) => api.patch(`/v1/shipments/${id}/status`, data),
     assignClient: (id: string, clientId: string | null) => api.patch(`/v1/shipments/${id}/client`, { clientId }),
     delete: (id: string) => api.delete(`/v1/shipments/${id}`),
     listItems: (id: string) => api.get(`/v1/shipments/${id}/items`),
     addItem: (id: string, data: unknown) => api.post(`/v1/shipments/${id}/items`, data),
     deleteItems: (id: string) => api.delete(`/v1/shipments/${id}/items`),
   },

  // Provider configs
  providers: {
    getAll: () => api.get('/v1/providers'),
    create: (data: unknown) => api.post('/v1/providers', data),
    delete: (id: string) => api.delete(`/v1/providers/${id}`),
    health: () => api.get('/v1/providers/health'),
    test: (id: string) => api.post(`/v1/providers/${id}/test`),
  },

  // Notifications
  notifications: {
    getAll: () => api.get('/v1/notifications'),
    getPage: (page: number, size: number) => api.get(`/v1/notifications?page=${page}&size=${size}`),
    unreadCount: () => api.get('/v1/notifications/unread-count'),
    markRead: (ids: string[]) => api.patch('/v1/notifications/read', { notificationIds: ids }),
    markAllRead: () => api.patch('/v1/notifications/read-all'),
    archive: (id: string) => api.patch(`/v1/notifications/${id}/archive`),
    delete: (id: string) => api.delete(`/v1/notifications/${id}`),
  },
  // Notification Rules
  notificationRules: {
    getAll: () => api.get('/v1/notification-rules'),
    getPage: (page: number, size: number) => api.get(`/v1/notification-rules?page=${page}&size=${size}`),
    create: (data: unknown) => api.post('/v1/notification-rules', data),
    update: (id: string, data: unknown) => api.put(`/v1/notification-rules/${id}`, data),
    delete: (id: string) => api.delete(`/v1/notification-rules/${id}`),
    test: (data: unknown) => api.post('/v1/notification-rules/test', data),
  },

  // Roles
  roles: {
    list: () => api.get('/v1/roles'),
    get: (id: string) => api.get(`/v1/roles/${id}`),
    create: (data: unknown) => api.post('/v1/roles', data),
    update: (id: string, data: unknown) => api.put(`/v1/roles/${id}`, data),
    delete: (id: string) => api.delete(`/v1/roles/${id}`),
  },

  // Team
  team: {
    list: () => api.get('/v1/team'),
    invite: (data: unknown) => api.post('/v1/team', data),
    update: (id: string, data: unknown) => api.put(`/v1/team/${id}`, data),
    remove: (id: string) => api.delete(`/v1/team/${id}`),
    stats: () => api.get('/v1/team/stats'),
  },

  // ERP
  erp: {
    getAll: () => api.get('/v1/erp'),
    create: (data: unknown) => api.post('/v1/erp', data),
    update: (id: string, data: unknown) => api.put(`/v1/erp/${id}`, data),
    delete: (id: string) => api.delete(`/v1/erp/${id}`),
    test: (id: string) => api.post(`/v1/erp/${id}/test`),
    sync: (id: string, data: unknown) => api.post(`/v1/erp/${id}/sync`, data),
    syncLogs: () => api.get('/v1/erp/sync-logs'),
    health: () => api.get('/v1/erp/health'),
    products: (id: string) => api.get(`/v1/erp/${id}/products`),
    orders: (id: string) => api.get(`/v1/erp/${id}/orders`),
    contacts: (id: string) => api.get(`/v1/erp/${id}/contacts`),
  },

  // Fleet Hub (flotte propre du client — docs/07-integration-fleet-hub.md)
  fleetHub: {
    getAll: () => api.get('/v1/fleethub'),
    create: (data: unknown) => api.post('/v1/fleethub', data),
    update: (id: string, data: unknown) => api.put(`/v1/fleethub/${id}`, data),
    delete: (id: string) => api.delete(`/v1/fleethub/${id}`),
    test: (id: string) => api.post(`/v1/fleethub/${id}/test`),
    vehicles: (id: string) => api.get(`/v1/fleethub/${id}/vehicles`),
  },

  // Audit
  audit: {
    getAll: (page = 0, size = 20) => api.get(`/v1/audit?page=${page}&size=${size}`),
    getByAction: (action: string, page = 0, size = 20) => api.get(`/v1/audit/action/${action}?page=${page}&size=${size}`),
    getByEntity: (entityType: string, page = 0, size = 20) => api.get(`/v1/audit/entity/${entityType}?page=${page}&size=${size}`),
    getByUser: (userId: string, page = 0, size = 20) => api.get(`/v1/audit/user/${userId}?page=${page}&size=${size}`),
    getStats: () => api.get('/v1/audit/stats'),
  },

  // Currencies (duplicate — use `currency` above)
  currencies: {
    getAll: () => api.get('/v1/currencies'),
    getRate: (from: string, to: string) => api.get(`/v1/currencies/rate?from=${from}&to=${to}`),
    convert: (amount: number, from: string, to: string) => api.get(`/v1/currencies/convert?amount=${amount}&from=${from}&to=${to}`),
  },

  // Document Export
  export: {
    quotesPdf: (data: QuoteRequest) => api.post('/v1/documents/quotes/pdf', data, { responseType: 'blob' }),
    shippingLabelPdf: (id: string) => api.get(`/v1/documents/shipments/${id}/pdf`, { responseType: 'blob' }),
    cmrPdf: (id: string) => api.get(`/v1/documents/shipments/${id}/cmr`, { responseType: 'blob' }),
    dgdPdf: (id: string) => api.get(`/v1/documents/shipments/${id}/dgd`, { responseType: 'blob' }),
    certificateOfOriginPdf: (id: string) => api.get(`/v1/documents/shipments/${id}/certificate-of-origin`, { responseType: 'blob' }),
    csv: {
      shipments: () => api.get('/v1/export/shipments', { responseType: 'blob' }),
      carriers: () => api.get('/v1/export/carriers', { responseType: 'blob' }),
    },
  },

  // Tracking
  tracking: {
    getShipment: (id: string) => api.get(`/v1/tracking/shipments/${id}`),
    getPosition: (id: string) => api.get(`/v1/tracking/shipments/${id}/position`),
    lookup: (trackingNumber: string, mode: string) => api.post('/v1/tracking/lookup', { trackingNumber, mode }),
    sync: (id: string) => api.post(`/v1/tracking/shipments/${id}/sync`),
  },

  // Analytics
  analytics: {
    dashboard: (period: string) => api.get(`/v1/analytics/dashboard?period=${period}`),
    shipmentsOverTime: (period: string, granularity: string) =>
      api.get(`/v1/analytics/shipments-over-time?period=${period}&granularity=${granularity}`),
    shipmentsByStatus: () => api.get('/v1/analytics/shipments-by-status'),
    costByCarrier: () => api.get('/v1/analytics/cost-by-carrier'),
    costByMode: () => api.get('/v1/analytics/cost-by-mode'),
    topRoutes: (limit: number) => api.get(`/v1/analytics/top-routes?limit=${limit}`),
    incotermUsage: () => api.get('/v1/analytics/incoterm-usage'),
    weightDistribution: () => api.get('/v1/analytics/weight-distribution'),
    volumeDistribution: () => api.get('/v1/analytics/volume-distribution'),
    costTrends: (period: string = '90d', granularity: string = 'month') =>
      api.get(`/v1/analytics/cost-trends?period=${period}&granularity=${granularity}`),
    carrierPerformance: () => api.get('/v1/analytics/carrier-performance'),
  },

  // Client Portal (admin management)
  clients: {
    list: () => api.get('/v1/clients'),
    create: (data: { email: string; password: string; fullName: string; phone?: string }) =>
      api.post('/v1/clients', data),
    update: (id: string, data: { fullName?: string; phone?: string; active?: boolean }) =>
      api.put(`/v1/clients/${id}`, data),
    resetPassword: (id: string, newPassword: string) =>
      api.post(`/v1/clients/${id}/reset-password`, { newPassword }),
    delete: (id: string) => api.delete(`/v1/clients/${id}`),
    stats: () => api.get('/v1/clients/stats'),
  },

  // Shared Links (admin management)
  sharedLinks: {
    list: () => api.get('/v1/shared'),
    create: (data: { shipmentId: string; label?: string; expiresHours?: number }) =>
      api.post('/v1/shared', data),
    linksForShipment: (shipmentId: string) => api.get(`/v1/shared/shipment/${shipmentId}`),
    revoke: (id: string) => api.delete(`/v1/shared/${id}`),
    stats: () => api.get('/v1/shared/stats'),
  },

  // Client Portal (client-facing)
  clientAuth: {
    login: (email: string, password: string) =>
      api.post('/v1/client/auth/login', { email, password }),
    me: () => api.get('/v1/client/auth/me'),
  },

  clientPortal: {
    shipments: () => api.get('/v1/client/shipments'),
    shipmentDetail: (id: string) => api.get(`/v1/client/shipments/${id}`),
  },

  // Documents
  documents: {
    exportPdf: (type: string, shipmentId?: string) => {
      if (type === 'quote') return api.post('/v1/documents/quotes/pdf', {}, { responseType: 'blob' });
      if (shipmentId) return api.get(`/v1/documents/shipments/${shipmentId}/${type}`, { responseType: 'blob' });
      return api.get(`/v1/documents/shipments/placeholder/${type}`, { responseType: 'blob' });
    },
  },

  // Tracking Map (proxy)
  trackingMap: {
    getFlights: (bbox: string) => api.get(`/v1/tracking-map/flights?lamin=${bbox.split(',')[0]}&lamax=${bbox.split(',')[1]}&lomin=${bbox.split(',')[2]}&lomax=${bbox.split(',')[3]}`),
    getLiveVessels: (bbox: string) => api.get(`/v1/tracking-map/vessels/live?latMin=${bbox.split(',')[0]}&latMax=${bbox.split(',')[1]}&lonMin=${bbox.split(',')[2]}&lonMax=${bbox.split(',')[3]}`),
    searchVessels: (query: string) => api.get(`/v1/tracking-map/vessels/search?query=${encodeURIComponent(query)}`),
    getVesselPosition: (mmsi: string) => api.get(`/v1/tracking-map/vessels/position/${mmsi}`),
  },

  // Shared tracking (public, no auth)
  sharedTracking: {
    access: (token: string) => api.get(`/v1/shared/access/${token}`),
  },

  // Billing
  // /actuator/health est le seul endpoint actuator public (voir SecurityConfig.PUBLIC_PATHS) --
  // pas de show-details activé côté backend, donc juste un statut global UP/DOWN, pas de
  // détail par composant (DB, stockage...).
  system: {
    health: () => api.get('/actuator/health'),
  },

  billing: {
    getPlans: () => api.get('/v1/billing/plans'),
    status: () => api.get('/v1/billing/status'),
    subscription: () => api.get('/v1/billing/subscription'),
    checkout: (data: { planId: string; billingCycle: string }) =>
      api.post('/v1/billing/checkout', data),
    portal: () => api.post('/v1/billing/portal'),
    invoices: () => api.get('/v1/billing/invoices'),
  },

  // Rate Optimization (P3)
  optimization: {
    analyzeRoutes: () => api.post('/v1/optimization/analyze-routes').then(r => r.data),
    predict: (params: { origin: string; destination: string; mode?: string; weight?: number; volume?: number }) =>
      api.post('/v1/optimization/predict', null, { params }).then(r => r.data),
    getRecommendations: () => api.get('/v1/optimization/recommendations').then(r => r.data),
    getLaneAnalysis: () => api.get('/v1/optimization/lane-analysis').then(r => r.data),
    findConsolidation: () => api.post('/v1/optimization/consolidation').then(r => r.data),
    getConsolidation: () => api.get('/v1/optimization/consolidation').then(r => r.data),
    getStats: () => api.get('/v1/optimization/stats').then(r => r.data),
    acceptOptimization: (id: string) => api.patch(`/v1/optimization/accept/${id}`).then(r => r.data),
    acceptConsolidation: (id: string) => api.patch(`/v1/optimization/consolidation/accept/${id}`).then(r => r.data),
  },

  // Import
  import: {
    carriers: (formData: FormData) => api.post('/v1/import/carriers', formData),
    preview: (formData: FormData) => api.post('/v1/import/preview', formData),
  },

  // Customs / Douanes
  customs: {
    getTariffInfo: (hsCode: string, origin: string, dest: string) =>
      api.get(`/v1/customs/tariff-info?hsCode=${hsCode}&origin=${origin}&dest=${dest}`),
    getDuty: (hsCode: string, origin: string, dest: string, goodsValue: number, freight = 0, insurance = 0) =>
      api.get(`/v1/customs/duty?hsCode=${hsCode}&origin=${origin}&dest=${dest}&goodsValue=${goodsValue}&freight=${freight}&insurance=${insurance}`),
    getVat: (origin: string, dest: string, goodsValue: number, freight = 0, insurance = 0, incoterm = 'FOB', b2b = true) =>
      api.get(`/v1/customs/vat?origin=${origin}&dest=${dest}&goodsValue=${goodsValue}&freight=${freight}&insurance=${insurance}&incoterm=${incoterm}&b2b=${b2b}`),
    getVatRates: () => api.get('/v1/customs/vat-rates'),
    getEUCountries: () => api.get('/v1/customs/eu-countries'),
    search: (keyword: string, dest: string) =>
      api.get(`/v1/customs/search?keyword=${encodeURIComponent(keyword)}&dest=${dest}`),
    getAgreements: (country?: string) =>
      api.get(`/v1/customs/agreements${country ? `?country=${country}` : ''}`),
  },

  // EORI
  eori: {
    list: () => api.get('/v1/eori'),
    getPage: (page: number, size: number) => api.get(`/v1/eori?page=${page}&size=${size}`),
    getDefault: () => api.get('/v1/eori/default'),
    create: (data: { eori: string; holderName: string; holderAddress?: string; holderCountry?: string; isDefault?: boolean }) =>
      api.post('/v1/eori', data),
    setDefault: (id: string) => api.put(`/v1/eori/${id}/default`),
    delete: (id: string) => api.delete(`/v1/eori/${id}`),
    validate: (eori: string) => api.post('/v1/eori/validate', { eori }),
  },

  // Trade Agreements
  tradeAgreements: {
    list: () => api.get('/v1/trade-agreements'),
    getByCode: (code: string) => api.get(`/v1/trade-agreements/${code}`),
    getByCountry: (countryCode: string) => api.get(`/v1/trade-agreements/by-country/${countryCode}`),
    getByChapter: (chapter: string) => api.get(`/v1/trade-agreements/by-chapter/${chapter}`),
  },

  // Preferential Regimes & Cumulation (P0)
  preferential: {
    verifyOrigin: (data: { hsCode: string; originCountry: string; destCountry: string; valueAdded: number; totalCost: number; manufacturingSteps?: string[] }) =>
      api.post('/v1/compliance/preferential/verify-origin', data),
    calculate: (data: { hsCode: string; originCountry: string; destCountry: string; goodsValue: number; valueAdded: number; totalCost: number }) =>
      api.post('/v1/compliance/preferential/calculate', data),
    getRates: (hsCode: string, destCountry: string, goodsValue = 100, valueAdded = 0, totalCost = 0) =>
      api.get(`/v1/compliance/preferential/rates?hsCode=${encodeURIComponent(hsCode)}&destCountry=${encodeURIComponent(destCountry)}&goodsValue=${goodsValue}&valueAdded=${valueAdded}&totalCost=${totalCost}`),
    verifyCumulation: (data: { hsCode: string; originCountry: string; destCountry: string; valueAdded: number; totalCost: number; materials: { hsCode: string; originCountry: string; value: number; isOriginating: boolean; description?: string }[] }) =>
      api.post('/v1/compliance/preferential/verify-cumulation', data),
    verifyOriginWithCumulation: (data: { hsCode: string; originCountry: string; destCountry: string; valueAdded: number; totalCost: number; manufacturingSteps?: string[]; materials: { hsCode: string; originCountry: string; value: number; isOriginating: boolean; description?: string }[] }) =>
      api.post('/v1/compliance/preferential/verify-origin-with-cumulation', data),
    listCumulationGroups: () => api.get('/v1/compliance/preferential/cumulation-groups'),
  },

  // Customs Declarations (P1)
  declarations: {
    dau: {
      list: () => api.get('/v1/customs-declarations'),
      getPage: (page: number, size: number) => api.get(`/v1/customs-declarations?page=${page}&size=${size}`),
      create: (data: unknown) => api.post('/v1/customs-declarations', data),
      updateStatus: (id: string, data: { status: string }) => api.put(`/v1/customs-declarations/${id}/status`, data),
      delete: (id: string) => api.delete(`/v1/customs-declarations/${id}`),
      stats: () => api.get('/v1/customs-declarations/stats'),
    },
    debp: {
      list: () => api.get('/v1/deb-declarations'),
      create: (data: unknown) => api.post('/v1/deb-declarations', data),
      updateStatus: (id: string, data: { status: string }) => api.put(`/v1/deb-declarations/${id}/status`, data),
      delete: (id: string) => api.delete(`/v1/deb-declarations/${id}`),
      getByPeriod: (period: string) => api.get(`/v1/deb-declarations/by-period/${period}`),
    },
    ics2: {
      list: () => api.get('/v1/ics2-declarations'),
      create: (data: unknown) => api.post('/v1/ics2-declarations', data),
      updateStatus: (id: string, data: { status: string }) => api.put(`/v1/ics2-declarations/${id}/status`, data),
      delete: (id: string) => api.delete(`/v1/ics2-declarations/${id}`),
      stats: () => api.get('/v1/ics2-declarations/stats'),
    },
    exportd: {
      list: () => api.get('/v1/export-declarations'),
      create: (data: unknown) => api.post('/v1/export-declarations', data),
      updateStatus: (id: string, data: { status: string }) => api.put(`/v1/export-declarations/${id}/status`, data),
      delete: (id: string) => api.delete(`/v1/export-declarations/${id}`),
      stats: () => api.get('/v1/export-declarations/stats'),
    },
  },

  // Denied Party Screening (P2)
  dps: {
    screen: (data: { name: string; countryCode?: string }) => api.post('/v1/dps/screen', data),
    history: () => api.get('/v1/dps/history'),
    getById: (id: string) => api.get(`/v1/dps/${id}`),
    stats: () => api.get('/v1/dps/stats'),
    sanctionedEntities: () => api.get('/v1/dps/sanctioned-entities'),
    alerts: () => api.get('/v1/dps/alerts'),
  },

  // Landed Cost Calculator (P2)
  landedCosts: {
    list: () => api.get('/v1/landed-costs'),
    getById: (id: string) => api.get(`/v1/landed-costs/${id}`),
    calculate: (data: unknown) => api.post('/v1/landed-costs/calculate', data),
    update: (id: string, data: unknown) => api.put(`/v1/landed-costs/${id}`, data),
    delete: (id: string) => api.delete(`/v1/landed-costs/${id}`),
    fromShipment: (shipmentId: string) => api.post(`/v1/landed-costs/from-shipment/${shipmentId}`),
    stats: () => api.get('/v1/landed-costs/stats'),
    whatIf: (scenarios: unknown[]) => api.post('/v1/landed-costs/what-if', scenarios),
    share: (id: string) => api.post(`/v1/landed-costs/${id}/share`),
    getPublic: (token: string) => api.get(`/v1/landed-costs/public/${token}`),
  },

  // HS Code Suggestions (P2)
  hsSuggestions: {
    suggest: (data: { productDescription: string }) => api.post('/v1/hs-suggestions/suggest', data),
    suggestFromImage: (file: File) => {
      const formData = new FormData();
      formData.append('file', file);
      return api.post('/v1/hs-suggestions/suggest-from-image', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
    },
    history: () => api.get('/v1/hs-suggestions/history'),
    confirm: (id: string, data: { selectedCode: string }) => api.put(`/v1/hs-suggestions/${id}/confirm`, data),
  },

  // Document Parser / OCR (P2)
  documentParser: {
    parseText: (data: { text: string; documentType: string }) => api.post('/v1/document-parser/parse/text', data),
    parsePdf: (file: File, documentType: string) => {
      const formData = new FormData();
      formData.append('file', file);
      formData.append('documentType', documentType);
      return api.post('/v1/document-parser/parse/pdf', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
    },
    history: () => api.get('/v1/document-parser/history'),
    getById: (id: string) => api.get(`/v1/document-parser/${id}`),
    getByType: (docType: string) => api.get(`/v1/document-parser/type/${docType}`),
    stats: () => api.get('/v1/document-parser/stats'),
  },

  // Carrier Invoices / AP Billing (P3)
  carrierInvoices: {
    list: () => api.get('/v1/carrier-invoices'),
    getPage: (page: number, size: number) => api.get(`/v1/carrier-invoices?page=${page}&size=${size}`),
    getById: (id: string) => api.get(`/v1/carrier-invoices/${id}`),
    create: (data: unknown) => api.post('/v1/carrier-invoices', data),
    updateStatus: (id: string, data: { status: string; reason?: string }) => api.put(`/v1/carrier-invoices/${id}/status`, data),
    reconcile: (id: string, data: { negotiatedRate: number; notes: string }) => api.put(`/v1/carrier-invoices/${id}/reconcile`, data),
    delete: (id: string) => api.delete(`/v1/carrier-invoices/${id}`),
    stats: () => api.get('/v1/carrier-invoices/stats'),
  },

  // ETA Predictions (P3)
  eta: {
    list: () => api.get('/v1/eta-predictions'),
    getById: (id: string) => api.get(`/v1/eta-predictions/${id}`),
    predict: (data: unknown) => api.post('/v1/eta-predictions/predict', data),
    updateActual: (id: string, data: { actualArrival: string }) => api.put(`/v1/eta-predictions/${id}/actual`, data),
    getByLane: (origin: string, destination: string) => api.get(`/v1/eta-predictions/by-lane?origin=${origin}&destination=${destination}`),
    stats: () => api.get('/v1/eta-predictions/stats'),
  },

  // Quality Metrics (Six Sigma)
  quality: {
    metrics: () => api.get('/v1/quality/metrics'),
  },

  // Financial Reporting (P3)
  financials: {
    listShipments: () => api.get('/v1/financials/shipments'),
    getShipment: (id: string) => api.get(`/v1/financials/shipments/${id}`),
    createShipment: (data: unknown) => api.post('/v1/financials/shipments', data),
    updateShipment: (id: string, data: unknown) => api.put(`/v1/financials/shipments/${id}`, data),
    deleteShipment: (id: string) => api.delete(`/v1/financials/shipments/${id}`),
    dashboard: () => api.get('/v1/financials/dashboard'),
    byCarrier: () => api.get('/v1/financials/by-carrier'),
    byLane: () => api.get('/v1/financials/by-lane'),
    topLanes: (limit = 10) => api.get(`/v1/financials/top-lanes?limit=${limit}`),
    topCarriers: (limit = 10) => api.get(`/v1/financials/top-carriers?limit=${limit}`),
  },

  // Approval Workflows (P4)
  approvals: {
    listWorkflows: () => api.get('/v1/approvals/workflows'),
    createWorkflow: (data: unknown) => api.post('/v1/approvals/workflows', data),
    updateWorkflow: (id: string, data: unknown) => api.put(`/v1/approvals/workflows/${id}`, data),
    deleteWorkflow: (id: string) => api.delete(`/v1/approvals/workflows/${id}`),
    listRequests: () => api.get('/v1/approvals/requests'),
    myRequests: () => api.get('/v1/approvals/requests/my'),
    pendingApprovals: () => api.get('/v1/approvals/requests/pending'),
    createRequest: (data: unknown) => api.post('/v1/approvals/requests', data),
    approve: (id: string, data: { notes?: string }) => api.put(`/v1/approvals/requests/${id}/approve`, data),
    reject: (id: string, data: { notes?: string }) => api.put(`/v1/approvals/requests/${id}/reject`, data),
    cancel: (id: string) => api.put(`/v1/approvals/requests/${id}/cancel`),
    getHistory: (id: string) => api.get(`/v1/approvals/requests/${id}/history`),
    stats: () => api.get('/v1/approvals/stats'),
  },

  // Suggestions d'action du moteur d'orchestration (Phase 3, plan PRO min.)
  orchestrationSuggestions: {
    list: (status?: string) =>
      api.get('/v1/orchestration-suggestions', { params: status ? { status } : undefined }),
    get: (id: string) => api.get(`/v1/orchestration-suggestions/${id}`),
    approve: (id: string, note?: string) =>
      api.post(`/v1/orchestration-suggestions/${id}/approve`, { note }),
    reject: (id: string, note?: string) =>
      api.post(`/v1/orchestration-suggestions/${id}/reject`, { note }),
  },

  // Carbon Offsets (P4)
  carbonOffsets: {
    list: () => api.get('/v1/carbon-offsets'),
    getById: (id: string) => api.get(`/v1/carbon-offsets/${id}`),
    create: (data: unknown) => api.post('/v1/carbon-offsets', data),
    update: (id: string, data: unknown) => api.put(`/v1/carbon-offsets/${id}`, data),
    delete: (id: string) => api.delete(`/v1/carbon-offsets/${id}`),
    stats: () => api.get('/v1/carbon-offsets/stats'),
    dashboard: () => api.get('/v1/carbon-offsets/dashboard'),
  },

  // Payment Terms (P4)
  paymentTerms: {
    list: () => api.get('/v1/payment-terms'),
    getDefault: () => api.get('/v1/payment-terms/default'),
    create: (data: unknown) => api.post('/v1/payment-terms', data),
    update: (id: string, data: unknown) => api.put(`/v1/payment-terms/${id}`, data),
    delete: (id: string) => api.delete(`/v1/payment-terms/${id}`),
    seed: () => api.post('/v1/payment-terms/seed'),
  },

  // E-Commerce Integrations (P3)
  ecommerce: {
    list: () => api.get('/v1/ecommerce/integrations'),
    create: (data: unknown) => api.post('/v1/ecommerce/integrations', data),
    update: (id: string, data: unknown) => api.put(`/v1/ecommerce/integrations/${id}`, data),
    remove: (id: string) => api.delete(`/v1/ecommerce/integrations/${id}`),
    sync: (id: string) => api.post(`/v1/ecommerce/integrations/${id}/sync`),
    orders: (id: string) => api.get(`/v1/ecommerce/integrations/${id}/orders`),
    syncLog: () => api.get('/v1/ecommerce/sync-log'),
  },

  // Supply Chain Finance (P4)
  finance: {
    request: (data: { invoiceId: string; amount: number }) => api.post('/v1/finance/request', data),
    approve: (id: string) => api.post(`/v1/finance/${id}/approve`),
    fund: (id: string) => api.post(`/v1/finance/${id}/fund`),
    repay: (id: string) => api.post(`/v1/finance/${id}/repay`),
    history: () => api.get('/v1/finance/history'),
    stats: () => api.get('/v1/finance/stats'),
    earlyDiscount: (invoiceId: string, amount: number) => api.get(`/v1/finance/early-payment-discount?invoiceId=${invoiceId}&amount=${amount}`),
  },

  // Multi-Branch (P4)
  branches: {
    list: () => api.get('/v1/branches'),
    add: (data: { parentCompanyId: string; branchCompanyId: string; branchName: string }) => api.post('/v1/branches/add', data),
    remove: (id: string) => api.delete(`/v1/branches/${id}`),
    parent: () => api.get('/v1/branches/parent'),
    consolidatedReport: () => api.get('/v1/branches/consolidated-report'),
    transfers: () => api.get('/v1/branches/transfers'),
    createTransfer: (data: unknown) => api.post('/v1/branches/transfers', data),
  },

  // CSRD Reporting (P4)
  csrd: {
    report: () => api.get('/v1/carbon-offsets/csrd-report'),
  },

  // IncoKalk Academy (P4)
  academy: {
    modules: () => api.get('/v1/academy/modules'),
    module: (id: string) => api.get(`/v1/academy/modules/${id}`),
    enroll: (id: string) => api.post(`/v1/academy/modules/${id}/enroll`),
    updateProgress: (id: string, data: { progress: number }) => api.put(`/v1/academy/enrollments/${id}/progress`, data),
    submitQuiz: (id: string, data: { answers: Record<string, string> }) => api.post(`/v1/academy/modules/${id}/quiz`, data),
    certificate: (id: string) => api.get(`/v1/academy/enrollments/${id}/certificate`),
    dashboard: () => api.get('/v1/academy/dashboard'),
    createModule: (data: unknown) => api.post('/v1/academy/modules', data),
    updateModule: (id: string, data: unknown) => api.put(`/v1/academy/modules/${id}`, data),
  },

  // Branding / White-label Portal (P3)
  branding: {
    get: () => api.get('/v1/branding'),
    update: (data: unknown) => api.put('/v1/branding', data),
    uploadLogo: (file: File) => {
      const fd = new FormData();
      fd.append('file', file);
      return api.post('/v1/branding/logo', fd, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
    },
    preview: (data: unknown) => api.post('/v1/branding/preview', data),
  },

  // Email Intake (P3)
  emailIntake: {
    list: () => api.get('/v1/email-intake'),
    create: (data: unknown) => api.post('/v1/email-intake', data),
    update: (id: string, data: unknown) => api.put(`/v1/email-intake/${id}`, data),
    remove: (id: string) => api.delete(`/v1/email-intake/${id}`),
    test: (id: string) => api.post(`/v1/email-intake/${id}/test`),
    logs: () => api.get('/v1/email-intake/logs'),
  },

  // Client Invoices / AR (P4)
  clientInvoices: {
    list: () => api.get('/v1/client-invoices'),
    getById: (id: string) => api.get(`/v1/client-invoices/${id}`),
    create: (data: unknown) => api.post('/v1/client-invoices', data),
    update: (id: string, data: unknown) => api.put(`/v1/client-invoices/${id}`, data),
    updateStatus: (id: string, data: { status: string }) => api.put(`/v1/client-invoices/${id}/status`, data),
    recordPayment: (id: string, data: { amount: number; reference: string }) => api.post(`/v1/client-invoices/${id}/payment`, data),
    delete: (id: string) => api.delete(`/v1/client-invoices/${id}`),
    stats: () => api.get('/v1/client-invoices/stats'),
    overdue: () => api.get('/v1/client-invoices/overdue'),
    dashboard: () => api.get('/v1/client-invoices/dashboard'),
  },

  // Warehouses / Réception (P1)
  warehouses: {
    list: () => api.get('/v1/warehouses'),
    get: (id: string) => api.get(`/v1/warehouses/${id}`),
    create: (data: unknown) => api.post('/v1/warehouses', data),
    update: (id: string, data: unknown) => api.put(`/v1/warehouses/${id}`, data),
    delete: (id: string) => api.delete(`/v1/warehouses/${id}`),
  },

  // Inventaire / Stock (P1)
  inventory: {
    items: {
      list: (q?: string) => api.get('/v1/inventory/items', { params: q ? { q } : {} }),
      get: (id: string) => api.get(`/v1/inventory/items/${id}`),
      create: (data: unknown) => api.post('/v1/inventory/items', data),
      update: (id: string, data: unknown) => api.put(`/v1/inventory/items/${id}`, data),
      delete: (id: string) => api.delete(`/v1/inventory/items/${id}`),
    },
    resolve: (barcode: string) => api.get('/v1/inventory/resolve', { params: { barcode } }),
    barcodes: {
      list: (itemId: string) => api.get(`/v1/inventory/items/${itemId}/barcodes`),
      add: (itemId: string, data: { barcode: string; type?: string }) => api.post(`/v1/inventory/items/${itemId}/barcodes`, data),
      remove: (itemId: string, barcodeId: string) => api.delete(`/v1/inventory/items/${itemId}/barcodes/${barcodeId}`),
    },
    balances: (warehouseId?: string) => api.get('/v1/inventory/balances', { params: warehouseId ? { warehouseId } : {} }),
    movements: (itemId: string) => api.get('/v1/inventory/movements', { params: { itemId } }),
    adjust: (data: { warehouseId: string; itemId: string; quantity: number; note?: string }) =>
      api.post('/v1/inventory/adjustments', data),
  },

  // Réception marchandises / Scan (P1)
  receivings: {
    list: (params?: { status?: string; warehouseId?: string; shipmentId?: string }) =>
      api.get('/v1/receivings', { params }),
    get: (id: string) => api.get(`/v1/receivings/${id}`),
    create: (data: unknown) => api.post('/v1/receivings', data),
    addLine: (id: string, data: { itemId: string; quantityExpected: number; unit?: string }) =>
      api.post(`/v1/receivings/${id}/lines`, data),
    scan: (id: string, data: unknown) => api.post(`/v1/receivings/${id}/scan`, data),
    damage: (id: string, data: { itemId: string; quantity: number; notes?: string }) =>
      api.post(`/v1/receivings/${id}/damage`, data),
    complete: (id: string) => api.post(`/v1/receivings/${id}/complete`),
    cancel: (id: string) => api.post(`/v1/receivings/${id}/cancel`),
    discrepancies: () => api.get('/v1/receivings/discrepancies'),
    resolveDiscrepancy: (id: string, notes?: string) =>
      api.post(`/v1/receivings/discrepancies/${id}/resolve`, { notes }),
  },
};

// Télécharge un fichier depuis une route protégée par JWT (un simple <a href> ne peut
// pas transmettre le header Authorization, donc on récupère le contenu en blob via axios
// puis on déclenche le téléchargement navigateur nous-mêmes).
export async function downloadAuthedFile(url: string, filename: string) {
  const res = await api.get(url, { responseType: "blob" });
  const blobUrl = window.URL.createObjectURL(new Blob([res.data]));
  const a = document.createElement("a");
  a.href = blobUrl;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  window.URL.revokeObjectURL(blobUrl);
}
