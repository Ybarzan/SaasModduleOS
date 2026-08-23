import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useSearchParams } from 'react-router-dom';
import { Boxes, SlidersHorizontal, Loader2, ArrowDownUp, Search } from 'lucide-react';
import toast from 'react-hot-toast';
import { incokalkAPI } from '../lib/api';
import { useAuthStore } from '../stores/auth';

interface Warehouse {
  id: string;
  name: string;
  code?: string;
}

interface InventoryItem {
  id: string;
  name: string;
  sku?: string;
  unit?: string;
}

interface StockBalance {
  id: string;
  warehouseId: string;
  itemId: string;
  quantityOnHand: number;
  quantityReserved: number;
  quantityInTransit: number;
  lastUpdated?: string;
}

interface StockMovement {
  id: string;
  itemId: string;
  warehouseId: string;
  quantity: number;
  type: string;
  note?: string;
  createdAt?: string;
}

const typeLabels: Record<string, string> = {
  RECEIPT: 'Réception',
  DAMAGED: 'Endommagé',
  ADJUSTMENT: 'Ajustement',
  CYCLE_COUNT: 'Inventaire',
  OUTBOUND: 'Sortie',
  TRANSFER: 'Transfert',
};

const typeColors: Record<string, string> = {
  RECEIPT: 'bg-success/10 text-success',
  DAMAGED: 'bg-danger/10 text-danger',
  ADJUSTMENT: 'bg-accent-soft text-accent-strong',
  CYCLE_COUNT: 'bg-accent-soft text-accent-strong',
  OUTBOUND: 'bg-warning/10 text-warning',
  TRANSFER: 'bg-accent-soft text-accent-strong',
};

type ApiError = { response?: { data?: { message?: string } } };

