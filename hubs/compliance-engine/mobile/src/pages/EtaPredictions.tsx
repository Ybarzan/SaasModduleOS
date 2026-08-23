import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, Loader2 } from 'lucide-react';
import { mobileApi } from '../lib/api';
import { useAuthStore } from '../stores/auth';

const MANAGER_ROLES = ['OWNER', 'ADMIN', 'MANAGER'];

interface Prediction {
  id: string;
  origin: string;
  destination: string;
  predictedArrival: string;
  confidencePercent: number;
  confidenceLevel: string;
}

const CONFIDENCE_BADGE: Record<string, string> = {
  HIGH: 'badge-accent',
  MEDIUM: 'badge-warning',
  LOW: 'badge-danger',
};

const CONFIDENCE_LABEL: Record<string, string> = {
  HIGH: 'Fiable',
  MEDIUM: 'Modérée',
  LOW: 'Faible',
};

function formatDate(iso?: string): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString('fr-FR', { day: '2-digit', month: 'short', year: 'numeric' });
}

const EtaPredictions = () => {
  const navigate = useNavigate();
  const role = useAuthStore((s) => s.user?.role);
  const canView = role ? MANAGER_ROLES.includes(role) : false;

  const { data = [], isLoading, isError } = useQuery({
    queryKey: ['mobile-eta-predictions'],
    queryFn: async () => (await mobileApi.eta.list()).data as Prediction[],
    enabled: canView,
  });

  return (
    <>
      <div className="header-bar row">
        <button onClick={() => navigate(-1)} style={{ background: 'none', border: 'none', cursor: 'pointer' }}>
          <ArrowLeft size={20} />
        </button>
        <h1 className="title" style={{ margin: 0, fontSize: 18 }}>Prédictions ETA</h1>
      </div>

      <div className="stack" style={{ marginTop: 12 }}>
        {!canView && <div className="empty-state">Réservé aux managers, administrateurs et propriétaires.</div>}
        {canView && isLoading && (
          <div className="center-screen" style={{ minHeight: 200 }}>
            <Loader2 className="spin" color="rgb(var(--c-ink-soft))" />
          </div>
        )}
        {canView && isError && <p className="error-text">Impossible de charger les prédictions ETA.</p>}
        {canView && !isLoading && data.length === 0 && <div className="empty-state">Aucune prédiction disponible.</div>}

        {canView && data.map((p) => {
          const level = p.confidenceLevel?.toUpperCase();
          return (
            <div key={p.id} className="card">
              <div className="row-between" style={{ marginBottom: 6 }}>
                <span style={{ fontWeight: 700 }}>{p.origin} → {p.destination}</span>
                <span className={`badge ${CONFIDENCE_BADGE[level] || 'badge-accent'}`}>
                  {CONFIDENCE_LABEL[level] || p.confidenceLevel} · {p.confidencePercent}%
                </span>
              </div>
              <p className="text-sm text-soft" style={{ margin: 0 }}>Arrivée prévue le {formatDate(p.predictedArrival)}</p>
            </div>
          );
        })}
      </div>
    </>
  );
};

export default EtaPredictions;
