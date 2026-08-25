import { useMemo, useState } from 'react';
import { motion, useReducedMotion } from 'framer-motion';
import { Search, PackageOpen } from 'lucide-react';
import PageReveal, { PageRevealItem } from '../ui/PageReveal';

export interface WarehouseTile {
  item: {
    id: string;
    name: string;
    sku?: string;
    unit?: string;
    unitPrice?: number;
    category?: string;
  };
  balance: {
    id: string;
    itemId: string;
    warehouseId: string;
    quantityOnHand: number;
    quantityReserved: number;
    quantityInTransit: number;
  };
}

interface Props {
  data: WarehouseTile[];
  onSelectTile: (tile: WarehouseTile) => void;
  selectedBalanceId?: string;
}

// Teintes sobres dérivées de l'accent de l'app (vert), pas un arc-en-ciel arbitraire.
const PALETTE = [
  { h: 154, s: 40, l: 45 }, // vert (accent)
  { h: 210, s: 35, l: 50 }, // bleu ardoise
  { h: 30, s: 46, l: 46 }, // argile
  { h: 280, s: 24, l: 52 }, // prune
  { h: 190, s: 36, l: 44 }, // sarcelle
  { h: 350, s: 32, l: 52 }, // terre rosée
];

const MIN_TILE = 64;
const MAX_TILE = 140;
const RESERVED_WARNING_RATIO = 0.85;

const zoneColor = (index: number) => PALETTE[index % PALETTE.length];
const hsl = (c: { h: number; s: number; l: number }, alpha: number) =>
  `hsl(${c.h} ${c.s}% ${c.l}% / ${alpha})`;

const VirtualWarehouseMap = ({ data, onSelectTile, selectedBalanceId }: Props) => {
  const reduceMotion = useReducedMotion();
  const [search, setSearch] = useState('');

  const maxQty = useMemo(
    () => Math.max(1, ...data.map((d) => d.balance.quantityOnHand)),
    [data],
  );

  const zones = useMemo(() => {
    const groups = new Map<string, WarehouseTile[]>();
    for (const tile of data) {
      const category = tile.item.category?.trim() || 'Non classé';
      if (!groups.has(category)) groups.set(category, []);
      groups.get(category)!.push(tile);
    }
    return Array.from(groups.entries())
      .sort(([a], [b]) => {
        if (a === 'Non classé') return 1;
        if (b === 'Non classé') return -1;
        return a.localeCompare(b);
      })
      .map(([category, tiles], index) => ({
        category,
        color: zoneColor(index),
        tiles: [...tiles].sort((a, b) => b.balance.quantityOnHand - a.balance.quantityOnHand),
      }));
  }, [data]);

  const tileSize = (qty: number) => {
    const ratio = Math.sqrt(Math.max(0, qty)) / Math.sqrt(maxQty);
    return Math.round(MIN_TILE + (MAX_TILE - MIN_TILE) * ratio);
  };

  const matchesSearch = (tile: WarehouseTile) => {
    if (!search.trim()) return true;
    const q = search.toLowerCase();
    return (
      tile.item.name.toLowerCase().includes(q) ||
      (tile.item.sku ?? '').toLowerCase().includes(q)
    );
  };

  if (data.length === 0) {
    return (
      <div className="bg-surface rounded-none border border-line px-6 py-16 text-center text-ink-soft">
        <PackageOpen size={32} className="mx-auto mb-3 text-ink-soft" />
        <p>Aucun article en stock dans cet entrepôt pour le moment.</p>
      </div>
    );
  }

  return (
    <div className="bg-surface rounded-none border border-line p-5">
      <div className="relative mb-5 max-w-xs">
        <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-soft" />
        <input
          type="text"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Rechercher un article..."
          className="pl-9 pr-3 py-2 border border-line rounded-none text-sm focus:ring-2 focus:ring-accent focus:border-transparent w-full bg-bg text-ink"
        />
      </div>

      <PageReveal className="flex flex-wrap gap-4 items-start">
        {zones.map((zone) => (
          <PageRevealItem key={zone.category} className="flex-1 min-w-[260px]">
            <div
              className="rounded-none border p-4 h-full"
              style={{
                borderColor: hsl(zone.color, 0.3),
                backgroundColor: hsl(zone.color, 0.05),
              }}
            >
              <div className="flex items-center gap-2 mb-3">
                <span
                  className="w-2.5 h-2.5 rounded-full shrink-0"
                  style={{ backgroundColor: hsl(zone.color, 1) }}
                />
                <h3 className="text-sm font-semibold text-ink">{zone.category}</h3>
                <span className="text-xs text-ink-soft">({zone.tiles.length})</span>
              </div>
              <div className="flex flex-wrap gap-2.5">
                {zone.tiles.map((tile) => {
                  const size = tileSize(tile.balance.quantityOnHand);
                  const reservedRatio =
                    tile.balance.quantityOnHand > 0
                      ? tile.balance.quantityReserved / tile.balance.quantityOnHand
                      : tile.balance.quantityReserved > 0
                        ? 1
                        : 0;
                  const nearlyReserved = reservedRatio >= RESERVED_WARNING_RATIO;
                  const dimmed = !matchesSearch(tile);
                  const isSelected = selectedBalanceId === tile.balance.id;
                  return (
                    <motion.button
                      key={tile.balance.id}
                      type="button"
                      onClick={() => onSelectTile(tile)}
                      whileHover={reduceMotion ? undefined : { scale: 1.06 }}
                      whileTap={reduceMotion ? undefined : { scale: 0.96 }}
                      animate={{ opacity: dimmed ? 0.25 : 1 }}
                      style={{
                        width: size,
                        height: size,
                        backgroundColor: hsl(zone.color, 0.14),
                        borderColor: nearlyReserved ? 'rgb(var(--c-warning))' : hsl(zone.color, 0.45),
                      }}
                      className={`flex flex-col items-center justify-center rounded-none border-2 p-1.5 text-center transition-shadow ${
                        isSelected ? 'ring-2 ring-accent ring-offset-2 ring-offset-surface' : ''
                      }`}
                      title={`${tile.item.name} — ${tile.balance.quantityOnHand} ${tile.item.unit ?? 'PCS'}`}
                    >
                      <span className="text-[11px] font-semibold text-ink leading-tight line-clamp-2">
                        {tile.item.name}
                      </span>
                      <span className="text-[11px] text-ink-soft mt-0.5">
                        {tile.balance.quantityOnHand} {tile.item.unit ?? 'PCS'}
                      </span>
                      {nearlyReserved && (
                        <span className="text-[9px] text-warning font-medium mt-0.5">Réservé</span>
                      )}
                    </motion.button>
                  );
                })}
              </div>
            </div>
          </PageRevealItem>
        ))}
      </PageReveal>
    </div>
  );
};

export default VirtualWarehouseMap;
