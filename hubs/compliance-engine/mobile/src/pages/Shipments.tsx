import { useState, useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { Search, Loader2, ChevronRight } from 'lucide-react';
import { mobileApi } from '../lib/api';
import { STATUS_LABELS } from '../types';
import type { RecentShipment } from '../types';

const STATUS_BADGE: Record<string, string> = {
  DRAFT: 'badge-accent',
  QUOTED: 'badge-accent',
  BOOKED: 'badge-accent',
  IN_TRANSIT: 'badge-warning',
  DELIVERED: 'badge-accent',
  CANCELLED: 'badge-danger',
};

const Shipments = () => {
  const [search, setSearch] = useState('');

  const { data = [], isLoading, isError } = useQuery({
    queryKey: ['mobile-recent-shipments'],
    queryFn: async () => (await mobileApi.recentShipments()).data as RecentShipment[],
  });

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return data;
    return data.filter(
      (s) =>
        s.order_number?.toLowerCase().includes(q) ||
        s.destination?.city?.toLowerCase().includes(q) ||
        s.destination?.country?.toLowerCase().includes(q)
    );
  }, [data, search]);

  return (
    <>
      <div className="header-bar">
        <h1 className="title" style={{ marginBottom: 12 }}>Expéditions</h1>
        <div style={{ position: 'relative' }}>
          <Search size={16} color="rgb(var(--c-ink-soft))" style={{ position: 'absolute', left: 12, top: '50%', transform: 'translateY(-50%)' }} />
          <input
            className="input"
            style={{ paddingLeft: 36 }}
            placeholder="Rechercher..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
      </div>

      <div className="stack" style={{ marginTop: 12 }}>
        {isLoading && (
          <div className="center-screen" style={{ minHeight: 200 }}>
            <Loader2 className="spin" color="rgb(var(--c-ink-soft))" />
          </div>
        )}

        {isError && <p className="error-text">Impossible de charger les expéditions.</p>}

        {!isLoading && filtered.length === 0 && (
          <div className="empty-state">Aucune expédition trouvée.</div>
        )}

        {filtered.map((s) => (
          <Link key={s.id} to={`/shipments/${s.id}`} className="card row-between">
            <div>
              <p style={{ fontWeight: 700, margin: '0 0 4px' }}>{s.order_number}</p>
              <p className="text-sm text-soft" style={{ margin: 0 }}>
                {s.origin?.country || s.origin?.city || '—'} → {s.destination?.country || s.destination?.city || '—'}
              </p>
            </div>
            <div className="row">
              <span className={`badge ${STATUS_BADGE[s.status] || 'badge-accent'}`}>
                {STATUS_LABELS[s.status] || s.status}
              </span>
              <ChevronRight size={16} color="rgb(var(--c-ink-soft))" />
            </div>
          </Link>
        ))}
      </div>
    </>
  );
};

export default Shipments;
