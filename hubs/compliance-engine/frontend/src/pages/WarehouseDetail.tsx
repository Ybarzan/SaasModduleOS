import { useMemo, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  ArrowLeft, Warehouse as WarehouseIcon, MapPin, Boxes, Package, Layers, Coins,
  LayoutGrid, List, Loader2, SlidersHorizontal, ArrowDownUp, X,
} from 'lucide-react';
import toast from 'react-hot-toast';
import { incokalkAPI } from '../lib/api';
import { useAuthStore } from '../stores/auth';
import Card from '../components/ui/Card';
import VirtualWarehouseMap, { type WarehouseTile } from '../components/warehouse/VirtualWarehouseMap';
import { formatEur, formatNumber } from '../lib/formatNumber';

interface Warehouse {
  id: string;
  name: string;
  code?: string;
  address?: string;
  city?: string;
  country?: string;
  active: boolean;
}

interface InventoryItem {
  id: string;
  name: string;
  sku?: string;
  unit?: string;
  unitPrice?: number;
  category?: string;
}

interface StockBalance {
  id: string;
  warehouseId: string;
  itemId: string;
  quantityOnHand: number;
  quantityReserved: number;
  quantityInTransit: number;
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

const currency = (n: number) => formatEur(n, { maximumFractionDigits: 0 });

const WarehouseDetail = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const canManage = useAuthStore((s) => s.hasMinimumRole('MANAGER'));

  const [view, setView] = useState<'map' | 'list'>('map');
  const [selectedBalanceId, setSelectedBalanceId] = useState<string | null>(null);
  const [adjustOpen, setAdjustOpen] = useState(false);
  const [movementsOpen, setMovementsOpen] = useState(false);
  const [form, setForm] = useState({ quantity: 0, note: '' });

  const { data: warehouseData, isLoading: warehouseLoading } = useQuery({
    queryKey: ['warehouse', id],
    queryFn: async () => (await incokalkAPI.warehouses.get(id!))?.data,
    enabled: !!id,
  });
  const warehouse = warehouseData as Warehouse | undefined;

  const { data: itemsData } = useQuery({
    queryKey: ['inventory-items-all'],
    queryFn: async () => (await incokalkAPI.inventory.items.list())?.data ?? [],
  });
  const items = useMemo(() => (Array.isArray(itemsData) ? itemsData : []) as InventoryItem[], [itemsData]);

  const { data: balancesData, isLoading: balancesLoading } = useQuery({
    queryKey: ['inventory-balances', id],
    queryFn: async () => (await incokalkAPI.inventory.balances(id))?.data ?? [],
    enabled: !!id,
  });
  const balances = useMemo(() => (Array.isArray(balancesData) ? balancesData : []) as StockBalance[], [balancesData]);

  const tiles: WarehouseTile[] = useMemo(
    () =>
      balances
        .map((balance) => {
          const item = items.find((i) => i.id === balance.itemId);
          if (!item) return null;
          return { item, balance };
        })
        .filter((t): t is WarehouseTile => t !== null),
    [balances, items],
  );

  const stats = useMemo(() => {
    const unitsTotal = tiles.reduce((sum, t) => sum + t.balance.quantityOnHand, 0);
    const valueTotal = tiles.reduce((sum, t) => sum + t.balance.quantityOnHand * (t.item.unitPrice ?? 0), 0);
    const categories = new Set(tiles.map((t) => t.item.category?.trim() || 'Non classé'));
    return { itemCount: tiles.length, unitsTotal, valueTotal, categoryCount: categories.size };
  }, [tiles]);

  const selected = useMemo(
    () => tiles.find((t) => t.balance.id === selectedBalanceId) ?? null,
    [tiles, selectedBalanceId],
  );

  const { data: movementsData } = useQuery({
    queryKey: ['inventory-movements', selected?.item.id],
    queryFn: async () => (await incokalkAPI.inventory.movements(selected!.item.id))?.data ?? [],
    enabled: movementsOpen && !!selected,
  });
  const movements = (Array.isArray(movementsData) ? movementsData : []) as StockMovement[];

