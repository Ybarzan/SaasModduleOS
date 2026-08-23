import axios from 'axios';
import { useAuthStore } from '../stores/auth';

const BASE_URL = import.meta.env.VITE_API_URL || '/api';

export const api = axios.create({ baseURL: BASE_URL });

let isRefreshing = false;
let failedQueue: Array<{ resolve: (value: unknown) => void; reject: (reason?: unknown) => void }> = [];

const processQueue = (error: unknown, token: string | null = null) => {
  failedQueue.forEach((p) => (error ? p.reject(error) : p.resolve(token)));
  failedQueue = [];
};

api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// Même contrat de refresh que le web (frontend/src/lib/api.ts) : rotation du
// refresh token, retries mis en file pendant le refresh en cours. Pas de
// redirection impérative ici -- on nettoie juste le store, et c'est le routeur
// (ProtectedRoute) qui redirige vers /login au prochain rendu.
api.interceptors.response.use(
  (r) => r,
  async (err) => {
    const originalRequest = err.config;

    if ((err.response?.status === 401 || err.response?.status === 403) && !originalRequest._retry) {
      const refreshToken = useAuthStore.getState().refreshToken;

      if (!refreshToken) {
        useAuthStore.getState().logout();
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
        const { data } = await axios.post(`${BASE_URL}/v1/auth/refresh`, { refreshToken });
        useAuthStore.getState().setTokens(data.token, data.refreshToken);
        processQueue(null, data.token);
        originalRequest.headers.Authorization = `Bearer ${data.token}`;
        return api(originalRequest);
      } catch (refreshError) {
        processQueue(refreshError, null);
        useAuthStore.getState().logout();
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }

    return Promise.reject(err);
  }
);

export interface LoginResponse {
  token: string;
  refreshToken: string;
  userId: string;
  email: string;
  fullName: string;
  role: string;
}

export const mobileApi = {
  auth: {
    login: (email: string, password: string) =>
      api.post<LoginResponse>('/v1/auth/login', { email, password }),
  },
  dashboard: () => api.get('/v1/mobile/dashboard'),
  quickQuote: (params: { origin: string; destination: string; weight: number; incoterm: string }) =>
    api.get('/v1/mobile/quick-quote', { params }),
  notifications: {
    list: () => api.get('/v1/mobile/notifications'),
    markRead: (id: string) => api.post(`/v1/mobile/notifications/${id}/read`),
    markAllRead: () => api.post('/v1/mobile/notifications/read-all'),
  },
  device: {
    register: (deviceToken: string, platform: string, appVersion?: string) =>
      api.post('/v1/mobile/device/register', { deviceToken, platform, appVersion }),
    unregister: (deviceToken: string) => api.post('/v1/mobile/device/unregister', { deviceToken }),
  },
  recentShipments: () => api.get('/v1/mobile/recent-shipments'),
  shipment: {
    get: (id: string) => api.get(`/v1/shipments/${id}`),
    updateStatus: (id: string, status: string) =>
      api.patch(`/v1/shipments/${id}/status`, { status }),
    items: (id: string) => api.get(`/v1/shipments/${id}/items`),
  },
  documentParser: {
    parseImage: (file: Blob, filename: string, documentType: string) => {
      const form = new FormData();
      form.append('file', file, filename);
      return api.post('/v1/document-parser/parse/image', form, {
        params: { documentType },
        headers: { 'Content-Type': 'multipart/form-data' },
      });
    },
  },
  approvals: {
    pending: () => api.get('/v1/approvals/requests/pending'),
    approve: (id: string, notes?: string) =>
      api.put(`/v1/approvals/requests/${id}/approve`, notes ? { notes } : {}),
    reject: (id: string, notes?: string) =>
      api.put(`/v1/approvals/requests/${id}/reject`, notes ? { notes } : {}),
  },
  eta: {
    list: () => api.get('/v1/eta-predictions'),
  },
  carrierBookings: {
    list: () => api.get('/v1/carrier-bookings'),
  },
  receivings: {
    list: () => api.get('/v1/receivings'),
    scan: (id: string, data: unknown) => api.post(`/v1/receivings/${id}/scan`, data),
  },
  notificationRules: {
    list: () => api.get('/v1/notification-rules'),
  },
  trackingMap: {
    getFlights: (bbox: string) => {
      const [lamin, lamax, lomin, lomax] = bbox.split(',');
      return api.get(`/v1/tracking-map/flights?lamin=${lamin}&lamax=${lamax}&lomin=${lomin}&lomax=${lomax}`);
    },
    searchVessels: (query: string) => api.get(`/v1/tracking-map/vessels/search?query=${encodeURIComponent(query)}`),
  },
  customs: {
    getDuty: (hsCode: string, origin: string, dest: string, goodsValue: number) =>
      api.get(`/v1/customs/duty?hsCode=${encodeURIComponent(hsCode)}&origin=${encodeURIComponent(origin)}&dest=${encodeURIComponent(dest)}&goodsValue=${goodsValue}`),
  },
  hsSuggestions: {
    suggest: (data: { productDescription: string }) => api.post('/v1/hs-suggestions/suggest', data),
  },
  tradeAgreements: {
    list: () => api.get('/v1/trade-agreements'),
  },
  eori: {
    list: () => api.get('/v1/eori'),
    validate: (eori: string) => api.post('/v1/eori/validate', { eori }),
  },
  dps: {
    screen: (data: { name: string; countryCode?: string }) => api.post('/v1/dps/screen', data),
  },
  quality: {
    metrics: () => api.get('/v1/quality/metrics'),
  },
  shippingRates: {
    compare: (originCountry: string, destinationCountry: string, transportMode: string, weightKg?: number) =>
      api.get(`/v1/shipping-rates/compare?originCountry=${encodeURIComponent(originCountry)}&destinationCountry=${encodeURIComponent(destinationCountry)}&transportMode=${transportMode}${weightKg ? `&weightKg=${weightKg}` : ''}`),
  },
  emailIntake: {
    logs: () => api.get('/v1/email-intake/logs'),
  },
  insurance: {
    listQuotes: () => api.get('/v1/insurance/quotes'),
  },
};
