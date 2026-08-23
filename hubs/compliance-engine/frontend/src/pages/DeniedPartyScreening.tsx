import type { AxiosError } from 'axios';
import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Search,
  ShieldCheck,
  AlertOctagon,
  AlertTriangle,
  ShieldAlert,
  Loader2,
  Activity,
  CheckCircle,
  XCircle,
  Lock,
  Database,
  Globe,
  FileText,
} from 'lucide-react';
import toast from 'react-hot-toast';
import { incokalkAPI } from '../lib/api';

interface DpsCheck {
  id: string;
  checkedName: string;
  checkType: string;
  result: 'CLEAR' | 'MATCH' | 'POSSIBLE_MATCH' | 'BLOCKED';
  matchedListName: string | null;
  matchedEntryId: string | null;
  matchedEntryDetails: string | null;
  riskLevel: 'NONE' | 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  countryCode: string | null;
  notes: string | null;
  checkedByUserId: string;
  createdAt: string;
}

interface DpsStats {
  total: number;
  CLEAR: number;
  MATCH: number;
  POSSIBLE_MATCH: number;
  BLOCKED: number;
}

interface SanctionedEntity {
  id: string;
  name: string;
  type: string;
  country: string;
  reason: string;
  programme: string;
  source: string;
}

const resultConfig: Record<string, { label: string; bg: string; text: string; icon: React.ElementType }> = {
  CLEAR: { label: 'CLAR', bg: 'bg-success/10 border-success/40', text: 'text-success', icon: ShieldCheck },
  MATCH: { label: 'CORRESPONDANCE TROUVÉE', bg: 'bg-danger/10 border-danger/40', text: 'text-danger', icon: AlertOctagon },
  POSSIBLE_MATCH: { label: 'CORRESPONDANCE POSSIBLE', bg: 'bg-warning/10 border-warning/40', text: 'text-warning', icon: AlertTriangle },
  BLOCKED: { label: 'BLOQUÉ', bg: 'bg-danger/10 border-danger/40', text: 'text-danger', icon: Lock },
};

const riskConfig: Record<string, { label: string; color: string }> = {
  NONE: { label: 'Aucun', color: 'bg-surface-2 text-ink-soft' },
  LOW: { label: 'Faible', color: 'bg-success/10 text-success' },
  MEDIUM: { label: 'Moyen', color: 'bg-warning/10 text-warning' },
  HIGH: { label: 'Élevé', color: 'bg-warning/10 text-warning' },
  CRITICAL: { label: 'Critique', color: 'bg-danger/10 text-danger' },
};

