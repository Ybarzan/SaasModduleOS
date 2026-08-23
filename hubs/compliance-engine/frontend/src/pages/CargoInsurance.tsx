import { useState, useEffect } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Umbrella, Loader2, ShieldCheck, Activity, TrendingUp, TrendingDown, Info, History, FileCheck2, Save } from 'lucide-react';
import { incokalkAPI } from '../lib/api';
import { useAuthStore } from '../stores/auth';
import toast from 'react-hot-toast';
import { formatEur } from '../lib/formatNumber';

interface InsuranceCalculation {
  premiumAmount: number;
  premiumRate: number;
  goodsValue: number;
  coverageAmount: number;
  transportMode: string;
  coverageType: string;
  note?: string;
}

interface InsuranceQuote {
  id: string;
  goodsValue: number;
  weightKg?: number | null;
  transportMode?: string | null;
  goodsCategory?: string | null;
  premiumRate: number;
  premiumAmount: number;
  coverageAmount: number;
  coverageType?: string | null;
  status: 'QUOTE' | 'POLICY';
  policyNumber?: string | null;
  createdAt: string;
}

const CATEGORIES = [
  { value: 'STANDARD', label: 'Standard', desc: 'Marchandises classiques' },
  { value: 'FRAGILE', label: 'Fragile', desc: 'Céramique, verre, etc.' },
  { value: 'HAUTE_VALEUR', label: 'Haute valeur', desc: 'Électronique, luxe, bijoux' },
  { value: 'PERISSABLE', label: 'Périssable', desc: 'Alimentaire, pharmaceutique' },
  { value: 'DANGEREUX', label: 'Dangereux', desc: 'Matières premières chimiques' },
  { value: 'ELECTRONIQUE', label: 'Électronique', desc: 'Composants, serveurs' },
];

const MODES = [
  { value: 'SEA', label: 'Maritime', icon: '🚢' },
  { value: 'AIR', label: 'Aérien', icon: '✈️' },
  { value: 'ROAD', label: 'Routier', icon: '🚛' },
];

const BASE_RATES: Record<string, Record<string, number>> = {
  SEA: { STANDARD: 0.003, FRAGILE: 0.0042, HAUTE_VALEUR: 0.0054, PERISSABLE: 0.0048, DANGEREUX: 0.006, ELECTRONIQUE: 0.0039 },
  AIR: { STANDARD: 0.005, FRAGILE: 0.007, HAUTE_VALEUR: 0.009, PERISSABLE: 0.008, DANGEREUX: 0.01, ELECTRONIQUE: 0.0065 },
  ROAD: { STANDARD: 0.004, FRAGILE: 0.0056, HAUTE_VALEUR: 0.0072, PERISSABLE: 0.0064, DANGEREUX: 0.008, ELECTRONIQUE: 0.0052 },
};

const COVERAGE_TYPES = [
  { code: 'ICC A', title: 'Couverture "Tous risques"', desc: 'Couverture la plus étendue. Protège contre toutes les pertes ou dommages, sauf exclusions spécifiques (guerre, grèves, vice propre). Idéal pour les marchandises à haute valeur.', color: 'bg-success' },
  { code: 'ICC B', title: 'Couverture étendue', desc: "Couvre les dommages causés par le feu, l'explosion, le naufrage, le chavirement, les chocs, les intempéries, le vol, les avaries d'eau. Adapté aux marchandises standard et fragiles.", color: 'bg-danger' },
  { code: 'ICC C', title: 'Couverture de base', desc: 'Couvre les sinistres majeurs uniquement : naufrage, incendie, explosion, chavirement, déchargement sur le quai. Recommandé pour les marchandises à faible valeur ou robustes.', color: 'bg-warning' },
];

