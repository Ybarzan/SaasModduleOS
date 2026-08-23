import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, Loader2 } from 'lucide-react';
import { mobileApi } from '../lib/api';
import { useAuthStore } from '../stores/auth';

const MANAGER_ROLES = ['OWNER', 'ADMIN', 'MANAGER'];

interface SigmaResult {
  opportunities: number;
  yieldPct: number;
  dpmo: number;
  sigma: number | null;
}

interface Ctq {
  key: string;
  label: string;
  result: SigmaResult;
}

interface QualityReport {
  characteristics: Ctq[];
  overall: SigmaResult;
}

function formatSigma(sigma: number | null): string {
  return sigma == null ? '—' : `${sigma.toFixed(2)}σ`;
}

function sigmaBadge(sigma: number | null): string {
  if (sigma == null) return 'badge-accent';
  if (sigma >= 4.5) return 'badge-accent';
  if (sigma >= 3) return 'badge-warning';
  return 'badge-danger';
}

const Quality = () => {
  const navigate = useNavigate();
  const role = useAuthStore((s) => s.user?.role);
  const canView = role ? MANAGER_ROLES.includes(role) : false;

  const { data, isLoading, isError } = useQuery({
    queryKey: ['mobile-quality-metrics'],
    queryFn: async () => (await mobileApi.quality.metrics()).data as QualityReport,
    enabled: canView,
  });

  return (
    <>
      <div className="header-bar row">
        <button onClick={() => navigate(-1)} style={{ background: 'none', border: 'none', cursor: 'pointer' }}>
          <ArrowLeft size={20} />
        </button>
        <h1 className="title" style={{ margin: 0, fontSize: 18 }}>Qualité Six Sigma</h1>
      </div>

      <div className="stack" style={{ marginTop: 12 }}>
        {!canView && <div className="empty-state">Réservé aux managers, administrateurs et propriétaires.</div>}
        {canView && isLoading && (
          <div className="center-screen" style={{ minHeight: 200 }}>
            <Loader2 className="spin" color="rgb(var(--c-ink-soft))" />
          </div>
        )}
        {canView && isError && <p className="error-text">Impossible de charger les métriques qualité.</p>}

        {canView && data && (
          <>
            <div className="card" style={{ textAlign: 'center' }}>
              <p className="section-label">Niveau sigma global</p>
              <div className="kpi-value" style={{ fontSize: 32 }}>{formatSigma(data.overall.sigma)}</div>
              <p className="text-sm text-soft" style={{ margin: '4px 0 0' }}>
                Rendement {data.overall.yieldPct.toFixed(1)}% · {Math.round(data.overall.dpmo)} DPMO
              </p>
            </div>

            {data.characteristics.map((ctq) => (
              <div key={ctq.key} className="card row-between">
                <span style={{ fontWeight: 600 }}>{ctq.label}</span>
                <span className={`badge ${sigmaBadge(ctq.result.sigma)}`}>{formatSigma(ctq.result.sigma)}</span>
              </div>
            ))}
          </>
        )}
      </div>
    </>
  );
};

export default Quality;