const DeniedPartyScreening = () => {
  const queryClient = useQueryClient();
  const [name, setName] = useState('');
  const [countryCode, setCountryCode] = useState('');
  const [lastResult, setLastResult] = useState<DpsCheck | null>(null);
  const [activeTab, setActiveTab] = useState<'history' | 'alerts' | 'entities'>('history');

  const { data: stats } = useQuery({
    queryKey: ['dps-stats'],
    queryFn: async () => {
      const res = await incokalkAPI.dps.stats();
      return res.data as DpsStats;
    },
  });

  const { data: historyData, isLoading: historyLoading } = useQuery({
    queryKey: ['dps-history'],
    queryFn: async () => {
      const res = await incokalkAPI.dps.history();
      return res.data as DpsCheck[];
    },
  });

  const { data: entitiesData, isLoading: entitiesLoading } = useQuery({
    queryKey: ['dps-entities'],
    queryFn: async () => {
      const res = await incokalkAPI.dps.sanctionedEntities();
      return res.data as SanctionedEntity[];
    },
  });

  const { data: alertsData, isLoading: alertsLoading } = useQuery({
    queryKey: ['dps-alerts'],
    queryFn: async () => {
      const res = await incokalkAPI.dps.alerts();
      return res.data as DpsCheck[];
    },
  });

  const screenMutation = useMutation({
    mutationFn: (data: { name: string; countryCode?: string }) =>
      incokalkAPI.dps.screen(data),
    onSuccess: (res) => {
      const check = res.data as DpsCheck;
      setLastResult(check);
      queryClient.invalidateQueries({ queryKey: ['dps-history'] });
      queryClient.invalidateQueries({ queryKey: ['dps-stats'] });
      queryClient.invalidateQueries({ queryKey: ['dps-alerts'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || 'Erreur lors du screening');
    },
  });

  const handleScreen = (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;
    screenMutation.mutate({
      name: name.trim(),
      countryCode: countryCode.trim().toUpperCase() || undefined,
    });
  };

  const history = Array.isArray(historyData) ? historyData : [];
  const entities = Array.isArray(entitiesData) ? entitiesData : [];

  return (
    <div className="max-w-6xl mx-auto px-4 py-8">
      {/* Header */}
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-ink">Screening de parties</h1>
        <p className="text-ink-soft mt-1">Vérification contre les listes de sanctions internationales</p>
      </div>

      {/* Screening form */}
      <div className="bg-surface rounded-xl border border-line p-6 mb-8">
        <h2 className="text-lg font-semibold text-ink mb-4">Vérifier un nom</h2>
        <form onSubmit={handleScreen} className="flex flex-col sm:flex-row gap-3">
          <div className="flex-1">
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="w-full px-4 py-3 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
              placeholder="Nom de l'entité ou de la personne"
              required
            />
          </div>
          <div className="w-full sm:w-28">
            <input
              type="text"
              value={countryCode}
              onChange={(e) => setCountryCode(e.target.value.slice(0, 2).toUpperCase())}
              className="w-full px-4 py-3 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
              placeholder="Pays"
              maxLength={2}
            />
          </div>
          <button
            type="submit"
            disabled={screenMutation.isPending || !name.trim()}
            className="flex items-center justify-center gap-2 bg-accent text-white px-6 py-3 rounded-lg font-medium hover:bg-accent-strong disabled:opacity-50 transition-colors"
          >
            {screenMutation.isPending ? (
              <Loader2 size={18} className="animate-spin" />
            ) : (
              <Search size={18} />
            )}
            Vérifier
          </button>
        </form>
      </div>

      {/* Result display */}
      {lastResult && (() => {
        const rc = resultConfig[lastResult.result] || resultConfig.CLEAR;
        const RcIcon = rc.icon as React.ElementType;
        const risk = riskConfig[lastResult.riskLevel] || riskConfig.NONE;

        return (
          <div className={`rounded-xl border-2 ${rc.bg} p-6 mb-8`}>
            <div className="flex items-center gap-4">
              <div className={`w-14 h-14 rounded-full flex items-center justify-center ${rc.text}`}>
                <RcIcon size={32} />
              </div>
              <div className="flex-1">
                <p className={`text-2xl font-bold ${rc.text}`}>{rc.label}</p>
                <p className="text-sm text-ink-soft mt-1">
                  Nom vérifié : <span className="font-medium">{lastResult.checkedName}</span>
                </p>
              </div>
              <span className={`px-3 py-1 rounded-full text-sm font-medium ${risk.color}`}>
                {risk.label}
              </span>
            </div>
            {lastResult.matchedListName && (
              <div className="mt-4 pt-4 border-t border-current/10">
                <p className="text-sm text-ink-soft">
                  Liste : <span className="font-medium">{lastResult.matchedListName}</span>
                </p>
                {lastResult.matchedEntryDetails && (
                  <pre className="mt-2 text-xs text-ink-soft bg-surface/50 rounded p-3 overflow-x-auto">
                    {JSON.stringify(lastResult.matchedEntryDetails, null, 2)}
                  </pre>
                )}
              </div>
            )}
          </div>
        );
      })()}

      {/* Stats cards */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-8">
        <div className="bg-surface rounded-xl border border-line p-5">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-lg bg-accent-soft flex items-center justify-center">
              <Activity size={20} className="text-accent" />
            </div>
            <div>
              <p className="text-sm text-ink-soft">Total vérifications</p>
              <p className="text-2xl font-bold text-ink">{stats?.total ?? '—'}</p>
            </div>
          </div>
        </div>
        <div className="bg-surface rounded-xl border border-line p-5">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-lg bg-success/10 flex items-center justify-center">
              <CheckCircle size={20} className="text-success" />
            </div>
            <div>
              <p className="text-sm text-ink-soft">Clar</p>
              <p className="text-2xl font-bold text-ink">{stats?.CLEAR ?? '—'}</p>
            </div>
          </div>
        </div>
        <div className="bg-surface rounded-xl border border-line p-5">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-lg bg-warning/10 flex items-center justify-center">
              <AlertTriangle size={20} className="text-warning" />
            </div>
            <div>
              <p className="text-sm text-ink-soft">Correspondances</p>
              <p className="text-2xl font-bold text-ink">
                {(stats?.MATCH ?? 0) + (stats?.POSSIBLE_MATCH ?? 0)}
              </p>
            </div>
          </div>
        </div>
        <div className="bg-surface rounded-xl border border-line p-5">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-lg bg-danger/10 flex items-center justify-center">
              <XCircle size={20} className="text-danger" />
            </div>
            <div>
              <p className="text-sm text-ink-soft">Bloquées</p>
              <p className="text-2xl font-bold text-ink">{stats?.BLOCKED ?? '—'}</p>
            </div>
          </div>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex gap-4 mb-4 border-b border-line">
        <button
          onClick={() => setActiveTab('history')}
          className={`pb-3 px-1 text-sm font-medium border-b-2 transition-colors ${
            activeTab === 'history'
              ? 'border-accent/40 text-accent'
              : 'border-transparent text-ink-soft hover:text-ink'
          }`}
        >
          <FileText size={16} className="inline mr-1.5" />
          Historique
        </button>
        <button
          onClick={() => setActiveTab('entities')}
          className={`pb-3 px-1 text-sm font-medium border-b-2 transition-colors ${
            activeTab === 'entities'
              ? 'border-accent/40 text-accent'
              : 'border-transparent text-ink-soft hover:text-ink'
          }`}
        >
          <Database size={16} className="inline mr-1.5" />
          Entités sanctionnées
        </button>
        <button
          onClick={() => setActiveTab('alerts')}
          className={`pb-3 px-1 text-sm font-medium border-b-2 transition-colors ${
            activeTab === 'alerts'
              ? 'border-danger/40 text-danger'
              : 'border-transparent text-ink-soft hover:text-ink'
          }`}
        >
          <AlertOctagon size={16} className="inline mr-1.5" />
          Alertes
          {alertsData && alertsData.length > 0 && (
            <span className="ml-1.5 inline-flex items-center justify-center w-5 h-5 text-xs font-bold text-white bg-danger rounded-full">
              {alertsData.length}
            </span>
          )}
        </button>
      </div>

      {/* History table */}
      {activeTab === 'history' && (
        <div className="bg-surface rounded-xl border border-line overflow-hidden">
          {historyLoading ? (
            <div className="px-6 py-12 text-center text-ink-soft">
              <Loader2 size={24} className="animate-spin mx-auto mb-2 text-ink-soft" />
              Chargement...
            </div>
          ) : history.length === 0 ? (
            <div className="px-6 py-12 text-center text-ink-soft">
              <ShieldAlert size={32} className="mx-auto mb-3 text-ink-soft" />
              <p>Aucun screening effectué</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="bg-bg border-b border-line">
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Date</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Nom vérifié</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Résultat</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Niveau risque</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Liste</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Pays</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-line">
                  {history.map((check) => {
                    const rc = resultConfig[check.result] || resultConfig.CLEAR;
                    const risk = riskConfig[check.riskLevel] || riskConfig.NONE;
                    return (
                      <tr key={check.id} className="hover:bg-bg transition-colors">
                        <td className="px-6 py-4 text-sm text-ink-soft">
                          {new Date(check.createdAt).toLocaleDateString('fr-FR', {
                            day: '2-digit',
                            month: '2-digit',
                            year: 'numeric',
                            hour: '2-digit',
                            minute: '2-digit',
                          })}
                        </td>
                        <td className="px-6 py-4 text-sm font-medium text-ink">{check.checkedName}</td>
                        <td className="px-6 py-4">
                          <span className={`inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-medium ${rc.bg} ${rc.text}`}>
                            {rc.label}
                          </span>
                        </td>
                        <td className="px-6 py-4">
                          <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${risk.color}`}>
                            {risk.label}
                          </span>
                        </td>
                        <td className="px-6 py-4 text-sm text-ink-soft">{check.matchedListName || '—'}</td>
                        <td className="px-6 py-4">
                          {check.countryCode ? (
                            <span className="inline-flex items-center gap-1 text-sm text-ink">
                              <Globe size={14} className="text-ink-soft" />
                              {check.countryCode}
                            </span>
                          ) : (
                            <span className="text-sm text-ink-soft">—</span>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Alerts tab */}
      {activeTab === 'alerts' && (
        <div className="bg-surface rounded-xl border border-line overflow-hidden">
          {alertsLoading ? (
            <div className="px-6 py-12 text-center text-ink-soft">
              <Loader2 size={24} className="animate-spin mx-auto mb-2 text-ink-soft" />
              Chargement...
            </div>
          ) : !alertsData || alertsData.length === 0 ? (
            <div className="px-6 py-12 text-center text-ink-soft">
              <ShieldCheck size={32} className="mx-auto mb-3 text-success" />
              <p className="text-success font-medium">Aucune alerte active</p>
              <p className="text-sm text-ink-soft mt-1">Tous les screenings sont CLEAR ou LOW</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="bg-danger/10 border-b border-danger/40">
                    <th className="text-left text-xs font-medium text-danger uppercase tracking-wider px-6 py-3">Date</th>
                    <th className="text-left text-xs font-medium text-danger uppercase tracking-wider px-6 py-3">Nom</th>
                    <th className="text-left text-xs font-medium text-danger uppercase tracking-wider px-6 py-3">Résultat</th>
                    <th className="text-left text-xs font-medium text-danger uppercase tracking-wider px-6 py-3">Niveau risque</th>
                    <th className="text-left text-xs font-medium text-danger uppercase tracking-wider px-6 py-3">Liste</th>
                    <th className="text-left text-xs font-medium text-danger uppercase tracking-wider px-6 py-3">Détails</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-line">
                  {alertsData.map((alert) => {
                    const rc = resultConfig[alert.result] || resultConfig.CLEAR;
                    const risk = riskConfig[alert.riskLevel] || riskConfig.NONE;
                    return (
                      <tr key={alert.id} className="hover:bg-danger/10/50 transition-colors">
                        <td className="px-6 py-4 text-sm text-ink-soft">
                          {new Date(alert.createdAt).toLocaleDateString('fr-FR', {
                            day: '2-digit',
                            month: '2-digit',
                            year: 'numeric',
                            hour: '2-digit',
                            minute: '2-digit',
                          })}
                        </td>
                        <td className="px-6 py-4 text-sm font-medium text-ink">{alert.checkedName}</td>
                        <td className="px-6 py-4">
                          <span className={`inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-medium ${rc.bg} ${rc.text}`}>
                            {rc.label}
                          </span>
                        </td>
                        <td className="px-6 py-4">
                          <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${risk.color}`}>
                            {risk.label}
                          </span>
                        </td>
                        <td className="px-6 py-4 text-sm text-ink-soft">{alert.matchedListName || '—'}</td>
                        <td className="px-6 py-4 text-xs text-ink-soft max-w-[300px] truncate" title={alert.matchedEntryDetails || ''}>
                          {alert.matchedEntryDetails || '—'}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Sanctioned Entities tab */}
      {activeTab === 'entities' && (
        <div className="bg-surface rounded-xl border border-line overflow-hidden">
          {entitiesLoading ? (
            <div className="px-6 py-12 text-center text-ink-soft">
              <Loader2 size={24} className="animate-spin mx-auto mb-2 text-ink-soft" />
              Chargement...
            </div>
          ) : entities.length === 0 ? (
            <div className="px-6 py-12 text-center text-ink-soft">
              <Database size={32} className="mx-auto mb-3 text-ink-soft" />
              <p>Aucune donnée disponible</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="bg-bg border-b border-line">
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">ID</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Nom</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Type</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Pays</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Raison</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Programme</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Source</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-line">
                  {entities.map((entity) => (
                    <tr key={entity.id} className="hover:bg-bg transition-colors">
                      <td className="px-6 py-4 text-sm text-ink-soft font-mono">{entity.id}</td>
                      <td className="px-6 py-4 text-sm font-medium text-ink">{entity.name}</td>
                      <td className="px-6 py-4 text-sm text-ink-soft">{entity.type}</td>
                      <td className="px-6 py-4 text-sm text-ink-soft">{entity.country || '—'}</td>
                      <td className="px-6 py-4 text-sm text-ink-soft max-w-[200px] truncate" title={entity.reason}>{entity.reason}</td>
                      <td className="px-6 py-4 text-sm text-ink-soft">{entity.programme}</td>
                      <td className="px-6 py-4 text-sm text-ink-soft">{entity.source}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default DeniedPartyScreening;