const CargoInsurance = () => {
  const queryClient = useQueryClient();
  const isAuthed = !!useAuthStore((s) => s.token);
  const [goodsValue, setGoodsValue] = useState(50000);
  const [weight, setWeight] = useState(500);
  const [mode, setMode] = useState('SEA');
  const [category, setCategory] = useState('STANDARD');
  const [result, setResult] = useState<InsuranceCalculation | null>(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [eurUsdRate, setEurUsdRate] = useState<number | null>(null);
  const [rateLoading, setRateLoading] = useState(true);

  const { data: quotes = [] } = useQuery<InsuranceQuote[]>({
    queryKey: ['insurance-quotes'],
    queryFn: async () => (await incokalkAPI.insurance.listQuotes()).data,
    enabled: isAuthed,
  });

  useEffect(() => {
    const fetchRates = async () => {
      setRateLoading(true);
      try {
        const res = await incokalkAPI.currency.getRates('EUR');
        const rate = res.data?.rates?.USD ?? null;
        setEurUsdRate(rate);
      } catch {
        setEurUsdRate(null);
      } finally {
        setRateLoading(false);
      }
    };
    fetchRates();
  }, []);

  const handleCalculate = async () => {
    setLoading(true);
    try {
      const res = await incokalkAPI.logistics.calculateInsurance({
        goodsValue,
        weightKg: weight,
        transportMode: mode,
        goodsCategory: category,
      });
      setResult(res.data);
    } catch {
      toast.error('Erreur lors du calcul');
    } finally {
      setLoading(false);
    }
  };

  const handleSaveQuote = async () => {
    if (!result) return;
    setSaving(true);
    try {
      await incokalkAPI.insurance.saveQuote({
        goodsValue,
        weightKg: weight,
        transportMode: mode,
        goodsCategory: category,
      });
      queryClient.invalidateQueries({ queryKey: ['insurance-quotes'] });
      toast.success('Devis enregistré dans l\'historique');
    } catch {
      toast.error('Enregistrement impossible');
    } finally {
      setSaving(false);
    }
  };

  const handleActivatePolicy = async (id: string) => {
    try {
      const res = await incokalkAPI.insurance.activatePolicy(id);
      toast.success(`Police ${res.data.policyNumber} émise`);
      queryClient.invalidateQueries({ queryKey: ['insurance-quotes'] });
    } catch {
      toast.error('Souscription impossible');
    }
  };

  const today = new Date().toLocaleDateString('fr-FR', { year: 'numeric', month: 'long', day: 'numeric' });
  const rateIsUp = eurUsdRate !== null && eurUsdRate > 1.0;

  const premiumBarWidth = result ? Math.min((result.premiumAmount / result.coverageAmount) * 100, 100) : 0;
  const coverageBarWidth = 100;

  return (
    <div className="min-h-screen bg-gradient-to-b from-danger via-white to-accent">
      <div className="max-w-5xl mx-auto px-4 py-12">
        <div className="text-center mb-10">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-danger/10 mb-4">
            <Umbrella size={32} className="text-danger" />
          </div>
          <h1 className="text-3xl sm:text-4xl font-extrabold text-ink mb-3">Assurance cargo</h1>
          <p className="text-ink-soft max-w-xl mx-auto">Estimez la prime d'assurance pour votre marchandise selon le mode de transport et la catégorie.</p>
        </div>

        {/* Indicateur du marché maritime */}
        <div className="bg-surface rounded-2xl shadow-sm border border-line p-5 mb-8">
          <div className="flex items-center gap-2 mb-3">
            <Activity size={18} className="text-danger" />
            <h2 className="text-sm font-semibold text-ink">Indicateur du marché maritime</h2>
          </div>
          {rateLoading ? (
            <div className="flex items-center gap-2 text-sm text-ink-soft">
              <Loader2 size={16} className="animate-spin" /> Chargement des taux de change...
            </div>
          ) : eurUsdRate !== null ? (
            <div className="flex flex-wrap items-center gap-6">
              <div className="flex-1 min-w-[200px]">
                <div className="flex items-center gap-3">
                  <div className="bg-danger/10 rounded-xl px-4 py-2.5">
                    <span className="text-xs text-ink-soft block">Taux EUR/USD</span>
                    <span className={`text-xl font-bold ${rateIsUp ? 'text-success' : 'text-danger'}`}>{eurUsdRate.toFixed(4)}</span>
                  </div>
                  <div className={`flex items-center gap-1 rounded-xl px-4 py-2.5 ${rateIsUp ? 'bg-success/10' : 'bg-danger/10'}`}>
                    {rateIsUp ? (
                      <TrendingUp size={18} className="text-success" />
                    ) : (
                      <TrendingDown size={18} className="text-danger" />
                    )}
                    <span className={`text-sm font-bold ${rateIsUp ? 'text-success' : 'text-danger'}`}>
                      {rateIsUp ? '↗ Hausse' : '↘ Baisse'}
                    </span>
                  </div>
                </div>
              </div>
              <div className="text-xs text-ink-soft">
                Dernière mise à jour : {today}
              </div>
            </div>
          ) : (
            <div className="text-sm text-ink-soft">Données de taux de change indisponibles</div>
          )}
        </div>

        <div className="grid lg:grid-cols-2 gap-6">
          {/* Formulaire */}
          <div className="bg-surface rounded-2xl shadow-sm border border-line p-6 space-y-5">
            <h2 className="text-sm font-semibold text-ink-soft uppercase tracking-wider">Détails de l'expédition</h2>

            <div>
              <div className="flex justify-between items-center mb-1.5">
                <label className="text-sm font-medium text-ink">Valeur marchandises (€)</label>
                <input type="number" value={goodsValue} onChange={e => setGoodsValue(Number(e.target.value))} className="w-28 px-2 py-1 border border-line rounded text-right text-sm bg-bg" />
              </div>
              <input type="range" value={goodsValue} onChange={e => setGoodsValue(Number(e.target.value))} className="w-full h-1.5 bg-surface-2 rounded-lg appearance-none cursor-pointer accent-accent" min="100" max="1000000" step="100" />
            </div>

            <div>
              <label className="text-xs text-ink-soft mb-1 block">Poids (kg)</label>
              <input type="number" value={weight} onChange={e => setWeight(Number(e.target.value))} className="w-full px-3 py-2 border border-line rounded-lg text-sm bg-bg" />
            </div>

            <div>
              <label className="text-xs text-ink-soft mb-1 block">Mode de transport</label>
              <div className="grid grid-cols-3 gap-2">
                {MODES.map(m => (
                  <button key={m.value} onClick={() => setMode(m.value)} className={`py-2.5 rounded-lg text-sm font-medium transition-colors ${mode === m.value ? 'bg-danger text-white' : 'bg-surface-2 text-ink-soft hover:bg-surface-2'}`}>
                    {m.icon} {m.label}
                  </button>
                ))}
              </div>
            </div>

            <div>
              <label className="text-xs text-ink-soft mb-1 block">Catégorie de marchandise</label>
              <div className="grid grid-cols-2 gap-2">
                {CATEGORIES.map(c => (
                  <button key={c.value} onClick={() => setCategory(c.value)} className={`p-3 rounded-lg text-left transition-colors ${category === c.value ? 'bg-danger/10 border-2 border-danger/40' : 'bg-bg border border-line hover:border-line'}`}>
                    <div className="text-sm font-medium text-ink">{c.label}</div>
                    <div className="text-[10px] text-ink-soft">{c.desc}</div>
                  </button>
                ))}
              </div>
            </div>

            <button onClick={handleCalculate} disabled={loading} className="w-full bg-danger text-white py-3 rounded-xl font-semibold hover:bg-danger/90 disabled:opacity-50 flex items-center justify-center gap-2 text-sm transition-colors">
              {loading ? <><Loader2 className="animate-spin" /> Calcul...</> : 'Calculer la prime'}
            </button>
          </div>

          {/* Résultat */}
          <div className="space-y-4">
            {result ? (
              <>
                <div className="bg-surface rounded-2xl shadow-sm border border-line p-6">
                  <h2 className="text-sm font-semibold text-ink-soft uppercase tracking-wider mb-4">Estimation de la prime</h2>
                  <div className="grid grid-cols-2 gap-4 mb-4">
                    <div className="bg-danger/10 rounded-xl p-4 text-center">
                      <div className="text-3xl font-bold text-danger">{formatEur(result.premiumAmount)}</div>
                      <div className="text-xs text-ink-soft">Prime annuelle</div>
                    </div>
                    <div className="bg-success/10 rounded-xl p-4 text-center">
                      <div className="text-3xl font-bold text-success">{(result.premiumRate * 100).toFixed(2)}%</div>
                      <div className="text-xs text-ink-soft">Taux de prime</div>
                    </div>
                  </div>
                  <div className="space-y-2 text-sm">
                    <div className="flex justify-between py-1"><span className="text-ink-soft">Valeur marchandises</span><span className="font-medium">{formatEur(result.goodsValue)}</span></div>
                    <div className="flex justify-between py-1"><span className="text-ink-soft">Couverture (110%)</span><span className="font-bold text-success">{formatEur(result.coverageAmount)}</span></div>
                    <div className="flex justify-between py-1"><span className="text-ink-soft">Mode</span><span className="font-medium">{result.transportMode}</span></div>
                  </div>
                </div>

                {/* Bar chart Prime vs Couverture */}
                <div className="bg-surface rounded-2xl shadow-sm border border-line p-6">
                  <h3 className="text-sm font-semibold text-ink mb-4">Prime vs Couverture</h3>
                  <div className="space-y-3">
                    <div>
                      <div className="flex justify-between text-xs text-ink-soft mb-1">
                        <span>Couverture (110%)</span>
                        <span>{formatEur(result.coverageAmount)}</span>
                      </div>
                      <div className="w-full h-4 bg-surface-2 rounded-full overflow-hidden">
                        <div className="h-full bg-success/10 rounded-full" style={{ width: `${coverageBarWidth}%` }} />
                      </div>
                    </div>
                    <div>
                      <div className="flex justify-between text-xs text-ink-soft mb-1">
                        <span>Prime annuelle</span>
                        <span>{formatEur(result.premiumAmount)}</span>
                      </div>
                      <div className="w-full h-4 bg-surface-2 rounded-full overflow-hidden">
                        <div className="h-full bg-danger rounded-full" style={{ width: `${premiumBarWidth}%` }} />
                      </div>
                    </div>
                  </div>
                  <div className="mt-3 text-xs text-ink-soft text-center">
                    Ratio prime/couverture : {((result.premiumAmount / result.coverageAmount) * 100).toFixed(2)}%
                  </div>
                </div>

                <div className="bg-success/10 border border-success/40 rounded-xl p-4 flex items-start gap-3">
                  <ShieldCheck size={20} className="text-success mt-0.5 flex-shrink-0" />
                  <div>
                    <div className="text-sm font-bold text-success">{result.coverageType}</div>
                    <div className="text-xs text-success mt-0.5">Type de couverture recommandé</div>
                  </div>
                </div>
                {isAuthed && (
                  <button onClick={handleSaveQuote} disabled={saving} className="w-full bg-ink text-white py-3 rounded-xl font-semibold hover:bg-ink disabled:opacity-50 flex items-center justify-center gap-2 text-sm transition-colors">
                    {saving ? <><Loader2 className="animate-spin" /> Enregistrement...</> : <><Save size={16} /> Enregistrer le devis</>}
                  </button>
                )}
                {result.note && (
                  <div className="bg-bg rounded-xl p-4 text-xs text-ink-soft">{result.note}</div>
                )}
              </>
            ) : (
              <div className="bg-surface rounded-2xl shadow-sm border border-line p-12 text-center">
                <Umbrella className="h-10 w-10 mx-auto mb-3 text-ink-soft" />
                <p className="text-sm text-ink-soft">Remplissez le formulaire pour estimer la prime d'assurance</p>
              </div>
            )}
          </div>
        </div>

        {/* Historique des devis & polices */}
        {isAuthed && (
          <div className="mt-8 bg-surface rounded-2xl shadow-sm border border-line p-6">
            <div className="flex items-center gap-2 mb-5">
              <History size={18} className="text-danger" />
              <h2 className="text-sm font-semibold text-ink">Historique des devis & polices</h2>
            </div>
            {quotes.length === 0 ? (
              <p className="text-sm text-ink-soft text-center py-6">Aucun devis enregistré. Calculez une prime puis cliquez « Enregistrer le devis ».</p>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-line">
                      <th className="text-left py-2.5 px-3 text-xs font-semibold text-ink-soft uppercase">Date</th>
                      <th className="text-left py-2.5 px-3 text-xs font-semibold text-ink-soft uppercase">Valeur</th>
                      <th className="text-center py-2.5 px-3 text-xs font-semibold text-ink-soft uppercase">Mode</th>
                      <th className="text-right py-2.5 px-3 text-xs font-semibold text-ink-soft uppercase">Prime</th>
                      <th className="text-center py-2.5 px-3 text-xs font-semibold text-ink-soft uppercase">Statut</th>
                      <th className="text-center py-2.5 px-3 text-xs font-semibold text-ink-soft uppercase">Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {quotes.map((q) => (
                      <tr key={q.id} className="border-b border-line hover:bg-danger/20 transition-colors">
                        <td className="py-3 px-3 text-ink-soft">{new Date(q.createdAt).toLocaleDateString('fr-FR')}</td>
                        <td className="py-3 px-3 font-medium text-ink">{formatEur(q.goodsValue)}</td>
                        <td className="py-3 px-3 text-center text-ink-soft">{q.transportMode ?? '—'}</td>
                        <td className="py-3 px-3 text-right font-bold text-danger">{formatEur(q.premiumAmount)}</td>
                        <td className="py-3 px-3 text-center">
                          {q.status === 'POLICY' ? (
                            <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium bg-success/10 text-success">
                              <FileCheck2 size={12} /> {q.policyNumber}
                            </span>
                          ) : (
                            <span className="inline-flex items-center px-2.5 py-1 rounded-full text-xs font-medium bg-warning/10 text-warning">Devis</span>
                          )}
                        </td>
                        <td className="py-3 px-3 text-center">
                          {q.status === 'QUOTE' && (
                            <button onClick={() => handleActivatePolicy(q.id)} className="px-3 py-1.5 bg-success text-white text-xs font-medium rounded-lg hover:bg-success/90 transition-colors">
                              Souscrire la police
                            </button>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}

        {/* Taux d'assurance par catégorie */}
        <div className="mt-10 bg-surface rounded-2xl shadow-sm border border-line p-6">
          <h2 className="text-sm font-semibold text-ink mb-5">Taux d'assurance par catégorie</h2>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-line">
                  <th className="text-left py-2.5 px-3 text-xs font-semibold text-ink-soft uppercase">Catégorie</th>
                  {MODES.map(m => (
                    <th key={m.value} className="text-center py-2.5 px-3 text-xs font-semibold text-ink-soft uppercase">{m.icon} {m.label}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {CATEGORIES.map(c => (
                  <tr key={c.value} className="border-b border-line hover:bg-danger/20 transition-colors">
                    <td className="py-3 px-3">
                      <div className="font-medium text-ink">{c.label}</div>
                      <div className="text-[10px] text-ink-soft">{c.desc}</div>
                    </td>
                    {MODES.map(m => {
                      const rate = BASE_RATES[m.value][c.value];
                      const barPct = rate * 100 * 50;
                      return (
                        <td key={m.value} className="text-center py-3 px-3">
                          <div className="text-danger font-bold">{(rate * 100).toFixed(1)}%</div>
                          <div className="mt-1 w-full bg-surface-2 rounded-full h-1.5 overflow-hidden">
                            <div className="h-full bg-danger/10 rounded-full" style={{ width: `${Math.min(barPct, 100)}%` }} />
                          </div>
                        </td>
                      );
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="text-[10px] text-ink-soft mt-3">Taux de base indicatifs. La prime finale dépend de la valeur, du poids et du profil de risque.</p>
        </div>

        {/* Catégories de couverture */}
        <div className="mt-8 bg-surface rounded-2xl shadow-sm border border-line p-6">
          <div className="flex items-center gap-2 mb-5">
            <Info size={18} className="text-danger" />
            <h2 className="text-sm font-semibold text-ink">Catégories de couverture</h2>
          </div>
          <div className="grid md:grid-cols-3 gap-4">
            {COVERAGE_TYPES.map(ct => (
              <div key={ct.code} className="border border-line rounded-xl p-5 hover:shadow-sm transition-shadow">
                <div className="flex items-center gap-2 mb-3">
                  <div className={`w-3 h-3 rounded-full ${ct.color}`} />
                  <span className="font-bold text-ink text-sm">{ct.code}</span>
                </div>
                <div className="text-xs font-semibold text-ink-soft mb-2">{ct.title}</div>
                <p className="text-xs text-ink-soft leading-relaxed">{ct.desc}</p>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
};

export default CargoInsurance;