  const adjustMutation = useMutation({
    mutationFn: () =>
      incokalkAPI.inventory.adjust({
        warehouseId: selected!.balance.warehouseId,
        itemId: selected!.item.id,
        quantity: Number(form.quantity) || 0,
        note: form.note || undefined,
      }),
    onSuccess: () => {
      toast.success('Stock ajusté');
      setAdjustOpen(false);
      setForm({ quantity: 0, note: '' });
      queryClient.invalidateQueries({ queryKey: ['inventory-balances'] });
    },
    onError: (err: ApiError) => toast.error(err.response?.data?.message || 'Erreur lors de l’ajustement'),
  });

  const openTile = (tile: WarehouseTile) => setSelectedBalanceId(tile.balance.id);

  if (warehouseLoading) {
    return (
      <div className="max-w-6xl mx-auto px-4 py-16 text-center text-ink-soft">
        <Loader2 size={24} className="animate-spin mx-auto mb-2" />
        Chargement...
      </div>
    );
  }

  if (!warehouse) {
    return (
      <div className="max-w-6xl mx-auto px-4 py-16 text-center text-ink-soft">
        <WarehouseIcon size={32} className="mx-auto mb-3" />
        <p>Entrepôt introuvable.</p>
        <button onClick={() => navigate('/warehouses')} className="mt-4 text-accent font-medium hover:underline">
          Retour aux entrepôts
        </button>
      </div>
    );
  }

  return (
    <div className="max-w-6xl mx-auto px-4 py-8">
      <button
        onClick={() => navigate('/warehouses')}
        className="flex items-center gap-1.5 text-sm text-ink-soft hover:text-ink transition-colors mb-4"
      >
        <ArrowLeft size={16} />
        Entrepôts
      </button>

      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between mb-6 gap-4">
        <div className="flex items-center gap-3">
          <div className="w-12 h-12 rounded-none bg-accent-soft flex items-center justify-center shrink-0">
            <WarehouseIcon size={22} className="text-accent" />
          </div>
          <div>
            <h1 className="text-2xl font-bold text-ink flex items-center gap-2">
              {warehouse.name}
              {warehouse.code && <span className="text-sm font-mono text-ink-soft">{warehouse.code}</span>}
            </h1>
            {(warehouse.address || warehouse.city || warehouse.country) && (
              <p className="text-ink-soft mt-0.5 text-sm flex items-center gap-1">
                <MapPin size={12} />
                {[warehouse.address, warehouse.city, warehouse.country].filter(Boolean).join(', ')}
              </p>
            )}
          </div>
        </div>
        <div className="flex items-center gap-1 bg-surface-2 rounded-none p-1">
          <button
            onClick={() => setView('map')}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-none text-sm font-medium transition-colors ${
              view === 'map' ? 'bg-surface text-ink shadow-sm' : 'text-ink-soft hover:text-ink'
            }`}
          >
            <LayoutGrid size={14} />
            Plan
          </button>
          <button
            onClick={() => setView('list')}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-none text-sm font-medium transition-colors ${
              view === 'list' ? 'bg-surface text-ink shadow-sm' : 'text-ink-soft hover:text-ink'
            }`}
          >
            <List size={14} />
            Vue liste
          </button>
        </div>
      </div>

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        <Card variant="stat">
          <Package size={18} className="mx-auto mb-2 text-accent" />
          <p className="text-2xl font-bold text-ink">{stats.itemCount}</p>
          <p className="text-xs text-ink-soft mt-1">Articles distincts</p>
        </Card>
        <Card variant="stat">
          <Boxes size={18} className="mx-auto mb-2 text-accent" />
          <p className="text-2xl font-bold text-ink">{formatNumber(stats.unitsTotal)}</p>
          <p className="text-xs text-ink-soft mt-1">Unités en stock</p>
        </Card>
        <Card variant="stat">
          <Coins size={18} className="mx-auto mb-2 text-accent" />
          <p className="text-2xl font-bold text-ink">{currency(stats.valueTotal)}</p>
          <p className="text-xs text-ink-soft mt-1">Valeur estimée</p>
        </Card>
        <Card variant="stat">
          <Layers size={18} className="mx-auto mb-2 text-accent" />
          <p className="text-2xl font-bold text-ink">{stats.categoryCount}</p>
          <p className="text-xs text-ink-soft mt-1">Catégories</p>
        </Card>
      </div>

