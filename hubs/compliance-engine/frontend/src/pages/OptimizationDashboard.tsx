import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { incokalkAPI } from '../lib/api';
import type { RateOptimization, ConsolidationOpportunity, LaneAnalysis } from '../types';
import { TrendingDown, BarChart3, Truck, Package, Route, Zap, CheckCircle, Clock, AlertTriangle, Loader2, Search, type LucideIcon } from 'lucide-react';
import toast from 'react-hot-toast';

const FLAG_MAP: Record<string, string> = {
  FR: '\u{1F1EB}\u{1F1F7}', DE: '\u{1F1E9}\u{1F1EA}', NL: '\u{1F1F3}\u{1F1F1}', CN: '\u{1F1E8}\u{1F1F3}',
  US: '\u{1F1FA}\u{1F1F8}', GB: '\u{1F1EC}\u{1F1E7}', BE: '\u{1F1E7}\u{1F1EA}', ES: '\u{1F1EA}\u{1F1F8}',
  IT: '\u{1F1EE}\u{1F1F9}', PT: '\u{1F1F5}\u{1F1F9}', MA: '\u{1F1F2}\u{1F1E6}', JP: '\u{1F1EF}\u{1F1F5}',
  KR: '\u{1F1F0}\u{1F1F7}', IN: '\u{1F1EE}\u{1F1F3}', BR: '\u{1F1E7}\u{1F1F7}', CA: '\u{1F1E8}\u{1F1E6}',
};

const getFlag = (code: string) => {
  if (!code) return '\u{1F30D}';
  const upper = code.toUpperCase();
  return FLAG_MAP[upper] || `\u{1F30D}`;
};

const formatCurrency = (amount: number) =>
  new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR', minimumFractionDigits: 0, maximumFractionDigits: 0 }).format(amount);

const formatNumber = (n: number) =>
  new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 1 }).format(n);

interface PredictionResult {
  predictedCost: number;
  recommendedCarrier?: string;
  confidence: number;
  savingsEstimate: number;
}

