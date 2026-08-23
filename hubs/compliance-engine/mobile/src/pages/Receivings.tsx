import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, Loader2 } from 'lucide-react';
import { mobileApi } from '../lib/api';

interface ReceivingOrder {
  id: string;
  orderNumber: string;
  status: 'DRAFT' | 'RECEIVING' | 'COMPLETED' | 'CANCELLED';
}

const STATUS_BADGE: Record<string, string> = {
  DRAFT: 'badge-accent',
  RECEIVING: 'badge-warning',
  COMPLETED: 'badge-accent',
  CANCELLED: 'badge-danger',
};

const STATUS_LABEL: Record<string, string> = {
  DRAFT: 'Brouillon',
  RECEIVING: 'En réception',
  COMPLETED: 'Terminé',
  CANCELLED: 'Annulé',
};

const Receivings = () => {
  const navigate = useNavigate();

  const { data = [], isLoading, isError } = useQuery({
    queryKey: ['mobile-receivings'],
    queryFn: async () => (await mobileApi.receivings.list()).data as ReceivingOrder[],
  });

  return (
    <>
      <div className="header-bar row">
        <button onClick={() => navigate(-1)} style={{ background: 'none', border: 'none', cursor: 'pointer' }}>
          <ArrowLeft size={20} />
        </button>
        <h1 className="title" style={{ margin: 0, fontSize: 18 }}>Bons de réception</h1>
      </div>

      <div className="stack" style={{ marginTop: 12 }}>
        {isLoading && (
          <div className="center-screen" style={{ minHeight: 200 }}>
            <Loader2 className="spin" color="rgb(var(--c-ink-soft))" />
          </div>
        )}
        {isError && <p className="error-text">Impossible de charger les bons de réception.</p>}
        {!isLoading && data.length === 0 && <div className="empty-state">Aucun bon de réception.</div>}

        {data.map((o) => (
          <div key={o.id} className="card row-between">
            <span style={{ fontWeight: 700 }}>{o.orderNumber}</span>
            <span className={`badge ${STATUS_BADGE[o.status] || 'badge-accent'}`}>
              {STATUS_LABEL[o.status] || o.status}
            </span>
          </div>
        ))}
      </div>
    </>
  );
};

export default Receivings;
