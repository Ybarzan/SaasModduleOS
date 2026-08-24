import type { AxiosError } from 'axios';
import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Webhook, Plus, Trash2, RefreshCw, CheckCircle, XCircle, Loader2, X } from 'lucide-react';
import toast from 'react-hot-toast';
import { incokalkAPI } from '../lib/api';

interface WebhookConfig {
  id: string;
  url: string;
  events: string[];
  status: 'ACTIVE' | 'DISABLED' | 'FAILING';
  secret?: string;
  lastTriggerAt?: string;
  lastResponseCode?: number;
  createdAt: string;
}

const AVAILABLE_EVENTS = [
  'shipment.created',
  'shipment.updated',
  'shipment.delivered',
  'shipment.cancelled',
  'quote.created',
  'tracking.updated',
];

const eventLabels: Record<string, string> = {
  'shipment.created': 'Expédition créée',
  'shipment.updated': 'Expédition mise à jour',
  'shipment.delivered': 'Expédition livrée',
  'shipment.cancelled': 'Expédition annulée',
  'quote.created': 'Devis créé',
  'tracking.updated': 'Tracking mis à jour',
};

const statusIcons: Record<string, typeof CheckCircle> = {
  ACTIVE: CheckCircle,
  DISABLED: XCircle,
  FAILING: XCircle,
};

const statusColors: Record<string, string> = {
  ACTIVE: 'text-success',
  DISABLED: 'text-ink-soft',
  FAILING: 'text-danger',
};

const statusLabels: Record<string, string> = {
  ACTIVE: 'Actif',
  DISABLED: 'Désactivé',
  FAILING: 'En échec',
};

