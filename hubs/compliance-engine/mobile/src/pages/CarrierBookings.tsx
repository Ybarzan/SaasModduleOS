import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, Loader2 } from 'lucide-react';
import { mobileApi } from '../lib/api';

interface CarrierBooking {
  id: string;
  carrierReference?: string;
  carrierTrackingNumber?: string;
  carrierBookingStatus: string;
  carrier?: { name: string };
}

const STATUS_BADGE: Record<string, string> = {
  PENDING: 'badge-warning',
  SUBMITTED: 'badge-warning',
  CONFIRMED: 'badge-accent',
  COMPLETED: 'badge-accent',
  REJECTED: 'badge-danger',
  CANCELLED: 'badge-danger',
  FAILED: 'badge-danger',
};

const STATUS_LABEL: Record<string, string> = {
  PENDING: 'En attente',
  SUBMITTED: 'Soumise',
  CONFIRMED: 'Confirmée',
  COMPLETED: 'Terminée',
  REJECTED: 'Refusée',
  CANCELLED: 'Annulée',
  FAILED: 'Échec',
};

const CarrierBookings = () => {
  const navigate = useNavigate();

  const { data = [], isLoading, isError } = useQuery({
    queryKey: ['mobile-carrier-bookings'],
    queryFn: async () => {
      const res = await mobileApi.carrierBookings.list();
      return (Array.isArray(res.data) ? res.data : res.data?.content || []) as CarrierBooking[];
    },
  });

  return (
    <>
      <div className="header-bar row">
        <button onClick={() => navigate(-1)} style={{ background: 'none', border: 'none', cursor: 'pointer' }}>
          <ArrowLeft size={20} />
        </button>
        <h1 className="title" style={{ margin: 0, fontSize: 18 }}>Réservations</h1>
      </div>

      <div className="stack" style={{ marginTop: 12 }}>
        {isLoading && (
          <div className="center-screen" style={{ minHeight: 200 }}>
            <Loader2 className="spin" color="rgb(var(--c-ink-soft))" />
          </div>
        )}
        {isError && <p className="error-text">Impossible de charger les réservations.</p>}
        {!isLoading && data.length === 0 && <div className="empty-state">Aucune réservation.</div>}

        {data.map((b) => (
          <div key={b.id} className="card">
            <div className="row-between" style={{ marginBottom: 6 }}>
              <span style={{ fontWeight: 700 }}>{b.carrier?.name || 'Transporteur inconnu'}</span>
              <span className={`badge ${STATUS_BADGE[b.carrierBookingStatus] || 'badge-accent'}`}>
                {STATUS_LABEL[b.carrierBookingStatus] || b.carrierBookingStatus}
              </span>
            </div>
            <p className="text-sm text-soft" style={{ margin: 0 }}>
              Réf. {b.carrierReference || '—'}{b.carrierTrackingNumber ? ` · Suivi ${b.carrierTrackingNumber}` : ''}
            </p>
          </div>
        ))}
      </div>
    </>
  );
};

export default CarrierBookings;
