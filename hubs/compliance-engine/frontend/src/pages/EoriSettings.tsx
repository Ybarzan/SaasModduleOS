import type { AxiosError } from 'axios';
import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Trash2, Star, Loader2, Plus, CheckCircle, AlertCircle, FileText } from 'lucide-react';
import toast from 'react-hot-toast';
import { incokalkAPI } from '../lib/api';
import Pagination from '../components/Pagination';

const PAGE_SIZE = 20;

interface EoriEntry {
  id: string;
  eori: string;
  holderName: string;
  holderAddress: string;
  holderCountry: string;
  type: string;
  isDefault: boolean;
  isValid: boolean;
  createdAt: string;
}

const EORI_REGEX = /^[A-Z]{2}\d{8,15}$/;

const EoriSettings = () => {
  const queryClient = useQueryClient();
  const [formOpen, setFormOpen] = useState(false);
  const [form, setForm] = useState({
    eori: '',
    holderName: '',
    holderAddress: '',
    holderCountry: '',
    isDefault: false,
  });
  const [eoriError, setEoriError] = useState('');
  const [deleteConfirm, setDeleteConfirm] = useState<string | null>(null);
  const [page, setPage] = useState(0);

  const { data: eoriList, isLoading } = useQuery({
    queryKey: ['eori-list', page],
    queryFn: async () => {
      const res = await incokalkAPI.eori.getPage(page, PAGE_SIZE);
      return res.data as EoriEntry[] | { content: EoriEntry[]; totalPages: number };
    },
  });

  const eoris = Array.isArray(eoriList) ? eoriList : eoriList?.content ?? [];
  const totalPages: number = Array.isArray(eoriList) ? 1 : eoriList?.totalPages ?? 1;

  const createMutation = useMutation({
    mutationFn: (data: typeof form) => incokalkAPI.eori.create(data),
    onSuccess: () => {
      toast.success('EORI ajouté avec succès');
      setFormOpen(false);
      setForm({ eori: '', holderName: '', holderAddress: '', holderCountry: '', isDefault: false });
      queryClient.invalidateQueries({ queryKey: ['eori-list'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || "Erreur lors de l'ajout de l'EORI");
    },
  });

  const setDefaultMutation = useMutation({
    mutationFn: (id: string) => incokalkAPI.eori.setDefault(id),
    onSuccess: () => {
      toast.success('EORI par défaut mis à jour');
      queryClient.invalidateQueries({ queryKey: ['eori-list'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || 'Erreur lors de la mise à jour');
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => incokalkAPI.eori.delete(id),
    onSuccess: () => {
      toast.success('EORI supprimé');
      setDeleteConfirm(null);
      queryClient.invalidateQueries({ queryKey: ['eori-list'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || 'Erreur lors de la suppression');
    },
  });

  const handleEoriChange = (value: string) => {
    const upper = value.toUpperCase().replace(/[^A-Z0-9]/g, '');
    setForm({ ...form, eori: upper });
    if (upper && !EORI_REGEX.test(upper)) {
      setEoriError('Format invalide : 2 lettres + 8 à 15 chiffres (ex: FR12345678901)');
    } else {
      setEoriError('');
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!EORI_REGEX.test(form.eori)) {
      setEoriError('Format invalide : 2 lettres + 8 à 15 chiffres (ex: FR12345678901)');
      return;
    }
    try {
      const res = await incokalkAPI.eori.validate(form.eori);
      if (!res.data?.valid) {
        toast.error('EORI invalide selon la validation douanière');
        return;
      }
    } catch {
      toast.error('Impossible de valider l\'EORI');
      return;
    }
    createMutation.mutate(form);
  };

  const defaultEori = eoris.find((e: EoriEntry) => e.isDefault);

  return (
    <div className="max-w-6xl mx-auto px-4 py-8">
      {/* Header */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between mb-8 gap-4">
        <div>
          <h1 className="text-2xl font-bold text-ink">
            <span className="text-accent font-normal" aria-hidden="true">:: </span>
            Configuration EORI
          </h1>
          <p className="text-ink-soft mt-1">Gérez vos numéros EORI pour les opérations douanières EU</p>
        </div>
        <button
          onClick={() => setFormOpen(true)}
          className="flex items-center gap-2 bg-accent text-white px-4 py-2 rounded-none font-medium hover:bg-accent-strong transition-colors"
        >
          <Plus size={18} />
          Ajouter un EORI
        </button>
      </div>

      {/* EORI list */}
      <div className="bg-surface rounded-none border border-line overflow-hidden">
        <div className="px-6 py-4 border-b border-line">
          <h2 className="text-lg font-semibold text-ink">Numéros EORI</h2>
        </div>

        {isLoading ? (
          <div className="px-6 py-12 text-center text-ink-soft">
            <Loader2 size={24} className="animate-spin mx-auto mb-2 text-ink-soft" />
            Chargement...
          </div>
        ) : eoris.length === 0 ? (
          <div className="px-6 py-12 text-center text-ink-soft">
            <FileText size={32} className="mx-auto mb-3 text-ink-soft" />
            <p className="font-medium text-ink-soft">Aucun EORI configuré</p>
            <p className="text-sm text-ink-soft mt-1">
              Ajoutez un numéro EORI pour démarrer les opérations douanières
            </p>
          </div>
        ) : (
          <div className="divide-y divide-line">
            {eoris.map((eori: EoriEntry) => (
              <div key={eori.id} className="px-6 py-4 flex items-center justify-between hover:bg-bg transition-colors">
                <div className="flex items-center gap-4">
                  <div className="w-10 h-10 rounded-none bg-accent-soft flex items-center justify-center text-sm font-bold text-accent-strong">
                    {eori.eori.slice(0, 2)}
                  </div>
                  <div>
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-mono font-semibold text-ink">{eori.eori}</span>
                      {eori.isDefault && (
                        <span className="inline-flex items-center gap-1 text-xs font-medium bg-accent-soft text-accent-strong px-2 py-0.5 rounded-full">
                          <Star size={10} />
                          Par défaut
                        </span>
                      )}
                      {eori.isValid && (
                        <span className="inline-flex items-center gap-1 text-xs font-medium bg-success/10 text-success px-2 py-0.5 rounded-full">
                          <CheckCircle size={10} />
                          Validé
                        </span>
                      )}
                    </div>
                    <p className="text-sm text-ink-soft mt-0.5">{eori.holderName}</p>
                    <p className="text-xs text-ink-soft">
                      {eori.holderAddress && `${eori.holderAddress} · `}
                      {eori.holderCountry}
                    </p>
                  </div>
                </div>

                <div className="flex items-center gap-2 relative">
                  {!eori.isDefault && (
                    <button
                      onClick={() => setDefaultMutation.mutate(eori.id)}
                      disabled={setDefaultMutation.isPending}
                      className="p-1.5 rounded-none text-ink-soft hover:text-accent hover:bg-accent-soft transition-colors disabled:opacity-50"
                      title="Définir par défaut"
                    >
                      <Star size={16} />
                    </button>
                  )}

                  {deleteConfirm === eori.id ? (
                    <div className="flex items-center gap-1">
                      <span className="text-xs text-ink-soft mr-1">Confirmer ?</span>
                      <button
                        onClick={() => deleteMutation.mutate(eori.id)}
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
                      onClick={() => setDeleteConfirm(eori.id)}
                      className="p-1.5 rounded-none text-ink-soft hover:text-danger hover:bg-danger/10 transition-colors"
                      title="Supprimer"
                    >
                      <Trash2 size={16} />
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />

      {/* Default EORI info */}
      {defaultEori && (
        <div className="mt-6 bg-accent-soft border border-accent/20 rounded-none px-4 py-3 flex items-center gap-3">
          <AlertCircle size={18} className="text-accent" />
          <p className="text-sm text-accent-strong">
            EORI par défaut : <span className="font-mono font-semibold">{defaultEori.eori}</span> — {defaultEori.holderName}
          </p>
        </div>
      )}

      {/* Create Modal */}
      {formOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div className="absolute inset-0 bg-black/40" onClick={() => setFormOpen(false)} />
          <div className="relative bg-surface rounded-none shadow-2xl w-full max-w-md mx-4 p-6">
            <h3 className="text-lg font-semibold text-ink mb-4">Ajouter un EORI</h3>
            <form onSubmit={handleSubmit} className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-ink mb-1">Numéro EORI</label>
                <input
                  type="text"
                  value={form.eori}
                  onChange={(e) => handleEoriChange(e.target.value)}
                  className={`w-full px-3 py-2 border rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm font-mono uppercase ${
                    eoriError ? 'border-danger/40' : 'border-line'
                  }`}
                  placeholder="FR12345678901"
                  required
                  maxLength={17}
                />
                {eoriError && (
                  <p className="mt-1 text-xs text-danger flex items-center gap-1">
                    <AlertCircle size={12} />
                    {eoriError}
                  </p>
                )}
              </div>
              <div>
                <label className="block text-sm font-medium text-ink mb-1">Nom du titulaire</label>
                <input
                  type="text"
                  value={form.holderName}
                  onChange={(e) => setForm({ ...form, holderName: e.target.value })}
                  className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                  placeholder="Entreprise Exemple SAS"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-ink mb-1">Adresse du titulaire</label>
                <input
                  type="text"
                  value={form.holderAddress}
                  onChange={(e) => setForm({ ...form, holderAddress: e.target.value })}
                  className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                  placeholder="123 Rue de l'Exemple, 75001 Paris"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-ink mb-1">Pays du titulaire (2 lettres)</label>
                <input
                  type="text"
                  value={form.holderCountry}
                  onChange={(e) => setForm({ ...form, holderCountry: e.target.value.toUpperCase().slice(0, 2) })}
                  className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm font-mono uppercase"
                  placeholder="FR"
                  maxLength={2}
                />
              </div>
              <label className="flex items-center gap-2 cursor-pointer">
                <input
                  type="checkbox"
                  checked={form.isDefault}
                  onChange={(e) => setForm({ ...form, isDefault: e.target.checked })}
                  className="w-4 h-4 text-accent border-line rounded focus:ring-accent"
                />
                <span className="text-sm text-ink">Définir comme EORI par défaut</span>
              </label>
              <div className="flex gap-3 pt-2">
                <button
                  type="button"
                  onClick={() => setFormOpen(false)}
                  className="flex-1 px-4 py-2 border border-line rounded-none text-sm font-medium text-ink hover:bg-bg transition-colors"
                >
                  Annuler
                </button>
                <button
                  type="submit"
                  disabled={createMutation.isPending || !!eoriError}
                  className="flex-1 px-4 py-2 bg-accent text-white rounded-none text-sm font-medium hover:bg-accent-strong disabled:opacity-50 transition-colors flex items-center justify-center gap-2"
                >
                  {createMutation.isPending && <Loader2 size={14} className="animate-spin" />}
                  Ajouter
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default EoriSettings;
