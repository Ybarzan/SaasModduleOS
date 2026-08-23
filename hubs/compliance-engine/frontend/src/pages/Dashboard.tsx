import { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAuthStore } from '../stores/auth';
import { incokalkAPI } from '../lib/api';
import toast from 'react-hot-toast';
import { SkeletonCard } from '../components/ui/Skeleton';
import Badge from '../components/ui/Badge';
import { formatNumber, formatEur } from '../lib/formatNumber';
import {
  Trash2,
  Package,
  DollarSign,
  Truck,
  Leaf,
  Weight,
  Box,
  Calculator,
  TrendingUp,
  BarChart3,
  ArrowRight,
  Globe,
  FileText,
  Loader2,
  ChevronDown,
  ChevronUp,
  RefreshCw,
  Ship,
  Plane,
  Route,
  ArrowLeftRight,
} from 'lucide-react';
import type {
  DashboardStats,
  ShipmentsOverTime,
  ShipmentByStatus,
  CostByCarrier,
  CostByMode,
  TopRoute,
  IncotermUsage,
} from '../types';

import { STATUS_LABELS } from '@/lib/constants';

// Miroir de BillingService.getPlans() (limits.simulationsPerMonth du plan FREE, backend) --
// tous les plans payants sont illimités (-1), seul FREE a un plafond, donc pas besoin
// d'un appel API supplémentaire rien que pour cette constante.
const FREE_PLAN_SIMULATION_LIMIT = 5;

interface FxRate {
  from: string;
  to: string;
  convertedAmount: number;
}

interface DistributionBucket {
  bucket?: string;
  range?: string;
  label?: string;
  count?: number;
  value?: number;
}

interface CostTrend {
  period: string;
  totalCost: number;
  shipmentCount: number;
}

