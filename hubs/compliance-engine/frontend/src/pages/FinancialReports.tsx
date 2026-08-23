import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  TrendingUp,
  TrendingDown,
  Euro,
  Truck,
  Route,
  Package,
  BarChart3,
  Loader2,
} from 'lucide-react';
import { incokalkAPI } from '../lib/api';

interface DashboardData {
  totalRevenue: number;
  totalCost: number;
  totalMargin: number;
  marginPercent: number;
  shipmentCount: number;
  avgRevenuePerShipment: number;
  avgMarginPerShipment: number;
}

interface CarrierData {
  carrier: string;
  revenue: number;
  cost: number;
  margin: number;
  shipments: number;
}

interface LaneData {
  lane: string;
  revenue: number;
  cost: number;
  margin: number;
  shipments: number;
}

interface ShipmentFinancial {
  id: string;
  reference: string;
  client: string;
  lane: string;
  carrier: string;
  mode: string;
  revenue: number;
  cost: number;
  margin: number;
}

type Tab = 'carrier' | 'lane' | 'shipment';

const TABS: { key: Tab; label: string; icon: typeof Truck }[] = [
  { key: 'carrier', label: 'Par transporteur', icon: Truck },
  { key: 'lane', label: 'Par lane', icon: Route },
  { key: 'shipment', label: 'Par expédition', icon: Package },
];

const formatEUR = (value: number) =>
  new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' }).format(value);

const formatPct = (value: number) =>
  new Intl.NumberFormat('fr-FR', { style: 'percent', minimumFractionDigits: 1, maximumFractionDigits: 1 }).format(value / 100);