const Webhooks = () => {
  const queryClient = useQueryClient();
  const [addOpen, setAddOpen] = useState(false);
  const [form, setForm] = useState({
    url: '',
    events: [] as string[],
    secret: '',
  });
  const [deleteConfirm, setDeleteConfirm] = useState<string | null>(null);

  const { data: webhooksData, isLoading } = useQuery({
    queryKey: ['webhooks'],
    queryFn: async () => {
      const res = await incokalkAPI.notificationRules.getAll();
      return (res.data as WebhookConfig[]) || [];
    },
  });

  const webhooks: WebhookConfig[] = (webhooksData as WebhookConfig[]) || [];

  const createMutation = useMutation({
    mutationFn: (data: typeof form) => incokalkAPI.notificationRules.create({
      name: `Webhook - ${data.url}`,
      eventType: data.events.join(','),
      isActive: true,
      sendWebhook: true,
      sendEmail: false,
      sendInApp: false,
      webhookUrl: data.url,
      webhookSecret: data.secret || undefined,
    }),
    onSuccess: () => {
      toast.success('Webhook créé avec succès');
      setAddOpen(false);
      setForm({ url: '', events: [], secret: '' });
      queryClient.invalidateQueries({ queryKey: ['webhooks'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || 'Erreur lors de la création du webhook');
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => incokalkAPI.notificationRules.delete(id),
    onSuccess: () => {
      toast.success('Webhook supprimé');
      setDeleteConfirm(null);
      queryClient.invalidateQueries({ queryKey: ['webhooks'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || 'Erreur lors de la suppression');
    },
  });

  const testMutation = useMutation({
    mutationFn: (data: { webhookUrl: string; webhookSecret?: string }) =>
      incokalkAPI.notificationRules.test(data),
    onSuccess: () => {
      toast.success('Test webhook envoyé avec succès');
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || 'Échec du test webhook');
    },
  });

  const toggleEvent = (event: string) => {
    setForm((prev) => ({
      ...prev,
      events: prev.events.includes(event)
        ? prev.events.filter((e) => e !== event)
        : [...prev.events, event],
    }));
  };

  const handleAdd = () => {
    if (!form.url.trim()) {
      toast.error("L'URL du webhook est requise");
      return;
    }
    if (form.events.length === 0) {
      toast.error('Sélectionnez au moins un événement');
      return;
    }
    createMutation.mutate(form);
  };

  return (
    <div className="max-w-6xl mx-auto px-4 py-8">
      {/* Header */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between mb-8 gap-4">
        <div>
          <h1 className="text-2xl font-bold text-ink">
            <span className="text-accent font-normal" aria-hidden="true">:: </span>
            Webhooks
          </h1>
          <p className="text-ink-soft mt-1">Configuration des notifications HTTP</p>
        </div>
        <button
          onClick={() => setAddOpen(true)}
          className="flex items-center gap-2 bg-terra-600 text-white px-4 py-2 rounded-none font-medium hover:bg-terra-700 transition-colors"
        >
          <Plus size={18} />
          Ajouter un webhook
        </button>
      </div>

      {/* Webhooks table */}
      <div className="relative bg-surface rounded-none border border-line overflow-hidden">
        <span className="hud-corner hud-corner-tl" aria-hidden="true" />
        <span className="hud-corner hud-corner-tr" aria-hidden="true" />
        <span className="hud-corner hud-corner-bl" aria-hidden="true" />
        <span className="hud-corner hud-corner-br" aria-hidden="true" />
        <div className="px-6 py-4 border-b border-line flex items-center gap-2">
          <Webhook size={18} className="text-terra-500" />
          <h2 className="text-lg font-semibold text-ink">Webhooks configurés</h2>
        </div>

        {isLoading ? (
          <div className="px-6 py-12 text-center text-ink-soft">
            <Loader2 size={24} className="animate-spin mx-auto mb-2 text-ink-soft" />
            Chargement...
          </div>
        ) : webhooks.length === 0 ? (
          <div className="px-6 py-12 text-center text-ink-soft">
            <Webhook size={32} className="mx-auto mb-3 text-ink-soft" />
            <p className="font-medium text-ink-soft">Aucun webhook configuré</p>
            <p className="text-sm text-ink-soft mt-1">Ajoutez un webhook pour recevoir des notifications HTTP</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="bg-bg border-b border-line">
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">URL</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Événements</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Statut</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Secret</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Dernier appel</th>
                  <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {webhooks.map((wh) => {
                  const StatusIcon = statusIcons[wh.status] || XCircle;
                  const events = typeof wh.events === 'string' ? (wh.events as unknown as string).split(',') : wh.events;

                  return (
                    <tr key={wh.id} className="hover:bg-bg transition-colors">
                      <td className="px-6 py-4">
                        <span className="text-sm font-mono text-ink break-all">{wh.url}</span>
                      </td>
                      <td className="px-6 py-4">
                        <div className="flex flex-wrap gap-1">
                          {events.map((ev) => (
                            <span
                              key={ev}
                              className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-accent-soft text-accent-strong"
                            >
                              {eventLabels[ev] || ev}
                            </span>
                          ))}
                        </div>
                      </td>
                      <td className="px-6 py-4">
                        <div className="flex items-center gap-1.5">
                          <StatusIcon size={14} className={statusColors[wh.status] || 'text-ink-soft'} />
                          <span className="text-sm text-ink">{statusLabels[wh.status] || wh.status}</span>
                        </div>
                      </td>
                      <td className="px-6 py-4">
                        <span className="text-sm font-mono text-ink-soft">
                          {wh.secret ? (
                            <span className="flex items-center gap-1">
                              <span className="w-2 h-2 rounded-full bg-success" />
                              Configuré
                            </span>
                          ) : (
                            <span className="text-ink-soft">—</span>
                          )}
                        </span>
                      </td>
                      <td className="px-6 py-4 text-sm text-ink-soft">
                        {wh.lastTriggerAt
                          ? new Date(wh.lastTriggerAt).toLocaleString('fr-FR')
                          : '—'}
                      </td>
                      <td className="px-6 py-4 text-right">
                        <div className="flex items-center justify-end gap-2">
                          <button
                            onClick={() => testMutation.mutate({ webhookUrl: wh.url, webhookSecret: wh.secret })}
                            disabled={testMutation.isPending}
                            className="p-1.5 rounded-none text-ink-soft hover:text-terra-600 hover:bg-terra-50 transition-colors disabled:opacity-50"
                            title="Tester le webhook"
                          >
                            {testMutation.isPending ? (
                              <Loader2 size={14} className="animate-spin" />
                            ) : (
                              <RefreshCw size={14} />
                            )}
                          </button>
                          {deleteConfirm === wh.id ? (
                            <div className="flex items-center gap-1">
                              <button
                                onClick={() => deleteMutation.mutate(wh.id)}
                                disabled={deleteMutation.isPending}
                                className="px-2 py-1 text-xs bg-danger text-white rounded hover:bg-danger/90 transition-colors"
                              >
                                {deleteMutation.isPending ? <Loader2 size={12} className="animate-spin" /> : 'Oui'}
                              </button>
                              <button
                                onClick={() => setDeleteConfirm(null)}
                                className="px-2 py-1 text-xs bg-surface-2 text-ink-soft rounded hover:bg-line transition-colors"
                              >
                                Non
                              </button>
                            </div>
                          ) : (
                            <button
                              onClick={() => setDeleteConfirm(wh.id)}
                              className="p-1.5 rounded-none text-ink-soft hover:text-danger hover:bg-danger/10 transition-colors"
                              title="Supprimer"
                            >
                              <Trash2 size={14} />
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Add Webhook Modal */}
      {addOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div className="absolute inset-0 bg-black/40" onClick={() => setAddOpen(false)} />
          <div className="relative bg-surface rounded-none shadow-2xl w-full max-w-lg mx-4 p-6">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-lg font-semibold text-ink">Ajouter un webhook</h3>
              <button
                onClick={() => setAddOpen(false)}
                className="p-1.5 text-ink-soft hover:text-ink-soft hover:bg-surface-2 rounded-none transition-colors"
              >
                <X size={18} />
              </button>
            </div>

            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-ink mb-1">URL du webhook *</label>
                <input
                  type="url"
                  value={form.url}
                  onChange={(e) => setForm({ ...form, url: e.target.value })}
                  className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-terra-500 focus:border-transparent text-sm"
                  placeholder="https://votre-serveur.com/webhook"
                  required
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-ink mb-2">Événements *</label>
                <div className="grid grid-cols-2 gap-2">
                  {AVAILABLE_EVENTS.map((event) => (
                    <label
                      key={event}
                      className={`flex items-center gap-2 p-2.5 rounded-none border cursor-pointer transition-colors ${
                        form.events.includes(event)
                          ? 'border-terra-300 bg-terra-50'
                          : 'border-line hover:border-line'
                      }`}
                    >
                      <input
                        type="checkbox"
                        checked={form.events.includes(event)}
                        onChange={() => toggleEvent(event)}
                        className="w-4 h-4 text-terra-600 border-line rounded focus:ring-terra-500"
                      />
                      <span className="text-sm text-ink">{eventLabels[event] || event}</span>
                    </label>
                  ))}
                </div>
              </div>

              <div>
                <label className="block text-sm font-medium text-ink mb-1">Secret (optionnel)</label>
                <input
                  type="text"
                  value={form.secret}
                  onChange={(e) => setForm({ ...form, secret: e.target.value })}
                  className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-terra-500 focus:border-transparent text-sm"
                  placeholder="Clé secrète pour signer les payloads"
                />
                <p className="text-xs text-ink-soft mt-1">Utilisé pour vérifier l'authenticité des notifications</p>
              </div>
            </div>

            <div className="flex gap-3 pt-6">
              <button
                type="button"
                onClick={() => setAddOpen(false)}
                className="flex-1 px-4 py-2 border border-line rounded-none text-sm font-medium text-ink hover:bg-bg transition-colors"
              >
                Annuler
              </button>
              <button
                onClick={handleAdd}
                disabled={createMutation.isPending}
                className="flex-1 px-4 py-2 bg-terra-600 text-white rounded-none text-sm font-medium hover:bg-terra-700 disabled:opacity-50 transition-colors flex items-center justify-center gap-2"
              >
                {createMutation.isPending && <Loader2 size={14} className="animate-spin" />}
                Créer le webhook
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default Webhooks;
