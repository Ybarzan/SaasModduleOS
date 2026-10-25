import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAuthStore } from '../stores/auth';
import { incokalkAPI } from '../lib/api';
import toast from 'react-hot-toast';
import { SkeletonCard } from '../components/ui/Skeleton';
import Badge from '../components/ui/Badge';
import { formatNumber } from '../lib/formatNumber';
import {
  Trash2,
  Calculator,
  FileText,
  Loader2,
  ChevronDown,
  ChevronUp,
  RefreshCw,
  ArrowLeftRight,
} from 'lucide-react';
import type { DashboardStats } from '../types';

// Miroir de BillingService.getPlans() (limits.simulationsPerMonth du plan FREE, backend) --
// tous les plans payants sont illimités (-1), seul FREE a un plafond, donc pas besoin
// d'un appel API supplémentaire rien que pour cette constante.
const FREE_PLAN_SIMULATION_LIMIT = 5;

interface FxRate {
  from: string;
  to: string;
  convertedAmount: number;
}

interface RoiPlan {
  recommended?: boolean;
  priceMonthly: number;
  priceAnnual: number;
}

interface Simulation {
  id: string | number;
  createdAt: string;
  incotermCode: string;
  originCountry?: string;
  destinationCountry?: string;
  productValue?: number;
  currency?: string;
  totalCost?: number;
}

