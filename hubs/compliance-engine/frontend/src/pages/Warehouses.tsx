import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { Warehouse as WarehouseIcon, Plus, Trash2, Loader2, MapPin, Boxes } from 'lucide-react';
import toast from 'react-hot-toast';
import { incokalkAPI } from '../lib/api';
import { useAuthStore } from '../stores/auth';
import { formatEur } from '../lib/formatNumber';

interface Warehouse {
  id: string;
  name: string;
  code: string;
  address?: string;
  city?: string;
  country?: string;
  active: boolean;
}

interface InventoryItemLite {
  id: string;
  unitPrice?: number;
}

interface StockBalanceLite {
  warehouseId: string;
  itemId: string;
  quantityOnHand: number;
}

const EMPTY_FORM = { name: '', code: '', address: '', city: '', country: '', active: true };

type ApiError = { response?: { data?: { message?: string } } };
type WarehousePayload = typeof EMPTY_FORM;

const Warehouses = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const isAdmin = useAuthStore((s) => s.isAdmin());
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState<Warehouse | null>(null);
  const [deleteConfirm, setDeleteConfirm] = useState<string | null>(null);
  const [form, setForm] = useState({ ...EMPTY_FORM });

  const { data, isLoading } = useQuery({
    queryKey: ['warehouses'],
    queryFn: async () => (await incokalkAPI.warehouses.list())?.data ?? [],
  });

  const warehouses = Array.isArray(data) ? data : [];

  const { data: itemsData } = useQuery({
    queryKey: ['inventory-items-all'],
    queryFn: async () => (await incokalkAPI.inventory.items.list())?.data ?? [],
  });
  const items = (Array.isArray(itemsData) ? itemsData : []) as InventoryItemLite[];

  const { data: balancesData } = useQuery({
    queryKey: ['inventory-balances', ''],
    queryFn: async () => (await incokalkAPI.inventory.balances())?.data ?? [],
  });
  const balances = (Array.isArray(balancesData) ? balancesData : []) as StockBalanceLite[];

  const warehouseStats = (warehouseId: string) => {
    const rows = balances.filter((b) => b.warehouseId === warehouseId);
    const totalValue = rows.reduce((sum, b) => {
      const unitPrice = items.find((i) => i.id === b.itemId)?.unitPrice ?? 0;
      return sum + b.quantityOnHand * unitPrice;
    }, 0);
    return { itemCount: rows.length, totalValue };
  };

  const saveMutation = useMutation({
    mutationFn: (payload: WarehousePayload) =>
      editing ? incokalkAPI.warehouses.update(editing.id, payload) : incokalkAPI.warehouses.create(payload),
    onSuccess: () => {
      toast.success(editing ? 'Entrepôt mis à jour' : 'Entrepôt créé');
      setShowForm(false);
      setEditing(null);
      setForm({ ...EMPTY_FORM });
      queryClient.invalidateQueries({ queryKey: ['warehouses'] });
    },
    onError: (err: ApiError) => toast.error(err.response?.data?.message || 'Erreur lors de la sauvegarde'),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => incokalkAPI.warehouses.delete(id),
    onSuccess: () => {
      toast.success('Entrepôt désactivé');
      setDeleteConfirm(null);
      queryClient.invalidateQueries({ queryKey: ['warehouses'] });
    },
    onError: (err: ApiError) => toast.error(err.response?.data?.message || 'Erreur lors de la suppression'),
  });

  const openAdd = () => {
    setEditing(null);
    setForm({ ...EMPTY_FORM });
    setShowForm(true);
  };

  const openEdit = (w: Warehouse) => {
    setEditing(w);
    setForm({
      name: w.name,
      code: w.code,
      address: w.address ?? '',
      city: w.city ?? '',
      country: w.country ?? '',
      active: w.active,
    });
    setShowForm(true);
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    saveMutation.mutate({ ...form });
  };

  return (
    <div className="max-w-6xl mx-auto px-4 py-8">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between mb-8 gap-4">
        <div>
          <h1 className="text-2xl font-bold text-ink">Entrepôts</h1>
          <p className="text-ink-soft mt-1">Sites de réception et de stockage</p>
        </div>
        {isAdmin && (
          <button
            onClick={openAdd}
            className="flex items-center gap-2 bg-accent text-white px-4 py-2 rounded-lg font-medium hover:bg-accent-strong transition-colors"
          >
            <Plus size={18} />
            Nouvel entrepôt
          </button>
        )}
      </div>

      <div className="bg-surface rounded-xl border border-line overflow-hidden">
        {isLoading ? (
          <div className="px-6 py-12 text-center text-ink-soft">
            <Loader2 size={24} className="animate-spin mx-auto mb-2 text-ink-soft" />
            Chargement...
          </div>
        ) : warehouses.length === 0 ? (
          <div className="px-6 py-12 text-center text-ink-soft">
            <WarehouseIcon size={32} className="mx-auto mb-3 text-ink-soft" />
            <p>Aucun entrepôt. Créez votre premier site de réception.</p>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 p-4">
            {warehouses.map((w) => {
              const stats = warehouseStats(w.id);
              return (
              <div
                key={w.id}
                onClick={() => navigate(`/warehouses/${w.id}`)}
                className="border border-line rounded-xl p-4 hover:shadow-md hover:border-accent/40 transition-shadow cursor-pointer"
              >
                <div className="flex items-start justify-between">
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 rounded-lg bg-accent-soft flex items-center justify-center">
                      <WarehouseIcon size={20} className="text-accent" />
                    </div>
                    <div>
                      <p className="text-sm font-semibold text-ink">{w.name}</p>
                      {w.code && <p className="text-xs font-mono text-ink-soft">{w.code}</p>}
                    </div>
                  </div>
                  <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${
                    w.active ? 'bg-success/10 text-success' : 'bg-surface-2 text-ink-soft'
                  }`}>
                    {w.active ? 'Actif' : 'Inactif'}
                  </span>
                </div>
                {(w.address || w.city || w.country) && (
                  <p className="mt-3 text-xs text-ink-soft flex items-center gap-1">
                    <MapPin size={12} />
                    {[w.address, w.city, w.country].filter(Boolean).join(', ')}
                  </p>
                )}
                <p className="mt-3 text-xs text-ink-soft flex items-center gap-1.5">
                  <Boxes size={12} />
                  {stats.itemCount} article{stats.itemCount > 1 ? 's' : ''} ·{' '}
                  <span className="font-medium text-ink">
                    {formatEur(stats.totalValue, { maximumFractionDigits: 0 })}
                  </span>
                </p>
                {isAdmin && (
                  <div className="mt-4 flex items-center justify-end gap-1 border-t border-line pt-3">
                    <button
                      onClick={(e) => { e.stopPropagation(); openEdit(w); }}
                      className="px-3 py-1.5 text-xs font-medium text-accent hover:bg-accent-soft rounded-lg transition-colors"
                    >
                      Modifier
                    </button>
                    {deleteConfirm === w.id ? (
                      <div className="flex items-center gap-1">
                        <button
                          onClick={(e) => { e.stopPropagation(); deleteMutation.mutate(w.id); }}
                          disabled={deleteMutation.isPending}
                          className="px-2 py-1 text-xs bg-danger text-white rounded hover:bg-danger/90 transition-colors"
                        >
                          Confirmer
                        </button>
                        <button
                          onClick={(e) => { e.stopPropagation(); setDeleteConfirm(null); }}
                          className="px-2 py-1 text-xs bg-surface-2 text-ink-soft rounded hover:bg-line transition-colors"
                        >
                          Annuler
                        </button>
                      </div>
                    ) : (
                      <button
                        onClick={(e) => { e.stopPropagation(); setDeleteConfirm(w.id); }}
                        className="p-1.5 rounded-lg text-ink-soft hover:text-danger hover:bg-danger/10 transition-colors"
                        title="Désactiver"
                      >
                        <Trash2 size={15} />
                      </button>
                    )}
                  </div>
                )}
              </div>
              );
            })}
          </div>
        )}
      </div>

      {showForm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div className="absolute inset-0 bg-black/40" onClick={() => setShowForm(false)} />
          <div className="relative bg-surface rounded-xl shadow-2xl w-full max-w-lg mx-4 p-6">
            <h3 className="text-lg font-semibold text-ink mb-4">
              {editing ? 'Modifier l’entrepôt' : 'Nouvel entrepôt'}
            </h3>
            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">Nom *</label>
                  <input
                    type="text"
                    value={form.name}
                    onChange={(e) => setForm({ ...form, name: e.target.value })}
                    className="w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                    required
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">Code</label>
                  <input
                    type="text"
                    value={form.code}
                    onChange={(e) => setForm({ ...form, code: e.target.value })}
                    className="w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                    placeholder="WH-01"
                  />
                </div>
              </div>
              <div>
                <label className="block text-sm font-medium text-ink mb-1">Adresse</label>
                <input
                  type="text"
                  value={form.address}
                  onChange={(e) => setForm({ ...form, address: e.target.value })}
                  className="w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">Ville</label>
                  <input
                    type="text"
                    value={form.city}
                    onChange={(e) => setForm({ ...form, city: e.target.value })}
                    className="w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">Pays</label>
                  <input
                    type="text"
                    value={form.country}
                    onChange={(e) => setForm({ ...form, country: e.target.value })}
                    className="w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                    placeholder="FR"
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
                  className="flex-1 px-4 py-2 border border-line rounded-lg text-sm font-medium text-ink hover:bg-bg transition-colors"
                >
                  Annuler
                </button>
                <button
                  type="submit"
                  disabled={saveMutation.isPending}
                  className="flex-1 px-4 py-2 bg-accent text-white rounded-lg text-sm font-medium hover:bg-accent-strong disabled:opacity-50 transition-colors flex items-center justify-center gap-2"
                >
                  {saveMutation.isPending && <Loader2 size={14} className="animate-spin" />}
                  {editing ? 'Mettre à jour' : 'Créer'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default Warehouses;
