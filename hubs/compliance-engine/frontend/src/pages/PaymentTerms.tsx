import type { AxiosError } from 'axios';
import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Calendar, DollarSign, Percent, Plus, Trash2, CheckCircle, Loader2 } from 'lucide-react';
import toast from 'react-hot-toast';
import { incokalkAPI } from '../lib/api';
import { useAuthStore } from '../stores/auth';

interface PaymentTerm {
  id: string;
  name: string;
  code: string;
  daysUntilDue: number;
  earlyPaymentDiscountPercent: number;
  earlyPaymentDiscountDays: number;
  default: boolean;
}

const PaymentTerms = () => {
  const queryClient = useQueryClient();
  const isAdmin = useAuthStore((s) => s.isAdmin());
  const [showForm, setShowForm] = useState(false);
  const [editingTerm, setEditingTerm] = useState<PaymentTerm | null>(null);
  const [deleteConfirm, setDeleteConfirm] = useState<string | null>(null);
  const [form, setForm] = useState({
    name: '',
    code: '',
    daysUntilDue: 30,
    earlyPaymentDiscountPercent: 0,
    earlyPaymentDiscountDays: 0,
    isDefault: false,
  });

  const { data: termsData, isLoading } = useQuery({
    queryKey: ['payment-terms'],
    queryFn: async () => {
      const res = await incokalkAPI.paymentTerms.list();
      return (res?.data ?? []) as PaymentTerm[];
    },
  });

  const terms = Array.isArray(termsData) ? termsData : [];

  const saveMutation = useMutation({
    mutationFn: (data: { id?: string; name: string; code: string; daysUntilDue: number; earlyPaymentDiscountPercent: number; earlyPaymentDiscountDays: number; isDefault: boolean }) => {
      if (data.id) return incokalkAPI.paymentTerms.update(data.id, data);
      return incokalkAPI.paymentTerms.create(data);
    },
    onSuccess: () => {
      toast.success(editingTerm ? 'Condition mise à jour' : 'Condition créée');
      setShowForm(false);
      setEditingTerm(null);
      setForm({ name: '', code: '', daysUntilDue: 30, earlyPaymentDiscountPercent: 0, earlyPaymentDiscountDays: 0, isDefault: false });
      queryClient.invalidateQueries({ queryKey: ['payment-terms'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || 'Erreur lors de la sauvegarde');
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => incokalkAPI.paymentTerms.delete(id),
    onSuccess: () => {
      toast.success('Condition de paiement supprimée');
      setDeleteConfirm(null);
      queryClient.invalidateQueries({ queryKey: ['payment-terms'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || 'Erreur lors de la suppression');
    },
  });

  const setDefaultMutation = useMutation({
    mutationFn: (term: PaymentTerm) => incokalkAPI.paymentTerms.update(term.id, {
      name: term.name,
      code: term.code,
      daysUntilDue: term.daysUntilDue,
      earlyPaymentDiscountPercent: term.earlyPaymentDiscountPercent,
      earlyPaymentDiscountDays: term.earlyPaymentDiscountDays,
      isDefault: true,
    }),
    onSuccess: () => {
      toast.success('Condition définie par défaut');
      queryClient.invalidateQueries({ queryKey: ['payment-terms'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || 'Erreur');
    },
  });

  const openAddForm = () => {
    setEditingTerm(null);
    setForm({ name: '', code: '', daysUntilDue: 30, earlyPaymentDiscountPercent: 0, earlyPaymentDiscountDays: 0, isDefault: false });
    setShowForm(true);
  };

  const openEditForm = (term: PaymentTerm) => {
    setEditingTerm(term);
    setForm({
      name: term.name,
      code: term.code,
      daysUntilDue: term.daysUntilDue,
      earlyPaymentDiscountPercent: term.earlyPaymentDiscountPercent,
      earlyPaymentDiscountDays: term.earlyPaymentDiscountDays,
      isDefault: term.default,
    });
    setShowForm(true);
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    saveMutation.mutate({ id: editingTerm?.id, ...form });
  };

  return (
    <div className="max-w-6xl mx-auto px-4 py-8">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between mb-8 gap-4">
        <div>
          <h1 className="text-2xl font-bold text-ink">
            <span className="text-accent font-normal" aria-hidden="true">:: </span>
            Conditions de paiement
          </h1>
          <p className="text-ink-soft mt-1">Gérez les termes de paiement</p>
        </div>
        {isAdmin && (
          <button
            onClick={openAddForm}
            className="flex items-center gap-2 bg-accent text-white px-4 py-2 rounded-none font-medium hover:bg-accent-strong transition-colors"
          >
            <Plus size={18} />
            Nouveau terme
          </button>
        )}
      </div>

      <div className="bg-surface rounded-none border border-line overflow-hidden">
        <div className="px-6 py-4 border-b border-line">
          <h2 className="text-lg font-semibold text-ink">Liste des conditions</h2>
        </div>

        {isLoading ? (
          <div className="px-6 py-12 text-center text-ink-soft">
            <Loader2 size={24} className="animate-spin mx-auto mb-2 text-ink-soft" />
            Chargement...
          </div>
        ) : terms.length === 0 ? (
          <div className="px-6 py-12 text-center text-ink-soft">
            <Calendar size={32} className="mx-auto mb-3 text-ink-soft" />
            <p>Aucune condition de paiement</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="bg-bg border-b border-line">
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Nom</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Code</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Jours nets</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Escompte %</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Jours escompte</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Statut</th>
                  {isAdmin && <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Actions</th>}
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {terms.map((term) => (
                  <tr key={term.id} className="hover:bg-bg transition-colors">
                    <td className="px-6 py-4">
                      <div className="flex items-center gap-3">
                        <div className="w-8 h-8 rounded-none bg-success/10 flex items-center justify-center">
                          <DollarSign size={16} className="text-success" />
                        </div>
                        <div>
                          <span className="text-sm font-medium text-ink">{term.name}</span>
                          {term.default && (
                            <span className="ml-2 inline-flex items-center gap-1 text-xs bg-accent-soft text-accent-strong px-2 py-0.5 rounded-full">
                              <CheckCircle size={10} />
                              Par défaut
                            </span>
                          )}
                        </div>
                      </div>
                    </td>
                    <td className="px-6 py-4 text-sm font-mono text-ink-soft">{term.code}</td>
                    <td className="px-6 py-4 text-sm text-ink">{term.daysUntilDue} jours</td>
                    <td className="px-6 py-4 text-sm text-ink">
                      {term.earlyPaymentDiscountPercent > 0 ? (
                        <span className="flex items-center gap-1">
                          <Percent size={14} className="text-success" />
                          {term.earlyPaymentDiscountPercent}%
                        </span>
                      ) : (
                        <span className="text-ink-soft">—</span>
                      )}
                    </td>
                    <td className="px-6 py-4 text-sm text-ink">
                      {term.earlyPaymentDiscountDays > 0 ? `${term.earlyPaymentDiscountDays} jours` : <span className="text-ink-soft">—</span>}
                    </td>
                    <td className="px-6 py-4">
                      <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium ${
                        term.default ? 'bg-success/10 text-success' : 'bg-surface-2 text-ink-soft'
                      }`}>
                        {term.default ? 'Actif' : 'Standard'}
                      </span>
                    </td>
                    {isAdmin && (
                      <td className="px-6 py-4 text-right">
                        <div className="flex items-center justify-end gap-1">
                          {!term.default && (
                            <button
                              onClick={() => setDefaultMutation.mutate(term)}
                              disabled={setDefaultMutation.isPending}
                              className="p-1.5 rounded-none text-ink-soft hover:text-accent hover:bg-accent-soft transition-colors"
                              title="Définir par défaut"
                            >
                              <CheckCircle size={16} />
                            </button>
                          )}
                          <button
                            onClick={() => openEditForm(term)}
                            className="p-1.5 rounded-none text-ink-soft hover:text-accent hover:bg-accent-soft transition-colors"
                            title="Modifier"
                          >
                            <Percent size={16} />
                          </button>
                          {deleteConfirm === term.id ? (
                            <div className="flex items-center gap-1">
                              <button
                                onClick={() => deleteMutation.mutate(term.id)}
                                disabled={deleteMutation.isPending}
                                className="px-2 py-1 text-xs bg-danger text-white rounded hover:bg-danger/90 transition-colors"
                              >
                                {deleteMutation.isPending ? <Loader2 size={12} className="animate-spin" /> : 'Confirmer'}
                              </button>
                              <button
                                onClick={() => setDeleteConfirm(null)}
                                className="px-2 py-1 text-xs bg-surface-2 text-ink-soft rounded hover:bg-line transition-colors"
                              >
                                Annuler
                              </button>
                            </div>
                          ) : (
                            <button
                              onClick={() => setDeleteConfirm(term.id)}
                              className="p-1.5 rounded-none text-ink-soft hover:text-danger hover:bg-danger/10 transition-colors"
                              title="Supprimer"
                            >
                              <Trash2 size={16} />
                            </button>
                          )}
                        </div>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Add / Edit modal */}
      {showForm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div className="absolute inset-0 bg-black/40" onClick={() => setShowForm(false)} />
          <div className="relative bg-surface rounded-none shadow-2xl w-full max-w-md mx-4 p-6">
            <h3 className="text-lg font-semibold text-ink mb-4">
              {editingTerm ? 'Modifier la condition' : 'Nouvelle condition de paiement'}
            </h3>
            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">Nom</label>
                  <input
                    type="text"
                    value={form.name}
                    onChange={(e) => setForm({ ...form, name: e.target.value })}
                    className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                    placeholder="30 jours net"
                    required
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">Code</label>
                  <input
                    type="text"
                    value={form.code}
                    onChange={(e) => setForm({ ...form, code: e.target.value })}
                    className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                    placeholder="NET30"
                    required
                  />
                </div>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">Jours nets</label>
                  <input
                    type="number"
                    value={form.daysUntilDue}
                    onChange={(e) => setForm({ ...form, daysUntilDue: parseInt(e.target.value) || 0 })}
                    className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                    min={0}
                    required
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">Escompte %</label>
                  <input
                    type="number"
                    value={form.earlyPaymentDiscountPercent}
                    onChange={(e) => setForm({ ...form, earlyPaymentDiscountPercent: parseFloat(e.target.value) || 0 })}
                    className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                    min={0}
                    max={100}
                    step={0.1}
                  />
                </div>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">Jours d'escompte</label>
                  <input
                    type="number"
                    value={form.earlyPaymentDiscountDays}
                    onChange={(e) => setForm({ ...form, earlyPaymentDiscountDays: parseInt(e.target.value) || 0 })}
                    className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                    min={0}
                  />
                </div>
                <div className="flex items-end pb-2">
                  <label className="flex items-center gap-2 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={form.isDefault}
                      onChange={(e) => setForm({ ...form, isDefault: e.target.checked })}
                      className="rounded border-line text-accent focus:ring-accent"
                    />
                    <span className="text-sm font-medium text-ink">Par défaut</span>
                  </label>
                </div>
              </div>
              <div className="flex gap-3 pt-2">
                <button
                  type="button"
                  onClick={() => setShowForm(false)}
                  className="flex-1 px-4 py-2 border border-line rounded-none text-sm font-medium text-ink hover:bg-bg transition-colors"
                >
                  Annuler
                </button>
                <button
                  type="submit"
                  disabled={saveMutation.isPending}
                  className="flex-1 px-4 py-2 bg-accent text-white rounded-none text-sm font-medium hover:bg-accent-strong disabled:opacity-50 transition-colors flex items-center justify-center gap-2"
                >
                  {saveMutation.isPending && <Loader2 size={14} className="animate-spin" />}
                  {editingTerm ? 'Mettre à jour' : 'Créer'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default PaymentTerms;
