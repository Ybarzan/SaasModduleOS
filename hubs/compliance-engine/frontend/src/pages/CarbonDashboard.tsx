import type { AxiosError } from 'axios';
import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Leaf, TrendingDown, TrendingUp, DollarSign, Trash2, Loader2, AlertCircle, Plus } from 'lucide-react';
import toast from 'react-hot-toast';
import { incokalkAPI } from '../lib/api';
import { formatNumber } from '../lib/formatNumber';

interface CarbonOffset {
  id: string;
  co2EmissionsKg: number;
  offsetCreditsPurchased: number;
  offsetCreditsRetired: number;
  offsetProvider: string;
  offsetProjectName: string;
  offsetProjectType: string;
  offsetCostPerTon: number;
  offsetTotalCost: number;
  offsetCurrency: string;
  certificationId: string;
  retiredAt: string;
  status: string;
  notes: string;
  createdAt: string;
}

interface CarbonOffsetInput {
  co2EmissionsKg: number;
  offsetCreditsPurchased: number;
  offsetProvider: string;
  offsetProjectName: string;
  offsetProjectType: string;
  offsetCostPerTon: number;
  offsetTotalCost: number;
  offsetCurrency: string;
  certificationId: string;
  notes: string;
}

interface DashboardStats {
  totalEmissions: number;
  totalOffset: number;
  netEmissions: number;
  offsetPercent: number;
  totalCost: number;
}