const Inventory = () => {
  const queryClient = useQueryClient();
  const [searchParams] = useSearchParams();
  const canManage = useAuthStore((s) => s.hasMinimumRole('MANAGER'));
  const [warehouseId, setWarehouseId] = useState<string>('');
  const [search, setSearch] = useState<string>(searchParams.get('q') ?? '');
  const [adjustFor, setAdjustFor] = useState<StockBalance | null>(null);
  const [movementFor, setMovementFor] = useState<string | null>(null);
  const [form, setForm] = useState({ quantity: 0, note: '' });

  const { data: warehousesData } = useQuery({
    queryKey: ['warehouses'],
    queryFn: async () => (await incokalkAPI.warehouses.list())?.data ?? [],
  });
  const warehouses = (Array.isArray(warehousesData) ? warehousesData : []) as Warehouse[];

  const { data: itemsData } = useQuery({
    queryKey: ['inventory-items-all'],
    queryFn: async () => (await incokalkAPI.inventory.items.list())?.data ?? [],
  });
  const items = (Array.isArray(itemsData) ? itemsData : []) as InventoryItem[];
  const itemName = (id: string) => items.find((i) => i.id === id)?.name ?? id;
  const itemUnit = (id: string) => items.find((i) => i.id === id)?.unit ?? 'PCS';
  const warehouseName = (id: string) => warehouses.find((w) => w.id === id)?.name ?? id;

  const { data, isLoading } = useQuery({
    queryKey: ['inventory-balances', warehouseId],
    queryFn: async () => (await incokalkAPI.inventory.balances(warehouseId || undefined))?.data ?? [],
  });
  const balances = (Array.isArray(data) ? data : []) as StockBalance[];

  const filteredBalances = balances.filter((b) => {
    if (!search.trim()) return true;
    const item = items.find((i) => i.id === b.itemId);
    return (item?.name ?? '').toLowerCase().includes(search.toLowerCase())
      || (item?.sku ?? '').toLowerCase().includes(search.toLowerCase());
  });

  const { data: movementsData } = useQuery({
    queryKey: ['inventory-movements', movementFor],
    queryFn: async () => (await incokalkAPI.inventory.movements(movementFor!))?.data ?? [],
    enabled: !!movementFor,
  });
  const movements = (Array.isArray(movementsData) ? movementsData : []) as StockMovement[];

  const adjustMutation = useMutation({
    mutationFn: () =>
      incokalkAPI.inventory.adjust({
        warehouseId: adjustFor!.warehouseId,
        itemId: adjustFor!.itemId,
        quantity: Number(form.quantity) || 0,
        note: form.note || undefined,
      }),
    onSuccess: () => {
      toast.success('Stock ajusté');
      setAdjustFor(null);
      setForm({ quantity: 0, note: '' });
      queryClient.invalidateQueries({ queryKey: ['inventory-balances'] });
    },
    onError: (err: ApiError) => toast.error(err.response?.data?.message || 'Erreur lors de l’ajustement'),
  });

  return (
    <div className="max-w-6xl mx-auto px-4 py-8">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between mb-8 gap-4">
        <div>
          <h1 className="text-2xl font-bold text-ink">Stock</h1>
          <p className="text-ink-soft mt-1">Soldes par entrepôt et mouvements</p>
        </div>
        <select
          value={warehouseId}
          onChange={(e) => setWarehouseId(e.target.value)}
          className="px-3 py-2 border border-line rounded-lg text-sm focus:ring-2 focus:ring-accent focus:border-transparent"
        >
          <option value="">Tous les entrepôts</option>
          {warehouses.map((w) => (
            <option key={w.id} value={w.id}>{w.name}{w.code ? ` (${w.code})` : ''}</option>
          ))}
        </select>
        <div className="relative">
          <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-soft" />
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Rechercher article / SKU..."
            className="pl-9 pr-3 py-2 border border-line rounded-lg text-sm focus:ring-2 focus:ring-accent focus:border-transparent w-64"
          />
        </div>
      </div>

      <div className="bg-surface rounded-xl border border-line overflow-hidden">
        {isLoading ? (
          <div className="px-6 py-12 text-center text-ink-soft">
            <Loader2 size={24} className="animate-spin mx-auto mb-2 text-ink-soft" />
            Chargement...
          </div>
        ) : filteredBalances.length === 0 ? (
          <div className="px-6 py-12 text-center text-ink-soft">
            <Boxes size={32} className="mx-auto mb-3 text-ink-soft" />
            <p>{search.trim() ? 'Aucun article ne correspond à cette recherche.' : 'Aucun stock enregistré. Les réceptions et ajustements apparaîtront ici.'}</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="bg-bg border-b border-line">
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Article</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Entrepôt</th>
                  <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">En stock</th>
                  <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Réservé</th>
                  <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">En transit</th>
                  {canManage && <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Actions</th>}
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {filteredBalances.map((b) => (
                  <tr key={b.id} className="hover:bg-bg transition-colors">
                    <td className="px-6 py-4 text-sm font-medium text-ink">{itemName(b.itemId)}</td>
                    <td className="px-6 py-4 text-sm text-ink-soft">{warehouseName(b.warehouseId)}</td>
                    <td className="px-6 py-4 text-right text-sm font-semibold text-ink">
                      {b.quantityOnHand} <span className="text-ink-soft font-normal">{itemUnit(b.itemId)}</span>
                    </td>
                    <td className="px-6 py-4 text-right text-sm text-ink-soft">{b.quantityReserved}</td>
                    <td className="px-6 py-4 text-right text-sm text-ink-soft">{b.quantityInTransit}</td>
                    {canManage && (
                      <td className="px-6 py-4 text-right">
                        <button
                          onClick={() => { setAdjustFor(b); setForm({ quantity: 0, note: '' }); }}
                          className="p-1.5 rounded-lg text-ink-soft hover:text-accent hover:bg-accent-soft transition-colors"
                          title="Ajuster le stock"
                        >
                          <SlidersHorizontal size={16} />
                        </button>
                        <button
                          onClick={() => setMovementFor(b.itemId)}
                          className="p-1.5 rounded-lg text-ink-soft hover:text-accent hover:bg-accent/20 transition-colors"
                          title="Mouvements"
                        >
                          <ArrowDownUp size={16} />
                        </button>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {adjustFor && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div className="absolute inset-0 bg-black/40" onClick={() => setAdjustFor(null)} />
          <div className="relative bg-surface rounded-xl shadow-2xl w-full max-w-md mx-4 p-6">
            <h3 className="text-lg font-semibold text-ink mb-1">Ajuster le stock</h3>
            <p className="text-sm text-ink-soft mb-4">
              {itemName(adjustFor.itemId)} — {warehouseName(adjustFor.warehouseId)}
              <br />
              Solde actuel : <span className="font-semibold">{adjustFor.quantityOnHand} {itemUnit(adjustFor.itemId)}</span>
            </p>
            <form
              onSubmit={(e) => { e.preventDefault(); adjustMutation.mutate(); }}
              className="space-y-4"
            >
              <div>
                <label className="block text-sm font-medium text-ink mb-1">
                  Variation (négative pour une sortie) *
                </label>
                <input
                  type="number"
                  value={form.quantity}
                  onChange={(e) => setForm({ ...form, quantity: parseFloat(e.target.value) || 0 })}
                  className="w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                  step={0.01}
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-ink mb-1">Motif</label>
                <input
                  type="text"
                  value={form.note}
                  onChange={(e) => setForm({ ...form, note: e.target.value })}
                  className="w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                  placeholder="Inventaire, casse, erreur..."
                />
              </div>
              <div className="flex gap-3 pt-2">
                <button
                  type="button"
                  onClick={() => setAdjustFor(null)}
                  className="flex-1 px-4 py-2 border border-line rounded-lg text-sm font-medium text-ink hover:bg-bg transition-colors"
                >
                  Annuler
                </button>
                <button
                  type="submit"
                  disabled={adjustMutation.isPending}
                  className="flex-1 px-4 py-2 bg-accent text-white rounded-lg text-sm font-medium hover:bg-accent-strong disabled:opacity-50 transition-colors flex items-center justify-center gap-2"
                >
                  {adjustMutation.isPending && <Loader2 size={14} className="animate-spin" />}
                  Ajuster
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {movementFor && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div className="absolute inset-0 bg-black/40" onClick={() => setMovementFor(null)} />
          <div className="relative bg-surface rounded-xl shadow-2xl w-full max-w-lg mx-4 p-6 max-h-[80vh] overflow-y-auto">
            <h3 className="text-lg font-semibold text-ink mb-4">Mouvements — {itemName(movementFor)}</h3>
            {movements.length === 0 ? (
              <p className="text-sm text-ink-soft">Aucun mouvement pour cet article.</p>
            ) : (
              <ul className="space-y-3">
                {movements.map((m) => (
                  <li key={m.id} className="flex items-center justify-between border border-line rounded-lg p-3">
                    <div>
                      <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${typeColors[m.type] ?? 'bg-surface-2 text-ink-soft'}`}>
                        {typeLabels[m.type] ?? m.type}
                      </span>
                      {m.note && <p className="text-xs text-ink-soft mt-1">{m.note}</p>}
                      <p className="text-xs text-ink-soft mt-0.5">
                        {warehouseName(m.warehouseId)} · {m.createdAt ? new Date(m.createdAt).toLocaleString() : ''}
                      </p>
                    </div>
                    <span className={`text-sm font-semibold ${m.quantity >= 0 ? 'text-success' : 'text-danger'}`}>
                      {m.quantity >= 0 ? '+' : ''}{m.quantity} {itemUnit(m.itemId)}
                    </span>
                  </li>
                ))}
              </ul>
            )}
            <div className="flex gap-3 pt-4">
              <button
                onClick={() => setMovementFor(null)}
                className="flex-1 px-4 py-2 border border-line rounded-lg text-sm font-medium text-ink hover:bg-bg transition-colors"
              >
                Fermer
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default Inventory;
