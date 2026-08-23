import { useQuery } from '@tanstack/react-query';
import { Shield, CheckCircle, AlertTriangle, Clock, FileCheck, BarChart3, Loader2 } from 'lucide-react';
import { api } from '../lib/api';

interface ComplianceStats {
  declarationsPending: number;
  dpsAlerts: number;
  sanctionsMatches: number;
  expiringEori: number;
}

interface ComplianceAlert {
  id: string;
  type: string;
  title: string;
  description: string;
  severity: 'low' | 'medium' | 'high';
  date: string;
}

const SEVERITY_CONFIG: Record<string, { color: string; bg: string }> = {
  high: { color: 'text-danger', bg: 'bg-danger/10' },
  medium: { color: 'text-warning', bg: 'bg-warning/10' },
  low: { color: 'text-accent-strong', bg: 'bg-accent-soft' },
};

const Compliance = () => {
  const { data: statsData, isLoading: statsLoading } = useQuery({
    queryKey: ['compliance-stats'],
    queryFn: async () => {
      const res = await api.get('/v1/compliance/stats');
      return res.data as ComplianceStats;
    },
  });

  const { data: alertsData, isLoading: alertsLoading } = useQuery({
    queryKey: ['compliance-alerts'],
    queryFn: async () => {
      const res = await api.get('/v1/compliance/alerts');
      return (res.data?.alerts || res.data || []) as ComplianceAlert[];
    },
  });

  const stats = statsData || { declarationsPending: 0, dpsAlerts: 0, sanctionsMatches: 0, expiringEori: 0 };
  const alerts = Array.isArray(alertsData) ? alertsData : [];

  const formatDate = (d: string) => {
    if (!d) return '—';
    return new Date(d).toLocaleDateString('fr-FR', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
  };

  const statCards = [
    {
      label: 'Déclarations en attente',
      value: stats.declarationsPending,
      icon: FileCheck,
      color: 'text-accent',
      bg: 'bg-accent-soft',
    },
    {
      label: 'Alertes DPS',
      value: stats.dpsAlerts,
      icon: AlertTriangle,
      color: 'text-warning',
      bg: 'bg-warning/10',
    },
    {
      label: 'Correspondances sanctions',
      value: stats.sanctionsMatches,
      icon: Shield,
      color: 'text-danger',
      bg: 'bg-danger/10',
    },
    {
      label: 'EORI expirant',
      value: stats.expiringEori,
      icon: Clock,
      color: 'text-accent',
      bg: 'bg-accent/10',
    },
  ];

  if (statsLoading) {
    return (
      <div className="py-12 text-center text-ink-soft">
        <Loader2 size={24} className="animate-spin mx-auto mb-2 text-ink-soft" />
        Chargement...
      </div>
    );
  }

  return (
    <div className="max-w-6xl mx-auto px-4 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-ink">Conformité</h1>
        <p className="text-ink-soft mt-1">Tableau de bord réglementaire</p>
      </div>

      {/* Stats Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        {statCards.map((card) => {
          const Icon = card.icon;
          return (
            <div
              key={card.label}
              className="bg-surface rounded-xl border border-line p-5 flex items-center gap-4"
            >
              <div className={`w-10 h-10 rounded-lg ${card.bg} flex items-center justify-center shrink-0`}>
                <Icon size={20} className={card.color} />
              </div>
              <div>
                <p className="text-2xl font-bold text-ink">{card.value}</p>
                <p className="text-xs text-ink-soft">{card.label}</p>
              </div>
            </div>
          );
        })}
      </div>

      {/* Compliance Areas */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-8">
        <div className="bg-surface rounded-xl border border-line p-5">
          <div className="flex items-center gap-2 mb-3">
            <FileCheck size={16} className="text-accent" />
            <h3 className="text-sm font-semibold text-ink">Déclarations douanières</h3>
          </div>
          <div className="flex items-center justify-between text-sm">
            <span className="text-ink-soft">En attente</span>
            <span className="font-semibold text-ink">{stats.declarationsPending}</span>
          </div>
          <div className="mt-2 w-full bg-surface-2 rounded-full h-1.5">
            <div
              className="bg-accent h-1.5 rounded-full"
              style={{ width: `${Math.min(stats.declarationsPending * 10, 100)}%` }}
            />
          </div>
        </div>

        <div className="bg-surface rounded-xl border border-line p-5">
          <div className="flex items-center gap-2 mb-3">
            <AlertTriangle size={16} className="text-warning" />
            <h3 className="text-sm font-semibold text-ink">DPS (Denied Party Screening)</h3>
          </div>
          <div className="flex items-center justify-between text-sm">
            <span className="text-ink-soft">Alertes</span>
            <span className="font-semibold text-ink">{stats.dpsAlerts}</span>
          </div>
          <div className="mt-2 w-full bg-surface-2 rounded-full h-1.5">
            <div
              className="bg-warning h-1.5 rounded-full"
              style={{ width: `${Math.min(stats.dpsAlerts * 20, 100)}%` }}
            />
          </div>
        </div>

        <div className="bg-surface rounded-xl border border-line p-5">
          <div className="flex items-center gap-2 mb-3">
            <Shield size={16} className="text-danger" />
            <h3 className="text-sm font-semibold text-ink">Listes de sanctions</h3>
          </div>
          <div className="flex items-center justify-between text-sm">
            <span className="text-ink-soft">Correspondances</span>
            <span className="font-semibold text-ink">{stats.sanctionsMatches}</span>
          </div>
          <div className="mt-2 w-full bg-surface-2 rounded-full h-1.5">
            <div
              className="bg-danger h-1.5 rounded-full"
              style={{ width: `${Math.min(stats.sanctionsMatches * 25, 100)}%` }}
            />
          </div>
        </div>

        <div className="bg-surface rounded-xl border border-line p-5">
          <div className="flex items-center gap-2 mb-3">
            <Clock size={16} className="text-accent" />
            <h3 className="text-sm font-semibold text-ink">EORI</h3>
          </div>
          <div className="flex items-center justify-between text-sm">
            <span className="text-ink-soft">Expirant bientôt</span>
            <span className="font-semibold text-ink">{stats.expiringEori}</span>
          </div>
          <div className="mt-2 w-full bg-surface-2 rounded-full h-1.5">
            <div
              className="bg-accent h-1.5 rounded-full"
              style={{ width: `${Math.min(stats.expiringEori * 20, 100)}%` }}
            />
          </div>
        </div>
      </div>

      {/* Recent Alerts */}
      <div className="bg-surface rounded-xl border border-line">
        <div className="flex items-center gap-2 px-5 py-4 border-b border-line">
          <BarChart3 size={16} className="text-ink-soft" />
          <h2 className="text-sm font-semibold text-ink">Alertes récentes</h2>
        </div>
        {alertsLoading ? (
          <div className="py-8 text-center text-ink-soft">
            <Loader2 size={20} className="animate-spin mx-auto mb-2 text-ink-soft" />
            Chargement...
          </div>
        ) : alerts.length === 0 ? (
          <div className="py-8 text-center text-ink-soft">
            <CheckCircle size={24} className="mx-auto mb-2 text-ink-soft" />
            <p className="text-sm">Aucune alerte récente</p>
          </div>
        ) : (
          <div className="divide-y divide-line">
            {alerts.map((alert) => {
              const sv = SEVERITY_CONFIG[alert.severity] || SEVERITY_CONFIG.low;
              return (
                <div key={alert.id} className="px-5 py-3 flex items-start gap-3">
                  <div className={`w-6 h-6 rounded-full ${sv.bg} flex items-center justify-center shrink-0 mt-0.5`}>
                    <AlertTriangle size={12} className={sv.color} />
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium text-ink">{alert.title}</p>
                    <p className="text-xs text-ink-soft mt-0.5">{alert.description}</p>
                  </div>
                  <span className={`shrink-0 px-2 py-0.5 rounded text-xs font-medium ${sv.bg} ${sv.color}`}>
                    {alert.severity}
                  </span>
                  <span className="shrink-0 text-xs text-ink-soft">{formatDate(alert.date)}</span>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
};

export default Compliance;