const FinancialReports = () => {
  const [activeTab, setActiveTab] = useState<Tab>('carrier');

  const { data: dashboard, isLoading: dashLoading } = useQuery<DashboardData>({
    queryKey: ['financials-dashboard'],
    queryFn: async () => {
      const res = await incokalkAPI.financials.dashboard();
      return res.data;
    },
  });

  const { data: carrierData = [], isLoading: carrierLoading } = useQuery<CarrierData[]>({
    queryKey: ['financials-by-carrier'],
    queryFn: async () => {
      const res = await incokalkAPI.financials.byCarrier();
      return res.data;
    },
    enabled: activeTab === 'carrier',
  });

  const { data: laneData = [], isLoading: laneLoading } = useQuery<LaneData[]>({
    queryKey: ['financials-by-lane'],
    queryFn: async () => {
      const res = await incokalkAPI.financials.byLane();
      return res.data;
    },
    enabled: activeTab === 'lane',
  });

  const { data: shipmentData = [], isLoading: shipmentLoading } = useQuery<ShipmentFinancial[]>({
    queryKey: ['financials-shipments'],
    queryFn: async () => {
      const res = await incokalkAPI.financials.listShipments();
      return res.data;
    },
    enabled: activeTab === 'shipment',
  });

  const currentLoading =
    activeTab === 'carrier' ? carrierLoading : activeTab === 'lane' ? laneLoading : shipmentLoading;

  const sortedCarriers = [...carrierData].sort((a, b) => b.margin - a.margin);
  const sortedLanes = [...laneData].sort((a, b) => b.margin - a.margin);

  const maxRevenue = Math.max(
    ...carrierData.map((c) => c.revenue),
    ...laneData.map((l) => l.revenue),
    1,
  );

  const renderMarginBar = (margin: number, maxVal: number) => {
    const pct = Math.abs(margin) / (maxVal || 1);
    const isPositive = margin >= 0;
    return (
      <div className="flex items-center gap-2">
        <div className="w-24 h-2 bg-surface-2 rounded-full overflow-hidden">
          <div
            className={`h-full rounded-full ${isPositive ? 'bg-success' : 'bg-danger'}`}
            style={{ width: `${Math.min(pct * 100, 100)}%` }}
          />
        </div>
        <span className={`text-sm font-medium ${isPositive ? 'text-success' : 'text-danger'}`}>
          {formatEUR(margin)}
        </span>
      </div>
    );
  };

  return (
    <div className="max-w-6xl mx-auto px-4 py-8">
      {/* Header */}
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-ink">Reporting financier</h1>
        <p className="text-ink-soft mt-1">P&L par expédition, transporteur et lane</p>
      </div>

      {/* Dashboard cards */}
      {dashLoading ? (
        <div className="flex items-center justify-center py-12 text-ink-soft">
          <Loader2 size={24} className="animate-spin mr-2" />
          Chargement...
        </div>
      ) : dashboard ? (
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-4 mb-8">
          {/* Total Revenue */}
          <div className="bg-surface rounded-xl border border-line p-5">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-lg bg-accent-soft flex items-center justify-center">
                <Euro size={20} className="text-accent" />
              </div>
              <div>
                <p className="text-sm text-ink-soft">Chiffre d'affaires total</p>
                <p className="text-xl font-bold text-ink">{formatEUR(dashboard.totalRevenue)}</p>
              </div>
            </div>
          </div>

          {/* Total Cost */}
          <div className="bg-surface rounded-xl border border-line p-5">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-lg bg-warning/10 flex items-center justify-center">
                <TrendingDown size={20} className="text-warning" />
              </div>
              <div>
                <p className="text-sm text-ink-soft">Coûts totaux</p>
                <p className="text-xl font-bold text-ink">{formatEUR(dashboard.totalCost)}</p>
              </div>
            </div>
          </div>

          {/* Total Margin */}
          <div className="bg-surface rounded-xl border border-line p-5">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-lg bg-success/10 flex items-center justify-center">
                <TrendingUp size={20} className={dashboard.totalMargin >= 0 ? 'text-success' : 'text-danger'} />
              </div>
              <div>
                <p className="text-sm text-ink-soft">Marge brute</p>
                <p className={`text-xl font-bold ${dashboard.totalMargin >= 0 ? 'text-success' : 'text-danger'}`}>
                  {formatEUR(dashboard.totalMargin)}
                </p>
              </div>
            </div>
          </div>

          {/* Margin % */}
          <div className="bg-surface rounded-xl border border-line p-5">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-lg bg-accent/10 flex items-center justify-center">
                <BarChart3 size={20} className="text-accent" />
              </div>
              <div>
                <p className="text-sm text-ink-soft">Taux de marge</p>
                <p className="text-xl font-bold text-ink">{formatPct(dashboard.marginPercent)}</p>
              </div>
            </div>
          </div>

          {/* Shipment count */}
          <div className="bg-surface rounded-xl border border-line p-5">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-lg bg-accent/10 flex items-center justify-center">
                <Package size={20} className="text-accent" />
              </div>
              <div>
                <p className="text-sm text-ink-soft">Nombre d'expéditions</p>
                <p className="text-xl font-bold text-ink">{dashboard.shipmentCount}</p>
              </div>
            </div>
          </div>

          {/* Avg margin per shipment */}
          <div className="bg-surface rounded-xl border border-line p-5">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-lg bg-success/10 flex items-center justify-center">
                <Euro size={20} className="text-success" />
              </div>
              <div>
                <p className="text-sm text-ink-soft">Marge moyenne/expédition</p>
                <p className="text-xl font-bold text-ink">{formatEUR(dashboard.avgMarginPerShipment)}</p>
              </div>
            </div>
          </div>
        </div>
      ) : null}

      {/* Tabs */}
      <div className="flex border-b border-line mb-6 overflow-x-auto">
        {TABS.map((tab) => {
          const Icon = tab.icon;
          const isActive = activeTab === tab.key;
          return (
            <button
              key={tab.key}
              onClick={() => setActiveTab(tab.key)}
              className={`flex items-center gap-2 px-4 py-3 text-sm font-medium border-b-2 transition-colors whitespace-nowrap ${
                isActive
                  ? 'border-accent/40 text-accent'
                  : 'border-transparent text-ink-soft hover:text-ink hover:border-line'
              }`}
            >
              <Icon size={16} />
              {tab.label}
            </button>
          );
        })}
      </div>

      {/* Tab content */}
      {currentLoading ? (
        <div className="flex items-center justify-center py-12 text-ink-soft">
          <Loader2 size={24} className="animate-spin mr-2" />
          Chargement...
        </div>
      ) : activeTab === 'carrier' ? (
        /* Par transporteur */
        <div className="bg-surface rounded-xl border border-line overflow-hidden">
          <div className="px-6 py-4 border-b border-line">
            <h2 className="text-lg font-semibold text-ink">Performance par transporteur</h2>
          </div>
          {sortedCarriers.length === 0 ? (
            <div className="px-6 py-12 text-center text-ink-soft">
              <Truck size={32} className="mx-auto mb-3 text-ink-soft" />
              <p>Aucune donnée transporteur</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="bg-bg border-b border-line">
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Transporteur</th>
                    <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Chiffre d'affaires</th>
                    <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Coûts</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Marge</th>
                    <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Marge %</th>
                    <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Nb expéditions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-line">
                  {sortedCarriers.map((row) => (
                    <tr key={row.carrier} className="hover:bg-bg transition-colors">
                      <td className="px-6 py-4 text-sm font-medium text-ink">{row.carrier}</td>
                      <td className="px-6 py-4 text-sm text-ink text-right">{formatEUR(row.revenue)}</td>
                      <td className="px-6 py-4 text-sm text-ink text-right">{formatEUR(row.cost)}</td>
                      <td className="px-6 py-4">{renderMarginBar(row.margin, maxRevenue)}</td>
                      <td className="px-6 py-4 text-sm text-right">
                        <span className={row.margin >= 0 ? 'text-success' : 'text-danger'}>
                          {formatPct(row.revenue > 0 ? (row.margin / row.revenue) * 100 : 0)}
                        </span>
                      </td>
                      <td className="px-6 py-4 text-sm text-ink text-right">{row.shipments}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      ) : activeTab === 'lane' ? (
        /* Par lane */
        <div className="bg-surface rounded-xl border border-line overflow-hidden">
          <div className="px-6 py-4 border-b border-line">
            <h2 className="text-lg font-semibold text-ink">Performance par lane</h2>
          </div>
          {sortedLanes.length === 0 ? (
            <div className="px-6 py-12 text-center text-ink-soft">
              <Route size={32} className="mx-auto mb-3 text-ink-soft" />
              <p>Aucune donnée lane</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="bg-bg border-b border-line">
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Lane (Origin → Destination)</th>
                    <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Chiffre d'affaires</th>
                    <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Coûts</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Marge</th>
                    <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Marge %</th>
                    <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Nb expéditions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-line">
                  {sortedLanes.map((row) => (
                    <tr key={row.lane} className="hover:bg-bg transition-colors">
                      <td className="px-6 py-4 text-sm font-medium text-ink">{row.lane}</td>
                      <td className="px-6 py-4 text-sm text-ink text-right">{formatEUR(row.revenue)}</td>
                      <td className="px-6 py-4 text-sm text-ink text-right">{formatEUR(row.cost)}</td>
                      <td className="px-6 py-4">{renderMarginBar(row.margin, maxRevenue)}</td>
                      <td className="px-6 py-4 text-sm text-right">
                        <span className={row.margin >= 0 ? 'text-success' : 'text-danger'}>
                          {formatPct(row.revenue > 0 ? (row.margin / row.revenue) * 100 : 0)}
                        </span>
                      </td>
                      <td className="px-6 py-4 text-sm text-ink text-right">{row.shipments}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      ) : (
        /* Par expédition */
        <div className="bg-surface rounded-xl border border-line overflow-hidden">
          <div className="px-6 py-4 border-b border-line">
            <h2 className="text-lg font-semibold text-ink">Détail par expédition</h2>
          </div>
          {shipmentData.length === 0 ? (
            <div className="px-6 py-12 text-center text-ink-soft">
              <Package size={32} className="mx-auto mb-3 text-ink-soft" />
              <p>Aucune donnée d'expédition</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="bg-bg border-b border-line">
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Expédition</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Client</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Lane</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Transporteur</th>
                    <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Mode</th>
                    <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Revenu</th>
                    <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Coûts</th>
                    <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Marge</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-line">
                  {shipmentData.map((row) => (
                    <tr key={row.id} className="hover:bg-bg transition-colors">
                      <td className="px-6 py-4 text-sm font-medium text-accent">{row.reference}</td>
                      <td className="px-6 py-4 text-sm text-ink">{row.client}</td>
                      <td className="px-6 py-4 text-sm text-ink">{row.lane}</td>
                      <td className="px-6 py-4 text-sm text-ink">{row.carrier}</td>
                      <td className="px-6 py-4">
                        <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-surface-2 text-ink">
                          {row.mode}
                        </span>
                      </td>
                      <td className="px-6 py-4 text-sm text-ink text-right">{formatEUR(row.revenue)}</td>
                      <td className="px-6 py-4 text-sm text-ink text-right">{formatEUR(row.cost)}</td>
                      <td className="px-6 py-4 text-sm font-medium text-right">
                        <span className={row.margin >= 0 ? 'text-success' : 'text-danger'}>
                          {formatEUR(row.margin)}
                        </span>
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
  );
};

export default FinancialReports;
