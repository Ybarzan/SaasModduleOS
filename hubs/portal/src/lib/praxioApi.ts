const API_URL = import.meta.env.VITE_PRAXIO_API_URL ?? 'http://localhost:8092';

export type HubStatusValue = 'OK' | 'NOT_CONFIGURED' | 'UNREACHABLE' | 'NOT_APPLICABLE';

export interface HubStatus<T> {
  status: HubStatusValue;
  data: T | null;
}

export interface ComplianceEngineSummary {
  simulationsThisMonth: number;
  openDeclarations: number;
  overdueInvoicesCount: number;
  overdueInvoicesAmount: number;
}

export interface FleetHubSummary {
  activeShipments: number;
  availableTrucks: number;
  openCarrierBookings: number;
}

export interface LogistixSummary {
  activeWarehouses: number;
  openReceivingOrders: number;
  openDiscrepancies: number;
}

export interface FinanceSummary {
  overdueInvoicesCount: number;
  overdueInvoicesAmount: number;
}

export interface CrossHubDashboard {
  complianceEngine: HubStatus<ComplianceEngineSummary>;
  fleetHub: HubStatus<FleetHubSummary>;
  logistix: HubStatus<LogistixSummary>;
  finance: HubStatus<FinanceSummary>;
}

export interface ComplianceEngineBillingSummary {
  plan: string;
  status: string;
  billingCycle: string;
  amount: number;
  currency: string;
  nextBillingDate: string | null;
  cancelAtPeriodEnd: boolean;
}

export interface FleetHubBillingSummary {
  plan: string;
  status: string;
  trialEndsAt: string | null;
  amount: number | null;
  currency: string | null;
  nextBillingDate: string | null;
  stripeConfigured: boolean;
}

export interface CrossHubBilling {
  complianceEngine: HubStatus<ComplianceEngineBillingSummary>;
  fleetHub: HubStatus<FleetHubBillingSummary>;
  logistix: HubStatus<never>;
}

export interface DashboardKeysStatus {
  complianceEngineConfigured: boolean;
  fleetHubConfigured: boolean;
  logistixConfigured: boolean;
  financeConfigured: boolean;
}

export interface DashboardKeysUpdate {
  complianceEngineApiKey?: string;
  fleetHubApiKey?: string;
  logistixApiKey?: string;
  financeApiKey?: string;
}

export type HubId = 'complianceEngine' | 'fleetHub' | 'logistix' | 'finance';

export interface SsoCredentialsStatus {
  complianceEngineConfigured: boolean;
  complianceEngineEmail: string | null;
  fleetHubConfigured: boolean;
  fleetHubEmail: string | null;
  logistixConfigured: boolean;
  logistixEmail: string | null;
  financeConfigured: boolean;
  financeEmail: string | null;
}

export interface SsoCredentialsUpdate {
  complianceEngineEmail?: string;
  complianceEnginePassword?: string;
  fleetHubEmail?: string;
  fleetHubPassword?: string;
  logistixEmail?: string;
  logistixPassword?: string;
  financeEmail?: string;
  financePassword?: string;
}

/** Levée sur un 401 (mauvais mot de passe) -- distincte d'une erreur réseau générique. */
export class UnauthorizedError extends Error {
  constructor() {
    super('Mot de passe administrateur incorrect');
  }
}

async function request<T>(path: string, password: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${API_URL}${path}`, {
    ...init,
    headers: {
      ...init?.headers,
      Authorization: `Basic ${btoa(`admin:${password}`)}`,
    },
  });
  if (res.status === 401) {
    throw new UnauthorizedError();
  }
  if (!res.ok) {
    throw new Error(`Échec de la requête (${res.status})`);
  }
  return res.json() as Promise<T>;
}

export interface SsoLaunchResult {
  redirectUrl: string;
}

export const praxioAPI = {
  getDashboard: (password: string) => request<CrossHubDashboard>('/api/dashboard', password),
  getBilling: (password: string) => request<CrossHubBilling>('/api/billing', password),
  getKeysStatus: (password: string) => request<DashboardKeysStatus>('/api/dashboard/keys', password),
  updateKeys: (password: string, keys: DashboardKeysUpdate) =>
    request<DashboardKeysStatus>('/api/dashboard/keys', password, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(keys),
    }),

  getSsoStatus: (password: string) => request<SsoCredentialsStatus>('/api/sso/credentials', password),
  updateSsoCredentials: (password: string, credentials: SsoCredentialsUpdate) =>
    request<SsoCredentialsStatus>('/api/sso/credentials', password, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(credentials),
    }),
  launchSso: (password: string, hub: HubId) =>
    request<SsoLaunchResult>(`/api/sso/launch/${hub}`, password, { method: 'POST' }),
};