interface CarrierPerf {
  carrierName: string;
  onTimeRate: number;
  totalShipments: number;
  avgCost?: number;
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

const STATUS_COLORS: Record<string, string> = {
  DRAFT: 'bg-ink-soft',
  QUOTED: 'bg-accent',
  BOOKED: 'bg-accent-strong',
  IN_TRANSIT: 'bg-warning',
  DELIVERED: 'bg-success',
  CANCELLED: 'bg-danger',
};

const MODE_ICONS: Record<string, typeof Ship> = {
  SEA: Ship,
  AIR: Plane,
  ROAD: Truck,
};

const MODE_COLORS: Record<string, string> = {
  SEA: 'bg-accent',
  AIR: 'bg-accent-strong',
  ROAD: 'bg-warning',
};

const HorizontalBarChart = ({
  data,
  maxValue,
  colorClass,
}: {
  data: { label: string; value: number; sublabel?: string }[];
  maxValue: number;
  colorClass: string;
}) => (
  <div className="space-y-3">
    {data.map((item, i) => (
      <div key={i} className="flex items-center space-x-3">
        <span className="text-sm text-ink-soft w-32 truncate">{item.label}</span>
        <div className="flex-1 bg-surface-2 rounded-full h-6 overflow-hidden">
          <div
            className={`h-full rounded-full ${colorClass} transition-all duration-500`}
            style={{ width: `${maxValue > 0 ? (item.value / maxValue) * 100 : 0}%` }}
          />
        </div>
        <span className="text-sm font-medium text-ink-soft w-24 text-right">
          {formatNumber(item.value)}
        </span>
        {item.sublabel && (
          <span className="text-xs text-ink-soft w-20 text-right">{item.sublabel}</span>
        )}
      </div>
    ))}
  </div>
);

const VerticalBarChart = ({ data }: { data: { label: string; value: number }[] }) => {
  const maxVal = Math.max(...data.map((d) => d.value), 1);
  return (
    <div className="flex items-end space-x-1 h-48">
      {data.map((item, i) => (
        <div key={i} className="flex-1 flex flex-col items-center">
          <div className="text-xs text-ink-soft mb-1">{item.value}</div>
          <div
            className="w-full bg-accent rounded-t hover:bg-accent transition-all"
            style={{
              height: `${(item.value / maxVal) * 100}%`,
              minHeight: item.value > 0 ? '4px' : '0',
            }}
          />
          <div className="text-xs text-ink-soft mt-1 truncate w-full text-center">
            {item.label}
          </div>
        </div>
      ))}
    </div>
  );
};

const Dashboard = () => {
  const user = useAuthStore((state) => state.user);
  const queryClient = useQueryClient();

  const [period, setPeriod] = useState('30d');
  const [historyOpen, setHistoryOpen] = useState(false);
  const [roi, setRoi] = useState({
    shipments: 30,
    avgCost: 250,
    hoursSaved: 20,
    hourlyRate: 40,
    marginPct: 10,
  });

  const periods = [
    { label: '7 jours', value: '7d' },
    { label: '30 jours', value: '30d' },
    { label: '90 jours', value: '90d' },
    { label: '1 an', value: '1y' },
    { label: 'Tout', value: 'all' },
  ];

  const getGranularity = useCallback(() => {
    switch (period) {
      case '7d':
        return 'DAY';
      case '30d':
        return 'DAY';
      case '90d':
        return 'WEEK';
      case '1y':
        return 'MONTH';
      default:
        return 'MONTH';
    }
  }, [period]);

  // Auto-refresh every 60 seconds
  useEffect(() => {
    const interval = setInterval(() => {
      queryClient.invalidateQueries({ queryKey: ['analytics', period] });
    }, 60000);
    return () => clearInterval(interval);
  }, [period, queryClient]);

  // Analytics queries
  const { data: statsData, isLoading: statsLoading } = useQuery({
    queryKey: ['analytics', 'dashboard', period],
    queryFn: async () => {
      const res = await incokalkAPI.analytics.dashboard(period);
      return res.data as DashboardStats;
    },
    enabled: !!user,
    refetchInterval: 60000,
  });

  const { data: overtimeData = [], isLoading: overtimeLoading } = useQuery({
    queryKey: ['analytics', 'overtime', period],
    queryFn: async () => {
      const res = await incokalkAPI.analytics.shipmentsOverTime(period, getGranularity());
      return res.data as ShipmentsOverTime[];
    },
    enabled: !!user,
  });

  const { data: statusData = [], isLoading: statusLoading } = useQuery({
    queryKey: ['analytics', 'status'],
    queryFn: async () => {
      const res = await incokalkAPI.analytics.shipmentsByStatus();
      return res.data as ShipmentByStatus[];
    },
    enabled: !!user,
  });

  const { data: carrierCostData = [], isLoading: carrierCostLoading } = useQuery({
    queryKey: ['analytics', 'carrier-cost'],
    queryFn: async () => {
      const res = await incokalkAPI.analytics.costByCarrier();
      return res.data as CostByCarrier[];
    },
    enabled: !!user,
  });

  const { data: modeCostData = [], isLoading: modeCostLoading } = useQuery({
    queryKey: ['analytics', 'mode-cost'],
    queryFn: async () => {
      const res = await incokalkAPI.analytics.costByMode();
      return res.data as CostByMode[];
    },
    enabled: !!user,
  });

  const { data: topRoutesData = [], isLoading: topRoutesLoading } = useQuery({
    queryKey: ['analytics', 'top-routes'],
    queryFn: async () => {
      const res = await incokalkAPI.analytics.topRoutes(5);
      return res.data as TopRoute[];
    },
    enabled: !!user,
  });

  const { data: incotermData = [], isLoading: incotermLoading } = useQuery({
    queryKey: ['analytics', 'incoterms'],
    queryFn: async () => {
      const res = await incokalkAPI.analytics.incotermUsage();
      return res.data as IncotermUsage[];
    },
    enabled: !!user,
  });

  const { data: weightDistData = [], isLoading: weightLoading } = useQuery({
    queryKey: ['analytics', 'weight-dist'],
    queryFn: async () => {
      const res = await incokalkAPI.analytics.weightDistribution();
      return res.data;
    },
    enabled: !!user,
  });

  const { data: volumeDistData = [], isLoading: volumeLoading } = useQuery({
    queryKey: ['analytics', 'volume-dist'],
    queryFn: async () => {
      const res = await incokalkAPI.analytics.volumeDistribution();
      return res.data;
    },
    enabled: !!user,
  });

  const { data: costTrendsData = [], isLoading: costTrendsLoading } = useQuery({
    queryKey: ['analytics', 'cost-trends'],
    queryFn: async () => {
      const res = await incokalkAPI.analytics.costTrends('90d', 'month');
      return res.data;
    },
    enabled: !!user,
  });

  const { data: carrierPerfData = [], isLoading: carrierPerfLoading } = useQuery({
    queryKey: ['analytics', 'carrier-performance'],
    queryFn: async () => {
      const res = await incokalkAPI.analytics.carrierPerformance();
      return res.data;
    },
    enabled: !!user,
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
            <h1 className="text-3xl font-bold text-ink">Tableau de bord</h1>
            <p className="text-ink-soft mt-1">
              Vue d'ensemble de vos activités logistiques
            </p>
          </div>
          <div className="flex items-center gap-3 overflow-x-auto touch-scroll">
            <button
              onClick={refreshAll}
              className="flex items-center gap-2 px-3 py-2 text-sm text-ink-soft bg-surface border border-line rounded-lg hover:bg-surface-2 transition-colors whitespace-nowrap tap-target"
            >
              <RefreshCw size={14} />
              Actualiser
            </button>
            <div className="flex bg-surface border border-line rounded-lg p-1 whitespace-nowrap">
              {periods.map((p) => (
                <button
                  key={p.value}
                  onClick={() => setPeriod(p.value)}
                  className={`px-3 py-1.5 text-sm rounded-md transition-colors tap-target ${
                    period === p.value
                      ? 'bg-accent text-white'
                      : 'text-ink-soft hover:bg-surface-2'
                  }`}
                >
                  {p.label}
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* Row 1 — KPI Cards */}
        {statsLoading ? (
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
            {[...Array(4)].map((_, i) => (
              <SkeletonCard key={i} />
            ))}
          </div>
        ) : stats ? (
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
            <div className="bg-surface rounded-2xl border border-line p-6">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium text-ink-soft">Expéditions totales</p>
                  <p className="text-3xl font-bold text-ink mt-1">
                    {formatNumber(stats.totalShipments)}
                  </p>
                  <Badge variant="info" className="mt-2">
                    {formatNumber(stats.activeShipments)} actives
                  </Badge>
                </div>
                <div className="w-12 h-12 bg-accent-soft rounded-xl flex items-center justify-center">
                  <Package className="text-accent" size={24} />
                </div>
              </div>
            </div>

            <div className="bg-surface rounded-2xl border border-line p-6">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium text-ink-soft">Coût total</p>
                  <p className="text-3xl font-bold text-ink mt-1">
                    {formatEur(stats.totalShippingCost, {
                      minimumFractionDigits: 0,
                      maximumFractionDigits: 0,
                    })}
                  </p>
                  <p className="text-sm text-ink-soft mt-1">
                    Moy:{' '}
                    {formatEur(stats.averageShippingCost, {
                      minimumFractionDigits: 0,
                      maximumFractionDigits: 0,
                    })}
                  </p>
                </div>
                <div className="w-12 h-12 bg-accent-soft rounded-xl flex items-center justify-center">
                  <DollarSign className="text-accent" size={24} />
                </div>
              </div>
            </div>

            <div className="bg-surface rounded-2xl border border-line p-6">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium text-ink-soft">Transporteurs</p>
                  <p className="text-3xl font-bold text-ink mt-1">
                    {formatNumber(stats.totalCarriers)}
                  </p>
                  <Badge variant="info" className="mt-2">
                    {formatNumber(stats.activeCarriers)} actifs
                  </Badge>
                </div>
                <div className="w-12 h-12 bg-accent-soft rounded-xl flex items-center justify-center">
                  <Truck className="text-accent" size={24} />
                </div>
              </div>
            </div>

            <div className="bg-surface rounded-2xl border border-line p-6">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium text-ink-soft">CO₂ total</p>
                  <p className="text-3xl font-bold text-ink mt-1">
                    {formatNumber(stats.totalCo2Kg, {
                      minimumFractionDigits: 1,
                      maximumFractionDigits: 1,
                    })}{' '}
                    kg
                  </p>
                  <p className="text-sm text-ink-soft mt-1">
                    Moy: {formatNumber(stats.averageCo2PerShipment, {
                      minimumFractionDigits: 1,
                      maximumFractionDigits: 1,
                    })}{' '}
                    kg/expédition
                  </p>
                </div>
                <div className="w-12 h-12 bg-success/10 rounded-xl flex items-center justify-center">
                  <Leaf className="text-success" size={24} />
                </div>
              </div>
            </div>
          </div>
        ) : null}

        {/* Row 2 — Secondary KPIs */}
        {statsLoading ? (
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
            {[...Array(4)].map((_, i) => (
              <SkeletonCard key={i} />
            ))}
          </div>
        ) : stats ? (
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
            <div className="bg-surface rounded-2xl border border-line p-4">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 bg-warning/10 rounded-lg flex items-center justify-center">
                  <Weight className="text-warning" size={20} />
                </div>
                <div>
                  <p className="text-xs text-ink-soft">Poids total</p>
                  <p className="text-lg font-bold text-ink">
                    {stats.totalWeightKg >= 1000
                      ? `${formatNumber(stats.totalWeightKg / 1000, { minimumFractionDigits: 2, maximumFractionDigits: 2 })} t`
                      : `${formatNumber(stats.totalWeightKg, { minimumFractionDigits: 1, maximumFractionDigits: 1 })} kg`}
                  </p>
                </div>
              </div>
            </div>

            <div className="bg-surface rounded-2xl border border-line p-4">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 bg-accent/10 rounded-lg flex items-center justify-center">
                  <Box className="text-accent" size={20} />
                </div>
                <div>
                  <p className="text-xs text-ink-soft">Volume total</p>
                  <p className="text-lg font-bold text-ink">
                    {formatNumber(stats.totalVolumeM3, {
                      minimumFractionDigits: 2,
                      maximumFractionDigits: 2,
                    })}{' '}
                    m³
                  </p>
                </div>
              </div>
            </div>

            <div className="bg-surface rounded-2xl border border-line p-4">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 bg-accent-soft rounded-lg flex items-center justify-center">
                  <FileText className="text-accent" size={20} />
                </div>
                <div>
                  <p className="text-xs text-ink-soft">Valeur marchandises</p>
                  <p className="text-lg font-bold text-ink">
                    {formatEur(stats.totalGoodsValue, {
                      minimumFractionDigits: 0,
                      maximumFractionDigits: 0,
                    })}
                  </p>
                </div>
              </div>
            </div>

            <div className="bg-surface rounded-2xl border border-line p-4">
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
          </div>
        ) : null}

        {/* Row 2b — Taux de change */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-6">
          {fxLoading ? (
            [...Array(3)].map((_, i) => (
              <div key={i} className="bg-surface rounded-2xl border border-line p-4 animate-pulse">
                <div className="h-4 bg-surface-2 rounded w-1/2 mb-2" />
                <div className="h-6 bg-surface-2 rounded w-1/3" />
              </div>
            ))
          ) : fxRates && fxRates.length > 0 ? (
            fxRates.map((fx: FxRate, i: number) => (
              <div key={i} className="bg-surface rounded-2xl border border-line p-4">
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
            <div className="col-span-3 bg-surface rounded-2xl border border-line p-6 text-center text-ink-soft text-sm">
              Taux de change indisponible — configurez EXCHANGERATE_API_KEY
            </div>
          )}
        </div>

        {/* Row 3 — Charts */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 mb-6">
          {/* Evolution des expéditions — 2/3 */}
          <div className="lg:col-span-2 bg-surface rounded-2xl border border-line p-6">
            <div className="flex items-center gap-2 mb-6">
              <BarChart3 className="text-ink-soft" size={20} />
              <h2 className="text-lg font-semibold text-ink">Évolution des expéditions</h2>
            </div>
            {overtimeLoading ? (
              <div className="h-48 flex items-center justify-center">
                <Loader2 className="animate-spin text-ink-soft" size={24} />
              </div>
            ) : overtimeData.length > 0 ? (
              <VerticalBarChart
                data={overtimeData.map((d) => ({
                  label: new Date(d.date).toLocaleDateString('fr-FR', {
                    day: '2-digit',
                    month: 'short',
                  }),
                  value: d.count,
                }))}
              />
            ) : (
              <div className="h-48 flex flex-col items-center justify-center text-ink-soft">
                <BarChart3 size={32} className="mb-2" />
                <p className="text-sm">Aucune donnée disponible pour cette période</p>
              </div>
            )}
          </div>

          {/* Répartition par statut — 1/3 */}
          <div className="bg-surface rounded-2xl border border-line p-6">
            <div className="flex items-center gap-2 mb-6">
              <TrendingUp className="text-ink-soft" size={20} />
              <h2 className="text-lg font-semibold text-ink">Répartition par statut</h2>
            </div>
            {statusLoading ? (
              <div className="space-y-3">
                {[...Array(4)].map((_, i) => (
                  <div key={i} className="animate-pulse">
                    <div className="h-4 bg-surface-2 rounded w-full mb-2" />
                    <div className="h-3 bg-surface-2 rounded w-3/4" />
                  </div>
                ))}
              </div>
            ) : statusData.length > 0 ? (
              <div className="space-y-4">
                {statusData.map((s, i) => (
                  <div key={i}>
                    <div className="flex items-center justify-between mb-1">
                      <span className="text-sm text-ink-soft">
                        {STATUS_LABELS[s.status] || s.status}
                      </span>
                      <span className="text-sm font-medium text-ink-soft">
                        {s.count} ({s.percentage.toFixed(0)}%)
                      </span>
                    </div>
                    <div className="w-full bg-surface-2 rounded-full h-2.5 overflow-hidden">
                      <div
                        className={`h-full rounded-full ${STATUS_COLORS[s.status] || 'bg-ink-soft'} transition-all duration-500`}
                        style={{ width: `${s.percentage}%` }}
                      />
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <div className="h-32 flex flex-col items-center justify-center text-ink-soft">
                <p className="text-sm">Aucune donnée disponible</p>
              </div>
            )}
          </div>
        </div>

        {/* Row 3b — Carrier cost + Mode cost */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 mb-6">
          {/* Coût par transporteur — 2/3 */}
          <div className="lg:col-span-2 bg-surface rounded-2xl border border-line p-6">
            <div className="flex items-center gap-2 mb-6">
              <Truck className="text-ink-soft" size={20} />
              <h2 className="text-lg font-semibold text-ink">Coût par transporteur</h2>
            </div>
            {carrierCostLoading ? (
              <div className="space-y-3">
                {[...Array(3)].map((_, i) => (
                  <div key={i} className="animate-pulse flex items-center space-x-3">
                    <div className="h-4 bg-surface-2 rounded w-32" />
                    <div className="flex-1 h-6 bg-surface-2 rounded-full" />
                    <div className="h-4 bg-surface-2 rounded w-24" />
                  </div>
                ))}
              </div>
            ) : carrierCostData.length > 0 ? (
              <HorizontalBarChart
                data={carrierCostData.map((c) => ({
                  label: c.carrierName,
                  value: c.totalCost,
                  sublabel: `${c.shipmentCount} exp., Moy: ${formatEur(c.averageCost, { maximumFractionDigits: 0 })}`,
                }))}
                maxValue={Math.max(...carrierCostData.map((c) => c.totalCost))}
                colorClass="bg-accent-strong"
              />
            ) : (
              <div className="h-32 flex flex-col items-center justify-center text-ink-soft">
                <Truck size={32} className="mb-2" />
                <p className="text-sm">Aucune donnée disponible</p>
              </div>
            )}
          </div>

          {/* Coût par mode — 1/3 */}
          <div className="bg-surface rounded-2xl border border-line p-6">
            <div className="flex items-center gap-2 mb-6">
              <Globe className="text-ink-soft" size={20} />
              <h2 className="text-lg font-semibold text-ink">Coût par mode</h2>
            </div>
            {modeCostLoading ? (
              <div className="space-y-3">
                {[...Array(3)].map((_, i) => (
                  <div key={i} className="animate-pulse">
                    <div className="h-20 bg-surface-2 rounded-lg" />
                  </div>
                ))}
              </div>
            ) : modeCostData.length > 0 ? (
              <div className="space-y-3">
                {modeCostData.map((m, i) => {
                  const Icon = MODE_ICONS[m.mode] || Truck;
                  return (
                    <div
                      key={i}
                      className="border border-line rounded-lg p-4"
                    >
                      <div className="flex items-center gap-3">
                        <div
                          className={`w-10 h-10 rounded-lg flex items-center justify-center ${MODE_COLORS[m.mode] || 'bg-bg0'} bg-opacity-10`}
                        >
                          <Icon
                            className={`${MODE_COLORS[m.mode]?.replace('bg-', 'text-') || 'text-ink-soft'}`}
                            size={20}
                          />
                        </div>
                        <div className="flex-1">
                          <p className="text-sm font-medium text-ink">{m.mode}</p>
                          <p className="text-xs text-ink-soft">
                            {m.count} expéditions
                          </p>
                        </div>
                        <div className="text-right">
                          <p className="text-sm font-bold text-ink">
                            {formatEur(m.totalCost, {
                              maximumFractionDigits: 0,
                            })}
                          </p>
                          <p className="text-xs text-ink-soft">
                            Moy:{' '}
                            {formatEur(m.averageCost, {
                              maximumFractionDigits: 0,
                            })}
                          </p>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            ) : (
              <div className="h-32 flex flex-col items-center justify-center text-ink-soft">
                <p className="text-sm">Aucune donnée disponible</p>
              </div>
            )}
          </div>
        </div>

        {/* Row 4 — Top routes + Incoterms */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-6">
          {/* Top 5 itinéraires */}
          <div className="bg-surface rounded-2xl border border-line p-6">
            <div className="flex items-center gap-2 mb-6">
              <Route className="text-ink-soft" size={20} />
              <h2 className="text-lg font-semibold text-ink">Top 5 itinéraires</h2>
            </div>
            {topRoutesLoading ? (
              <div className="space-y-3">
                {[...Array(5)].map((_, i) => (
                  <div key={i} className="animate-pulse flex items-center space-x-3">
                    <div className="h-8 w-8 bg-surface-2 rounded-full" />
                    <div className="flex-1 h-4 bg-surface-2 rounded" />
                    <div className="h-4 bg-surface-2 rounded w-20" />
                  </div>
                ))}
              </div>
            ) : topRoutesData.length > 0 ? (
              <div className="space-y-3">
                {topRoutesData.map((r, i) => (
                  <div
                    key={i}
                    className="flex items-center gap-3 p-3 rounded-lg hover:bg-surface-2 transition-colors"
                  >
                    <span className="w-8 h-8 bg-surface-2 rounded-full flex items-center justify-center text-sm font-bold text-ink-soft">
                      {i + 1}
                    </span>
                    <div className="flex items-center gap-2 flex-1">
                      <span className="text-sm font-medium text-ink">{r.origin}</span>
                      <ArrowRight size={14} className="text-ink-soft" />
                      <span className="text-sm font-medium text-ink">{r.destination}</span>
                    </div>
                    <div className="text-right">
                      <span className="text-sm font-medium text-ink-soft">{r.count} exp.</span>
                      <span className="text-xs text-ink-soft block">
                        {formatEur(r.totalCost, { maximumFractionDigits: 0 })}
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <div className="h-32 flex flex-col items-center justify-center text-ink-soft">
                <Globe size={32} className="mb-2" />
                <p className="text-sm">Aucune donnée disponible</p>
              </div>
            )}
          </div>

          {/* Utilisation Incoterms */}
          <div className="bg-surface rounded-2xl border border-line p-6">
            <div className="flex items-center gap-2 mb-6">
              <FileText className="text-ink-soft" size={20} />
              <h2 className="text-lg font-semibold text-ink">Utilisation Incoterms</h2>
            </div>
            {incotermLoading ? (
              <div className="space-y-3">
                {[...Array(4)].map((_, i) => (
                  <div key={i} className="animate-pulse flex items-center space-x-3">
                    <div className="h-4 bg-surface-2 rounded w-16" />
                    <div className="flex-1 h-6 bg-surface-2 rounded-full" />
                    <div className="h-4 bg-surface-2 rounded w-12" />
                  </div>
                ))}
              </div>
            ) : incotermData.length > 0 ? (
              <HorizontalBarChart
                data={incotermData.map((ic) => ({
                  label: ic.code,
                  value: ic.count,
                  sublabel: `${ic.percentage.toFixed(0)}%`,
                }))}
                maxValue={Math.max(...incotermData.map((ic) => ic.count))}
                colorClass="bg-accent"
              />
            ) : (
              <div className="h-32 flex flex-col items-center justify-center text-ink-soft">
                <p className="text-sm">Aucune donnée disponible</p>
              </div>
            )}
          </div>
        </div>

        {/* Row 5 — Weight & Volume distributions */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-6">
          {/* Distribution poids */}
          <div className="bg-surface rounded-2xl border border-line p-6">
            <div className="flex items-center gap-2 mb-6">
              <Weight className="text-ink-soft" size={20} />
              <h2 className="text-lg font-semibold text-ink">Distribution poids</h2>
            </div>
            {weightLoading ? (
              <div className="h-48 flex items-center justify-center">
                <Loader2 className="animate-spin text-ink-soft" size={24} />
              </div>
            ) : weightDistData && weightDistData.length > 0 ? (
              <VerticalBarChart
                data={weightDistData.map((d: DistributionBucket) => ({
                  label: d.bucket || d.range || d.label,
                  value: d.count || d.value,
                }))}
              />
            ) : (
              <div className="h-48 flex flex-col items-center justify-center text-ink-soft">
                <Weight size={32} className="mb-2" />
                <p className="text-sm">Aucune donnée disponible</p>
              </div>
            )}
          </div>

          {/* Distribution volume */}
          <div className="bg-surface rounded-2xl border border-line p-6">
            <div className="flex items-center gap-2 mb-6">
              <Box className="text-ink-soft" size={20} />
              <h2 className="text-lg font-semibold text-ink">Distribution volume</h2>
            </div>
            {volumeLoading ? (
              <div className="h-48 flex items-center justify-center">
                <Loader2 className="animate-spin text-ink-soft" size={24} />
              </div>
            ) : volumeDistData && volumeDistData.length > 0 ? (
              <VerticalBarChart
                data={volumeDistData.map((d: DistributionBucket) => ({
                  label: d.bucket || d.range || d.label,
                  value: d.count || d.value,
                }))}
              />
            ) : (
              <div className="h-48 flex flex-col items-center justify-center text-ink-soft">
                <Box size={32} className="mb-2" />
                <p className="text-sm">Aucune donnée disponible</p>
              </div>
            )}
          </div>
        </div>

        {/* Row 6 — Cost Trends + Carrier Performance */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-6">
          {/* Cost Trends */}
          <div className="bg-surface rounded-2xl border border-line p-6">
            <div className="flex items-center gap-2 mb-4">
              <TrendingUp size={20} className="text-success" />
              <h2 className="text-lg font-semibold text-ink">Évolution des coûts</h2>
            </div>
            {costTrendsLoading ? (
              <div className="h-48 flex items-center justify-center">
                <Loader2 className="animate-spin text-ink-soft" size={24} />
              </div>
            ) : costTrendsData.length > 0 ? (
              <div className="space-y-3">
                {costTrendsData.map((trend: CostTrend, i: number) => {
                  const maxCost = Math.max(...costTrendsData.map((t: CostTrend) => t.totalCost), 1);
                  return (
                    <div key={i} className="flex items-center space-x-3">
                      <span className="text-xs text-ink-soft w-20 truncate">{trend.period}</span>
                      <div className="flex-1 bg-surface-2 rounded-full h-5 overflow-hidden">
                        <div
                          className="h-full rounded-full bg-success transition-all duration-500"
                          style={{ width: `${(trend.totalCost / maxCost) * 100}%` }}
                        />
                      </div>
                      <span className="text-xs font-medium text-ink-soft w-24 text-right">
                        {trend.totalCost != null ? formatNumber(trend.totalCost, { minimumFractionDigits: 0 }) : ''} €
                      </span>
                      <span className="text-[10px] text-ink-soft w-12 text-right">
                        {trend.shipmentCount} exp.
                      </span>
                    </div>
                  );
                })}
              </div>
            ) : (
              <div className="h-48 flex flex-col items-center justify-center text-ink-soft">
                <TrendingUp size={32} className="mb-2" />
                <p className="text-sm">Aucune donnée de coût</p>
              </div>
            )}
          </div>

          {/* Carrier Performance */}
          <div className="bg-surface rounded-2xl border border-line p-6">
            <div className="flex items-center gap-2 mb-4">
              <BarChart3 size={20} className="text-accent" />
              <h2 className="text-lg font-semibold text-ink">Performance transporteurs</h2>
            </div>
            {carrierPerfLoading ? (
              <div className="h-48 flex items-center justify-center">
                <Loader2 className="animate-spin text-ink-soft" size={24} />
              </div>
            ) : carrierPerfData.length > 0 ? (
              <div className="space-y-3">
                {carrierPerfData.slice(0, 6).map((cp: CarrierPerf, i: number) => (
                  <div key={i} className="flex items-center space-x-3">
                    <span className="text-xs text-ink-soft w-28 truncate font-medium">{cp.carrierName}</span>
                    <div className="flex-1">
                      <div className="flex items-center gap-2">
                        <div className="flex-1 bg-surface-2 rounded-full h-4 overflow-hidden">
                          <div
                            className={`h-full rounded-full transition-all duration-500 ${
                              cp.onTimeRate >= 90 ? 'bg-success' :
                              cp.onTimeRate >= 70 ? 'bg-warning' : 'bg-danger'
                            }`}
                            style={{ width: `${cp.onTimeRate || 0}%` }}
                          />
                        </div>
                        <span className="text-[10px] font-medium text-ink-soft w-12 text-right">
                          {cp.onTimeRate?.toFixed(0)}%
                        </span>
                      </div>
                      <div className="flex items-center gap-3 mt-0.5">
                        <span className="text-[10px] text-ink-soft">{cp.totalShipments} exp.</span>
                        <span className="text-[10px] text-ink-soft">
                          {cp.avgCost != null ? formatNumber(cp.avgCost, { minimumFractionDigits: 0 }) : ''} €/exp.
                        </span>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <div className="h-48 flex flex-col items-center justify-center text-ink-soft">
                <BarChart3 size={32} className="mb-2" />
                <p className="text-sm">Aucune donnée de performance</p>
              </div>
            )}
          </div>
        </div>

        {/* ROI Calculator */}
        <div className="bg-surface rounded-2xl border border-line p-6 mb-6">
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
        <div className="bg-surface rounded-2xl border border-line overflow-hidden mb-6">
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
