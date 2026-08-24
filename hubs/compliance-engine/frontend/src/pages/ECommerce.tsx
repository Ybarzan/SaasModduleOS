import type { AxiosError } from 'axios';
import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  ShoppingCart, Globe, Store, Plus, RefreshCw, Trash2, ExternalLink,
  Clock, CheckCircle, XCircle, AlertTriangle, X, Loader2,
} from 'lucide-react';
import toast from 'react-hot-toast';
import { incokalkAPI } from '../lib/api';

interface Integration {
  id: string;
  platform: string;
  storeUrl: string;
  apiKey: string;
  apiSecret: string;
  webhookSecret: string;
  syncFrequencyMin: number;
  active: boolean;
  lastSyncAt: string;
  createdAt: string;
}

interface SyncLogEntry {
  id: string;
  integrationId: string;
  platform: string;
  storeUrl: string;
  status: string;
  message: string;
  startedAt: string;
  completedAt: string;
}

const PLATFORM_META: Record<string, { label: string; icon: typeof ShoppingCart; color: string }> = {
  SHOPIFY: { label: 'Shopify', icon: ShoppingCart, color: 'text-success bg-success/10' },
  WOOCOMMERCE: { label: 'WooCommerce', icon: Globe, color: 'text-accent bg-accent/10' },
  PRESTASHOP: { label: 'PrestaShop', icon: Store, color: 'text-accent bg-accent-soft' },
};

const emptyForm = {
  platform: 'SHOPIFY',
  storeUrl: '',
  apiKey: '',
  apiSecret: '',
  webhookSecret: '',
  syncFrequencyMin: 60,
};

