import type { AxiosError, AxiosResponse } from 'axios';
import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Key, Plus, Trash2, Copy, Loader2 } from 'lucide-react';
import toast from 'react-hot-toast';
import { api } from '../lib/api';
import { useAuthStore } from '../stores/auth';

interface ApiKey {
  id: string;
  name: string;
  keyPrefix: string;
  plan: string;
  dailyLimit: number;
  active: boolean;
  expiresAt?: string;
  createdAt: string;
  lastUsed?: string;
}

const statusColors: Record<string, string> = {
  ACTIVE: 'bg-success/15 text-success',
  REVOKED: 'bg-danger/15 text-danger',
  EXPIRED: 'bg-surface-2 text-ink-soft',
};

const statusLabels: Record<string, string> = {
  ACTIVE: 'Actif',
  REVOKED: 'Révoqué',
  EXPIRED: 'Expiré',
};

const ApiKeys = () => {
  const queryClient = useQueryClient();
  const isAdmin = useAuthStore((s) => s.isAdmin());
  const [generateOpen, setGenerateOpen] = useState(false);
  const [newKeyName, setNewKeyName] = useState('');
  const [generatedKey, setGeneratedKey] = useState<string | null>(null);
  const [deleteConfirm, setDeleteConfirm] = useState<string | null>(null);

  const { data: keysData, isLoading } = useQuery({
    queryKey: ['api-keys'],
    queryFn: async () => {
      const res = await api.get('/v1/api-keys');
      return (res.data as ApiKey[]) || [];
    },
  });

  const keys: ApiKey[] = (keysData as ApiKey[]) || [];

  const keyStatus = (k: ApiKey): 'ACTIVE' | 'REVOKED' | 'EXPIRED' => {
    if (!k.active) return 'REVOKED';
    if (k.expiresAt && new Date(k.expiresAt) < new Date()) return 'EXPIRED';
    return 'ACTIVE';
  };

  const generateMutation = useMutation({
    mutationFn: (name: string) => api.post<{ key: string }>('/v1/api-keys', { name }),
    onSuccess: (res: AxiosResponse<{ key: string }>) => {
      const keyValue = res.data?.key || '';
      setGeneratedKey(keyValue);
      toast.success('Clé API générée avec succès');
      queryClient.invalidateQueries({ queryKey: ['api-keys'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || 'Erreur lors de la génération de la clé');
    },
  });

  const revokeMutation = useMutation({
    mutationFn: (id: string) => api.delete(`/v1/api-keys/${id}`),
    onSuccess: () => {
      toast.success('Clé API révoquée');
      setDeleteConfirm(null);
      queryClient.invalidateQueries({ queryKey: ['api-keys'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || 'Erreur lors de la révocation');
    },
  });

  const handleCopy = (key: string) => {
    navigator.clipboard.writeText(key);
    toast.success('Clé copiée dans le presse-papier');
  };

  const handleGenerate = () => {
    if (!newKeyName.trim()) {
      toast.error('Le nom de la clé est requis');
      return;
    }
    generateMutation.mutate(newKeyName.trim());
  };

  const closeGenerateModal = () => {
    setGenerateOpen(false);
    setNewKeyName('');
    setGeneratedKey(null);
  };

  return (
    <div className="max-w-6xl mx-auto px-4 py-8">
      {/* Header */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between mb-8 gap-4">
        <div>
          <h1 className="text-2xl font-bold text-ink">
            <span className="text-accent font-normal" aria-hidden="true">:: </span>
            Clés API
          </h1>
          <p className="text-ink-soft mt-1">Gérez vos clés d'accès API</p>
        </div>
        {isAdmin && (
          <button
            onClick={() => setGenerateOpen(true)}
            className="flex items-center gap-2 bg-accent text-white px-4 py-2 rounded-none font-medium hover:bg-accent-strong transition-colors"
          >
            <Plus size={18} />
            Generate New Key
          </button>
        )}
      </div>

      {/* Keys table */}
      <div className="relative bg-surface rounded-none border border-line overflow-hidden">
        <span className="hud-corner hud-corner-tl" aria-hidden="true" />
        <span className="hud-corner hud-corner-tr" aria-hidden="true" />
        <span className="hud-corner hud-corner-bl" aria-hidden="true" />
        <span className="hud-corner hud-corner-br" aria-hidden="true" />
        <div className="px-6 py-4 border-b border-line flex items-center gap-2">
          <Key size={18} className="text-accent" />
          <h2 className="text-lg font-semibold text-ink">Clés d'API</h2>
        </div>

        {isLoading ? (
          <div className="px-6 py-12 text-center text-ink-soft">
            <Loader2 size={24} className="animate-spin mx-auto mb-2 text-ink-soft" />
            Chargement...
          </div>
        ) : keys.length === 0 ? (
          <div className="px-6 py-12 text-center text-ink-soft">
            <Key size={32} className="mx-auto mb-3 text-ink-soft" />
            <p className="font-medium text-ink-soft">Aucune clé API</p>
            <p className="text-sm text-ink-soft mt-1">Générez une clé pour accéder à l'API IncoKalk</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="bg-surface-2 border-b border-line">
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Nom</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Clé</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Statut</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Créée le</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Dernier usage</th>
                  {isAdmin && <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Actions</th>}
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {keys.map((keyItem) => (
                  <tr key={keyItem.id} className="hover:bg-surface-2 transition-colors">
                    <td className="px-6 py-4">
                      <span className="text-sm font-medium text-ink">{keyItem.name}</span>
                    </td>
                    <td className="px-6 py-4">
                      <span className="text-sm font-mono text-ink-soft">{keyItem.keyPrefix}••••••••••••</span>
                    </td>
                    <td className="px-6 py-4">
                      <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${statusColors[keyStatus(keyItem)]}`}>
                        {statusLabels[keyStatus(keyItem)]}
                      </span>
                    </td>
                    <td className="px-6 py-4 text-sm text-ink-soft">
                      {new Date(keyItem.createdAt).toLocaleDateString('fr-FR')}
                    </td>
                    <td className="px-6 py-4 text-sm text-ink-soft">
                      {keyItem.lastUsed ? new Date(keyItem.lastUsed).toLocaleDateString('fr-FR') : '—'}
                    </td>
                    {isAdmin && (
                      <td className="px-6 py-4 text-right">
                        {keyStatus(keyItem) === 'ACTIVE' && (
                          deleteConfirm === keyItem.id ? (
                            <div className="flex items-center justify-end gap-1">
                              <span className="text-xs text-ink-soft mr-1">Confirmer ?</span>
                              <button
                                onClick={() => revokeMutation.mutate(keyItem.id)}
                                disabled={revokeMutation.isPending}
                                className="px-2 py-1 text-xs bg-danger text-white rounded hover:bg-danger/90 transition-colors"
                              >
                                {revokeMutation.isPending ? <Loader2 size={12} className="animate-spin" /> : 'Oui'}
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
                              onClick={() => setDeleteConfirm(keyItem.id)}
                              className="p-1.5 rounded-none text-ink-soft hover:text-danger hover:bg-danger/10 transition-colors"
                              title="Révoquer"
                            >
                              <Trash2 size={16} />
                            </button>
                          )
                        )}
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Generate Key Modal */}
      {generateOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div className="absolute inset-0 bg-black/40" onClick={closeGenerateModal} />
          <div className="relative bg-surface rounded-none shadow-2xl w-full max-w-md mx-4 p-6">
            {generatedKey ? (
              <>
                <h3 className="text-lg font-semibold text-ink mb-2">Clé API générée</h3>
                <p className="text-sm text-ink-soft mb-4">
                  Copiez cette clé maintenant. Elle ne sera plus jamais affichée.
                </p>
                <div className="bg-surface-2 border border-line rounded-none p-4 mb-4">
                  <div className="flex items-center justify-between gap-2">
                    <code className="text-sm font-mono text-ink break-all select-all">
                      {generatedKey}
                    </code>
                    <button
                      onClick={() => handleCopy(generatedKey)}
                      className="p-2 bg-accent text-white rounded-none hover:bg-accent-strong transition-colors flex-shrink-0"
                      title="Copier"
                    >
                      <Copy size={16} />
                    </button>
                  </div>
                </div>
                <button
                  onClick={closeGenerateModal}
                  className="w-full px-4 py-2 bg-surface-2 text-ink-soft rounded-none hover:bg-line transition-colors text-sm font-medium"
                >
                  Fermer
                </button>
              </>
            ) : (
              <>
                <h3 className="text-lg font-semibold text-ink mb-4">Générer une nouvelle clé API</h3>
                <div className="space-y-4">
                  <div>
                    <label className="block text-sm font-medium text-ink-soft mb-1">Nom de la clé</label>
                    <input
                      type="text"
                      value={newKeyName}
                      onChange={(e) => setNewKeyName(e.target.value)}
                      className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                      placeholder="Production, Développement, ..."
                      required
                    />
                  </div>
                </div>
                <div className="flex gap-3 pt-4">
                  <button
                    type="button"
                    onClick={closeGenerateModal}
                    className="flex-1 px-4 py-2 border border-line rounded-none text-sm font-medium text-ink-soft hover:bg-surface-2 transition-colors"
                  >
                    Annuler
                  </button>
                  <button
                    onClick={handleGenerate}
                    disabled={generateMutation.isPending}
                    className="flex-1 px-4 py-2 bg-accent text-white rounded-none text-sm font-medium hover:bg-accent-strong disabled:opacity-50 transition-colors flex items-center justify-center gap-2"
                  >
                    {generateMutation.isPending && <Loader2 size={14} className="animate-spin" />}
                    Générer
                  </button>
                </div>
              </>
            )}
          </div>
        </div>
      )}

      {/* Delete/Revoke Confirmation */}
      {deleteConfirm && !generateOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div className="absolute inset-0 bg-black/40" onClick={() => setDeleteConfirm(null)} />
          <div className="relative bg-surface rounded-none shadow-2xl w-full max-w-md mx-4 p-6">
            <h3 className="text-lg font-semibold text-ink mb-2">Révoquer la clé API</h3>
            <p className="text-sm text-ink-soft mb-4">
              Êtes-vous sûr de vouloir révoquer cette clé ? Les applications utilisant cette clé ne pourront plus accéder à l'API.
            </p>
            <div className="flex gap-3">
              <button
                onClick={() => setDeleteConfirm(null)}
                className="flex-1 px-4 py-2 border border-line rounded-none text-sm font-medium text-ink-soft hover:bg-surface-2 transition-colors"
              >
                Annuler
              </button>
              <button
                onClick={() => revokeMutation.mutate(deleteConfirm)}
                disabled={revokeMutation.isPending}
                className="flex-1 px-4 py-2 bg-danger text-white rounded-none text-sm font-medium hover:bg-danger/90 disabled:opacity-50 transition-colors flex items-center justify-center gap-2"
              >
                {revokeMutation.isPending && <Loader2 size={14} className="animate-spin" />}
                Révoquer
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default ApiKeys;
