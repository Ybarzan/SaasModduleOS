import { useQuery } from '@tanstack/react-query';
import { Target, Loader2, AlertCircle, TrendingUp, Info } from 'lucide-react';
import { incokalkAPI } from '../lib/api';
import Card from '../components/ui/Card';
import PageReveal, { PageRevealItem } from '../components/ui/PageReveal';
import { formatNumber } from '../lib/formatNumber';

interface SigmaResult {
  opportunities: number;
  defects: number;
  yieldPct: number;
  dpmo: number;
  sigma: number | null;
}

interface Ctq {
  key: string;
  label: string;
  description: string;
  result: SigmaResult;
}

interface QualityReport {
  characteristics: Ctq[];
  overall: SigmaResult;
}

const sigmaTone = (sigma: number | null | undefined) => {
  if (sigma == null) return { text: 'text-ink-soft', bg: 'bg-surface-2', ring: 'border-line' };
  if (sigma >= 4.5) return { text: 'text-success', bg: 'bg-success/10', ring: 'border-success/30' };
  if (sigma >= 3) return { text: 'text-warning', bg: 'bg-warning/10', ring: 'border-warning/30' };
  return { text: 'text-danger', bg: 'bg-danger/10', ring: 'border-danger/30' };
};

const formatSigma = (sigma: number | null | undefined) => (sigma == null ? '—' : sigma.toFixed(2) + 'σ');
const formatDpmo = (r: SigmaResult) => (r.opportunities === 0 ? '—' : formatNumber(Math.round(r.dpmo)));
const formatYield = (r: SigmaResult) => (r.opportunities === 0 ? '—' : r.yieldPct.toFixed(1) + ' %');

