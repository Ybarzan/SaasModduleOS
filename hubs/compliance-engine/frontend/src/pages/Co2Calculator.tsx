import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { Leaf, Truck, Ship, Plane, ArrowRight, BarChart3, Info } from 'lucide-react';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Cell } from 'recharts';
import { formatNumber } from '../lib/formatNumber';

const EMISSION_FACTORS: Record<string, { label: string; factor: number; icon: typeof Ship; color: string }> = {
  SEA: { label: 'Maritime', factor: 0.016, icon: Ship, color: '#3B82B5' },
  AIR: { label: 'Aérien', factor: 0.255, icon: Plane, color: '#8B5CF6' },
  ROAD: { label: 'Routier', factor: 0.062, icon: Truck, color: '#D67057' },
};

const COMMON_ROUTES = [
  { from: 'Shanghai', to: 'Le Havre', distance: 19400, label: 'Chine → France' },
  { from: 'Rotterdam', to: 'New York', distance: 5800, label: 'Europe → US Est' },
  { from: 'Hambourg', to: 'Shanghai', distance: 19200, label: 'Allemagne → Chine' },
  { from: 'Marseille', to: 'Casablanca', distance: 1200, label: 'France → Maroc' },
  { from: 'Le Havre', to: 'Dakar', distance: 4200, label: 'France → Sénégal' },
  { from: 'Marseille', to: 'Djibouti', distance: 6400, label: 'France → Djibouti' },
];

interface Result {
  mode: string;
  co2Kg: number;
  co2PerTonneKm: number;
}

