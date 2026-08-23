import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, Loader2, Bell, BellOff } from 'lucide-react';
import { mobileApi } from '../lib/api';
import { useAuthStore } from '../stores/auth';

const MANAGER_ROLES = ['OWNER', 'ADMIN', 'MANAGER'];

interface NotificationRule {
  id: string;
  name: string;
  eventType: string;
  active: boolean;
}

const EVENT_LABEL: Record<string, string> = {
  SHIPMENT_STATUS_CHANGE: 'Changement de statut expédition',
  QUOTE_RECEIVED: 'Nouveau devis',
  PROVIDER_DOWN: 'Fournisseur indisponible',
};

const NotificationRules = () => {
  const navigate = useNavigate();
  const role = useAuthStore((s) => s.user?.role);
  const canView = role ? MANAGER_ROLES.includes(role) : false;

  const { data = [], isLoading, isError } = useQuery({
    queryKey: ['mobile-notification-rules'],
    queryFn: async () => {
      const res = await mobileApi.notificationRules.list();
      return (Array.isArray(res.data) ? res.data : res.data?.content || []) as NotificationRule[];
    },
    enabled: canView,
  });

  return (
    <>
      <div className="header-bar row">
        <button onClick={() => navigate(-1)} style={{ background: 'none', border: 'none', cursor: 'pointer' }}>
          <ArrowLeft size={20} />
        </button>
        <h1 className="title" style={{ margin: 0, fontSize: 18 }}>Règles de notification</h1>
      </div>

      <div className="stack" style={{ marginTop: 12 }}>
        {!canView && <div className="empty-state">Réservé aux managers, administrateurs et propriétaires.</div>}
        {canView && isLoading && (
          <div className="center-screen" style={{ minHeight: 200 }}>
            <Loader2 className="spin" color="rgb(var(--c-ink-soft))" />
          </div>
        )}
        {canView && isError && <p className="error-text">Impossible de charger les règles.</p>}
        {canView && !isLoading && data.length === 0 && <div className="empty-state">Aucune règle configurée.</div>}

        {canView && data.map((rule) => (
          <div key={rule.id} className="card row-between">
            <div>
              <p style={{ fontWeight: 700, margin: '0 0 4px' }}>{rule.name}</p>
              <p className="text-sm text-soft" style={{ margin: 0 }}>{EVENT_LABEL[rule.eventType] || rule.eventType}</p>
            </div>
            {rule.active ? (
              <Bell size={18} color="rgb(var(--c-accent))" />
            ) : (
              <BellOff size={18} color="rgb(var(--c-ink-soft))" />
            )}
          </div>
        ))}
      </div>
    </>
  );
};

export default NotificationRules;
