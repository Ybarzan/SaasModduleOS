import type { AxiosError } from 'axios';
import { useState, Fragment } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Ship, Plane, Truck, Train, ArrowRight, CheckCircle, XCircle,
  Loader2, Clock, TrendingUp, Package, ChevronDown, ChevronUp, AlertTriangle, BarChart3
} from 'lucide-react';
import toast from 'react-hot-toast';
import { incokalkAPI } from '../lib/api';
import { PredictionAccuracyChart, type AccuracyPoint } from '../components/PredictionAccuracyChart';

interface Prediction {
  id: string;
  origin: string;
  destination: string;
  mode: string;
  carrierName: string;
  predictedArrival: string;
  confidencePercent: number;
  confidenceLevel: string;
  baselineDays: number;
  predictedDays: number;
  carrierEstimateDays: number;
  varianceDays: number;
  riskFactors: string | null;
  seasonalFactor: number;
  congestionFactor: number;
  customsDelayDays: number;
  weatherDelayDays: number;
  isOnTime: boolean | null;
  actualArrival: string | null;
  actualDays: number | null;
  predictionAccuracy: number | null;
  notes: string | null;
  createdAt: string;
}

interface PredictionStats {
  total: number;
  avgAccuracy: number;
  onTimePercent: number;
  avgDays: number;
}

const countries = [
  { code: 'FR', name: 'France' },
  { code: 'DE', name: 'Allemagne' },
  { code: 'IT', name: 'Italie' },
  { code: 'ES', name: 'Espagne' },
  { code: 'NL', name: 'Pays-Bas' },
  { code: 'BE', name: 'Belgique' },
  { code: 'VN', name: 'Vietnam' },
  { code: 'CN', name: 'Chine' },
  { code: 'IN', name: 'Inde' },
  { code: 'JP', name: 'Japon' },
  { code: 'KR', name: 'Corée du Sud' },
  { code: 'US', name: 'États-Unis' },
  { code: 'GB', name: 'Royaume-Uni' },
  { code: 'MA', name: 'Maroc' },
  { code: 'TN', name: 'Tunisie' },
  { code: 'TR', name: 'Turquie' },
  { code: 'BR', name: 'Brésil' },
  { code: 'MX', name: 'Mexique' },
  { code: 'SG', name: 'Singapour' },
  { code: 'AE', name: 'EAU' },
  { code: 'SA', name: 'Arabie Saoudite' },
  { code: 'AU', name: 'Australie' },
  { code: 'ZA', name: 'Afrique du Sud' },
];

const modes = [
  { value: 'SEA', label: 'Maritime', icon: Ship },
  { value: 'AIR', label: 'Aérien', icon: Plane },
  { value: 'ROAD', label: 'Route', icon: Truck },
  { value: 'RAIL', label: 'Rail', icon: Train },
];

const getModeIcon = (mode: string) => {
  const m = modes.find((mo) => mo.value === mode);
  return m ? m.icon : Package;
};

const getConfidenceColor = (level: string) => {
  switch (level?.toUpperCase()) {
    case 'HIGH': return 'bg-success/10 text-success';
    case 'MEDIUM': return 'bg-warning/10 text-warning';
    case 'LOW': return 'bg-danger/10 text-danger';
    default: return 'bg-surface-2 text-ink';
  }
};

const getConfidenceBarColor = (level: string) => {
  switch (level?.toUpperCase()) {
    case 'HIGH': return 'bg-success';
    case 'MEDIUM': return 'bg-warning';
    case 'LOW': return 'bg-danger';
    default: return 'bg-bg0';
  }
};

const getCountryName = (code: string) => {
  const country = countries.find((c) => c.code === code);
  return country ? `${country.name} (${code})` : code;
};

const RISK_FACTOR_LABELS: Record<string, string> = {
  cape_routing: "Route actuelle via le Cap de Bonne-Espérance (Mer Rouge fermée) — environ 10 à 14 jours de plus que l'ancienne route par Suez",
  seasonal: 'Période de forte affluence',
  congestion: 'Port très fréquenté en ce moment',
  customs: 'Délai douanier possible (pays différents)',
  weather: 'Conditions météo défavorables',
  carrier_variability: 'Historique du transporteur peu régulier',
};