const Co2Calculator = () => {
  const [searchParams] = useSearchParams();
  // Pré-remplissage depuis VolumetricWeight (?weight=<poids facturable en kg>) --
  // lu une seule fois au montage, volontairement pas synchronisé en continu avec
  // l'URL : au-delà du pré-remplissage initial, c'est un champ normal du formulaire.
  const [weight, setWeight] = useState(() => {
    const fromQuery = Number(searchParams.get('weight'));
    return fromQuery > 0 ? fromQuery : 1000;
  });
  const [distance, setDistance] = useState(10000);
  const [selectedRoute, setSelectedRoute] = useState<number | null>(null);

  const results: Result[] = Object.entries(EMISSION_FACTORS).map(([key, { factor }]) => ({
    mode: key,
    co2Kg: Math.round(weight * distance * factor / 1000 * 100) / 100,
    co2PerTonneKm: factor,
  }));

  const minCo2 = Math.min(...results.map(r => r.co2Kg));
  const maxCo2 = Math.max(...results.map(r => r.co2Kg));
  const maxSaving = maxCo2 > 0 ? Math.round(((maxCo2 - minCo2) / maxCo2) * 100) : 0;

  const chartData = results.map(r => ({
    name: EMISSION_FACTORS[r.mode].label,
    co2: r.co2Kg,
    color: EMISSION_FACTORS[r.mode].color,
  }));

  const handleRouteSelect = (index: number) => {
    setSelectedRoute(index);
    setDistance(COMMON_ROUTES[index].distance);
  };

  return (
    <div className="min-h-screen bg-bg">
      <div className="max-w-6xl mx-auto px-4 py-12">
        <div className="text-center mb-12">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-accent-soft mb-4">
            <Leaf size={32} className="text-accent" />
          </div>
          <h1 className="text-3xl sm:text-4xl font-extrabold text-ink mb-3">
            <span className="text-accent font-normal" aria-hidden="true">:: </span>
            Calculateur d'émissions CO₂
          </h1>
          <p className="text-lg text-ink-soft max-w-2xl mx-auto">
            Estimez l'empreinte carbone de vos expéditions et comparez les modes de transport
          </p>
        </div>

        <div className="grid lg:grid-cols-3 gap-8">
          <div className="lg:col-span-1 space-y-6">
            <div className="bg-surface rounded-none shadow-lg border border-line p-6">
              <h2 className="text-lg font-bold text-ink mb-4">Paramètres</h2>

              <div className="space-y-5">
                <div>
                  <label className="block text-sm font-medium text-ink mb-2">
                    Poids de la marchandise (kg)
                  </label>
                  <input
                    type="number"
                    value={weight}
                    onChange={(e) => setWeight(Math.max(1, Number(e.target.value)))}
                    className="w-full px-4 py-2.5 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm bg-surface text-ink"
                    min="1"
                  />
                  <input
                    type="range"
                    value={weight}
                    onChange={(e) => setWeight(Number(e.target.value))}
                    className="w-full h-2 bg-surface-2 rounded-none appearance-none cursor-pointer accent-accent mt-2"
                    min="1"
                    max="50000"
                    step="100"
                  />
                  <div className="flex justify-between text-xs text-ink-soft mt-1">
                    <span>1 kg</span>
                    <span>50 000 kg</span>
                  </div>
                </div>

                <div>
                  <label className="block text-sm font-medium text-ink mb-2">
                    Distance (km)
                  </label>
                  <input
                    type="number"
                    value={distance}
                    onChange={(e) => setDistance(Math.max(1, Number(e.target.value)))}
                    className="w-full px-4 py-2.5 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm bg-surface text-ink"
                    min="1"
                  />
                  <input
                    type="range"
                    value={distance}
                    onChange={(e) => setDistance(Number(e.target.value))}
                    className="w-full h-2 bg-surface-2 rounded-none appearance-none cursor-pointer accent-accent mt-2"
                    min="100"
                    max="25000"
                    step="100"
                  />
                  <div className="flex justify-between text-xs text-ink-soft mt-1">
                    <span>100 km</span>
                    <span>25 000 km</span>
                  </div>
                </div>
              </div>
            </div>

            <div className="bg-surface rounded-none shadow-lg border border-line p-6">
              <h2 className="text-sm font-bold text-ink mb-3 uppercase tracking-wide">Routes courantes</h2>
              <div className="space-y-2">
                {COMMON_ROUTES.map((route, i) => (
                  <button
                    key={i}
                    onClick={() => handleRouteSelect(i)}
                    className={`w-full text-left px-3 py-2 rounded-none text-sm transition-all ${
                      selectedRoute === i
                        ? 'bg-accent-soft text-accent-strong font-semibold border border-accent/20'
                        : 'text-ink-soft hover:bg-surface-2 border border-transparent'
                    }`}
                  >
                    <span>{route.label}</span>
                    <span className="text-xs text-ink-soft ml-1">({formatNumber(route.distance)} km)</span>
                  </button>
                ))}
              </div>
            </div>
          </div>

          <div className="lg:col-span-2 space-y-6">
            <div className="grid grid-cols-3 gap-4">
              {results.map((r) => {
                const { label, icon: Icon, color } = EMISSION_FACTORS[r.mode];
                const isLowest = r.co2Kg === minCo2;
                return (
                  <div
                    key={r.mode}
                    className={`relative bg-surface rounded-none shadow-lg border-2 p-5 text-center transition-all ${
                      isLowest ? 'border-accent shadow-accent/20' : 'border-line'
                    }`}
                  >
                    {isLowest && (
                      <>
                        <span className="hud-corner hud-corner-tl" aria-hidden="true" />
                        <span className="hud-corner hud-corner-tr" aria-hidden="true" />
                        <span className="hud-corner hud-corner-bl" aria-hidden="true" />
                        <span className="hud-corner hud-corner-br" aria-hidden="true" />
                      </>
                    )}
                    <div className="flex justify-center mb-3">
                      <div className="w-12 h-12 rounded-none flex items-center justify-center" style={{ backgroundColor: color + '15' }}>
                        <Icon size={24} style={{ color }} />
                      </div>
                    </div>
                    <h3 className="text-sm font-semibold text-ink mb-1">{label}</h3>
                    <div className="text-2xl font-extrabold" style={{ color }}>
                      {formatNumber(r.co2Kg, { maximumFractionDigits: 0 })}
                    </div>
                    <div className="text-xs text-ink-soft mt-1">kg CO₂</div>
                    {isLowest && (
                      <div className="mt-2 inline-flex items-center gap-1 bg-accent-soft text-accent-strong text-[10px] font-bold px-2 py-1 rounded-full">
                        <Leaf size={10} />
                        LE PLUS VERTE
                      </div>
                    )}
                  </div>
                );
              })}
            </div>

            <div className="bg-surface rounded-none shadow-lg border border-line p-6">
              <h2 className="text-lg font-bold text-ink mb-4 flex items-center gap-2">
                <BarChart3 size={20} className="text-accent" />
                Comparaison des émissions
              </h2>
              <div className="h-64">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={chartData} layout="vertical" margin={{ left: 20, right: 30 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#F5E6CC" />
                    <XAxis type="number" tick={{ fontSize: 12, fill: '#3B82B5' }} />
                    <YAxis type="category" dataKey="name" tick={{ fontSize: 12, fill: '#1B4965' }} width={80} />
                    <Tooltip
                      formatter={(value) => [`${formatNumber(Number(value))} kg CO₂`, 'Émission']}
                      contentStyle={{ borderRadius: 12, border: '1px solid #F5E6CC' }}
                    />
                    <Bar dataKey="co2" radius={[0, 8, 8, 0]}>
                      {chartData.map((entry, index) => (
                        <Cell key={index} fill={entry.color} />
                      ))}
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </div>

            <div className="bg-accent-soft rounded-none border border-accent/20 p-6">
              <div className="flex items-start gap-3">
                <Info size={20} className="text-accent mt-0.5 flex-shrink-0" />
                <div>
                  <h3 className="font-bold text-ink mb-2">Réduction potentielle</h3>
                  <p className="text-sm text-ink-soft mb-3">
                    En passant du mode le plus polluant au plus vert, vous pouvez réduire vos émissions de
                    <strong className="text-accent-strong"> {maxSaving}%</strong> pour ce trajet.
                  </p>
                  <div className="flex flex-wrap gap-3">
                    <Link
                      to="/simulation"
                      className="inline-flex items-center gap-2 bg-accent text-white px-4 py-2 rounded-none text-sm font-semibold hover:bg-accent-strong transition-colors"
                    >
                      Simuler un Incoterm
                      <ArrowRight size={16} />
                    </Link>
                    <Link
                      to="/quotes"
                      className="inline-flex items-center gap-2 bg-surface text-ink px-4 py-2 rounded-none text-sm font-semibold hover:bg-surface-2 transition-colors border border-line"
                    >
                      Demander un devis vert
                    </Link>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Co2Calculator;
