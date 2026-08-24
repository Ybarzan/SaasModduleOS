import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useSearchParams } from 'react-router-dom';
import { Package, Plus, Search, Trash2, Loader2, Barcode } from 'lucide-react';
import toast from 'react-hot-toast';
import { incokalkAPI } from '../lib/api';
import { useAuthStore } from '../stores/auth';

interface InventoryItem {
  id: string;
  sku?: string;
  name: string;
  description?: string;
  hsCode?: string;
  originCountry?: string;
  unit?: string;
  unitPrice?: number;
  category?: string;
  active: boolean;
}

const EMPTY_FORM = {
  name: '',
  sku: '',
  description: '',
  hsCode: '',
  originCountry: '',
  unit: 'PCS',
  unitPrice: 0,
  category: '',
  active: true,
};

type ApiError = { response?: { data?: { message?: string } } };
type ItemPayload = typeof EMPTY_FORM;

const InventoryItems = () => {
  const queryClient = useQueryClient();
  const [searchParams] = useSearchParams();
  const canManage = useAuthStore((s) => s.hasMinimumRole('MANAGER'));
  const [search, setSearch] = useState(searchParams.get('q') ?? '');
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState<InventoryItem | null>(null);
  const [form, setForm] = useState({ ...EMPTY_FORM });
  const [barcodeFor, setBarcodeFor] = useState<InventoryItem | null>(null);
  const [barcodeValue, setBarcodeValue] = useState('');
  const [barcodeType, setBarcodeType] = useState('EAN13');
  const [deleteConfirm, setDeleteConfirm] = useState<string | null>(null);

  const { data, isLoading } = useQuery({
    queryKey: ['inventory-items', search],
    queryFn: async () => (await incokalkAPI.inventory.items.list(search || undefined))?.data ?? [],
  });

  const items = Array.isArray(data) ? data : [];

  const saveMutation = useMutation({
    mutationFn: (payload: ItemPayload) =>
      editing ? incokalkAPI.inventory.items.update(editing.id, payload) : incokalkAPI.inventory.items.create(payload),
    onSuccess: () => {
      toast.success(editing ? 'Article mis à jour' : 'Article créé');
      setShowForm(false);
      setEditing(null);
      setForm({ ...EMPTY_FORM });
      queryClient.invalidateQueries({ queryKey: ['inventory-items'] });
    },
    onError: (err: ApiError) => toast.error(err.response?.data?.message || 'Erreur lors de la sauvegarde'),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => incokalkAPI.inventory.items.delete(id),
    onSuccess: () => {
      toast.success('Article désactivé');
      setDeleteConfirm(null);
      queryClient.invalidateQueries({ queryKey: ['inventory-items'] });
    },
    onError: (err: ApiError) => toast.error(err.response?.data?.message || 'Erreur lors de la suppression'),
  });

  const barcodeMutation = useMutation({
    mutationFn: () => incokalkAPI.inventory.barcodes.add(barcodeFor!.id, { barcode: barcodeValue, type: barcodeType }),
    onSuccess: () => {
      toast.success('Code-barres associé');
      setBarcodeValue('');
      queryClient.invalidateQueries({ queryKey: ['inventory-items'] });
    },
    onError: (err: ApiError) => toast.error(err.response?.data?.message || 'Erreur'),
  });

  const openAdd = () => {
    setEditing(null);
    setForm({ ...EMPTY_FORM });
    setShowForm(true);
  };

  const openEdit = (item: InventoryItem) => {
    setEditing(item);
    setForm({
      name: item.name,
      sku: item.sku ?? '',
      description: item.description ?? '',
      hsCode: item.hsCode ?? '',
      originCountry: item.originCountry ?? '',
      unit: item.unit ?? 'PCS',
      unitPrice: item.unitPrice ?? 0,
      category: item.category ?? '',
      active: item.active,
    });
    setShowForm(true);
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    saveMutation.mutate({ ...form, unitPrice: Number(form.unitPrice) || 0 });
  };

  return (
    <div className="max-w-6xl mx-auto px-4 py-8">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between mb-8 gap-4">
        <div>
          <h1 className="text-2xl font-bold text-ink">
            <span className="text-accent font-normal" aria-hidden="true">:: </span>
            Catalogue articles
          </h1>
          <p className="text-ink-soft mt-1">Références produit, code-barres et données douanières</p>
        </div>
        {canManage && (
          <button
            onClick={openAdd}
            className="flex items-center gap-2 bg-accent text-white px-4 py-2 rounded-none font-medium hover:bg-accent-strong transition-colors"
          >
            <Plus size={18} />
            Nouvel article
          </button>
        )}
      </div>

      <div className="relative mb-6">
        <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-soft" />
        <input
          type="text"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="w-full pl-10 pr-4 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
          placeholder="Rechercher par nom, SKU ou code..."
        />
      </div>

      <div className="bg-surface rounded-none border border-line overflow-hidden">
        {isLoading ? (
          <div className="px-6 py-12 text-center text-ink-soft">
            <Loader2 size={24} className="animate-spin mx-auto mb-2 text-ink-soft" />
            Chargement...
          </div>
        ) : items.length === 0 ? (
          <div className="px-6 py-12 text-center text-ink-soft">
            <Package size={32} className="mx-auto mb-3 text-ink-soft" />
            <p>Aucun article trouvé</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="bg-bg border-b border-line">
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Article</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">SKU</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Code HS</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Origine</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Unité</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Statut</th>
                  {canManage && <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Actions</th>}
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {items.map((item) => (
                  <tr key={item.id} className="hover:bg-bg transition-colors">
                    <td className="px-6 py-4">
                      <div className="flex items-center gap-3">
                        <div className="w-8 h-8 rounded-none bg-accent/10 flex items-center justify-center">
                          <Package size={16} className="text-accent" />
                        </div>
                        <div>
                          <span className="text-sm font-medium text-ink">{item.name}</span>
                          {item.category && (
                            <span className="ml-2 text-xs text-ink-soft">{item.category}</span>
                          )}
                        </div>
                      </div>
                    </td>
                    <td className="px-6 py-4 text-sm font-mono text-ink-soft">{item.sku || '—'}</td>
                    <td className="px-6 py-4 text-sm font-mono text-ink-soft">{item.hsCode || '—'}</td>
                    <td className="px-6 py-4 text-sm text-ink-soft">{item.originCountry || '—'}</td>
                    <td className="px-6 py-4 text-sm text-ink-soft">{item.unit || 'PCS'}</td>
                    <td className="px-6 py-4">
                      <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${
                        item.active ? 'bg-success/10 text-success' : 'bg-surface-2 text-ink-soft'
                      }`}>
                        {item.active ? 'Actif' : 'Inactif'}
                      </span>
                    </td>
                    {canManage && (
                      <td className="px-6 py-4 text-right">
                        <div className="flex items-center justify-end gap-1">
                          <button
                            onClick={() => { setBarcodeFor(item); setBarcodeValue(''); }}
                            className="p-1.5 rounded-none text-ink-soft hover:text-accent hover:bg-accent-soft transition-colors"
                            title="Associer un code-barres"
                          >
                            <Barcode size={16} />
                          </button>
                          <button
                            onClick={() => openEdit(item)}
                            className="p-1.5 rounded-none text-ink-soft hover:text-accent hover:bg-accent-soft transition-colors"
                            title="Modifier"
                          >
                            <Package size={16} />
                          </button>
                          {deleteConfirm === item.id ? (
                            <div className="flex items-center gap-1">
                              <button
                                onClick={() => deleteMutation.mutate(item.id)}
                                disabled={deleteMutation.isPending}
                                className="px-2 py-1 text-xs bg-danger text-white rounded hover:bg-danger/90 transition-colors"
                              >
                                Confirmer
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
                              onClick={() => setDeleteConfirm(item.id)}
                              className="p-1.5 rounded-none text-ink-soft hover:text-danger hover:bg-danger/10 transition-colors"
                              title="Désactiver"
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

      {showForm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div className="absolute inset-0 bg-black/40" onClick={() => setShowForm(false)} />
          <div className="relative bg-surface rounded-none shadow-2xl w-full max-w-lg mx-4 p-6 max-h-[90vh] overflow-y-auto">
            <h3 className="text-lg font-semibold text-ink mb-4">
              {editing ? 'Modifier l’article' : 'Nouvel article'}
            </h3>
            <form onSubmit={handleSubmit} className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-ink mb-1">Nom *</label>
                <input
                  type="text"
                  value={form.name}
                  onChange={(e) => setForm({ ...form, name: e.target.value })}
                  className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                  required
                />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">SKU</label>
                  <input
                    type="text"
                    value={form.sku}
                    onChange={(e) => setForm({ ...form, sku: e.target.value })}
                    className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">Code HS</label>
                  <input
                    type="text"
                    value={form.hsCode}
                    onChange={(e) => setForm({ ...form, hsCode: e.target.value })}
                    className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                    placeholder="84713000"
                  />
                </div>
              </div>
              <div>
                <label className="block text-sm font-medium text-ink mb-1">Description</label>
                <textarea
                  value={form.description}
                  onChange={(e) => setForm({ ...form, description: e.target.value })}
                  className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                  rows={2}
                />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">Origine</label>
                  <input
                    type="text"
                    value={form.originCountry}
                    onChange={(e) => setForm({ ...form, originCountry: e.target.value.toUpperCase() })}
                    className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                    maxLength={3}
                    placeholder="CN"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">Unité</label>
                  <select
                    value={form.unit}
                    onChange={(e) => setForm({ ...form, unit: e.target.value })}
                    className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                  >
                    {['PCS', 'KG', 'L', 'M', 'CTN', 'BOX', 'PAL'].map((u) => (
                      <option key={u} value={u}>{u}</option>
                    ))}
                  </select>
                </div>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">Prix unitaire</label>
                  <input
                    type="number"
                    value={form.unitPrice}
                    onChange={(e) => setForm({ ...form, unitPrice: parseFloat(e.target.value) || 0 })}
                    className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                    min={0}
                    step={0.01}
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">Catégorie</label>
                  <input
                    type="text"
                    value={form.category}
                    onChange={(e) => setForm({ ...form, category: e.target.value })}
                    className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                  />
                </div>
              </div>
              <div className="flex items-center gap-2">
                <input
                  type="checkbox"
                  checked={form.active}
                  onChange={(e) => setForm({ ...form, active: e.target.checked })}
                  className="rounded border-line text-accent focus:ring-accent"
                />
                <span className="text-sm font-medium text-ink">Actif</span>
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
                  {editing ? 'Mettre à jour' : 'Créer'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {barcodeFor && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div className="absolute inset-0 bg-black/40" onClick={() => setBarcodeFor(null)} />
          <div className="relative bg-surface rounded-none shadow-2xl w-full max-w-md mx-4 p-6">
            <h3 className="text-lg font-semibold text-ink mb-1">Code-barres</h3>
            <p className="text-sm text-ink-soft mb-4">{barcodeFor.name}</p>
            <form
              onSubmit={(e) => { e.preventDefault(); barcodeMutation.mutate(); }}
              className="space-y-4"
            >
              <div>
                <label className="block text-sm font-medium text-ink mb-1">Code</label>
                <input
                  type="text"
                  value={barcodeValue}
                  onChange={(e) => setBarcodeValue(e.target.value)}
                  className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm font-mono"
                  placeholder="3760123456789"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-ink mb-1">Type</label>
                <select
                  value={barcodeType}
                  onChange={(e) => setBarcodeType(e.target.value)}
                  className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                >
                  {['EAN13', 'EAN8', 'UPCA', 'CODE128', 'CODE39', 'QR_CODE', 'ITF'].map((t) => (
                    <option key={t} value={t}>{t}</option>
                  ))}
                </select>
              </div>
              <div className="flex gap-3 pt-2">
                <button
                  type="button"
                  onClick={() => setBarcodeFor(null)}
                  className="flex-1 px-4 py-2 border border-line rounded-none text-sm font-medium text-ink hover:bg-bg transition-colors"
                >
                  Annuler
                </button>
                <button
                  type="submit"
                  disabled={barcodeMutation.isPending}
                  className="flex-1 px-4 py-2 bg-accent text-white rounded-none text-sm font-medium hover:bg-accent-strong disabled:opacity-50 transition-colors flex items-center justify-center gap-2"
                >
                  {barcodeMutation.isPending && <Loader2 size={14} className="animate-spin" />}
                  Associer
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default InventoryItems;