const ECommerce = () => {
  const queryClient = useQueryClient();
  const [createOpen, setCreateOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<Integration | null>(null);
  const [form, setForm] = useState(emptyForm);
  const [deleteConfirm, setDeleteConfirm] = useState<string | null>(null);
  const [showSyncLog, setShowSyncLog] = useState(false);

  const { data: integrations, isLoading } = useQuery({
    queryKey: ['ecommerce-integrations'],
    queryFn: async () => {
      const res = await incokalkAPI.ecommerce.list();
      return res.data as Integration[];
    },
  });

  const { data: syncLog } = useQuery({
    queryKey: ['ecommerce-sync-log'],
    queryFn: async () => {
      const res = await incokalkAPI.ecommerce.syncLog();
      return res.data as SyncLogEntry[];
    },
    enabled: showSyncLog,
  });

  const createMutation = useMutation({
    mutationFn: (data: typeof form) => incokalkAPI.ecommerce.create(data),
    onSuccess: () => {
      toast.success('Intégration ajoutée avec succès');
      setCreateOpen(false);
      setForm(emptyForm);
      queryClient.invalidateQueries({ queryKey: ['ecommerce-integrations'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || "Erreur lors de l'ajout");
    },
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: Partial<Integration> }) =>
      incokalkAPI.ecommerce.update(id, data),
    onSuccess: () => {
      toast.success('Intégration mise à jour');
      setEditTarget(null);
      queryClient.invalidateQueries({ queryKey: ['ecommerce-integrations'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || 'Erreur lors de la mise à jour');
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => incokalkAPI.ecommerce.remove(id),
    onSuccess: () => {
      toast.success('Intégration supprimée');
      setDeleteConfirm(null);
      queryClient.invalidateQueries({ queryKey: ['ecommerce-integrations'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || 'Erreur lors de la suppression');
    },
  });

  const syncMutation = useMutation({
    mutationFn: (id: string) => incokalkAPI.ecommerce.sync(id),
    onSuccess: () => {
      toast.success('Synchronisation lancée');
      queryClient.invalidateQueries({ queryKey: ['ecommerce-integrations'] });
      queryClient.invalidateQueries({ queryKey: ['ecommerce-sync-log'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || 'Erreur lors de la synchronisation');
    },
  });

  const toggleActiveMutation = useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) =>
      incokalkAPI.ecommerce.update(id, { active }),
    onSuccess: () => {
      toast.success('Statut mis à jour');
      queryClient.invalidateQueries({ queryKey: ['ecommerce-integrations'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || 'Erreur lors de la mise à jour');
    },
  });

  const handleCreate = (e: React.FormEvent) => {
    e.preventDefault();
    createMutation.mutate(form);
  };

  const handleEdit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!editTarget) return;
    updateMutation.mutate({ id: editTarget.id, data: form });
  };

  const openEdit = (integration: Integration) => {
    setEditTarget(integration);
    setForm({
      platform: integration.platform,
      storeUrl: integration.storeUrl,
      apiKey: integration.apiKey,
      apiSecret: integration.apiSecret,
      webhookSecret: integration.webhookSecret,
      syncFrequencyMin: integration.syncFrequencyMin,
    });
  };

  const closeForm = () => {
    setCreateOpen(false);
    setEditTarget(null);
    setForm(emptyForm);
  };

  const getPlatformIcon = (platform: string, className = 'w-5 h-5') => {
    const meta = PLATFORM_META[platform];
    if (!meta) return <ShoppingCart className={className} />;
    const Icon = meta.icon;
    return <Icon className={className} />;
  };

  const getPlatformColor = (platform: string) => {
    return PLATFORM_META[platform]?.color || 'text-ink-soft bg-surface-2';
  };

  const formatDate = (dateStr: string) => {
    if (!dateStr) return 'Jamais';
    return new Date(dateStr).toLocaleString('fr-FR');
  };

  return (
    <div className="max-w-7xl mx-auto px-4 py-8">
      {/* Header */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between mb-8 gap-4">
        <div>
          <h1 className="text-2xl font-bold text-ink">
            <span className="text-accent font-normal" aria-hidden="true">:: </span>
            Intégrations E-Commerce
          </h1>
          <p className="text-ink-soft mt-1">Connectez Shopify, WooCommerce & PrestaShop</p>
        </div>
        <div className="flex items-center gap-3">
          <button
            onClick={() => setShowSyncLog(!showSyncLog)}
            className="flex items-center gap-2 bg-surface border border-line text-ink-soft px-4 py-2 rounded-none font-medium hover:bg-surface-2 transition-colors"
          >
            <Clock size={18} />
            Historique sync
          </button>
          <button
            onClick={() => setCreateOpen(true)}
            className="flex items-center gap-2 bg-accent text-white px-4 py-2 rounded-none font-medium hover:bg-accent-strong transition-colors"
          >
            <Plus size={18} />
            Ajouter une intégration
          </button>
        </div>
      </div>

      {/* Integration Cards */}
      {isLoading ? (
        <div className="text-center py-12 text-ink-soft">
          <Loader2 size={24} className="animate-spin mx-auto mb-2 text-ink-soft" />
          Chargement...
        </div>
      ) : !integrations || integrations.length === 0 ? (
        <div className="bg-surface rounded-none border border-line p-12 text-center">
          <ShoppingCart size={48} className="mx-auto mb-4 text-ink-soft" />
          <p className="text-ink-soft text-lg mb-2">Aucune intégration</p>
          <p className="text-ink-soft text-sm mb-6">Connectez votre première boutique e-commerce</p>
          <button
            onClick={() => setCreateOpen(true)}
            className="inline-flex items-center gap-2 bg-accent text-white px-4 py-2 rounded-none font-medium hover:bg-accent-strong transition-colors"
          >
            <Plus size={18} />
            Ajouter une intégration
          </button>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-8">
          {integrations.map((integration) => {
            const meta = PLATFORM_META[integration.platform];
            return (
              <div
                key={integration.id}
                className="bg-surface rounded-none border border-line p-6 hover:shadow-md transition-shadow"
              >
                <div className="flex items-start justify-between mb-4">
                  <div className="flex items-center gap-3">
                    <div className={`w-10 h-10 rounded-none flex items-center justify-center ${getPlatformColor(integration.platform)}`}>
                      {getPlatformIcon(integration.platform, 'w-5 h-5')}
                    </div>
                    <div>
                      <h3 className="font-semibold text-ink">{meta?.label || integration.platform}</h3>
                      <p className="text-sm text-ink-soft">{integration.storeUrl}</p>
                    </div>
                  </div>
                  <button
                    onClick={() =>
                      toggleActiveMutation.mutate({ id: integration.id, active: !integration.active })
                    }
                    className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors ${
                      integration.active ? 'bg-success' : 'bg-line'
                    }`}
                  >
                    <span
                      className={`inline-block h-4 w-4 transform rounded-full bg-surface transition-transform ${
                        integration.active ? 'translate-x-6' : 'translate-x-1'
                      }`}
                    />
                  </button>
                </div>

                <div className="flex items-center gap-2 text-sm text-ink-soft mb-4">
                  <Clock size={14} />
                  <span>Dernière sync : {formatDate(integration.lastSyncAt)}</span>
                </div>

                <div className="flex items-center gap-2">
                  <button
                    onClick={() => syncMutation.mutate(integration.id)}
                    disabled={syncMutation.isPending}
                    className="flex items-center gap-1.5 px-3 py-1.5 bg-accent-soft text-accent-strong rounded-none text-sm font-medium hover:bg-accent-soft transition-colors disabled:opacity-50"
                  >
                    <RefreshCw size={14} className={syncMutation.isPending ? 'animate-spin' : ''} />
                    Sync
                  </button>
                  <button
                    onClick={() => openEdit(integration)}
                    className="flex items-center gap-1.5 px-3 py-1.5 bg-surface-2 text-ink-soft rounded-none text-sm font-medium hover:bg-line transition-colors"
                  >
                    <ExternalLink size={14} />
                    Modifier
                  </button>
                  {deleteConfirm === integration.id ? (
                    <div className="flex items-center gap-1">
                      <button
                        onClick={() => deleteMutation.mutate(integration.id)}
                        disabled={deleteMutation.isPending}
                        className="px-2 py-1.5 text-xs bg-danger text-white rounded hover:bg-danger transition-colors"
                      >
                        {deleteMutation.isPending ? <Loader2 size={12} className="animate-spin" /> : 'Oui'}
                      </button>
                      <button
                        onClick={() => setDeleteConfirm(null)}
                        className="px-2 py-1.5 text-xs bg-line text-ink-soft rounded hover:bg-line transition-colors"
                      >
                        Non
                      </button>
                    </div>
                  ) : (
                    <button
                      onClick={() => setDeleteConfirm(integration.id)}
                      className="p-1.5 rounded-none text-ink-soft hover:text-danger hover:bg-danger/10 transition-colors"
                      title="Supprimer"
                    >
                      <Trash2 size={16} />
                    </button>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Sync Log Section */}
      {showSyncLog && (
        <div className="bg-surface rounded-none border border-line overflow-hidden mb-8">
          <div className="px-6 py-4 border-b border-line flex items-center justify-between">
            <h2 className="text-lg font-semibold text-ink">Historique des synchronisations</h2>
            <button onClick={() => setShowSyncLog(false)} className="text-ink-soft hover:text-ink">
              <X size={20} />
            </button>
          </div>
          {!syncLog || syncLog.length === 0 ? (
            <div className="px-6 py-12 text-center text-ink-soft">
              <Clock size={32} className="mx-auto mb-3 text-ink-soft" />
              <p>Aucune synchronisation</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="bg-surface-2 border-b border-line">
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Plateforme</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Boutique</th>
                    <th className="text-center text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Statut</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Message</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Début</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Fin</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-line">
                  {syncLog.map((entry) => (
                    <tr key={entry.id} className="hover:bg-surface-2 transition-colors">
                      <td className="px-6 py-4">
                        <div className="flex items-center gap-2">
                          <span className={`w-7 h-7 rounded-none flex items-center justify-center ${getPlatformColor(entry.platform)}`}>
                            {getPlatformIcon(entry.platform, 'w-3.5 h-3.5')}
                          </span>
                          <span className="text-sm font-medium text-ink">
                            {PLATFORM_META[entry.platform]?.label || entry.platform}
                          </span>
                        </div>
                      </td>
                      <td className="px-6 py-4 text-sm text-ink-soft">{entry.storeUrl}</td>
                      <td className="px-6 py-4 text-center">
                        {entry.status === 'SUCCESS' ? (
                          <CheckCircle size={18} className="text-success inline-block" />
                        ) : entry.status === 'FAILED' ? (
                          <XCircle size={18} className="text-danger inline-block" />
                        ) : (
                          <AlertTriangle size={18} className="text-warning inline-block" />
                        )}
                      </td>
                      <td className="px-6 py-4 text-sm text-ink-soft max-w-xs truncate">{entry.message || '—'}</td>
                      <td className="px-6 py-4 text-sm text-ink-soft">{formatDate(entry.startedAt)}</td>
                      <td className="px-6 py-4 text-sm text-ink-soft">{entry.completedAt ? formatDate(entry.completedAt) : '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Add / Edit Modal */}
      {(createOpen || editTarget) && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div className="absolute inset-0 bg-black/40" onClick={closeForm} />
          <div className="relative bg-surface rounded-none shadow-2xl w-full max-w-lg mx-4 p-6">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-lg font-semibold text-ink">
                {editTarget ? "Modifier l'intégration" : 'Nouvelle intégration'}
              </h3>
              <button onClick={closeForm} className="text-ink-soft hover:text-ink">
                <X size={20} />
              </button>
            </div>
            <form onSubmit={editTarget ? handleEdit : handleCreate} className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-ink-soft mb-1">Plateforme</label>
                <select
                  value={form.platform}
                  onChange={(e) => setForm({ ...form, platform: e.target.value })}
                  className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                >
                  <option value="SHOPIFY">Shopify</option>
                  <option value="WOOCOMMERCE">WooCommerce</option>
                  <option value="PRESTASHOP">PrestaShop</option>
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-ink-soft mb-1">URL de la boutique</label>
                <input
                  type="url"
                  value={form.storeUrl}
                  onChange={(e) => setForm({ ...form, storeUrl: e.target.value })}
                  className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                  placeholder="https://maboutique.myshopify.com"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-ink-soft mb-1">Clé API</label>
                <input
                  type="text"
                  value={form.apiKey}
                  onChange={(e) => setForm({ ...form, apiKey: e.target.value })}
                  className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-ink-soft mb-1">Secret API</label>
                <input
                  type="password"
                  value={form.apiSecret}
                  onChange={(e) => setForm({ ...form, apiSecret: e.target.value })}
                  className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-ink-soft mb-1">Secret Webhook</label>
                <input
                  type="password"
                  value={form.webhookSecret}
                  onChange={(e) => setForm({ ...form, webhookSecret: e.target.value })}
                  className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-ink-soft mb-1">
                  Fréquence de synchronisation (minutes)
                </label>
                <input
                  type="number"
                  min={5}
                  step={5}
                  value={form.syncFrequencyMin}
                  onChange={(e) => setForm({ ...form, syncFrequencyMin: parseInt(e.target.value) || 60 })}
                  className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                />
              </div>
              <div className="flex gap-3 pt-2">
                <button
                  type="button"
                  onClick={closeForm}
                  className="flex-1 px-4 py-2 border border-line rounded-none text-sm font-medium text-ink-soft hover:bg-surface-2 transition-colors"
                >
                  Annuler
                </button>
                <button
                  type="submit"
                  disabled={createMutation.isPending || updateMutation.isPending}
                  className="flex-1 px-4 py-2 bg-accent text-white rounded-none text-sm font-medium hover:bg-accent-strong disabled:opacity-50 transition-colors flex items-center justify-center gap-2"
                >
                  {(createMutation.isPending || updateMutation.isPending) && (
                    <Loader2 size={14} className="animate-spin" />
                  )}
                  {editTarget ? "Mettre à jour" : "Ajouter"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default ECommerce;
