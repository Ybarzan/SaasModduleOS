import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Scale, ArrowRight, Leaf } from 'lucide-react';

const VolumetricWeight = () => {
  const [length, setLength] = useState(60);
  const [width, setWidth] = useState(40);
  const [height, setHeight] = useState(40);
  const [actualWeight, setActualWeight] = useState(15);
  const [carrierType, setCarrierType] = useState<'air' | 'sea' | 'road'>('air');

  const DIVISORS: Record<string, { divisor: number; label: string }> = {
    air: { divisor: 6000, label: 'Aérien (÷ 6000)' },
    sea: { divisor: 1000, label: 'Maritime (÷ 1000)' },
    road: { divisor: 3000, label: 'Routier (÷ 3000)' },
  };

  const divisor = DIVISORS[carrierType]?.divisor ?? 6000;
  const volumeCm3 = length * width * height;
  const volumeM3 = volumeCm3 / 1e6;
  const volumetricWeight = volumeCm3 / divisor;
  const chargeableWeight = Math.max(actualWeight, volumetricWeight);
  const isVolumetric = volumetricWeight > actualWeight;
  const difference = Math.abs(volumetricWeight - actualWeight);
  const extraCostPercent = isVolumetric ? ((volumetricWeight / actualWeight - 1) * 100).toFixed(1) : '0';

  return (
    <div className="min-h-screen bg-gradient-to-b from-accent via-white to-accent">
      <div className="max-w-4xl mx-auto px-4 py-12">
        <div className="text-center mb-10">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-accent/10 mb-4">
            <Scale size={32} className="text-accent" />
          </div>
          <h1 className="text-3xl sm:text-4xl font-extrabold text-ink mb-3">Poids volumétrique</h1>
          <p className="text-ink-soft max-w-xl mx-auto">Comparez poids réel vs poids volumétrique pour déterminer le poids facturable.</p>
        </div>

        <div className="grid lg:grid-cols-2 gap-6">
          {/* Formulaire */}
          <div className="bg-surface rounded-2xl shadow-sm border border-line p-6 space-y-5">
            <h2 className="text-sm font-semibold text-ink-soft uppercase tracking-wider">Dimensions du colis</h2>
            <div className="grid grid-cols-3 gap-3">
              <div>
                <label className="text-xs text-ink-soft mb-1 block">Longueur (cm)</label>
                <input type="number" value={length} onChange={e => setLength(Number(e.target.value))} className="w-full px-3 py-2.5 border border-line rounded-lg text-sm bg-bg text-center font-mono" />
              </div>
              <div>
                <label className="text-xs text-ink-soft mb-1 block">Largeur (cm)</label>
                <input type="number" value={width} onChange={e => setWidth(Number(e.target.value))} className="w-full px-3 py-2.5 border border-line rounded-lg text-sm bg-bg text-center font-mono" />
              </div>
              <div>
                <label className="text-xs text-ink-soft mb-1 block">Hauteur (cm)</label>
                <input type="number" value={height} onChange={e => setHeight(Number(e.target.value))} className="w-full px-3 py-2.5 border border-line rounded-lg text-sm bg-bg text-center font-mono" />
              </div>
            </div>

            <div>
              <label className="text-xs text-ink-soft mb-1 block">Poids réel (kg)</label>
              <input type="number" value={actualWeight} onChange={e => setActualWeight(Number(e.target.value))} className="w-full px-3 py-2.5 border border-line rounded-lg text-sm bg-bg" />
              <input type="range" value={actualWeight} onChange={e => setActualWeight(Number(e.target.value))} className="w-full h-1.5 bg-surface-2 rounded-lg appearance-none cursor-pointer accent-accent mt-2" min="0.1" max="1000" step="0.1" />
            </div>

            <div>
              <label className="text-xs text-ink-soft mb-1 block">Mode de transport</label>
              <div className="grid grid-cols-3 gap-2">
                {(['AIR', 'SEA', 'ROAD'] as const).map(mode => (
                  <button key={mode} onClick={() => setCarrierType(mode.toLowerCase() as 'air' | 'sea' | 'road')} className={`py-2.5 rounded-lg text-sm font-medium transition-colors ${carrierType === mode.toLowerCase() ? 'bg-accent text-white' : 'bg-surface-2 text-ink-soft hover:bg-surface-2'}`}>
                    {mode === 'AIR' ? 'Aérien' : mode === 'SEA' ? 'Maritime' : 'Routier'}
                  </button>
                ))}
              </div>
              <p className="text-[10px] text-ink-soft mt-1">Diviseur : {divisor}</p>
            </div>
          </div>

          {/* Résultats */}
          <div className="space-y-4">
            <div className="bg-surface rounded-2xl shadow-sm border border-line p-6">
              <h2 className="text-sm font-semibold text-ink-soft uppercase tracking-wider mb-4">Comparaison</h2>
              <div className="flex items-center gap-4 mb-6">
                <div className={`flex-1 rounded-xl p-4 text-center ${!isVolumetric ? 'bg-accent/10 border-2 border-accent/40' : 'bg-bg border border-line'}`}>
                  <div className="text-3xl font-bold text-ink">{actualWeight.toFixed(1)}</div>
                  <div className="text-xs text-ink-soft">kg réels</div>
                </div>
                <ArrowRight size={24} className="text-ink-soft flex-shrink-0" />
                <div className={`flex-1 rounded-xl p-4 text-center ${isVolumetric ? 'bg-accent/10 border-2 border-accent/40' : 'bg-bg border border-line'}`}>
                  <div className="text-3xl font-bold text-ink">{volumetricWeight.toFixed(1)}</div>
                  <div className="text-xs text-ink-soft">kg volumétriques</div>
                </div>
              </div>

              <div className="bg-bg rounded-xl p-4 text-center mb-4">
                <div className="text-xs text-ink-soft mb-1">Poids facturable</div>
                <div className="text-4xl font-extrabold text-accent-strong">{chargeableWeight.toFixed(1)} kg</div>
                <div className="text-xs text-ink-soft mt-1">
                  {isVolumetric ? `Le poids volumétrique est ${extraCostPercent}% plus élevé que le poids réel` : 'Le poids réel est utilisé pour la facturation'}
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3 text-sm">
                <div className="bg-bg rounded-lg p-3">
                  <div className="text-[10px] text-ink-soft uppercase">Volume</div>
                  <div className="font-bold text-ink">{volumeM3.toFixed(4)} m³</div>
                </div>
                <div className="bg-bg rounded-lg p-3">
                  <div className="text-[10px] text-ink-soft uppercase">Différence</div>
                  <div className={`font-bold ${isVolumetric ? 'text-warning' : 'text-success'}`}>{difference.toFixed(1)} kg</div>
                </div>
              </div>
            </div>

            {isVolumetric && (
              <div className="bg-warning/10 border border-warning/40 rounded-xl p-4">
                <div className="text-sm font-bold text-warning">⚠️ Poids volumétrique plus élevé</div>
                <div className="text-xs text-warning mt-1">
                  Vous serez facturé sur le poids volumétrique ({volumetricWeight.toFixed(1)} kg) plutôt que le poids réel ({actualWeight} kg).
                  Envisagez un emballage plus compact pour réduire le coût.
                </div>
              </div>
            )}

            {/* Le poids facturable calculé ici est directement l'entrée du calculateur
                CO2 (même unité, même concept) -- éviter la ressaisie manuelle. */}
            <Link
              to={`/co2?weight=${Math.round(chargeableWeight)}`}
              className="flex items-center justify-center gap-2 px-4 py-2.5 bg-accent-soft text-accent-strong rounded-lg text-sm font-medium hover:bg-accent/20 transition-colors"
            >
              <Leaf size={16} />
              Calculer l'impact CO₂ de ce poids
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
};

export default VolumetricWeight;
