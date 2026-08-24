import { useQuery } from '@tanstack/react-query';
import {
  FileText,
  Leaf,
  BarChart3,
  TrendingDown,
  CheckCircle,
  AlertTriangle,
  Clock,
  RefreshCw,
  Loader2,
} from 'lucide-react';
import { incokalkAPI } from '../lib/api';
import { useAuthStore } from '../stores/auth';

interface CsrdData {
  companyId: string;
  reportPeriod: string;
  totalEmissionsCO2: number;
  scope1: number;
  scope2: number;
  scope3: number;
  emissionsByLane: Array<{
    lane: string;
    co2Tonnes: number;
    percentage: number;
  }>;
  offsetCreditsPurchased: number;
  offsetCreditsRetired: number;
  netEmissions: number;
  esrsE1Compliant: boolean;
  recommendations: string[];
  generatedAt: string;
}

const formatCO2 = (value: number) =>
  new Intl.NumberFormat('fr-FR', { minimumFractionDigits: 1, maximumFractionDigits: 1 }).format(value) + ' t';

const formatPct = (value: number) =>
  new Intl.NumberFormat('fr-FR', { style: 'percent', minimumFractionDigits: 1, maximumFractionDigits: 1 }).format(value / 100);

const CsrdReport = () => {
  const canEdit = useAuthStore((s) => s.canEdit);

  const { data: report, isLoading, isError, refetch, isFetching } = useQuery<CsrdData>({
    queryKey: ['csrd-report'],
    queryFn: async () => {
      const res = await incokalkAPI.csrd.report();
      return res.data;
    },
  });

  const maxScope = Math.max(
    report?.scope1 ?? 0,
    report?.scope2 ?? 0,
    report?.scope3 ?? 0,
    1,
  );

  return (
    <div className="max-w-6xl mx-auto px-4 py-8">
      {/* Header */}
      <div className="mb-8 flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-ink">
            <span className="text-accent font-normal" aria-hidden="true">:: </span>
            Reporting CSRD
          </h1>
          <p className="text-ink-soft mt-1">Conformité CSRD & Taxonomie EU P4.26</p>
        </div>
        {canEdit() && (
          <button
            onClick={() => refetch()}
            disabled={isFetching}
            className="flex items-center gap-2 px-4 py-2 bg-accent text-white text-sm font-medium rounded-none hover:bg-accent-strong disabled:opacity-50 transition-colors"
          >
            <RefreshCw size={16} className={isFetching ? 'animate-spin' : ''} />
            Actualiser
          </button>
        )}
      </div>

      {isLoading ? (
        <div className="flex items-center justify-center py-20 text-ink-soft">
          <Loader2 size={32} className="animate-spin mr-3" />
          Chargement du rapport CSRD...
        </div>
      ) : isError ? (
        <div className="bg-danger/10 border border-danger/40 rounded-none p-8 text-center">
          <AlertTriangle size={40} className="mx-auto mb-3 text-danger" />
          <p className="text-danger font-medium">Erreur lors du chargement du rapport</p>
          <button
            onClick={() => refetch()}
            className="mt-4 px-4 py-2 bg-danger text-white text-sm font-medium rounded-none hover:bg-danger/90 transition-colors"
          >
            Réessayer
          </button>
        </div>
      ) : report ? (
        <>
          {/* Report meta */}
          <div className="bg-surface rounded-none border border-line p-5 mb-6">
            <div className="flex items-center gap-3 mb-4">
              <div className="w-10 h-10 rounded-none bg-accent/10 flex items-center justify-center">
                <FileText size={20} className="text-accent" />
              </div>
              <div>
                <p className="text-lg font-semibold text-ink">{report.reportPeriod}</p>
                <p className="text-sm text-ink-soft">Société: {report.companyId}</p>
              </div>
            </div>
            <div className="flex items-center gap-2">
              <Clock size={14} className="text-ink-soft" />
              <span className="text-xs text-ink-soft">
                Généré le {new Date(report.generatedAt).toLocaleString('fr-FR')}
              </span>
            </div>
          </div>

          {/* ESRS E1 badge */}
          <div className={`mb-6 p-4 rounded-none border ${
            report.esrsE1Compliant
              ? 'bg-success/10 border-success/40'
              : 'bg-warning/10 border-warning/40'
          }`}>
            <div className="flex items-center gap-3">
              {report.esrsE1Compliant ? (
                <CheckCircle size={24} className="text-success" />
              ) : (
                <AlertTriangle size={24} className="text-warning" />
              )}
              <div>
                <p className={`text-sm font-semibold ${
                  report.esrsE1Compliant ? 'text-success' : 'text-warning'
                }`}>
                  ESRS E1 — {report.esrsE1Compliant ? 'Conforme' : 'Non conforme'}
                </p>
                <p className="text-xs text-ink-soft">Conformité climatique selon la norme ESRS E1</p>
              </div>
            </div>
          </div>

          {/* Total CO2 */}
          <div className="relative bg-surface rounded-none border border-line p-6 mb-6">
            <span className="hud-corner hud-corner-tl" aria-hidden="true" />
            <span className="hud-corner hud-corner-tr" aria-hidden="true" />
            <span className="hud-corner hud-corner-bl" aria-hidden="true" />
            <span className="hud-corner hud-corner-br" aria-hidden="true" />
            <div className="flex items-center gap-3 mb-4">
              <div className="w-10 h-10 rounded-none bg-success/10 flex items-center justify-center">
                <Leaf size={20} className="text-success" />
              </div>
              <h2 className="text-lg font-semibold text-ink">Émissions CO₂</h2>
            </div>
            <p className="text-3xl font-bold text-ink mb-6">{formatCO2(report.totalEmissionsCO2)}</p>

            {/* Scope breakdown */}
            <div className="space-y-4">
              <div>
                <div className="flex items-center justify-between text-sm mb-1">
                  <span className="font-medium text-ink">Scope 1 — Émissions directes</span>
                  <span className="text-ink-soft">{formatCO2(report.scope1)}</span>
                </div>
                <div className="w-full h-3 bg-surface-2 rounded-full overflow-hidden">
                  <div
                    className="h-full bg-accent rounded-full transition-all"
                    style={{ width: `${(report.scope1 / maxScope) * 100}%` }}
                  />
                </div>
              </div>
              <div>
                <div className="flex items-center justify-between text-sm mb-1">
                  <span className="font-medium text-ink">Scope 2 — Énergie achetée</span>
                  <span className="text-ink-soft">{formatCO2(report.scope2)}</span>
                </div>
                <div className="w-full h-3 bg-surface-2 rounded-full overflow-hidden">
                  <div
                    className="h-full bg-warning rounded-full transition-all"
                    style={{ width: `${(report.scope2 / maxScope) * 100}%` }}
                  />
                </div>
              </div>
              <div>
                <div className="flex items-center justify-between text-sm mb-1">
                  <span className="font-medium text-ink">Scope 3 — Chaîne de valeur</span>
                  <span className="text-ink-soft">{formatCO2(report.scope3)}</span>
                </div>
                <div className="w-full h-3 bg-surface-2 rounded-full overflow-hidden">
                  <div
                    className="h-full bg-accent rounded-full transition-all"
                    style={{ width: `${(report.scope3 / maxScope) * 100}%` }}
                  />
                </div>
              </div>
            </div>
          </div>

          {/* Emissions by lane */}
          <div className="bg-surface rounded-none border border-line overflow-hidden mb-6">
            <div className="px-6 py-4 border-b border-line">
              <h2 className="text-lg font-semibold text-ink">Émissions par lane</h2>
            </div>
            {report.emissionsByLane.length === 0 ? (
              <div className="px-6 py-12 text-center text-ink-soft">
                <BarChart3 size={32} className="mx-auto mb-3 text-ink-soft" />
                <p>Aucune donnée par lane</p>
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full">
                  <thead>
                    <tr className="bg-bg border-b border-line">
                      <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Lane</th>
                      <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">CO₂ (tonnes)</th>
                      <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Part</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-line">
                    {report.emissionsByLane.map((row, idx) => (
                      <tr key={idx} className="hover:bg-bg transition-colors">
                        <td className="px-6 py-4 text-sm font-medium text-ink">{row.lane}</td>
                        <td className="px-6 py-4 text-sm text-ink text-right">{formatCO2(row.co2Tonnes)}</td>
                        <td className="px-6 py-4 text-sm text-ink text-right">{formatPct(row.percentage)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          {/* Offsets & net emissions */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-6">
            <div className="bg-surface rounded-none border border-line p-5">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-none bg-success/10 flex items-center justify-center">
                  <Leaf size={20} className="text-success" />
                </div>
                <div>
                  <p className="text-sm text-ink-soft">Crédits achetés</p>
                  <p className="text-xl font-bold text-ink">{report.offsetCreditsPurchased}</p>
                </div>
              </div>
            </div>
            <div className="bg-surface rounded-none border border-line p-5">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-none bg-accent-soft flex items-center justify-center">
                  <CheckCircle size={20} className="text-accent" />
                </div>
                <div>
                  <p className="text-sm text-ink-soft">Crédits retirés</p>
                  <p className="text-xl font-bold text-ink">{report.offsetCreditsRetired}</p>
                </div>
              </div>
            </div>
            <div className="bg-surface rounded-none border border-line p-5">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-none bg-warning/10 flex items-center justify-center">
                  <TrendingDown size={20} className="text-warning" />
                </div>
                <div>
                  <p className="text-sm text-ink-soft">Émissions nettes</p>
                  <p className="text-xl font-bold text-ink">{formatCO2(report.netEmissions)}</p>
                </div>
              </div>
            </div>
          </div>

          {/* Recommendations */}
          <div className="bg-surface rounded-none border border-line p-6">
            <h2 className="text-lg font-semibold text-ink mb-4">Recommandations</h2>
            {report.recommendations.length === 0 ? (
              <p className="text-ink-soft text-sm">Aucune recommandation</p>
            ) : (
              <ul className="space-y-3">
                {report.recommendations.map((rec, idx) => (
                  <li key={idx} className="flex items-start gap-2 text-sm text-ink">
                    <span className="mt-0.5 w-5 h-5 rounded-full bg-accent-soft text-accent flex items-center justify-center flex-shrink-0 text-xs font-bold">
                      {idx + 1}
                    </span>
                    {rec}
                  </li>
                ))}
              </ul>
            )}
          </div>
        </>
      ) : null}
    </div>
  );
};

export default CsrdReport;
