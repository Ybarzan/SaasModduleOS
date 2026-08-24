import type { AxiosError } from 'axios';
import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Truck,
  Plus,
  Trash2,
  Pencil,
  RefreshCw,
  Loader2,
  ChevronDown,
  ChevronUp,
  AlertTriangle,
  X,
} from 'lucide-react';
import toast from 'react-hot-toast';
import { incokalkAPI } from '../lib/api';
import type { FleetHubConfig, FleetHubConfigFormData, FleetHubVehicle } from '../types';

const EMPTY_FORM: FleetHubConfigFormData = {
  name: '',
  baseUrl: '',
  username: '',
  password: '',
  isActive: true,
};

const FleetHubSettings = () => {
  const queryClient = useQueryClient();
  const [showModal, setShowModal] = useState(false);
  const [editing, setEditing] = useState<FleetHubConfig | null>(null);
  const [form, setForm] = useState<FleetHubConfigFormData>({ ...EMPTY_FORM });
  const [deleteId, setDeleteId] = useState<string | null>(null);
  const [expanded, setExpanded] = useState<string | null>(null);

  const { data, isLoading } = useQuery({
    queryKey: ['fleethub-configs'],
    queryFn: async () => {
      const res = await incokalkAPI.fleetHub.getAll();
      return (res.data as FleetHubConfig[]) || [];
    },
  });

  const configs = data ?? [];

  const { data: vehiclesData, isFetching: vehiclesLoading } = useQuery({
    queryKey: ['fleethub-vehicles', expanded],
    queryFn: async () => {
      const res = await incokalkAPI.fleetHub.vehicles(expanded!);
      return (res.data as FleetHubVehicle[]) || [];
    },
    enabled: !!expanded,
  });

  const createMutation = useMutation({
    mutationFn: (d: FleetHubConfigFormData) => incokalkAPI.fleetHub.create(d),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['fleethub-configs'] });
      toast.success('Configuration créée');
      closeModal();
    },
    onError: (err: AxiosError<{ message?: string }>) => toast.error(err.response?.data?.message || 'Erreur lors de la création'),
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: FleetHubConfigFormData }) => incokalkAPI.fleetHub.update(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['fleethub-configs'] });
      toast.success('Configuration mise à jour');
      closeModal();
    },
    onError: (err: AxiosError<{ message?: string }>) => toast.error(err.response?.data?.message || 'Erreur lors de la mise à jour'),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => incokalkAPI.fleetHub.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['fleethub-configs'] });
      toast.success('Configuration supprimée');
      setDeleteId(null);
    },
    onError: () => toast.error('Erreur lors de la suppression'),
  });

  const testMutation = useMutation({
    mutationFn: (id: string) => incokalkAPI.fleetHub.test(id),
    onSuccess: (res, id) => {
      queryClient.invalidateQueries({ queryKey: ['fleethub-configs'] });
      const success = (res.data as { success: boolean }).success;
      if (success) {
        toast.success('Connexion réussie');
        if (expanded === id) queryClient.invalidateQueries({ queryKey: ['fleethub-vehicles', id] });
      } else {
        toast.error('Échec de la connexion — voir le détail sur la configuration');
      }
    },
    onError: () => toast.error('Erreur lors du test de connexion'),
  });

  const openCreate = () => {
    setEditing(null);
    setForm({ ...EMPTY_FORM });
    setShowModal(true);
  };

  const openEdit = (config: FleetHubConfig) => {
    setEditing(config);
    setForm({ name: config.name, baseUrl: config.baseUrl, username: config.username, password: '', isActive: config.isActive });
    setShowModal(true);
  };

  const closeModal = () => {
    setShowModal(false);
    setEditing(null);
    setForm({ ...EMPTY_FORM });
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.name.trim() || !form.baseUrl.trim() || !form.username.trim()) {
      toast.error('Nom, URL de base et nom d\'utilisateur sont obligatoires');
      return;
    }
    if (!editing && !form.password?.trim()) {
      toast.error('Le mot de passe est obligatoire à la création');
      return;
    }
    if (editing) {
      updateMutation.mutate({ id: editing.id, data: form });
    } else {
      createMutation.mutate(form);
    }
  };

  return (
    <div className="max-w-5xl mx-auto px-4 py-8">
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-2xl font-bold text-ink">
            <span className="text-accent font-normal" aria-hidden="true">:: </span>
            Intégration Fleet Hub
          </h1>
          <p className="text-ink-soft mt-1">
            Position GPS de la flotte propre du client, via l'API REST de fleet-hub — jamais d'accès
            direct à sa base.
          </p>
        </div>
        <button
          onClick={openCreate}
          className="flex items-center gap-2 bg-accent text-white px-4 py-2 rounded-none hover:bg-accent-strong transition-colors"
        >
          <Plus size={18} />
          Nouvelle configuration
        </button>
      </div>

      {isLoading ? (
        <div className="px-6 py-12 text-center text-ink-soft">
          <Loader2 size={24} className="animate-spin mx-auto mb-2 text-ink-soft" />
          Chargement...
        </div>
      ) : configs.length === 0 ? (
        <div className="bg-surface rounded-none border border-line px-6 py-12 text-center text-ink-soft">
          <Truck size={32} className="mx-auto mb-3 text-ink-soft" />
          <p>Aucune configuration fleet-hub</p>
          <p className="text-sm text-ink-soft mt-1">
            Ajoutez les identifiants d'un compte de service fleet-hub pour suivre votre flotte propre.
          </p>
        </div>
      ) : (
        <div className="space-y-3">
          {configs.map((config) => {
            const isExpanded = expanded === config.id;
            return (
              <div key={config.id} className="bg-surface rounded-none border border-line overflow-hidden">
                <div
                  className="flex items-center justify-between px-5 py-4 cursor-pointer hover:bg-bg transition-colors"
                  onClick={() => setExpanded(isExpanded ? null : config.id)}
                >
                  <div className="flex items-center gap-3 min-w-0">
                    {isExpanded ? <ChevronUp size={16} className="text-ink-soft shrink-0" /> : <ChevronDown size={16} className="text-ink-soft shrink-0" />}
                    <div className="min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-medium text-ink truncate">{config.name}</span>
                        <span
                          className={`text-[10px] font-medium uppercase tracking-wide border rounded-none px-1.5 py-0.5 ${
                            config.isActive ? 'text-success border-success/40' : 'text-ink-soft border-line'
                          }`}
                        >
                          [{config.isActive ? 'ACTIF' : 'INACTIF'}]
                        </span>
                      </div>
                      <p className="text-xs text-ink-soft truncate">{config.baseUrl} · {config.username}</p>
                      {config.lastError && (
                        <p className="text-xs text-danger flex items-center gap-1 mt-1">
                          <AlertTriangle size={12} />
                          {config.lastError}
                        </p>
                      )}
                    </div>
                  </div>
                  <div className="flex items-center gap-2 shrink-0" onClick={(e) => e.stopPropagation()}>
                    <button
                      onClick={() => testMutation.mutate(config.id)}
                      disabled={testMutation.isPending}
                      className="p-2 text-ink-soft hover:text-accent hover:bg-accent-soft rounded-none transition-colors disabled:opacity-50"
                      title="Tester la connexion"
                    >
                      {testMutation.isPending ? <Loader2 size={16} className="animate-spin" /> : <RefreshCw size={16} />}
                    </button>
                    <button
                      onClick={() => openEdit(config)}
                      className="p-2 text-ink-soft hover:text-accent hover:bg-accent-soft rounded-none transition-colors"
                      title="Modifier"
                    >
                      <Pencil size={16} />
                    </button>
                    <button
                      onClick={() => setDeleteId(config.id)}
                      className="p-2 text-ink-soft hover:text-danger hover:bg-danger/10 rounded-none transition-colors"
                      title="Supprimer"
                    >
                      <Trash2 size={16} />
                    </button>
                  </div>
                </div>

                {isExpanded && (
                  <div className="border-t border-line bg-bg px-5 py-4">
                    <p className="text-xs font-medium text-ink-soft uppercase tracking-wider mb-3">
                      Véhicules de la flotte
                    </p>
                    {vehiclesLoading ? (
                      <div className="text-center py-4">
                        <Loader2 size={16} className="animate-spin mx-auto text-ink-soft" />
                      </div>
                    ) : !vehiclesData || vehiclesData.length === 0 ? (
                      <p className="text-sm text-ink-soft">Aucun véhicule avec position GPS disponible</p>
                    ) : (
                      <div className="overflow-x-auto">
                        <table className="w-full text-sm">
                          <thead>
                            <tr className="text-left">
                              <th className="text-xs font-medium text-ink-soft uppercase tracking-wider py-2 pr-4">Immatriculation</th>
                              <th className="text-xs font-medium text-ink-soft uppercase tracking-wider py-2 pr-4">Véhicule</th>
                              <th className="text-xs font-medium text-ink-soft uppercase tracking-wider py-2 pr-4">Chauffeur</th>
                              <th className="text-xs font-medium text-ink-soft uppercase tracking-wider py-2 pr-4">Statut</th>
                              <th className="text-xs font-medium text-ink-soft uppercase tracking-wider py-2">Dernière position</th>
                            </tr>
                          </thead>
                          <tbody className="divide-y divide-line">
                            {vehiclesData.map((v) => (
                              <tr key={v.truckId}>
                                <td className="py-2 pr-4 font-mono text-ink">{v.registration}</td>
                                <td className="py-2 pr-4 text-ink-soft">{[v.brand, v.model].filter(Boolean).join(' ') || '—'}</td>
                                <td className="py-2 pr-4 text-ink-soft">{v.driverName || '—'}</td>
                                <td className="py-2 pr-4 text-ink-soft">{v.status}</td>
                                <td className="py-2 text-ink-soft">
                                  {v.lastGpsUpdate ? new Date(v.lastGpsUpdate).toLocaleString('fr-FR') : '—'}
                                </td>
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
          })}
        </div>
      )}

      {/* Create/Edit modal */}
      {showModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div className="absolute inset-0 bg-black/40" onClick={closeModal} />
          <div className="relative bg-surface rounded-none border border-line shadow-2xl w-full max-w-md mx-4 p-6">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-lg font-semibold text-ink">
                {editing ? 'Modifier la configuration' : 'Nouvelle configuration'}
              </h3>
              <button onClick={closeModal} className="p-1.5 text-ink-soft hover:bg-surface-2 rounded-none transition-colors">
                <X size={18} />
              </button>
            </div>
            <form onSubmit={handleSubmit} className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-ink mb-1">Nom</label>
                <input
                  type="text"
                  value={form.name}
                  onChange={(e) => setForm({ ...form, name: e.target.value })}
                  className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                  placeholder="Flotte principale"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-ink mb-1">URL de base</label>
                <input
                  type="url"
                  value={form.baseUrl}
                  onChange={(e) => setForm({ ...form, baseUrl: e.target.value })}
                  className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                  placeholder="https://fleethub.example.com"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-ink mb-1">Nom d'utilisateur</label>
                <input
                  type="text"
                  value={form.username}
                  onChange={(e) => setForm({ ...form, username: e.target.value })}
                  className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                  placeholder="integration@acme.io"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-ink mb-1">
                  Mot de passe {editing && <span className="text-ink-soft font-normal">(laisser vide pour conserver l'actuel)</span>}
                </label>
                <input
                  type="password"
                  value={form.password || ''}
                  onChange={(e) => setForm({ ...form, password: e.target.value })}
                  className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                  placeholder="Compte de service sans 2FA activée"
                />
              </div>
              <label className="flex items-center gap-2 text-sm text-ink cursor-pointer">
                <input
                  type="checkbox"
                  checked={form.isActive}
                  onChange={(e) => setForm({ ...form, isActive: e.target.checked })}
                  className="w-4 h-4 text-accent rounded focus:ring-accent"
                />
                Active
              </label>
              <div className="flex gap-3 pt-2">
                <button
                  type="button"
                  onClick={closeModal}
                  className="flex-1 px-4 py-2 border border-line rounded-none text-sm font-medium text-ink hover:bg-bg transition-colors"
                >
                  Annuler
                </button>
                <button
                  type="submit"
                  disabled={createMutation.isPending || updateMutation.isPending}
                  className="flex-1 px-4 py-2 bg-accent text-white rounded-none text-sm font-medium hover:bg-accent-strong disabled:opacity-50 transition-colors flex items-center justify-center gap-2"
                >
                  {(createMutation.isPending || updateMutation.isPending) && <Loader2 size={14} className="animate-spin" />}
                  {editing ? 'Mettre à jour' : 'Créer'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Delete confirmation */}
      {deleteId && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div className="absolute inset-0 bg-black/40" onClick={() => setDeleteId(null)} />
          <div className="relative bg-surface rounded-none border border-line shadow-2xl w-full max-w-sm mx-4 p-6">
            <h3 className="text-lg font-semibold text-ink mb-2">Supprimer la configuration</h3>
            <p className="text-sm text-ink-soft mb-4">
              Le suivi GPS des expéditions assignées à un camion de cette flotte cessera de fonctionner.
            </p>
            <div className="flex gap-3">
              <button
                onClick={() => setDeleteId(null)}
                className="flex-1 px-4 py-2 border border-line rounded-none text-sm font-medium text-ink hover:bg-bg transition-colors"
              >
                Annuler
              </button>
              <button
                onClick={() => deleteMutation.mutate(deleteId)}
                disabled={deleteMutation.isPending}
                className="flex-1 px-4 py-2 bg-danger text-white rounded-none text-sm font-medium hover:bg-danger/90 disabled:opacity-50 transition-colors flex items-center justify-center gap-2"
              >
                {deleteMutation.isPending && <Loader2 size={14} className="animate-spin" />}
                Confirmer la suppression
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default FleetHubSettings;