const CarbonDashboard = () => {
  const queryClient = useQueryClient();
  const [deleteConfirm, setDeleteConfirm] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({
    co2EmissionsKg: '',
    offsetCreditsPurchased: '',
    offsetProvider: '',
    offsetProjectName: '',
    offsetProjectType: 'reforestation',
    offsetCostPerTon: '',
    offsetTotalCost: '',
    offsetCurrency: 'EUR',
    certificationId: '',
    notes: '',
  });

  const { data: offsetsData, isLoading: offsetsLoading } = useQuery({
    queryKey: ['carbon-offsets'],
    queryFn: async () => {
      const res = await incokalkAPI.carbonOffsets.list();
      return res.data as CarbonOffset[] | { offsets: CarbonOffset[] };
    },
  });

  const offsets = Array.isArray(offsetsData) ? offsetsData : offsetsData?.offsets ?? [];

  const { data: dashboardStats } = useQuery({
    queryKey: ['carbon-dashboard-stats'],
    queryFn: async () => {
      const res = await incokalkAPI.carbonOffsets.dashboard();
      return res.data as DashboardStats;
    },
  });

  const createMutation = useMutation({
    mutationFn: (data: CarbonOffsetInput) => incokalkAPI.carbonOffsets.create(data),
    onSuccess: () => {
      toast.success('Enregistrement créé avec succès');
      setShowForm(false);
      setForm({
        co2EmissionsKg: '',
        offsetCreditsPurchased: '',
        offsetProvider: '',
        offsetProjectName: '',
        offsetProjectType: 'reforestation',
        offsetCostPerTon: '',
        offsetTotalCost: '',
        offsetCurrency: 'EUR',
        certificationId: '',
        notes: '',
      });
      queryClient.invalidateQueries({ queryKey: ['carbon-offsets'] });
      queryClient.invalidateQueries({ queryKey: ['carbon-dashboard-stats'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || 'Erreur lors de la création');
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => incokalkAPI.carbonOffsets.delete(id),
    onSuccess: () => {
      toast.success('Enregistrement supprimé');
      setDeleteConfirm(null);
      queryClient.invalidateQueries({ queryKey: ['carbon-offsets'] });
      queryClient.invalidateQueries({ queryKey: ['carbon-dashboard-stats'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || 'Erreur lors de la suppression');
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    createMutation.mutate({
      co2EmissionsKg: Number(form.co2EmissionsKg),
      offsetCreditsPurchased: Number(form.offsetCreditsPurchased),
      offsetProvider: form.offsetProvider,
      offsetProjectName: form.offsetProjectName,
      offsetProjectType: form.offsetProjectType,
      offsetCostPerTon: Number(form.offsetCostPerTon),
      offsetTotalCost: Number(form.offsetTotalCost),
      offsetCurrency: form.offsetCurrency,
      certificationId: form.certificationId,
      notes: form.notes,
    });
  };

  const statusBadge = (status: string) => {
    const map: Record<string, string> = {
      TRACKING: 'bg-accent-soft text-accent-strong',
      CREDITS_PURCHASED: 'bg-warning/10 text-warning',
      OFFSETTED: 'bg-success/10 text-success',
      PARTIAL: 'bg-warning/10 text-warning',
    };
    return map[status] || 'bg-surface-2 text-ink';
  };

  const stats = dashboardStats;

  return (
    <div className="max-w-6xl mx-auto px-4 py-8">
      {/* Header */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between mb-8 gap-4">
        <div>
          <h1 className="text-2xl font-bold text-ink">
            <span className="text-accent font-normal" aria-hidden="true">:: </span>
            Dashboard carbone
          </h1>
          <p className="text-ink-soft mt-1">Suivi des émissions CO2 et crédits d'offset</p>
        </div>
        <button
          onClick={() => setShowForm(!showForm)}
          className="flex items-center gap-2 bg-accent text-white px-4 py-2 rounded-none font-medium hover:bg-accent-strong transition-colors"
        >
          <Plus size={18} />
          {showForm ? 'Annuler' : 'Nouvel enregistrement'}
        </button>
      </div>

      {/* Stats cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        <div className="bg-surface rounded-none border border-line p-5">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-none bg-danger/10 flex items-center justify-center">
              <TrendingUp size={20} className="text-danger" />
            </div>
            <div>
              <p className="text-sm text-ink-soft">Émissions totales</p>
              <p className="text-2xl font-bold text-ink">{stats?.totalEmissions != null ? formatNumber(stats.totalEmissions) : '—'} kg CO2</p>
            </div>
          </div>
        </div>
        <div className="bg-surface rounded-none border border-line p-5">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-none bg-accent-soft flex items-center justify-center">
              <Leaf size={20} className="text-accent" />
            </div>
            <div>
              <p className="text-sm text-ink-soft">Crédits achetés</p>
              <p className="text-2xl font-bold text-ink">{stats?.totalOffset != null ? formatNumber(stats.totalOffset) : '—'} kg</p>
            </div>
          </div>
        </div>
        <div className="bg-surface rounded-none border border-line p-5">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-none bg-success/10 flex items-center justify-center">
              <TrendingDown size={20} className="text-success" />
            </div>
            <div>
              <p className="text-sm text-ink-soft">Crédits utilisés</p>
              <p className="text-2xl font-bold text-ink">{formatNumber(stats?.totalOffset && stats?.totalEmissions ? stats.totalOffset : 0)} kg</p>
            </div>
          </div>
        </div>
        <div className="bg-surface rounded-none border border-line p-5">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-none bg-warning/10 flex items-center justify-center">
              <AlertCircle size={20} className="text-warning" />
            </div>
            <div>
              <p className="text-sm text-ink-soft">Émissions nettes</p>
              <p className={`text-2xl font-bold ${(stats?.netEmissions ?? 0) > 0 ? 'text-danger' : 'text-success'}`}>
                {stats?.netEmissions != null ? formatNumber(stats.netEmissions) : '—'} kg
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* Progress bar */}
      <div className="bg-surface rounded-none border border-line p-5 mb-8">
        <div className="flex items-center justify-between mb-2">
          <h3 className="text-sm font-medium text-ink">Progression de l'offset</h3>
          <span className="text-sm font-semibold text-ink">{stats?.offsetPercent?.toFixed(1) ?? '0'}%</span>
        </div>
        <div className="w-full bg-surface-2 rounded-full h-3">
          <div
            className="bg-success h-3 rounded-full transition-all duration-500"
            style={{ width: `${Math.min(stats?.offsetPercent ?? 0, 100)}%` }}
          />
        </div>
      </div>

      {/* Cost summary */}
      <div className="bg-surface rounded-none border border-line p-5 mb-8">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-none bg-accent/10 flex items-center justify-center">
            <DollarSign size={20} className="text-accent" />
          </div>
          <div>
            <p className="text-sm text-ink-soft">Coût total des offsets</p>
            <p className="text-2xl font-bold text-ink">{stats?.totalCost != null ? formatNumber(stats.totalCost) : '—'} EUR</p>
          </div>
        </div>
      </div>

      {/* Create form */}
      {showForm && (
        <div className="bg-surface rounded-none border border-line p-6 mb-8">
          <h2 className="text-lg font-semibold text-ink mb-4">Nouvel enregistrement carbone</h2>
          <form onSubmit={handleSubmit} className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-ink mb-1">Émissions CO2 (kg)</label>
              <input
                type="number"
                value={form.co2EmissionsKg}
                onChange={(e) => setForm({ ...form, co2EmissionsKg: e.target.value })}
                className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                placeholder="0"
                required
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-ink mb-1">Crédits achetés (kg)</label>
              <input
                type="number"
                value={form.offsetCreditsPurchased}
                onChange={(e) => setForm({ ...form, offsetCreditsPurchased: e.target.value })}
                className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                placeholder="0"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-ink mb-1">Fournisseur offset</label>
              <input
                type="text"
                value={form.offsetProvider}
                onChange={(e) => setForm({ ...form, offsetProvider: e.target.value })}
                className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                placeholder="Nom du fournisseur"
                required
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-ink mb-1">Nom du projet</label>
              <input
                type="text"
                value={form.offsetProjectName}
                onChange={(e) => setForm({ ...form, offsetProjectName: e.target.value })}
                className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                placeholder="Nom du projet"
                required
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-ink mb-1">Type de projet</label>
              <select
                value={form.offsetProjectType}
                onChange={(e) => setForm({ ...form, offsetProjectType: e.target.value })}
                className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
              >
                <option value="reforestation">Reforestation</option>
                <option value="solaire">Solaire</option>
                <option value="capture méthane">Capture méthane</option>
                <option value="éolien">Éolien</option>
                <option value="autre">Autre</option>
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-ink mb-1">Coût par tonne</label>
              <input
                type="number"
                value={form.offsetCostPerTon}
                onChange={(e) => setForm({ ...form, offsetCostPerTon: e.target.value })}
                className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                placeholder="0.00"
                step="0.01"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-ink mb-1">Coût total</label>
              <input
                type="number"
                value={form.offsetTotalCost}
                onChange={(e) => setForm({ ...form, offsetTotalCost: e.target.value })}
                className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                placeholder="0.00"
                step="0.01"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-ink mb-1">Devise</label>
              <input
                type="text"
                value={form.offsetCurrency}
                onChange={(e) => setForm({ ...form, offsetCurrency: e.target.value })}
                className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                placeholder="EUR"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-ink mb-1">Numéro certification</label>
              <input
                type="text"
                value={form.certificationId}
                onChange={(e) => setForm({ ...form, certificationId: e.target.value })}
                className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                placeholder="ID de certification"
              />
            </div>
            <div className="sm:col-span-2">
              <label className="block text-sm font-medium text-ink mb-1">Notes</label>
              <textarea
                value={form.notes}
                onChange={(e) => setForm({ ...form, notes: e.target.value })}
                className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                rows={3}
                placeholder="Notes supplémentaires..."
              />
            </div>
            <div className="sm:col-span-2 flex gap-3 pt-2">
              <button
                type="button"
                onClick={() => setShowForm(false)}
                className="flex-1 px-4 py-2 border border-line rounded-none text-sm font-medium text-ink hover:bg-bg transition-colors"
              >
                Annuler
              </button>
              <button
                type="submit"
                disabled={createMutation.isPending}
                className="flex-1 px-4 py-2 bg-accent text-white rounded-none text-sm font-medium hover:bg-accent-strong disabled:opacity-50 transition-colors flex items-center justify-center gap-2"
              >
                {createMutation.isPending && <Loader2 size={14} className="animate-spin" />}
                Enregistrer
              </button>
            </div>
          </form>
        </div>
      )}

      {/* History table */}
      <div className="bg-surface rounded-none border border-line overflow-hidden">
        <div className="px-6 py-4 border-b border-line">
          <h2 className="text-lg font-semibold text-ink">Historique des offsets</h2>
        </div>

        {offsetsLoading ? (
          <div className="px-6 py-12 text-center text-ink-soft">
            <Loader2 size={24} className="animate-spin mx-auto mb-2 text-ink-soft" />
            Chargement...
          </div>
        ) : offsets.length === 0 ? (
          <div className="px-6 py-12 text-center text-ink-soft">
            <Leaf size={32} className="mx-auto mb-3 text-ink-soft" />
            <p>Aucun enregistrement carbone</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="bg-bg border-b border-line">
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Date</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Émissions (kg)</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Fournisseur</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Projet</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Type</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Coût</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Statut</th>
                  <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {offsets.map((offset: CarbonOffset) => (
                  <tr key={offset.id} className="hover:bg-bg transition-colors">
                    <td className="px-6 py-4 text-sm text-ink-soft">
                      {offset.createdAt ? new Date(offset.createdAt).toLocaleDateString('fr-FR') : '—'}
                    </td>
                    <td className="px-6 py-4 text-sm text-ink font-medium">
                      {offset.co2EmissionsKg != null ? formatNumber(offset.co2EmissionsKg) : ''}
                    </td>
                    <td className="px-6 py-4 text-sm text-ink-soft">{offset.offsetProvider}</td>
                    <td className="px-6 py-4 text-sm text-ink-soft">{offset.offsetProjectName}</td>
                    <td className="px-6 py-4 text-sm text-ink-soft">{offset.offsetProjectType}</td>
                    <td className="px-6 py-4 text-sm text-ink-soft">
                      {offset.offsetTotalCost != null ? formatNumber(offset.offsetTotalCost) : ''} {offset.offsetCurrency}
                    </td>
                    <td className="px-6 py-4">
                      <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${statusBadge(offset.status)}`}>
                        {offset.status}
                      </span>
                    </td>
                    <td className="px-6 py-4 text-right">
                      {deleteConfirm === offset.id ? (
                        <div className="flex items-center justify-end gap-1">
                          <span className="text-xs text-ink-soft mr-1">Confirmer ?</span>
                          <button
                            onClick={() => deleteMutation.mutate(offset.id)}
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
                          onClick={() => setDeleteConfirm(offset.id)}
                          className="p-1.5 rounded-none text-ink-soft hover:text-danger hover:bg-danger/10 transition-colors"
                          title="Supprimer"
                        >
                          <Trash2 size={16} />
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
};

export default CarbonDashboard;