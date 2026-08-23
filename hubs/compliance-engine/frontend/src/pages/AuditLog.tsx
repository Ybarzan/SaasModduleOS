import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ScrollText, Package, Truck, Search, Users, Database, ChevronLeft, ChevronRight, Loader2, Activity } from 'lucide-react';
import { incokalkAPI } from '../lib/api';
import type { AuditLog as AuditLogType, AuditLogStats } from '../types';

const ACTION_LABELS: Record<string, string> = {
  SHIPMENT_CREATED: 'Expédition créée',
  SHIPMENT_STATUS_CHANGED: 'Statut modifié',
  SHIPMENT_DELETED: 'Expédition supprimée',
  CARRIER_CREATED: 'Transporteur créé',
  CARRIER_UPDATED: 'Transporteur modifié',
  CARRIER_DELETED: 'Transporteur supprimé',
  QUOTE_CREATED: 'Devis créé',
  USER_INVITED: 'Utilisateur invité',
  USER_ROLE_CHANGED: 'Rôle modifié',
  ERP_SYNC: 'Synchronisation ERP',
};

const ACTION_COLORS: Record<string, string> = {
  CREATED: 'bg-success/10 text-success',
  UPDATED: 'bg-accent-soft text-accent-strong',
  DELETED: 'bg-danger/10 text-danger',
  STATUS_CHANGED: 'bg-warning/10 text-warning',
  INVITED: 'bg-accent-soft text-accent-strong',
  SYNC: 'bg-accent-soft text-accent-strong',
};

const ENTITY_ICONS: Record<string, typeof Package> = {
  SHIPMENT: Package,
  CARRIER: Truck,
  QUOTE: Search,
  USER: Users,
  ERP: Database,
};

function getActionBadgeClass(action: string): string {
  if (action.includes('CREATED')) return ACTION_COLORS.CREATED;
  if (action.includes('UPDATED')) return ACTION_COLORS.UPDATED;
  if (action.includes('DELETED')) return ACTION_COLORS.DELETED;
  if (action.includes('STATUS_CHANGED')) return ACTION_COLORS.STATUS_CHANGED;
  if (action.includes('INVITED')) return ACTION_COLORS.INVITED;
  if (action.includes('SYNC')) return ACTION_COLORS.SYNC;
  return 'bg-surface-2 text-ink';
}

const FILTER_TABS = [
  { key: 'all', label: 'Tous', icon: ScrollText },
  { key: 'SHIPMENT', label: 'Expéditions', icon: Package },
  { key: 'CARRIER', label: 'Transporteurs', icon: Truck },
  { key: 'QUOTE', label: 'Devis', icon: Search },
  { key: 'USER', label: 'Équipe', icon: Users },
  { key: 'ERP', label: 'ERP', icon: Database },
];