      {balancesLoading ? (
        <div className="bg-surface rounded-none border border-line px-6 py-16 text-center text-ink-soft">
          <Loader2 size={24} className="animate-spin mx-auto mb-2" />
          Chargement du stock...
        </div>
      ) : view === 'map' ? (
        <div className="flex flex-col lg:flex-row gap-5 items-start">
          <div className="flex-1 w-full min-w-0">
            <VirtualWarehouseMap data={tiles} onSelectTile={openTile} selectedBalanceId={selected?.balance.id} />
          </div>
          {selected && (
            <div className="w-full lg:w-80 shrink-0">
              <Card variant="flat">
                <div className="flex items-start justify-between mb-3">
                  <div>
                    <p className="text-sm font-semibold text-ink">{selected.item.name}</p>
                    {selected.item.sku && <p className="text-xs font-mono text-ink-soft">{selected.item.sku}</p>}
                  </div>
                  <button
                    onClick={() => setSelectedBalanceId(null)}
                    className="p-1 rounded-none text-ink-soft hover:text-ink hover:bg-surface-2 transition-colors"
                  >
                    <X size={16} />
                  </button>
                </div>
                <p className="text-xs text-ink-soft mb-3">
                  {selected.item.category?.trim() || 'Non classé'}
                </p>
                <div className="space-y-1.5 text-sm border-t border-line pt-3">
                  <div className="flex justify-between">
                    <span className="text-ink-soft">En stock</span>
                    <span className="font-semibold text-ink">
                      {selected.balance.quantityOnHand} {selected.item.unit ?? 'PCS'}
                    </span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-ink-soft">Réservé</span>
                    <span className="text-ink">{selected.balance.quantityReserved}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-ink-soft">En transit</span>
                    <span className="text-ink">{selected.balance.quantityInTransit}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-ink-soft">Valeur estimée</span>
                    <span className="font-semibold text-ink">
                      {currency(selected.balance.quantityOnHand * (selected.item.unitPrice ?? 0))}
                    </span>
                  </div>
                </div>
                {canManage && (
                  <div className="flex gap-2 mt-4 pt-3 border-t border-line">
                    <button
                      onClick={() => { setAdjustOpen(true); setForm({ quantity: 0, note: '' }); }}
                      className="flex-1 flex items-center justify-center gap-1.5 px-3 py-2 bg-accent text-white rounded-none text-xs font-medium hover:bg-accent-strong transition-colors"
                    >
                      <SlidersHorizontal size={14} />
                      Ajuster
                    </button>
                    <button
                      onClick={() => setMovementsOpen(true)}
                      className="flex-1 flex items-center justify-center gap-1.5 px-3 py-2 border border-line rounded-none text-xs font-medium text-ink hover:bg-bg transition-colors"
                    >
                      <ArrowDownUp size={14} />
                      Mouvements
                    </button>
                  </div>
                )}
              </Card>
            </div>
          )}
        </div>
      ) : (
        <div className="bg-surface rounded-none border border-line overflow-hidden">
          {tiles.length === 0 ? (
            <div className="px-6 py-12 text-center text-ink-soft">
              <Boxes size={32} className="mx-auto mb-3 text-ink-soft" />
              <p>Aucun stock enregistré dans cet entrepôt.</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="bg-bg border-b border-line">
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Article</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Catégorie</th>
                    <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">En stock</th>
                    <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Réservé</th>
                    <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">En transit</th>
                    {canManage && <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Actions</th>}
                  </tr>
                </thead>
                <tbody className="divide-y divide-line">
                  {tiles.map((t) => (
                    <tr key={t.balance.id} className="hover:bg-bg transition-colors">
                      <td className="px-6 py-4 text-sm font-medium text-ink">{t.item.name}</td>
                      <td className="px-6 py-4 text-sm text-ink-soft">{t.item.category?.trim() || 'Non classé'}</td>
                      <td className="px-6 py-4 text-right text-sm font-semibold text-ink">
                        {t.balance.quantityOnHand} <span className="text-ink-soft font-normal">{t.item.unit ?? 'PCS'}</span>
                      </td>
                      <td className="px-6 py-4 text-right text-sm text-ink-soft">{t.balance.quantityReserved}</td>
                      <td className="px-6 py-4 text-right text-sm text-ink-soft">{t.balance.quantityInTransit}</td>
                      {canManage && (
                        <td className="px-6 py-4 text-right">
                          <button
                            onClick={() => { setSelectedBalanceId(t.balance.id); setAdjustOpen(true); setForm({ quantity: 0, note: '' }); }}
                            className="p-1.5 rounded-none text-ink-soft hover:text-accent hover:bg-accent-soft transition-colors"
                            title="Ajuster le stock"
                          >
                            <SlidersHorizontal size={16} />
                          </button>
                          <button
                            onClick={() => { setSelectedBalanceId(t.balance.id); setMovementsOpen(true); }}
                            className="p-1.5 rounded-none text-ink-soft hover:text-accent hover:bg-accent/20 transition-colors"
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
      )}

      {adjustOpen && selected && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div className="absolute inset-0 bg-black/40" onClick={() => setAdjustOpen(false)} />
          <div className="relative bg-surface rounded-none shadow-2xl w-full max-w-md mx-4 p-6">
            <h3 className="text-lg font-semibold text-ink mb-1">Ajuster le stock</h3>
            <p className="text-sm text-ink-soft mb-4">
              {selected.item.name} — {warehouse.name}
              <br />
              Solde actuel : <span className="font-semibold">{selected.balance.quantityOnHand} {selected.item.unit ?? 'PCS'}</span>
            </p>
            <form onSubmit={(e) => { e.preventDefault(); adjustMutation.mutate(); }} className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-ink mb-1">
                  Variation (négative pour une sortie) *
                </label>
                <input
                  type="number"
                  value={form.quantity}
                  onChange={(e) => setForm({ ...form, quantity: parseFloat(e.target.value) || 0 })}
                  className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
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
                  className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                  placeholder="Inventaire, casse, erreur..."
                />
              </div>
              <div className="flex gap-3 pt-2">
                <button
                  type="button"
                  onClick={() => setAdjustOpen(false)}
                  className="flex-1 px-4 py-2 border border-line rounded-none text-sm font-medium text-ink hover:bg-bg transition-colors"
                >
                  Annuler
                </button>
                <button
                  type="submit"
                  disabled={adjustMutation.isPending}
                  className="flex-1 px-4 py-2 bg-accent text-white rounded-none text-sm font-medium hover:bg-accent-strong disabled:opacity-50 transition-colors flex items-center justify-center gap-2"
                >
                  {adjustMutation.isPending && <Loader2 size={14} className="animate-spin" />}
                  Ajuster
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {movementsOpen && selected && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div className="absolute inset-0 bg-black/40" onClick={() => setMovementsOpen(false)} />
          <div className="relative bg-surface rounded-none shadow-2xl w-full max-w-lg mx-4 p-6 max-h-[80vh] overflow-y-auto">
            <h3 className="text-lg font-semibold text-ink mb-4">Mouvements — {selected.item.name}</h3>
            {movements.length === 0 ? (
              <p className="text-sm text-ink-soft">Aucun mouvement pour cet article.</p>
            ) : (
              <ul className="space-y-3">
                {movements.map((m) => (
                  <li key={m.id} className="flex items-center justify-between border border-line rounded-none p-3">
                    <div>
                      <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${typeColors[m.type] ?? 'bg-surface-2 text-ink-soft'}`}>
                        {typeLabels[m.type] ?? m.type}
                      </span>
                      {m.note && <p className="text-xs text-ink-soft mt-1">{m.note}</p>}
                      <p className="text-xs text-ink-soft mt-0.5">
                        {m.createdAt ? new Date(m.createdAt).toLocaleString() : ''}
                      </p>
                    </div>
                    <span className={`text-sm font-semibold ${m.quantity >= 0 ? 'text-success' : 'text-danger'}`}>
                      {m.quantity >= 0 ? '+' : ''}{m.quantity} {selected.item.unit ?? 'PCS'}
                    </span>
                  </li>
                ))}
              </ul>
            )}
            <div className="flex gap-3 pt-4">
              <button
                onClick={() => setMovementsOpen(false)}
                className="flex-1 px-4 py-2 border border-line rounded-none text-sm font-medium text-ink hover:bg-bg transition-colors"
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

export default WarehouseDetail;