const StatusBadge = ({ status }: { status: string }) => {
  const styles: Record<string, string> = {
    PENDING: 'bg-warning/10 text-warning border border-warning/40',
    ACCEPTED: 'bg-success/10 text-success border border-success/40',
    REJECTED: 'bg-danger/10 text-danger border border-danger/40',
  };
  return (
    <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${styles[status] || 'bg-surface-2 text-ink'}`}>
      {status === 'PENDING' && <Clock size={12} className="mr-1" />}
      {status === 'ACCEPTED' && <CheckCircle size={12} className="mr-1" />}
      {status === 'REJECTED' && <AlertTriangle size={12} className="mr-1" />}
      {status}
    </span>
  );
};

const StatsCard = ({ icon: Icon, label, value, color, sub }: { icon: LucideIcon; label: string; value: string; color: string; sub?: string }) => (
  <div className="bg-surface rounded-none shadow-sm border border-[#DEB887]/30 p-5 relative overflow-hidden">
    <div className={`absolute left-0 top-0 bottom-0 w-1 ${color}`} />
    <div className="flex items-start justify-between pl-3">
      <div>
        <p className="text-sm text-ink-soft mb-1">{label}</p>
        <p className="text-2xl font-bold text-ink">{value}</p>
        {sub && <p className="text-xs text-ink-soft mt-1">{sub}</p>}
      </div>
      <div className={`p-2.5 rounded-none ${color.replace('bg-', 'bg-')}/10`}>
        <Icon size={20} className={color.replace('bg-', 'text-')} />
      </div>
    </div>
  </div>
);

const OptimizationDashboard = () => {
  const queryClient = useQueryClient();

  const [predictForm, setPredictForm] = useState({
    origin: '',
    destination: '',
    mode: '',
    weight: '',
    volume: '',
  });
  const [predictResult, setPredictResult] = useState<PredictionResult | null>(null);
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  const showToast = (msg: string) => {
    setToastMessage(msg);
    setTimeout(() => setToastMessage(null), 3000);
  };

  const { data: stats, isLoading: statsLoading } = useQuery({
    queryKey: ['optimization-stats'],
    queryFn: () => incokalkAPI.optimization.getStats(),
  });

  const { data: laneAnalysis = [], isLoading: lanesLoading } = useQuery({
    queryKey: ['lane-analysis'],
    queryFn: () => incokalkAPI.optimization.getLaneAnalysis(),
  });

  const { data: recommendations = [], isLoading: recsLoading } = useQuery({
    queryKey: ['optimization-recommendations'],
    queryFn: () => incokalkAPI.optimization.getRecommendations(),
  });

  const { data: consolidations = [], isLoading: consolLoading } = useQuery({
    queryKey: ['optimization-consolidation'],
    queryFn: () => incokalkAPI.optimization.getConsolidation(),
  });

  const analyzeMutation = useMutation({
    mutationFn: () => incokalkAPI.optimization.analyzeRoutes(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['optimization-stats'] });
      queryClient.invalidateQueries({ queryKey: ['lane-analysis'] });
      queryClient.invalidateQueries({ queryKey: ['optimization-recommendations'] });
      showToast('Analyse des routes termin\u00e9e');
    },
    onError: () => toast.error('Erreur lors de l\'analyse des routes'),
  });

  const predictMutation = useMutation({
    mutationFn: () => incokalkAPI.optimization.predict({
      origin: predictForm.origin,
      destination: predictForm.destination,
      mode: predictForm.mode || undefined,
      weight: predictForm.weight ? parseFloat(predictForm.weight) : undefined,
      volume: predictForm.volume ? parseFloat(predictForm.volume) : undefined,
    }),
    onSuccess: (data) => {
      setPredictResult(data);
      queryClient.invalidateQueries({ queryKey: ['optimization-recommendations'] });
    },
    onError: () => toast.error('Erreur lors de la pr\u00e9diction'),
  });

  const acceptOptMutation = useMutation({
    mutationFn: (id: string) => incokalkAPI.optimization.acceptOptimization(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['optimization-recommendations'] });
      queryClient.invalidateQueries({ queryKey: ['optimization-stats'] });
      showToast('Recommandation accept\u00e9e');
    },
  });

  const acceptConsolMutation = useMutation({
    mutationFn: (id: string) => incokalkAPI.optimization.acceptConsolidation(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['optimization-consolidation'] });
      queryClient.invalidateQueries({ queryKey: ['optimization-stats'] });
      showToast('Consolidation accept\u00e9e');
    },
  });

  const findConsolMutation = useMutation({
    mutationFn: () => incokalkAPI.optimization.findConsolidation(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['optimization-consolidation'] });
      queryClient.invalidateQueries({ queryKey: ['optimization-stats'] });
      showToast('Opportunit\u00e9s de consolidation trouv\u00e9es');
    },
  });

  const handlePredict = (e: React.FormEvent) => {
    e.preventDefault();
    if (!predictForm.origin || !predictForm.destination) {
      toast.error('Origine et destination requis');
      return;
    }
    predictMutation.mutate();
  };

  if (statsLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-[#FAF0E6]">
        <div className="text-center">
          <Loader2 className="h-12 w-12 animate-spin mx-auto text-[#C04000] mb-4" />
          <div className="text-xl text-ink-soft">Chargement du dashboard d'optimisation...</div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-[#FAF0E6] py-10">
      <div className="container mx-auto px-4 max-w-7xl">
        {toastMessage && (
          <div className="fixed top-20 right-4 z-50 bg-[#556B2F] text-white px-4 py-3 rounded-none shadow-lg flex items-center gap-2 animate-fade-in">
            <CheckCircle size={16} />
            <span className="text-sm font-medium">{toastMessage}</span>
          </div>
        )}

        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between mb-8 gap-4">
          <div>
            <h1 className="text-3xl font-bold text-[#1a1a2e] flex items-center gap-3">
              <div className="p-2 bg-[#C04000]/10 rounded-none">
                <TrendingDown size={28} className="text-[#C04000]" />
              </div>
              <span className="text-accent font-normal" aria-hidden="true">:: </span>
              Moteur d'optimisation tarifaire
            </h1>
            <p className="text-ink-soft mt-2 ml-[52px]">Analysez, prédisez et optimisez vos coûts d'expédition</p>
          </div>
          <button
            onClick={() => analyzeMutation.mutate()}
            disabled={analyzeMutation.isPending}
            className="bg-[#C04000] text-white px-6 py-3 rounded-none hover:bg-[#A03000] transition-colors flex items-center gap-2 font-medium shadow-sm disabled:opacity-50"
          >
            {analyzeMutation.isPending ? (
              <Loader2 size={18} className="animate-spin" />
            ) : (
              <Zap size={18} />
            )}
            Analyser les routes
          </button>
        </div>

        {/* Stats Cards */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
          <StatsCard
            icon={Route}
            label="Routes analysées"
            value={String(stats?.totalRoutes ?? 0)}
            color="bg-[#C04000]"
            sub={`${stats?.totalOptimizations ?? 0} optimisations`}
          />
          <StatsCard
            icon={TrendingDown}
            label="Économies totales"
            value={formatCurrency(stats?.totalSavings ?? 0)}
            color="bg-[#556B2F]"
            sub={`${formatCurrency(stats?.acceptedSavings ?? 0)} accept\u00e9es`}
          />
          <StatsCard
            icon={BarChart3}
            label="Confiance moyenne"
            value={`${((stats?.avgConfidence ?? 0) * 100).toFixed(0)}%`}
            color="bg-[#DEB887]"
            sub={`${stats?.pendingOptimizations ?? 0} en attente`}
          />
          <StatsCard
            icon={Package}
            label="Consolidations trouvées"
            value={String(stats?.totalConsolidationOpportunities ?? 0)}
            color="bg-[#556B2F]"
            sub={`${formatCurrency(stats?.consolidationSavings ?? 0)} \u00e9conomis\u00e9es`}
          />
        </div>

        {/* Prediction Form */}
        <div className="bg-surface rounded-none shadow-sm border border-[#DEB887]/30 p-6 mb-8">
          <h2 className="text-lg font-semibold text-[#1a1a2e] flex items-center gap-2 mb-4">
            <Search size={20} className="text-[#C04000]" />
            Prédiction de tarif
          </h2>
          <form onSubmit={handlePredict} className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4">
            <div>
              <label className="block text-sm font-medium text-ink mb-1">Origine *</label>
              <input
                type="text"
                value={predictForm.origin}
                onChange={(e) => setPredictForm({ ...predictForm, origin: e.target.value })}
                className="w-full border border-line rounded-none px-3 py-2 text-sm focus:ring-2 focus:ring-[#C04000] focus:border-[#C04000]"
                placeholder="Ex: FR"
                maxLength={2}
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-ink mb-1">Destination *</label>
              <input
                type="text"
                value={predictForm.destination}
                onChange={(e) => setPredictForm({ ...predictForm, destination: e.target.value })}
                className="w-full border border-line rounded-none px-3 py-2 text-sm focus:ring-2 focus:ring-[#C04000] focus:border-[#C04000]"
                placeholder="Ex: DE"
                maxLength={2}
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-ink mb-1">Mode</label>
              <select
                value={predictForm.mode}
                onChange={(e) => setPredictForm({ ...predictForm, mode: e.target.value })}
                className="w-full border border-line rounded-none px-3 py-2 text-sm focus:ring-2 focus:ring-[#C04000] focus:border-[#C04000]"
              >
                <option value="">Tous</option>
                <option value="SEA">Maritime</option>
                <option value="AIR">Aérien</option>
                <option value="ROAD">Routier</option>
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-ink mb-1">Poids (kg)</label>
              <input
                type="number"
                step="0.1"
                min="0"
                value={predictForm.weight}
                onChange={(e) => setPredictForm({ ...predictForm, weight: e.target.value })}
                className="w-full border border-line rounded-none px-3 py-2 text-sm focus:ring-2 focus:ring-[#C04000] focus:border-[#C04000]"
                placeholder="Ex: 500"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-ink mb-1">Volume (m³)</label>
              <input
                type="number"
                step="0.01"
                min="0"
                value={predictForm.volume}
                onChange={(e) => setPredictForm({ ...predictForm, volume: e.target.value })}
                className="w-full border border-line rounded-none px-3 py-2 text-sm focus:ring-2 focus:ring-[#C04000] focus:border-[#C04000]"
                placeholder="Ex: 2.5"
              />
            </div>
            <div className="sm:col-span-2 lg:col-span-5">
              <button
                type="submit"
                disabled={predictMutation.isPending}
                className="bg-[#C04000] text-white px-6 py-2.5 rounded-none hover:bg-[#A03000] transition-colors flex items-center gap-2 text-sm font-medium disabled:opacity-50"
              >
                {predictMutation.isPending ? <Loader2 size={16} className="animate-spin" /> : <Search size={16} />}
                Prédire le tarif
              </button>
            </div>
          </form>

          {/* Predict Result */}
          {predictResult && (
            <div className="mt-6 p-5 bg-[#FAF0E6]/50 rounded-none border border-[#DEB887]/40">
              <h3 className="font-semibold text-[#1a1a2e] mb-3 flex items-center gap-2">
                <TrendingDown size={16} className="text-[#C04000]" />
                Résultat de la prédiction
              </h3>
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 text-sm">
                <div>
                  <span className="text-ink-soft">Coût prédit :</span>
                  <p className="text-lg font-bold text-[#C04000]">{formatCurrency(predictResult.predictedCost || 0)}</p>
                </div>
                <div>
                  <span className="text-ink-soft">Transporteur :</span>
                  <p className="font-medium text-ink">{predictResult.recommendedCarrier || 'N/A'}</p>
                </div>
                <div>
                  <span className="text-ink-soft">Confiance :</span>
                  <p className="font-medium text-ink">{((predictResult.confidence || 0) * 100).toFixed(0)}%</p>
                </div>
                <div>
                  <span className="text-ink-soft">Économie estimée :</span>
                  <p className="font-medium text-success">{formatCurrency(predictResult.savingsEstimate || 0)}</p>
                </div>
              </div>
            </div>
          )}
        </div>

        {/* Lane Analysis Table */}
        <div className="bg-surface rounded-none shadow-sm border border-[#DEB887]/30 p-6 mb-8">
          <h2 className="text-lg font-semibold text-[#1a1a2e] flex items-center gap-2 mb-4">
            <Truck size={20} className="text-[#C04000]" />
            Tableau de bord des lanes
          </h2>
          {lanesLoading ? (
            <div className="flex items-center justify-center py-8">
              <Loader2 className="h-8 w-8 animate-spin text-[#C04000]" />
            </div>
          ) : laneAnalysis.length === 0 ? (
            <div className="text-center py-8 text-ink-soft">
              <Truck size={40} className="mx-auto mb-3 opacity-40" />
              <p>Aucune lane analysée. Lancez une analyse des routes pour commencer.</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-line">
                    <th className="text-left py-3 px-4 font-semibold text-ink-soft">Lane</th>
                    <th className="text-center py-3 px-4 font-semibold text-ink-soft">Expéditions</th>
                    <th className="text-center py-3 px-4 font-semibold text-ink-soft">Meilleur transporteur</th>
                    <th className="text-right py-3 px-4 font-semibold text-ink-soft">Meilleur coût</th>
                    <th className="text-right py-3 px-4 font-semibold text-ink-soft">Coût max</th>
                    <th className="text-right py-3 px-4 font-semibold text-ink-soft">Potentiel éco</th>
                    <th className="text-center py-3 px-4 font-semibold text-ink-soft">Ponctualité</th>
                  </tr>
                </thead>
                <tbody>
                  {laneAnalysis.map((lane: LaneAnalysis, idx: number) => (
                    <tr key={`${lane.origin}-${lane.destination}`} className={`border-b border-line ${idx % 2 === 0 ? 'bg-bg/50' : 'bg-surface'} hover:bg-[#FAF0E6]/30 transition-colors`}>
                      <td className="py-3 px-4 font-medium text-ink">
                        <span className="flex items-center gap-2">
                          {getFlag(lane.origin)} {lane.origin}
                          <span className="text-ink-soft mx-1">→</span>
                          {getFlag(lane.destination)} {lane.destination}
                        </span>
                      </td>
                      <td className="py-3 px-4 text-center text-ink">{lane.totalShipments}</td>
                      <td className="py-3 px-4 text-center text-ink">{lane.bestCarrier}</td>
                      <td className="py-3 px-4 text-right font-medium text-[#556B2F]">{formatCurrency(lane.bestCost)}</td>
                      <td className="py-3 px-4 text-right text-ink-soft">{formatCurrency(lane.worstCost)}</td>
                      <td className="py-3 px-4 text-right">
                        <span className="text-[#C04000] font-medium flex items-center justify-end gap-1">
                          <TrendingDown size={14} />
                          {formatCurrency(lane.potentialSavings)}
                        </span>
                      </td>
                      <td className="py-3 px-4 text-center">
                        <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${lane.avgOnTimeRate >= 0.9 ? 'bg-success/10 text-success' : lane.avgOnTimeRate >= 0.75 ? 'bg-warning/10 text-warning' : 'bg-danger/10 text-danger'}`}>
                          {(lane.avgOnTimeRate * 100).toFixed(0)}%
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* Recommendations */}
        <div className="bg-surface rounded-none shadow-sm border border-[#DEB887]/30 p-6 mb-8">
          <h2 className="text-lg font-semibold text-[#1a1a2e] flex items-center gap-2 mb-4">
            <Zap size={20} className="text-[#C04000]" />
            Recommandations
          </h2>
          {recsLoading ? (
            <div className="flex items-center justify-center py-8">
              <Loader2 className="h-8 w-8 animate-spin text-[#C04000]" />
            </div>
          ) : recommendations.length === 0 ? (
            <div className="text-center py-8 text-ink-soft">
              <Zap size={40} className="mx-auto mb-3 opacity-40" />
              <p>Aucune recommandation disponible. Lancez une analyse des routes.</p>
            </div>
          ) : (
            <div className="space-y-3">
              {recommendations.map((rec: RateOptimization) => (
                <div key={rec.id} className="flex flex-col sm:flex-row sm:items-center justify-between p-4 rounded-none border border-line hover:border-[#DEB887]/60 bg-[#FAF0E6]/20 transition-colors gap-3">
                  <div className="flex-1">
                    <div className="flex items-center gap-3 mb-1">
                      <span className="font-medium text-[#1a1a2e] flex items-center gap-1.5">
                        {getFlag(rec.origin)} {rec.origin}
                        <span className="text-ink-soft">→</span>
                        {getFlag(rec.destination)} {rec.destination}
                      </span>
                      <StatusBadge status={rec.status} />
                    </div>
                    <div className="flex items-center gap-4 text-sm text-ink-soft">
                      <span>Mode : {rec.transportMode}</span>
                      {rec.recommendedCarrier && <span>Transporteur : {rec.recommendedCarrier}</span>}
                      <span>Confiance : {(rec.confidence * 100).toFixed(0)}%</span>
                    </div>
                  </div>
                  <div className="flex items-center gap-4">
                    <div className="text-right">
                      <p className="text-lg font-bold text-[#C04000]">{formatCurrency(rec.predictedCost)}</p>
                      <p className="text-xs text-success flex items-center gap-1 justify-end">
                        <TrendingDown size={12} />
                        -{rec.savingsPercent.toFixed(0)}% ({formatCurrency(rec.savingsEstimate)})
                      </p>
                    </div>
                    {rec.status === 'PENDING' && (
                      <button
                        onClick={() => acceptOptMutation.mutate(rec.id)}
                        disabled={acceptOptMutation.isPending}
                        className="bg-[#556B2F] text-white px-4 py-2 rounded-none hover:bg-[#445820] transition-colors text-sm font-medium disabled:opacity-50"
                      >
                        Accepter
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Consolidation */}
        <div className="bg-surface rounded-none shadow-sm border border-[#DEB887]/30 p-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-lg font-semibold text-[#1a1a2e] flex items-center gap-2">
              <Package size={20} className="text-[#C04000]" />
              Consolidation d'expéditions
            </h2>
            <button
              onClick={() => findConsolMutation.mutate()}
              disabled={findConsolMutation.isPending}
              className="bg-[#556B2F] text-white px-4 py-2 rounded-none hover:bg-[#445820] transition-colors text-sm font-medium flex items-center gap-2 disabled:opacity-50"
            >
              {findConsolMutation.isPending ? <Loader2 size={14} className="animate-spin" /> : <Package size={14} />}
              Rechercher
            </button>
          </div>
          {consolLoading ? (
            <div className="flex items-center justify-center py-8">
              <Loader2 className="h-8 w-8 animate-spin text-[#C04000]" />
            </div>
          ) : consolidations.length === 0 ? (
            <div className="text-center py-8 text-ink-soft">
              <Package size={40} className="mx-auto mb-3 opacity-40" />
              <p>Aucune opportunité de consolidation trouvée.</p>
            </div>
          ) : (
            <div className="space-y-3">
              {consolidations.map((consol: ConsolidationOpportunity) => (
                <div key={consol.id} className="p-4 rounded-none border border-line hover:border-[#DEB887]/60 bg-[#FAF0E6]/20 transition-colors">
                  <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                    <div>
                      <div className="flex items-center gap-3 mb-1">
                        <span className="font-medium text-[#1a1a2e] flex items-center gap-1.5">
                          {getFlag(consol.origin)} {consol.origin}
                          <span className="text-ink-soft">→</span>
                          {getFlag(consol.destination)} {consol.destination}
                        </span>
                        <StatusBadge status={consol.status} />
                      </div>
                      <div className="flex items-center gap-4 text-sm text-ink-soft">
                        <span>{consol.shipmentCount} expéd.</span>
                        <span>{formatNumber(consol.totalWeightKg)} kg</span>
                        {consol.transportMode && <span>{consol.transportMode}</span>}
                        <span>Fenêtre : {consol.consolidationWindowDays}j</span>
                      </div>
                    </div>
                    <div className="flex items-center gap-4">
                      <div className="text-right">
                        <p className="text-lg font-bold text-[#556B2F]">{formatCurrency(consol.consolidatedCost)}</p>
                        <p className="text-xs text-ink-soft line-through">{formatCurrency(consol.combinedCost)}</p>
                        <p className="text-xs text-success font-medium flex items-center gap-1 justify-end">
                          <TrendingDown size={12} />
                          -{consol.savingsPercent.toFixed(0)}% économie ({formatCurrency(consol.estimatedSavings)})
                        </p>
                      </div>
                      {consol.status === 'PENDING' && (
                        <button
                          onClick={() => acceptConsolMutation.mutate(consol.id)}
                          disabled={acceptConsolMutation.isPending}
                          className="bg-[#556B2F] text-white px-4 py-2 rounded-none hover:bg-[#445820] transition-colors text-sm font-medium disabled:opacity-50"
                        >
                          Accepter
                        </button>
                      )}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default OptimizationDashboard;
