const API_URL = import.meta.env.VITE_PRAXIO_API_URL ?? 'http://localhost:8092';

export type HubStatusValue = 'OK' | 'NOT_CONFIGURED' | 'UNREACHABLE';

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

export interface CrossHubDashboard {
  complianceEngine: HubStatus<ComplianceEngineSummary>;
  fleetHub: HubStatus<FleetHubSummary>;
  logistix: HubStatus<LogistixSummary>;
}

export interface DashboardKeysStatus {
  complianceEngineConfigured: boolean;
  fleetHubConfigured: boolean;
  logistixConfigured: boolean;
}

export interface DashboardKeysUpdate {
  complianceEngineApiKey?: string;
  fleetHubApiKey?: string;
  logistixApiKey?: string;
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

export const praxioAPI = {
  getDashboard: (password: string) => request<CrossHubDashboard>('/api/dashboard', password),
  getKeysStatus: (password: string) => request<DashboardKeysStatus>('/api/dashboard/keys', password),
  updateKeys: (password: string, keys: DashboardKeysUpdate) =>
    request<DashboardKeysStatus>('/api/dashboard/keys', password, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(keys),
    }),
};
