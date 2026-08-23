import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, Loader2, Mail } from 'lucide-react';
import { mobileApi } from '../lib/api';
import { useAuthStore } from '../stores/auth';

const ADMIN_ROLES = ['OWNER', 'ADMIN'];

interface EmailIntakeLog {
  id: string;
  status: string;
  message: string;
  processedCount: number;
  errorCount: number;
  startedAt: string;
}

const STATUS_BADGE: Record<string, string> = {
  SUCCESS: 'badge-accent',
  ERROR: 'badge-danger',
  RUNNING: 'badge-warning',
};

function formatDateTime(iso?: string): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('fr-FR', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' });
}

// Vue lecture seule "mes documents sont-ils bien arrivés" -- la configuration des
// boîtes email (protocole, identifiants) reste une tâche de bureau, hors mobile.
const EmailIntake = () => {
  const navigate = useNavigate();
  const role = useAuthStore((s) => s.user?.role);
  const canView = role ? ADMIN_ROLES.includes(role) : false;

  const { data = [], isLoading, isError } = useQuery({
    queryKey: ['mobile-email-intake-logs'],
    queryFn: async () => (await mobileApi.emailIntake.logs()).data as EmailIntakeLog[],
    enabled: canView,
  });

  return (
    <>
      <div className="header-bar row">
        <button onClick={() => navigate(-1)} style={{ background: 'none', border: 'none', cursor: 'pointer' }}>
          <ArrowLeft size={20} />
        </button>
        <h1 className="title" style={{ margin: 0, fontSize: 18 }}>Suivi email entrant</h1>
      </div>

      <div className="stack" style={{ marginTop: 12 }}>
        {!canView && <div className="empty-state">Réservé aux administrateurs et propriétaires.</div>}
        {canView && isLoading && (
          <div className="center-screen" style={{ minHeight: 200 }}>
            <Loader2 className="spin" color="rgb(var(--c-ink-soft))" />
          </div>
        )}
        {canView && isError && <p className="error-text">Impossible de charger le journal.</p>}
        {canView && !isLoading && data.length === 0 && <div className="empty-state">Aucune synchronisation récente.</div>}

        {canView && data.map((log) => (
          <div key={log.id} className="card">
            <div className="row-between" style={{ marginBottom: 4 }}>
              <span className="row" style={{ gap: 6 }}>
                <Mail size={14} color="rgb(var(--c-ink-soft))" />
                <span className="text-sm text-soft">{formatDateTime(log.startedAt)}</span>
              </span>
              <span className={`badge ${STATUS_BADGE[log.status] || 'badge-accent'}`}>{log.status}</span>
            </div>
            <p className="text-sm" style={{ margin: 0 }}>
              {log.processedCount} document(s) traité(s){log.errorCount > 0 ? ` · ${log.errorCount} erreur(s)` : ''}
            </p>
          </div>
        ))}
      </div>
    </>
  );
};

export default EmailIntake;