const Dashboard = () => {
  const user = useAuthStore((state) => state.user);
  const queryClient = useQueryClient();

  const [historyOpen, setHistoryOpen] = useState(false);
  const [roi, setRoi] = useState({
    shipments: 30,
    avgCost: 250,
    hoursSaved: 20,
    hourlyRate: 40,
    marginPct: 10,
  });

  // Analytics — ne remonte plus que le compteur de simulations (voir SimulationRepository
  // côté backend) depuis le retrait du domaine Transport ; refetchInterval couvre
  // l'auto-refresh, pas besoin d'un intervalle manuel supplémentaire.
  const { data: statsData, isLoading: statsLoading } = useQuery({
    queryKey: ['analytics', 'dashboard'],
    queryFn: async () => {
      const res = await incokalkAPI.analytics.dashboard('30d');
      return res.data as DashboardStats;
    },
    enabled: !!user,
    refetchInterval: 60000,
  });

  const FX_PAIRS = [
    { from: 'EUR', to: 'USD' },
    { from: 'EUR', to: 'GBP' },
    { from: 'EUR', to: 'MAD' },
  ];

  const { data: fxRates, isLoading: fxLoading } = useQuery({
    queryKey: ['currencies', 'rates'],
    queryFn: async () => {
      const results = await Promise.all(
        FX_PAIRS.map((p) => incokalkAPI.currencies.convert(1, p.from, p.to))
      );
      return results.map((r) => r.data);
    },
    enabled: !!user,
    refetchInterval: 3600000,
  });

  // Simulation history (paginated, latest 10)
  const {
    data: simulationsData,
    isLoading: simLoading,
    error: simError,
  } = useQuery({
    queryKey: ['simulations'],
    queryFn: async () => {
      const response = await incokalkAPI.simulation.getHistory(0, 10);
      return response.data;
    },
    enabled: !!user,
  });
  const simulations = simulationsData?.content ?? simulationsData ?? [];
  const totalSimulations = simulationsData?.totalElements ?? simulations.length;

  const deleteMutation = useMutation({
    mutationFn: async (id: string) => {
      await incokalkAPI.simulation.delete(id);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['simulations'] });
      toast.success('Simulation supprimée avec succès');
    },
    onError: () => {
      toast.error('Erreur lors de la suppression');
    },
  });

  const handleDelete = (id: string) => {
    if (!confirm('Voulez-vous vraiment supprimer cette simulation ?')) return;
    deleteMutation.mutate(id);
  };

  // ROI calculator — plan pricing (Pro annual/mois)
  const { data: roiPlans = [] } = useQuery({
    queryKey: ['billing-plans'],
    queryFn: async () => {
      const res = await incokalkAPI.billing.getPlans();
      return res.data;
    },
  });

  const roiShipments = Number(roi.shipments) || 0;
  const roiAvg = Number(roi.avgCost) || 0;
  const roiHours = Number(roi.hoursSaved) || 0;
  const roiRate = Number(roi.hourlyRate) || 0;
  const roiMargin = (Number(roi.marginPct) || 0) / 100;
  const savingsTime = roiHours * roiRate;
  const savingsMargin = roiMargin * roiAvg * roiShipments;
  const savingsMonthly = savingsTime + savingsMargin;
  const roiProPlan = (roiPlans as RoiPlan[]).find((p: RoiPlan) => p.recommended) || (roiPlans as RoiPlan[]).find((p: RoiPlan) => p.priceMonthly > 0);
  const proMonthly = roiProPlan
    ? roiProPlan.priceAnnual > 0
      ? Math.round(roiProPlan.priceAnnual / 12)
      : roiProPlan.priceMonthly
    : 149;
  const netMonthly = savingsMonthly - proMonthly;
  const netAnnual = netMonthly * 12;

  const setRoiField = (field: keyof typeof roi) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setRoi((prev) => ({ ...prev, [field]: e.target.value }));

  const refreshAll = () => {
    queryClient.invalidateQueries({ queryKey: ['analytics'] });
    toast.success('Données actualisées');
  };

  if (!user) {
    return (
      <div className="min-h-screen bg-bg py-12">
        <div className="container mx-auto px-4">
          <div className="bg-warning/10 border border-warning/40 text-warning px-6 py-4 rounded">
            Veuillez vous connecter pour voir votre tableau de bord.
          </div>
        </div>
      </div>
    );
  }

  const stats = statsData;

  return (
    <div className="min-h-screen bg-bg">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between mb-8 gap-4">
          <div>
            <h1 className="text-3xl font-bold text-ink">
              Tableau de bord
            </h1>
            <p className="text-ink-soft mt-1">
              Vue d'ensemble de vos activités logistiques
            </p>
          </div>
          <button
            onClick={refreshAll}
            className="self-start sm:self-auto flex items-center gap-2 px-3 py-2 text-sm text-ink-soft bg-surface border border-line rounded-lg hover:bg-surface-2 transition-colors whitespace-nowrap tap-target"
          >
            <RefreshCw size={14} />
            Actualiser
          </button>
        </div>

        {/* Simulations ce mois + Taux de change — tout ce que l'API analytics renvoie
            encore (voir SimulationRepository) plus un widget indépendant (taux de
            change), regroupés dans une seule rangée depuis le retrait du domaine
            Transport (expéditions/transporteurs/coûts/itinéraires). */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
          {statsLoading ? (
            <SkeletonCard />
          ) : stats ? (
            <div className="bg-surface rounded-lg border border-line p-4">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 bg-accent-soft rounded-lg flex items-center justify-center shrink-0">
                  <Calculator className="text-accent" size={20} />
                </div>
                <div className="min-w-0">
                  <p className="text-xs text-ink-soft">Simulations ce mois</p>
                  <p className="text-lg font-bold text-ink">
                    {formatNumber(stats.simulationsThisMonth)}
                    {user?.plan === 'FREE' && (
                      <span className="text-ink-soft font-normal"> / {FREE_PLAN_SIMULATION_LIMIT}</span>
                    )}
                  </p>
                </div>
              </div>
              {user?.plan === 'FREE' && (() => {
                const usageRatio = stats.simulationsThisMonth / FREE_PLAN_SIMULATION_LIMIT;
                const nearLimit = usageRatio >= 0.8;
                return (
                  <div className="mt-3">
                    <div className="w-full bg-surface-2 rounded-full h-1.5">
                      <div
                        className={`h-1.5 rounded-full transition-all ${nearLimit ? 'bg-warning' : 'bg-accent'}`}
                        style={{ width: `${Math.min(100, usageRatio * 100)}%` }}
                      />
                    </div>
                    {nearLimit && (
                      <Link
                        to="/pricing"
                        className="inline-block mt-2 text-xs font-semibold text-warning hover:underline"
                      >
                        {stats.simulationsThisMonth >= FREE_PLAN_SIMULATION_LIMIT
                          ? 'Limite atteinte'
                          : 'Limite bientôt atteinte'}{' '}— voir les plans →
                      </Link>
                    )}
                  </div>
                );
              })()}
            </div>
          ) : null}

          {fxLoading ? (
            [...Array(3)].map((_, i) => (
              <div key={i} className="bg-surface rounded-lg border border-line p-4 animate-pulse">
                <div className="h-4 bg-surface-2 rounded w-1/2 mb-2" />
                <div className="h-6 bg-surface-2 rounded w-1/3" />
              </div>
            ))
          ) : fxRates && fxRates.length > 0 ? (
            fxRates.map((fx: FxRate, i: number) => (
              <div key={i} className="bg-surface rounded-lg border border-line p-4">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <div className="w-8 h-8 bg-warning/10 rounded-lg flex items-center justify-center">
                      <ArrowLeftRight className="text-warning" size={16} />
                    </div>
                    <span className="text-xs font-medium text-ink-soft uppercase">
                      {fx.from} → {fx.to}
                    </span>
                  </div>
                  <span className="text-lg font-bold text-ink">
                    {fx.convertedAmount.toFixed(4)}
                  </span>
                </div>
              </div>
            ))
          ) : (
            <div className="col-span-full bg-surface rounded-lg border border-line p-6 text-center text-ink-soft text-sm">
              Taux de change indisponible — configurez EXCHANGERATE_API_KEY
            </div>
          )}
        </div>

        {/* ROI Calculator */}
        <div className="bg-surface rounded-lg border border-line p-6 mb-6">
          <div className="flex items-center gap-2 mb-2">
            <Calculator className="text-accent" size={20} />
            <h2 className="text-lg font-semibold text-ink">Estimez vos économies</h2>
          </div>
          <p className="text-sm text-ink-soft mb-6">
            Comparez IncoKalk à votre ancienne méthode (transitaire classique, Excel + email, portails multiples).
          </p>

          <div className="grid grid-cols-2 lg:grid-cols-5 gap-4 mb-6">
            <div>
              <label className="block text-xs font-medium text-ink-soft mb-1">Expéditions / mois</label>
              <input
                type="number"
                min={0}
                value={roi.shipments}
                onChange={setRoiField('shipments')}
                className="w-full px-3 py-2 border border-line rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-accent"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-ink-soft mb-1">Coût moyen / expédition (€)</label>
              <input
                type="number"
                min={0}
                value={roi.avgCost}
                onChange={setRoiField('avgCost')}
                className="w-full px-3 py-2 border border-line rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-accent"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-ink-soft mb-1">Heures économisées / mois</label>
              <input
                type="number"
                min={0}
                value={roi.hoursSaved}
                onChange={setRoiField('hoursSaved')}
                className="w-full px-3 py-2 border border-line rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-accent"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-ink-soft mb-1">Taux horaire (€)</label>
              <input
                type="number"
                min={0}
                value={roi.hourlyRate}
                onChange={setRoiField('hourlyRate')}
                className="w-full px-3 py-2 border border-line rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-accent"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-ink-soft mb-1">Marge transitaire évitée (%)</label>
              <input
                type="number"
                min={0}
                max={100}
                value={roi.marginPct}
                onChange={setRoiField('marginPct')}
                className="w-full px-3 py-2 border border-line rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-accent"
              />
            </div>
          </div>

          <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
            <div className="bg-bg rounded-lg p-4">
              <p className="text-xs text-ink-soft">Temps gagné</p>
              <p className="text-lg font-bold text-ink">{formatNumber(savingsTime)} €/mois</p>
            </div>
            <div className="bg-bg rounded-lg p-4">
              <p className="text-xs text-ink-soft">Marge transitaire évitée</p>
              <p className="text-lg font-bold text-ink">{formatNumber(savingsMargin, { maximumFractionDigits: 0 })} €/mois</p>
            </div>
            <div className="bg-bg rounded-lg p-4">
              <p className="text-xs text-ink-soft">Coût plan Pro ({proMonthly}€/mois)</p>
              <p className="text-lg font-bold text-ink">-{formatNumber(proMonthly)} €/mois</p>
            </div>
            <div className={`rounded-lg p-4 ${netMonthly > 0 ? 'bg-success/10 border border-success/20' : 'bg-bg'}`}>
              <p className="text-xs text-ink-soft">Économies nettes</p>
              <p className={`text-lg font-bold ${netMonthly > 0 ? 'text-success' : 'text-ink'}`}>
                {netMonthly > 0 ? '+' : ''}{formatNumber(netMonthly, { maximumFractionDigits: 0 })} €/mois
              </p>
              <p className="text-xs text-ink-soft">{netAnnual > 0 ? '+' : ''}{formatNumber(netAnnual, { maximumFractionDigits: 0 })} €/an</p>
            </div>
          </div>
        </div>

        {/* Simulation History — Collapsible */}
        <div className="bg-surface rounded-lg border border-line overflow-hidden mb-6">
          <button
            onClick={() => setHistoryOpen(!historyOpen)}
            className="w-full flex items-center justify-between p-6 text-left hover:bg-surface-2 transition-colors"
          >
            <div className="flex items-center gap-3">
              <FileText className="text-ink-soft" size={20} />
              <h2 className="text-lg font-semibold text-ink">Historique des simulations</h2>
              <Badge variant="neutral">{totalSimulations}</Badge>
            </div>
            {historyOpen ? (
              <ChevronUp className="text-ink-soft" size={20} />
            ) : (
              <ChevronDown className="text-ink-soft" size={20} />
            )}
          </button>

          {historyOpen && (
            <div className="border-t border-line">
              {simLoading ? (
                <div className="p-8 text-center">
                  <Loader2 className="animate-spin text-ink-soft mx-auto" size={24} />
                  <p className="mt-2 text-sm text-ink-soft">Chargement des simulations...</p>
                </div>
              ) : simError ? (
                <div className="p-8 text-center text-danger">
                  Erreur lors du chargement des simulations
                </div>
              ) : simulations.length === 0 ? (
                <div className="p-8 text-center text-ink-soft">
                  Aucune simulation enregistrée.
                  <a
                    href="/simulation"
                    className="text-accent ml-2 hover:underline font-medium"
                  >
                    Commencer une simulation
                  </a>
                </div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full">
                    <thead>
                      <tr className="border-b bg-bg">
                        <th className="text-left py-3 px-6 font-semibold text-sm text-ink-soft">
                          Date
                        </th>
                        <th className="text-left py-3 px-6 font-semibold text-sm text-ink-soft">
                          Incoterm
                        </th>
                        <th className="text-left py-3 px-6 font-semibold text-sm text-ink-soft">
                          Origine
                        </th>
                        <th className="text-left py-3 px-6 font-semibold text-sm text-ink-soft">
                          Destination
                        </th>
                        <th className="text-left py-3 px-6 font-semibold text-sm text-ink-soft">
                          Valeur
                        </th>
                        <th className="text-left py-3 px-6 font-semibold text-sm text-ink-soft">
                          Coût total
                        </th>
                        <th className="text-right py-3 px-6 font-semibold text-sm text-ink-soft">
                          Actions
                        </th>
                      </tr>
                    </thead>
                    <tbody>
                      {simulations.map((simulation: Simulation) => (
                        <tr
                          key={simulation.id}
                          className="border-b last:border-b-0 hover:bg-surface-2 transition-colors"
                        >
                          <td className="py-3 px-6 text-sm">
                            {new Date(simulation.createdAt).toLocaleDateString('fr-FR')}
                          </td>
                          <td className="py-3 px-6">
                            <span className="bg-accent-soft text-accent px-2 py-1 rounded text-xs font-medium">
                              {simulation.incotermCode}
                            </span>
                          </td>
                          <td className="py-3 px-6 text-sm text-ink-soft">
                            {simulation.originCountry || '-'}
                          </td>
                          <td className="py-3 px-6 text-sm text-ink-soft">
                            {simulation.destinationCountry || '-'}
                          </td>
                          <td className="py-3 px-6 text-sm text-ink-soft">
                            {simulation.productValue != null ? formatNumber(simulation.productValue) : ''}{' '}
                            {simulation.currency || 'EUR'}
                          </td>
                          <td className="py-3 px-6 text-sm font-medium text-ink">
                            {simulation.totalCost != null ? formatNumber(simulation.totalCost, {
                              minimumFractionDigits: 2,
                              maximumFractionDigits: 2,
                            }) : ''}{' '}
                            €
                          </td>
                          <td className="py-3 px-6 text-right">
                            <button
                              onClick={() => handleDelete(simulation.id.toString())}
                              className="text-danger hover:text-danger p-1 transition-colors"
                              title="Supprimer"
                            >
                              <Trash2 size={16} />
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default Dashboard;
