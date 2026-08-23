import { useQuery } from '@tanstack/react-query';
import { incokalkAPI } from '../lib/api';
import {
  Activity, Server, ShieldCheck, AlertTriangle, WifiOff, Loader2
} from 'lucide-react';
import type { ProviderHealth as ProviderHealthType } from '../types';

const healthColors: Record<string, string> = {
  HEALTHY: 'bg-success',
  DEGRADED: 'bg-warning',
  DOWN: 'bg-danger',
  UNKNOWN: 'bg-ink-soft',
};

const healthBgColors: Record<string, string> = {
  HEALTHY: 'bg-success/10 border-success/30 text-success',
  DEGRADED: 'bg-warning/10 border-warning/30 text-warning',
  DOWN: 'bg-danger/10 border-danger/30 text-danger',
  UNKNOWN: 'bg-surface-2 border-line text-ink',
};

const healthLabels: Record<string, string> = {
  HEALTHY: 'Sain',
  DEGRADED: 'Dégradé',
  DOWN: 'Hors ligne',
  UNKNOWN: 'Inconnu',
};

const ProviderHealth = () => {
  const { data, isLoading } = useQuery({
    queryKey: ['providers-health'],
    queryFn: async () => {
      const res = await incokalkAPI.providers.health();
      return (res.data as ProviderHealthType[]) || [];
    },
    refetchInterval: 30000,
  });

  const healthList: ProviderHealthType[] = data ?? [];
  const connected = healthList.length;
  const healthy = healthList.filter((h) => h.healthStatus === 'HEALTHY').length;
  const degraded = healthList.filter((h) => h.healthStatus === 'DEGRADED').length;
  const down = healthList.filter((h) => h.healthStatus === 'DOWN').length;

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-bg">
        <div className="text-center">
          <Loader2 className="h-12 w-12 animate-spin mx-auto text-accent mb-4" />
          <div className="text-xl text-ink-soft">Chargement de l'état de santé...</div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-bg py-12">
      <div className="container mx-auto px-4">
        <div className="mb-8">
          <h1 className="text-4xl font-bold text-ink mb-2">État de santé</h1>
          <p className="text-ink-soft">Vue d'ensemble de vos fournisseurs de tarifs</p>
        </div>

        {/* Stats Row */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-8">
          <div className="bg-surface rounded-lg shadow p-5 border-l-4 border-accent">
            <div className="flex items-center space-x-3">
              <Server className="h-8 w-8 text-accent" />
              <div>
                <div className="text-2xl font-bold text-ink">{connected}</div>
                <div className="text-sm text-ink-soft">Connecté(s)</div>
              </div>
            </div>
          </div>
          <div className="bg-surface rounded-lg shadow p-5 border-l-4 border-success">
            <div className="flex items-center space-x-3">
              <ShieldCheck className="h-8 w-8 text-success" />
              <div>
                <div className="text-2xl font-bold text-ink">{healthy}</div>
                <div className="text-sm text-ink-soft">Sain(s)</div>
              </div>
            </div>
          </div>
          <div className="bg-surface rounded-lg shadow p-5 border-l-4 border-warning">
            <div className="flex items-center space-x-3">
              <AlertTriangle className="h-8 w-8 text-warning" />
              <div>
                <div className="text-2xl font-bold text-ink">{degraded}</div>
                <div className="text-sm text-ink-soft">Dégradé(s)</div>
              </div>
            </div>
          </div>
          <div className="bg-surface rounded-lg shadow p-5 border-l-4 border-danger">
            <div className="flex items-center space-x-3">
              <WifiOff className="h-8 w-8 text-danger" />
              <div>
                <div className="text-2xl font-bold text-ink">{down}</div>
                <div className="text-sm text-ink-soft">Hors ligne</div>
              </div>
            </div>
          </div>
        </div>

        {/* Timeline / Status List */}
        <div className="bg-surface rounded-lg shadow-lg overflow-hidden">
          <div className="px-6 py-4 border-b border-line">
            <div className="flex items-center space-x-2">
              <Activity size={20} className="text-ink-soft" />
              <h2 className="text-lg font-bold text-ink">Derniers statuts</h2>
            </div>
          </div>
          {healthList.length > 0 ? (
            <div className="divide-y divide-line">
              {healthList.map((h) => (
                <div key={h.providerType} className="px-6 py-4 flex items-center justify-between">
                  <div className="flex items-center space-x-3">
                    <div className={`w-3 h-3 rounded-full ${healthColors[h.healthStatus]}`} />
                    <div>
                      <div className="font-medium text-ink">{h.providerName}</div>
                      <div className="text-xs text-ink-soft">{h.providerType}</div>
                    </div>
                  </div>
                  <div className="flex items-center space-x-4">
                    <span
                      className={`text-xs font-medium px-2.5 py-1 rounded-full border ${healthBgColors[h.healthStatus]}`}
                    >
                      {healthLabels[h.healthStatus]}
                    </span>
                    <div className="text-right text-sm text-ink-soft">
                      {h.lastHealthCheck ? (
                        <div>{new Date(h.lastHealthCheck).toLocaleString('fr-FR')}</div>
                      ) : (
                        <div>—</div>
                      )}
                      {h.consecutiveFailures > 0 && (
                        <div className="text-xs text-danger">
                          {h.consecutiveFailures} échec(s)
                        </div>
                      )}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="px-6 py-12 text-center text-ink-soft">
              Aucun fournisseur configuré
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default ProviderHealth;