const CONFIDENCE_LABELS: Record<string, string> = {
  HIGH: 'Fiable',
  MEDIUM: 'Correcte',
  LOW: 'Incertaine',
};

const confidenceLabel = (level: string) => CONFIDENCE_LABELS[level?.toUpperCase()] ?? level;

const parseRiskFactors = (raw: string | null | undefined): string[] => {
  if (!raw) return [];
  return raw
    .split(',')
    .map((code) => code.trim())
    .filter(Boolean)
    .map((code) => RISK_FACTOR_LABELS[code] ?? code);
};

// Chaque niveau reste indépendamment libellé (texte avant couleur) : évite de faire porter
// l'identité à trois teintes juxtaposées, dont l'écart normal-vision serait insuffisant
// une fois côte à côte (validé via la skill dataviz — cf. plan de session).
const CONFIDENCE_COUNT_ORDER = [
  { level: 'HIGH', label: 'Fiable', barClass: 'bg-success', textClass: 'text-success' },
  { level: 'MEDIUM', label: 'Correcte', barClass: 'bg-warning', textClass: 'text-warning' },
  { level: 'LOW', label: 'Incertaine', barClass: 'bg-danger', textClass: 'text-danger' },
];

const EtaPredictions = () => {
  const queryClient = useQueryClient();
  const [selectedPrediction, setSelectedPrediction] = useState<string | null>(null);
  const [showLatestDetail, setShowLatestDetail] = useState(false);
  const [technicalDetailRowId, setTechnicalDetailRowId] = useState<string | null>(null);
  const [form, setForm] = useState({
    origin: '',
    destination: '',
    mode: 'SEA',
    carrierName: '',
  });

  const { data: predictionsData, isLoading: predictionsLoading } = useQuery({
    queryKey: ['eta-predictions'],
    queryFn: async () => {
      const res = await incokalkAPI.eta.list();
      return res.data as Prediction[] | { predictions: Prediction[] };
    },
  });

  const predictions = Array.isArray(predictionsData)
    ? predictionsData
    : predictionsData?.predictions ?? [];

  const confidenceCounts = CONFIDENCE_COUNT_ORDER.map((c) => ({
    ...c,
    count: predictions.filter((p: Prediction) => p.confidenceLevel?.toUpperCase() === c.level).length,
  }));
  const maxConfidenceCount = Math.max(1, ...confidenceCounts.map((c) => c.count));

  const accuracyData: AccuracyPoint[] = predictions
    .filter((p: Prediction) => p.actualDays != null)
    .slice(0, 8)
    .reverse()
    .map((p: Prediction) => ({
      label: `${p.origin}→${p.destination}`,
      predicted: p.predictedDays,
      actual: p.actualDays as number,
    }));

  const { data: stats } = useQuery({
    queryKey: ['eta-stats'],
    queryFn: async () => {
      const res = await incokalkAPI.eta.stats();
      return res.data as PredictionStats;
    },
  });

  const predictMutation = useMutation({
    mutationFn: (data: typeof form) => incokalkAPI.eta.predict(data),
    onSuccess: () => {
      toast.success('Prédiction générée avec succès');
      setForm({ origin: '', destination: '', mode: 'SEA', carrierName: '' });
      queryClient.invalidateQueries({ queryKey: ['eta-predictions'] });
      queryClient.invalidateQueries({ queryKey: ['eta-stats'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || 'Erreur lors de la prédiction');
    },
  });

  const updateActualMutation = useMutation({
    mutationFn: ({ id, actualArrival }: { id: string; actualArrival: string }) =>
      incokalkAPI.eta.updateActual(id, { actualArrival }),
    onSuccess: () => {
      toast.success('Arrivée réelle enregistrée');
      queryClient.invalidateQueries({ queryKey: ['eta-predictions'] });
      queryClient.invalidateQueries({ queryKey: ['eta-stats'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || "Erreur lors de l'enregistrement");
    },
  });

  const handlePredict = (e: React.FormEvent) => {
    e.preventDefault();
    predictMutation.mutate(form);
  };

  const formatDate = (dateStr: string) => {
    if (!dateStr) return '—';
    return new Date(dateStr).toLocaleDateString('fr-FR', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
    });
  };

  return (
    <div className="max-w-6xl mx-auto px-4 py-8">
      {/* Header */}
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-ink">Prédictions ETA</h1>
        <p className="text-ink-soft mt-1">Prédiction intelligente du temps d'arrivée</p>
      </div>

      {/* Stats cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        <div className="bg-surface rounded-xl border border-line p-5">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-lg bg-accent-soft flex items-center justify-center">
              <Package size={20} className="text-accent" />
            </div>
            <div>
              <p className="text-sm text-ink-soft">Total prédictions</p>
              <p className="text-2xl font-bold text-ink">{stats?.total ?? '—'}</p>
            </div>
          </div>
        </div>
        <div className="bg-surface rounded-xl border border-line p-5">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-lg bg-success/10 flex items-center justify-center">
              <TrendingUp size={20} className="text-success" />
            </div>
            <div>
              <p className="text-sm text-ink-soft">Précision moyenne</p>
              <p className="text-2xl font-bold text-ink">
                {stats?.avgAccuracy != null ? `${Math.round(stats.avgAccuracy)}%` : '—'}
              </p>
            </div>
          </div>
        </div>
        <div className="bg-surface rounded-xl border border-line p-5">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-lg bg-success/10 flex items-center justify-center">
              <CheckCircle size={20} className="text-success" />
            </div>
            <div>
              <p className="text-sm text-ink-soft">À l'heure %</p>
              <p className="text-2xl font-bold text-ink">
                {stats?.onTimePercent != null ? `${Math.round(stats.onTimePercent)}%` : '—'}
              </p>
            </div>
          </div>
        </div>
        <div className="bg-surface rounded-xl border border-line p-5">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-lg bg-warning/10 flex items-center justify-center">
              <Clock size={20} className="text-warning" />
            </div>
            <div>
              <p className="text-sm text-ink-soft">Jours moyens</p>
              <p className="text-2xl font-bold text-ink">
                {stats?.avgDays != null ? stats.avgDays.toFixed(1) : '—'}
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* Predict form */}
      <div className="bg-surface rounded-xl border border-line p-6 mb-8">
        <h2 className="text-lg font-semibold text-ink mb-4">Nouvelle prédiction</h2>
        <form onSubmit={handlePredict} className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4">
          <div>
            <label className="block text-sm font-medium text-ink mb-1">Origine</label>
            <select
              value={form.origin}
              onChange={(e) => setForm({ ...form, origin: e.target.value })}
              className="w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
              required
            >
              <option value="">Sélectionner...</option>
              {countries.map((c) => (
                <option key={c.code} value={c.code}>{c.name} ({c.code})</option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-ink mb-1">Destination</label>
            <select
              value={form.destination}
              onChange={(e) => setForm({ ...form, destination: e.target.value })}
              className="w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
              required
            >
              <option value="">Sélectionner...</option>
              {countries.map((c) => (
                <option key={c.code} value={c.code}>{c.name} ({c.code})</option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-ink mb-1">Mode</label>
            <select
              value={form.mode}
              onChange={(e) => setForm({ ...form, mode: e.target.value })}
              className="w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
              required
            >
              {modes.map((m) => (
                <option key={m.value} value={m.value}>{m.label}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-ink mb-1">Nom transporteur (optionnel)</label>
            <input
              type="text"
              value={form.carrierName}
              onChange={(e) => setForm({ ...form, carrierName: e.target.value })}
              className="w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
              placeholder="MSC, Maersk..."
            />
          </div>
          <div className="flex items-end">
            <button
              type="submit"
              disabled={predictMutation.isPending}
              className="w-full px-4 py-2 bg-accent text-white rounded-lg text-sm font-medium hover:bg-accent-strong disabled:opacity-50 transition-colors flex items-center justify-center gap-2"
            >
              {predictMutation.isPending ? <Loader2 size={14} className="animate-spin" /> : <TrendingUp size={14} />}
              Prédire ETA
            </button>
          </div>
        </form>
      </div>

      {/* Latest prediction result */}
      {predictions.length > 0 && (
        <div className="bg-surface rounded-xl border border-line p-6 mb-8">
          <h2 className="text-lg font-semibold text-ink mb-4">Dernière prédiction</h2>
          {(() => {
            const latest = predictions[0];
            const ModeIcon = getModeIcon(latest.mode);
            return (
              <div className="border border-line rounded-lg p-5">
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-4">
                  <div className="flex items-center gap-3">
                    <div className="w-12 h-12 rounded-lg bg-accent-soft flex items-center justify-center">
                      <ModeIcon size={24} className="text-accent" />
                    </div>
                    <div>
                      <div className="flex items-center gap-2 text-lg font-semibold text-ink">
                        <span>{getCountryName(latest.origin)}</span>
                        <ArrowRight size={18} className="text-ink-soft" />
                        <span>{getCountryName(latest.destination)}</span>
                      </div>
                      <p className="text-sm text-ink-soft">
                        {modes.find((m) => m.value === latest.mode)?.label || latest.mode}
                        {latest.carrierName ? ` — ${latest.carrierName}` : ''}
                      </p>
                    </div>
                  </div>
                  <div className="text-right">
                    <p className="text-2xl font-bold text-ink">
                      {formatDate(latest.predictedArrival)}
                    </p>
                  </div>
                </div>

                <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-4">
                  <div>
                    <p className="text-xs text-ink-soft">Jours prédits</p>
                    <p className="text-lg font-semibold text-ink">{latest.predictedDays}</p>
                  </div>
                  <div>
                    <p className="text-xs text-ink-soft">Jours de base</p>
                    <p className="text-lg font-semibold text-ink">{latest.baselineDays}</p>
                  </div>
                  <div>
                    <p className="text-xs text-ink-soft">Variance</p>
                    <p className={`text-lg font-semibold ${latest.varianceDays > 0 ? 'text-danger' : 'text-success'}`}>
                      {latest.varianceDays > 0 ? '+' : ''}{latest.varianceDays} jours
                    </p>
                  </div>
                  <div>
                    <p className="text-xs text-ink-soft">Niveau de confiance</p>
                    <div className="flex items-center gap-2">
                      <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${getConfidenceColor(latest.confidenceLevel)}`}>
                        {confidenceLabel(latest.confidenceLevel)}
                      </span>
                      <span className="text-sm text-ink-soft">{latest.confidencePercent}%</span>
                    </div>
                  </div>
                </div>

                {/* Confidence bar */}
                <div className="mb-4">
                  <div className="h-2 bg-surface-2 rounded-full overflow-hidden">
                    <div
                      className={`h-full rounded-full ${getConfidenceBarColor(latest.confidenceLevel)}`}
                      style={{ width: `${latest.confidencePercent}%` }}
                    />
                  </div>
                </div>

                {/* Risk factors — plain language */}
                {parseRiskFactors(latest.riskFactors).length > 0 && (
                  <div className="mb-4">
                    <p className="text-xs font-medium text-ink-soft mb-2 flex items-center gap-1.5">
                      <AlertTriangle size={12} />
                      Ce qui peut affecter ce délai
                    </p>
                    <ul className="space-y-1">
                      {parseRiskFactors(latest.riskFactors).map((label, i) => (
                        <li key={i} className="text-sm text-ink flex items-start gap-2">
                          <span className="w-1.5 h-1.5 rounded-full bg-warning mt-1.5 shrink-0" />
                          {label}
                        </li>
                      ))}
                    </ul>
                  </div>
                )}

                <button
                  type="button"
                  onClick={() => setShowLatestDetail((v) => !v)}
                  className="flex items-center gap-1 text-xs font-medium text-ink-soft hover:text-ink transition-colors"
                >
                  {showLatestDetail ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                  {showLatestDetail ? 'Masquer le détail technique' : 'Voir le détail technique'}
                </button>

                {/* Detail breakdown — technique, replié par défaut */}
                {showLatestDetail && (
                  <div className="bg-bg rounded-lg p-4 mt-3">
                    <p className="text-xs text-ink-soft mb-2">Détail</p>
                    <div className="grid grid-cols-2 sm:grid-cols-5 gap-3 text-sm">
                      <div>
                        <p className="text-ink-soft">Baseline</p>
                        <p className="font-medium text-ink">{latest.baselineDays} jours</p>
                      </div>
                      <div>
                        <p className="text-ink-soft">Saisonnier</p>
                        <p className="font-medium text-ink">
                          {latest.seasonalFactor > 0 ? '+' : ''}{latest.seasonalFactor}%
                        </p>
                      </div>
                      <div>
                        <p className="text-ink-soft">Congestion</p>
                        <p className="font-medium text-ink">
                          {latest.congestionFactor > 0 ? '+' : ''}{latest.congestionFactor}%
                        </p>
                      </div>
                      <div>
                        <p className="text-ink-soft">Douane</p>
                        <p className="font-medium text-ink">+{latest.customsDelayDays} jours</p>
                      </div>
                      <div>
                        <p className="text-ink-soft">Météo</p>
                        <p className="font-medium text-ink">+{latest.weatherDelayDays} jours</p>
                      </div>
                    </div>
                  </div>
                )}
              </div>
            );
          })()}
        </div>
      )}

      {/* Visualisation */}
      {predictions.length > 0 && (
        <div className="bg-surface rounded-xl border border-line p-6 mb-8">
          <div className="flex items-center gap-2 mb-6">
            <BarChart3 size={18} className="text-ink-soft" />
            <h2 className="text-lg font-semibold text-ink">Visualisation</h2>
          </div>
          <div className="grid grid-cols-1 lg:grid-cols-5 gap-8">
            <div className="lg:col-span-3">
              <p className="text-sm font-medium text-ink-soft mb-2">Jours prédits vs jours réels</p>
              {accuracyData.length > 0 ? (
                <PredictionAccuracyChart data={accuracyData} />
              ) : (
                <div className="h-64 flex items-center justify-center text-center text-sm text-ink-soft px-6 bg-bg rounded-lg">
                  Enregistrez une arrivée réelle sur une prédiction pour voir la précision du modèle ici.
                </div>
              )}
            </div>
            <div className="lg:col-span-2">
              <p className="text-sm font-medium text-ink-soft mb-4">Répartition par fiabilité</p>
              <div className="space-y-4">
                {confidenceCounts.map((c) => (
                  <div key={c.level}>
                    <div className="flex items-center justify-between text-sm mb-1.5">
                      <span className="text-ink">{c.label}</span>
                      <span className={`font-semibold ${c.textClass}`}>{c.count}</span>
                    </div>
                    <div className="h-2 bg-surface-2 rounded-full overflow-hidden">
                      <div
                        className={`h-full rounded-full ${c.barClass} transition-[width] duration-500`}
                        style={{ width: `${(c.count / maxConfidenceCount) * 100}%` }}
                      />
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* History table */}
      <div className="bg-surface rounded-xl border border-line overflow-hidden">
        <div className="px-6 py-4 border-b border-line">
          <h2 className="text-lg font-semibold text-ink">Historique des prédictions</h2>
        </div>

        {predictionsLoading ? (
          <div className="px-6 py-12 text-center text-ink-soft">
            <Loader2 size={24} className="animate-spin mx-auto mb-2 text-ink-soft" />
            Chargement...
          </div>
        ) : predictions.length === 0 ? (
          <div className="px-6 py-12 text-center text-ink-soft">
            <Package size={32} className="mx-auto mb-3 text-ink-soft" />
            <p>Aucune prédiction pour le moment</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="bg-bg border-b border-line">
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Lane</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Mode</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Transporteur</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Arrivée prédite</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Jours</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Confiance</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Précision</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">À l'heure?</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {predictions.map((pred: Prediction) => {
                  const ModeIcon = getModeIcon(pred.mode);
                  const isExpanded = selectedPrediction === pred.id;

                  return (
                    <Fragment key={pred.id}>
                      <tr
                        className="hover:bg-bg transition-colors cursor-pointer"
                        onClick={() => setSelectedPrediction(isExpanded ? null : pred.id)}
                      >
                        <td className="px-6 py-4">
                          <div className="flex items-center gap-2 text-sm">
                            <span className="font-medium text-ink">{pred.origin}</span>
                            <ArrowRight size={12} className="text-ink-soft" />
                            <span className="font-medium text-ink">{pred.destination}</span>
                          </div>
                        </td>
                        <td className="px-6 py-4">
                          <div className="flex items-center gap-2">
                            <ModeIcon size={16} className="text-ink-soft" />
                            <span className="text-sm text-ink-soft">{pred.mode}</span>
                          </div>
                        </td>
                        <td className="px-6 py-4 text-sm text-ink-soft">{pred.carrierName || '—'}</td>
                        <td className="px-6 py-4 text-sm text-ink font-medium">{formatDate(pred.predictedArrival)}</td>
                        <td className="px-6 py-4 text-sm text-ink-soft">{pred.predictedDays}</td>
                        <td className="px-6 py-4">
                          <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${getConfidenceColor(pred.confidenceLevel)}`}>
                            {confidenceLabel(pred.confidenceLevel)}
                          </span>
                        </td>
                        <td className="px-6 py-4 text-sm text-ink-soft">
                          {pred.predictionAccuracy != null ? `${Math.round(pred.predictionAccuracy)}%` : '—'}
                        </td>
                        <td className="px-6 py-4">
                          {pred.isOnTime === true && <CheckCircle size={18} className="text-success" />}
                          {pred.isOnTime === false && <XCircle size={18} className="text-danger" />}
                          {pred.isOnTime == null && <span className="text-ink-soft text-xs">—</span>}
                        </td>
                      </tr>
                      {isExpanded && (
                        <tr key={`${pred.id}-details`}>
                          <td colSpan={8} className="px-6 py-4 bg-bg">
                            {parseRiskFactors(pred.riskFactors).length > 0 && (
                              <div className="mb-3">
                                <p className="text-xs font-medium text-ink-soft mb-1.5 flex items-center gap-1.5">
                                  <AlertTriangle size={12} />
                                  Ce qui peut affecter ce délai
                                </p>
                                <ul className="space-y-1">
                                  {parseRiskFactors(pred.riskFactors).map((label, i) => (
                                    <li key={i} className="text-sm text-ink flex items-start gap-2">
                                      <span className="w-1.5 h-1.5 rounded-full bg-warning mt-1.5 shrink-0" />
                                      {label}
                                    </li>
                                  ))}
                                </ul>
                              </div>
                            )}
                            <button
                              type="button"
                              onClick={() => setTechnicalDetailRowId(technicalDetailRowId === pred.id ? null : pred.id)}
                              className="flex items-center gap-1 text-xs font-medium text-ink-soft hover:text-ink transition-colors"
                            >
                              {technicalDetailRowId === pred.id ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                              {technicalDetailRowId === pred.id ? 'Masquer le détail technique' : 'Voir le détail technique'}
                            </button>
                            {technicalDetailRowId === pred.id && (
                              <div className="grid grid-cols-2 sm:grid-cols-5 gap-4 text-sm mt-3">
                                <div>
                                  <p className="text-ink-soft">Jours baseline</p>
                                  <p className="font-medium text-ink">{pred.baselineDays} jours</p>
                                </div>
                                <div>
                                  <p className="text-ink-soft">Saisonnier</p>
                                  <p className="font-medium text-ink">
                                    {pred.seasonalFactor > 0 ? '+' : ''}{pred.seasonalFactor}%
                                  </p>
                                </div>
                                <div>
                                  <p className="text-ink-soft">Congestion</p>
                                  <p className="font-medium text-ink">
                                    {pred.congestionFactor > 0 ? '+' : ''}{pred.congestionFactor}%
                                  </p>
                                </div>
                                <div>
                                  <p className="text-ink-soft">Douane</p>
                                  <p className="font-medium text-ink">+{pred.customsDelayDays} jours</p>
                                </div>
                                <div>
                                  <p className="text-ink-soft">Météo</p>
                                  <p className="font-medium text-ink">+{pred.weatherDelayDays} jours</p>
                                </div>
                              </div>
                            )}
                            {pred.actualArrival && (
                              <div className="mt-3 pt-3 border-t border-line">
                                <p className="text-xs text-ink-soft">Arrivée réelle</p>
                                <p className="text-sm font-medium text-ink">{formatDate(pred.actualArrival)}</p>
                              </div>
                            )}
                            {!pred.actualArrival && (
                              <div className="mt-3 pt-3 border-t border-line">
                                <p className="text-xs text-ink-soft mb-1">Enregistrer l'arrivée réelle</p>
                                <input
                                  type="date"
                                  className="px-3 py-1 border border-line rounded-lg text-sm"
                                  onChange={(e) => {
                                    if (e.target.value) {
                                      updateActualMutation.mutate({ id: pred.id, actualArrival: e.target.value });
                                    }
                                  }}
                                />
                              </div>
                            )}
                          </td>
                        </tr>
                      )}
                    </Fragment>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
};

export default EtaPredictions;