const AuditLog = () => {
  const [activeTab, setActiveTab] = useState('all');
  const [page, setPage] = useState(0);
  const size = 20;

  const { data: statsData } = useQuery({
    queryKey: ['audit-stats'],
    queryFn: async () => {
      const res = await incokalkAPI.audit.getStats();
      return res.data as AuditLogStats;
    },
  });

  const { data: logsData, isLoading } = useQuery({
    queryKey: ['audit-logs', activeTab, page],
    queryFn: async () => {
      let res;
      if (activeTab === 'all') {
        res = await incokalkAPI.audit.getAll(page, size);
      } else {
        res = await incokalkAPI.audit.getByEntity(activeTab, page, size);
      }
      return res.data as { content: AuditLogType[]; totalElements: number; totalPages: number };
    },
  });

  const logs = logsData?.content || [];
  const totalPages = logsData?.totalPages || 0;
  const totalElements = logsData?.totalElements || 0;

  return (
    <div className="max-w-6xl mx-auto px-4 py-8">
      {/* Header */}
      <div className="mb-8">
        <div className="flex items-center gap-3 mb-2">
          <div className="w-10 h-10 rounded-lg bg-accent/10 flex items-center justify-center">
            <ScrollText size={20} className="text-accent" />
          </div>
          <div>
            <h1 className="text-2xl font-bold text-ink">Journal d'activité</h1>
            <p className="text-ink-soft text-sm">Historique des actions effectuées dans votre entreprise</p>
          </div>
        </div>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-8">
        <div className="bg-surface rounded-xl border border-line p-5">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-lg bg-accent/10 flex items-center justify-center">
              <Activity size={20} className="text-accent" />
            </div>
            <div>
              <p className="text-sm text-ink-soft">Total actions</p>
              <p className="text-2xl font-bold text-ink">{statsData?.total ?? '—'}</p>
            </div>
          </div>
        </div>
        <div className="bg-surface rounded-xl border border-line p-5">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-lg bg-success/10 flex items-center justify-center">
              <Package size={20} className="text-success" />
            </div>
            <div>
              <p className="text-sm text-ink-soft">Expéditions</p>
              <p className="text-2xl font-bold text-ink">
                {statsData?.byEntity?.SHIPMENT ?? '—'}
              </p>
            </div>
          </div>
        </div>
        <div className="bg-surface rounded-xl border border-line p-5">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-lg bg-accent-soft flex items-center justify-center">
              <Truck size={20} className="text-accent" />
            </div>
            <div>
              <p className="text-sm text-ink-soft">Transporteurs</p>
              <p className="text-2xl font-bold text-ink">
                {statsData?.byEntity?.CARRIER ?? '—'}
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* Filter Tabs */}
      <div className="flex flex-wrap gap-2 mb-6">
        {FILTER_TABS.map(({ key, label, icon: Icon }) => (
          <button
            key={key}
            onClick={() => { setActiveTab(key); setPage(0); }}
            className={`flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
              activeTab === key
                ? 'bg-accent text-white'
                : 'bg-surface border border-line text-ink-soft hover:bg-bg'
            }`}
          >
            <Icon size={16} />
            {label}
          </button>
        ))}
      </div>

      {/* Table */}
      <div className="bg-surface rounded-xl border border-line overflow-hidden">
        <div className="px-6 py-4 border-b border-line flex items-center justify-between">
          <h2 className="text-lg font-semibold text-ink">Activité récente</h2>
          <span className="text-sm text-ink-soft">{totalElements} entrées</span>
        </div>

        {isLoading ? (
          <div className="px-6 py-12 text-center text-ink-soft">
            <Loader2 size={24} className="animate-spin mx-auto mb-2 text-ink-soft" />
            Chargement...
          </div>
        ) : logs.length === 0 ? (
          <div className="px-6 py-12 text-center text-ink-soft">
            <ScrollText size={32} className="mx-auto mb-3 text-ink-soft" />
            <p>Aucune activité enregistrée</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="bg-bg border-b border-line">
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Date</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Utilisateur</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Action</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Entité</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Détails</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {logs.map((log) => {
                  const EntityIcon = ENTITY_ICONS[log.entityType] || Package;
                  return (
                    <tr key={log.id} className="hover:bg-bg transition-colors">
                      <td className="px-6 py-4 text-sm text-ink-soft whitespace-nowrap">
                        {new Date(log.createdAt).toLocaleDateString('fr-FR', {
                          day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
                        })}
                      </td>
                      <td className="px-6 py-4">
                        <div className="flex items-center gap-2">
                          <div className="w-7 h-7 rounded-full bg-surface-2 flex items-center justify-center text-xs font-medium text-ink-soft">
                            {(log.userEmail?.[0] || '?').toUpperCase()}
                          </div>
                          <span className="text-sm text-ink">{log.userEmail || 'Système'}</span>
                        </div>
                      </td>
                      <td className="px-6 py-4">
                        <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${getActionBadgeClass(log.action)}`}>
                          {ACTION_LABELS[log.action] || log.action}
                        </span>
                      </td>
                      <td className="px-6 py-4">
                        <div className="flex items-center gap-2 text-sm text-ink">
                          <EntityIcon size={14} className="text-ink-soft" />
                          <span>{log.entityName || log.entityType}</span>
                        </div>
                      </td>
                      <td className="px-6 py-4 text-sm text-ink-soft max-w-xs truncate">
                        {log.details || '—'}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="px-6 py-4 border-t border-line flex items-center justify-between">
            <span className="text-sm text-ink-soft">
              Page {page + 1} sur {totalPages}
            </span>
            <div className="flex items-center gap-2">
              <button
                onClick={() => setPage(Math.max(0, page - 1))}
                disabled={page === 0}
                className="p-2 rounded-lg border border-line text-ink-soft hover:bg-bg disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
              >
                <ChevronLeft size={16} />
              </button>
              <button
                onClick={() => setPage(Math.min(totalPages - 1, page + 1))}
                disabled={page >= totalPages - 1}
                className="p-2 rounded-lg border border-line text-ink-soft hover:bg-bg disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
              >
                <ChevronRight size={16} />
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default AuditLog;