const QualityDashboard = () => {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['quality-metrics'],
    queryFn: async () => {
      const res = await incokalkAPI.quality.metrics();
      return res.data as QualityReport;
    },
  });

  if (isLoading) {
    return (
      <div className="min-h-[60vh] flex items-center justify-center">
        <Loader2 size={28} className="animate-spin text-ink-soft" />
      </div>
    );
  }

  if (isError || !data) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-16 text-center">
        <AlertCircle size={32} className="mx-auto mb-3 text-danger" />
        <p className="text-ink-soft">Impossible de charger les indicateurs qualité.</p>
      </div>
    );
  }

  const overallTone = sigmaTone(data.overall.sigma);

  return (
    <div className="max-w-6xl mx-auto px-4 py-8">
      <PageReveal>
        <PageRevealItem className="flex items-start gap-3 mb-8">
          <div className="w-11 h-11 rounded-none bg-accent-soft flex items-center justify-center flex-shrink-0">
            <Target size={22} className="text-accent" />
          </div>
          <div>
            <h1 className="text-2xl font-bold text-ink">
              <span className="text-accent font-normal" aria-hidden="true">:: </span>
              Qualité Six Sigma
            </h1>
            <p className="text-ink-soft mt-1 max-w-2xl">
              Niveau sigma, DPMO et rendement calculés en direct sur vos données opérationnelles —
              expéditions, déclarations douanières, réceptions et facturation.
            </p>
          </div>
        </PageRevealItem>

        {/* Overall hero */}
        <PageRevealItem>
          <Card variant="flat" className={`relative mb-8 border ${overallTone.ring} ${overallTone.bg}`} hover={false}>
            <span className="hud-corner hud-corner-tl" aria-hidden="true" />
            <span className="hud-corner hud-corner-tr" aria-hidden="true" />
            <span className="hud-corner hud-corner-bl" aria-hidden="true" />
            <span className="hud-corner hud-corner-br" aria-hidden="true" />
            <div className="flex flex-wrap items-center justify-between gap-6 px-2 py-2">
              <div>
                <p className="text-xs font-medium uppercase tracking-wider text-ink-soft mb-1">
                  Niveau sigma global
                </p>
                <p className={`text-5xl font-bold tabular-nums ${overallTone.text}`}>
                  {formatSigma(data.overall.sigma)}
                </p>
              </div>
              <div className="flex gap-8">
                <div>
                  <p className="text-xs font-medium uppercase tracking-wider text-ink-soft mb-1">DPMO</p>
                  <p className="text-2xl font-semibold text-ink tabular-nums">{formatDpmo(data.overall)}</p>
                </div>
                <div>
                  <p className="text-xs font-medium uppercase tracking-wider text-ink-soft mb-1">Rendement</p>
                  <p className="text-2xl font-semibold text-ink tabular-nums">{formatYield(data.overall)}</p>
                </div>
                <div>
                  <p className="text-xs font-medium uppercase tracking-wider text-ink-soft mb-1">Occasions mesurées</p>
                  <p className="text-2xl font-semibold text-ink tabular-nums">
                    {formatNumber(data.overall.opportunities)}
                  </p>
                </div>
              </div>
            </div>
          </Card>
        </PageRevealItem>

        {/* CTQ grid */}
        <PageRevealItem>
          <h2 className="text-sm font-semibold uppercase tracking-wider text-ink-soft mb-4">
            Caractéristiques critiques qualité (CTQ)
          </h2>
        </PageRevealItem>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-5 mb-8">
          {data.characteristics.map((ctq) => {
            const tone = sigmaTone(ctq.result.sigma);
            const hasData = ctq.result.opportunities > 0;
            return (
              <PageRevealItem key={ctq.key}>
                <Card className="h-full flex flex-col">
                  <div className="flex items-start justify-between gap-3 mb-2">
                    <h3 className="font-semibold text-ink">{ctq.label}</h3>
                    <span
                      className={`px-2.5 py-1 rounded-full text-sm font-bold tabular-nums ${tone.bg} ${tone.text}`}
                    >
                      {formatSigma(ctq.result.sigma)}
                    </span>
                  </div>
                  <p className="text-xs text-ink-soft mb-4">{ctq.description}</p>

                  {hasData ? (
                    <div className="grid grid-cols-3 gap-3 mt-auto pt-3 border-t border-line">
                      <div>
                        <p className="text-[10px] uppercase tracking-wider text-ink-soft">DPMO</p>
                        <p className="text-sm font-semibold text-ink tabular-nums">{formatDpmo(ctq.result)}</p>
                      </div>
                      <div>
                        <p className="text-[10px] uppercase tracking-wider text-ink-soft">Rendement</p>
                        <p className="text-sm font-semibold text-ink tabular-nums">{formatYield(ctq.result)}</p>
                      </div>
                      <div>
                        <p className="text-[10px] uppercase tracking-wider text-ink-soft">Échantillon</p>
                        <p className="text-sm font-semibold text-ink tabular-nums">
                          {ctq.result.defects} / {ctq.result.opportunities}
                        </p>
                      </div>
                    </div>
                  ) : (
                    <div className="flex items-center gap-2 mt-auto pt-3 border-t border-line text-xs text-ink-soft">
                      <Info size={13} />
                      Pas encore assez de données pour ce calcul
                    </div>
                  )}
                </Card>
              </PageRevealItem>
            );
          })}
        </div>

        {/* Methodology note */}
        <PageRevealItem>
          <div className="flex items-start gap-3 text-xs text-ink-soft bg-surface-2 rounded-none p-4">
            <TrendingUp size={15} className="flex-shrink-0 mt-0.5" />
            <p>
              Le niveau sigma est calculé selon la méthode Six Sigma long terme (décalage de 1,5σ) à
              partir du rendement observé sur chaque caractéristique. Chaque carte se met à jour au fur
              et à mesure que vos expéditions, déclarations, réceptions et factures avancent dans leur
              cycle de vie — aucune valeur n'est simulée.
            </p>
          </div>
        </PageRevealItem>
      </PageReveal>
    </div>
  );
};

export default QualityDashboard;
