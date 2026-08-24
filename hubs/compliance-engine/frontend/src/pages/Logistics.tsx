import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Package, Truck, Plus, Trash2, Loader2, Box, Weight, Ruler } from 'lucide-react';
import { incokalkAPI } from '../lib/api';
import toast from 'react-hot-toast';
import { formatEur } from '../lib/formatNumber';

interface PackagingItem {
  sku: string;
  lengthCm: number;
  widthCm: number;
  heightCm: number;
  weightKg: number;
  quantity: number;
}

interface TruckOption {
  mode: string;
  label: string;
  costEur: number;
  transitDays: number;
  co2Kg: number;
  description: string;
  costPerPallet: number;
  recommended: boolean;
}

interface PackedBox {
  boxRef: string;
  lengthCm: number;
  widthCm: number;
  heightCm: number;
  totalWeightKg: number;
  utilizationPercent: number;
}

interface PackagingResult {
  totalBoxes: number;
  utilizationPercent: number;
  boxes: PackedBox[];
  unpackedItems?: unknown[];
}

interface TruckingResult {
  estimatedPallets: number;
  totalWeightKg: number;
  totalVolumeM3: number;
  options: TruckOption[];
}

const LogisticsDashboard = () => {
  const [searchParams] = useSearchParams();
  const [origin, setOrigin] = useState('CN');
  const [destination, setDestination] = useState('FR');
  const [items, setItems] = useState<PackagingItem[]>([]);
  const [newItem, setNewItem] = useState<PackagingItem>({ sku: searchParams.get('sku') ?? '', lengthCm: 30, widthCm: 20, heightCm: 15, weightKg: 2, quantity: 1 });

  const [packagingResult, setPackagingResult] = useState<PackagingResult | null>(null);
  const [truckingResult, setTruckingResult] = useState<TruckingResult | null>(null);
  const [loadingPack, setLoadingPack] = useState(false);
  const [loadingTruck, setLoadingTruck] = useState(false);

  const addItem = () => {
    if (!newItem.sku) { toast.error('SKU requis'); return; }
    setItems([...items, newItem]);
    setNewItem({ sku: '', lengthCm: 30, widthCm: 20, heightCm: 15, weightKg: 2, quantity: 1 });
  };

  const removeItem = (idx: number) => setItems(items.filter((_, i) => i !== idx));

  const calculateAll = async () => {
    if (items.length === 0) { toast.error('Ajoutez au moins un article'); return; }

    setLoadingPack(true);
    setLoadingTruck(true);

    try {
      const packRes = await incokalkAPI.logistics.calculatePackaging({ items });
      setPackagingResult(packRes.data);

      try {
        const truckRes = await incokalkAPI.logistics.calculateTrucking({
          originCountry: origin,
          destinationCountry: destination,
          weightKg: packRes.data.totalWeightKg,
          volumeM3: packRes.data.totalVolumeM3,
          palletCount: packRes.data.totalBoxes,
        });
        setTruckingResult(truckRes.data);
      } catch {
        toast.error('Erreur tarifs transport');
      }
    } catch {
      toast.error('Erreur calcul packaging');
    } finally {
      setLoadingPack(false);
      setLoadingTruck(false);
    }
  };

  const totalWeight = items.reduce((s, i) => s + i.weightKg * i.quantity, 0);
  const totalVolume = items.reduce((s, i) => s + (i.lengthCm * i.widthCm * i.heightCm) / 1e6 * i.quantity, 0);

  return (
    <div className="min-h-screen bg-gradient-to-b from-accent-soft via-surface to-success/10">
      <div className="max-w-6xl mx-auto px-4 py-12">
        <div className="text-center mb-12">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-accent-soft mb-4">
            <Package size={32} className="text-accent" />
          </div>
          <h1 className="text-3xl sm:text-4xl font-extrabold text-ink mb-3">
            <span className="text-accent font-normal" aria-hidden="true">:: </span>
            Dashboard Logistique
          </h1>
          <p className="text-ink-soft max-w-2xl mx-auto">
            Packaging, colisage et tarification transport — tout en un seul endroit.
          </p>
        </div>

        {/* Form */}
        <div className="bg-surface rounded-none shadow-lg border border-line p-6 mb-8">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {/* Route */}
            <div className="space-y-4">
              <h3 className="font-bold text-ink flex items-center gap-2"><Truck size={18} /> Route</h3>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-xs text-ink-soft mb-1 block">Origine</label>
                  <select value={origin} onChange={e => setOrigin(e.target.value)} className="w-full px-3 py-2 border rounded-none text-sm">
                    <option value="CN">Chine</option><option value="US">États-Unis</option><option value="DE">Allemagne</option>
                    <option value="JP">Japon</option><option value="MA">Maroc</option><option value="TR">Turquie</option>
                    <option value="IN">Inde</option><option value="VN">Vietnam</option><option value="FR">France</option>
                  </select>
                </div>
                <div>
                  <label className="text-xs text-ink-soft mb-1 block">Destination</label>
                  <select value={destination} onChange={e => setDestination(e.target.value)} className="w-full px-3 py-2 border rounded-none text-sm">
                    <option value="FR">France</option><option value="US">États-Unis</option><option value="DE">Allemagne</option>
                    <option value="GB">Royaume-Uni</option><option value="ES">Espagne</option><option value="IT">Italie</option>
                    <option value="NL">Pays-Bas</option><option value="BE">Belgique</option>
                  </select>
                </div>
              </div>
            </div>

            {/* Résumé */}
            <div className="space-y-4">
              <h3 className="font-bold text-ink flex items-center gap-2"><Box size={18} /> Résumé</h3>
              <div className="grid grid-cols-3 gap-3">
                <div className="bg-accent-soft rounded-none p-3 text-center">
                  <div className="text-2xl font-bold text-accent-strong">{items.length}</div>
                  <div className="text-xs text-ink-soft">Articles</div>
                </div>
                <div className="bg-success/10 rounded-none p-3 text-center">
                  <div className="text-2xl font-bold text-success">{totalWeight.toFixed(1)}</div>
                  <div className="text-xs text-ink-soft">kg</div>
                </div>
                <div className="bg-accent/10 rounded-none p-3 text-center">
                  <div className="text-2xl font-bold text-accent-strong">{totalVolume.toFixed(3)}</div>
                  <div className="text-xs text-ink-soft">m³</div>
                </div>
              </div>
            </div>
          </div>

          {/* Articles */}
          <div className="mt-6">
            <h3 className="font-bold text-ink mb-3 flex items-center gap-2"><Package size={18} /> Articles</h3>
            {items.length > 0 && (
              <div className="space-y-2 mb-4">
                {items.map((item, idx) => (
                  <div key={idx} className="flex items-center gap-3 bg-surface-2 rounded-none p-3 text-sm">
                    <span className="text-ink-soft w-6 text-center">{idx + 1}</span>
                    <span className="font-medium flex-1">{item.sku}</span>
                    <span className="text-ink-soft">{item.quantity}×</span>
                    <Ruler size={14} className="text-ink-soft" />
                    <span className="text-ink-soft">{item.lengthCm}×{item.widthCm}×{item.heightCm}cm</span>
                    <Weight size={14} className="text-ink-soft" />
                    <span className="text-ink-soft">{item.weightKg}kg</span>
                    <button onClick={() => removeItem(idx)} className="text-danger/60 hover:text-danger"><Trash2 size={14} /></button>
                  </div>
                ))}
              </div>
            )}
            <div className="flex gap-2 items-end flex-wrap">
              <input placeholder="SKU" value={newItem.sku} onChange={e => setNewItem({ ...newItem, sku: e.target.value })} className="flex-1 min-w-[120px] px-3 py-2 border rounded-none text-sm" />
              <input type="number" placeholder="L" value={newItem.lengthCm} onChange={e => setNewItem({ ...newItem, lengthCm: Number(e.target.value) })} className="w-20 px-2 py-2 border rounded-none text-sm" />
              <input type="number" placeholder="l" value={newItem.widthCm} onChange={e => setNewItem({ ...newItem, widthCm: Number(e.target.value) })} className="w-20 px-2 py-2 border rounded-none text-sm" />
              <input type="number" placeholder="H" value={newItem.heightCm} onChange={e => setNewItem({ ...newItem, heightCm: Number(e.target.value) })} className="w-20 px-2 py-2 border rounded-none text-sm" />
              <input type="number" placeholder="kg" value={newItem.weightKg} onChange={e => setNewItem({ ...newItem, weightKg: Number(e.target.value) })} className="w-20 px-2 py-2 border rounded-none text-sm" />
              <input type="number" placeholder="Qté" value={newItem.quantity} onChange={e => setNewItem({ ...newItem, quantity: Math.max(1, Number(e.target.value)) })} className="w-16 px-2 py-2 border rounded-none text-sm" />
              <button onClick={addItem} className="bg-accent text-white px-3 py-2 rounded-none hover:bg-accent-strong flex-shrink-0"><Plus size={18} /></button>
            </div>
          </div>

          <button onClick={calculateAll} disabled={loadingPack || items.length === 0} className="mt-6 w-full bg-gradient-to-r from-accent to-accent-strong text-white py-3 px-6 rounded-none font-bold hover:from-accent-strong hover:to-accent-strong disabled:opacity-50 flex items-center justify-center gap-2 text-lg">
            {(loadingPack || loadingTruck) ? <><Loader2 className="animate-spin" /> Calcul en cours...</> : <>Calculer le packaging & les tarifs</>}
          </button>
        </div>

        {/* Résultats */}
        {packagingResult && (
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            {/* Packaging */}
            <div className="relative bg-surface rounded-none shadow-lg border border-line p-6">
              <span className="hud-corner hud-corner-tl" aria-hidden="true" />
              <span className="hud-corner hud-corner-tr" aria-hidden="true" />
              <span className="hud-corner hud-corner-bl" aria-hidden="true" />
              <span className="hud-corner hud-corner-br" aria-hidden="true" />
              <h3 className="font-bold text-ink text-lg mb-4 flex items-center gap-2"><Package size={20} className="text-accent" /> Résultat packaging</h3>
              <div className="grid grid-cols-2 gap-4 mb-4">
                <div className="bg-accent-soft rounded-none p-4 text-center">
                  <div className="text-3xl font-bold text-accent-strong">{packagingResult.totalBoxes}</div>
                  <div className="text-sm text-ink-soft">Colis</div>
                </div>
                <div className="bg-success/10 rounded-none p-4 text-center">
                  <div className="text-3xl font-bold text-success">{packagingResult.utilizationPercent}%</div>
                  <div className="text-sm text-ink-soft">Remplissage</div>
                </div>
              </div>
              {packagingResult.boxes.map((box: PackedBox, idx: number) => (
                <div key={idx} className="border border-line rounded-none p-3 mb-2">
                  <div className="flex justify-between items-center">
                    <span className="font-medium text-sm">{box.boxRef}</span>
                    <span className="text-sm text-ink-soft">{box.lengthCm}×{box.widthCm}×{box.heightCm}cm</span>
                  </div>
                  <div className="flex justify-between items-center mt-1 text-xs text-ink-soft">
                    <span>{box.totalWeightKg} kg</span>
                    <span>{box.utilizationPercent.toFixed(1)}% rempli</span>
                  </div>
                  <div className="w-full bg-line rounded-full h-2 mt-2">
                    <div className="bg-accent rounded-full h-2" style={{ width: `${Math.min(box.utilizationPercent, 100)}%` }} />
                  </div>
                </div>
              ))}
              {(packagingResult.unpackedItems?.length ?? 0) > 0 && (
                <div className="mt-3 p-3 bg-danger/10 rounded-none text-sm text-danger">
                  ⚠️ {packagingResult.unpackedItems?.length} article(s) non placé(s)
                </div>
              )}
            </div>

            {/* Tarifs transport */}
            {truckingResult && (
              <div className="bg-surface rounded-none shadow-lg border border-line p-6">
                <h3 className="font-bold text-ink text-lg mb-4 flex items-center gap-2"><Truck size={20} className="text-success" /> Tarifs transport</h3>
                <p className="text-sm text-ink-soft mb-4">{truckingResult.estimatedPallets} palette{truckingResult.estimatedPallets > 1 ? 's' : ''} — {truckingResult.totalWeightKg} kg — {truckingResult.totalVolumeM3} m³</p>
                <div className="space-y-3">
                  {truckingResult.options.map((opt: TruckOption) => (
                    <div key={opt.mode} className={`rounded-none p-4 ${opt.recommended ? 'bg-success/10 border-2 border-success/40' : 'bg-surface-2 border border-line'}`}>
                      <div className="flex justify-between items-start">
                        <div>
                          <div className="flex items-center gap-2">
                            <span className="font-bold text-ink">{opt.label}</span>
                            {opt.recommended && <span className="bg-success text-white text-xs px-2 py-0.5 rounded-full">Recommandé</span>}
                          </div>
                          <p className="text-xs text-ink-soft mt-1">{opt.description}</p>
                          <div className="flex gap-4 mt-2 text-xs text-ink-soft">
                            <span>⏱ {opt.transitDays} jour{opt.transitDays > 1 ? 's' : ''}</span>
                            <span>💰 {opt.costPerPallet} €/palette</span>
                            <span>🌱 {opt.co2Kg} kg CO₂</span>
                          </div>
                        </div>
                        <div className="text-right">
                          <div className="text-2xl font-bold text-success">{formatEur(opt.costEur)}</div>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};

export default LogisticsDashboard;
