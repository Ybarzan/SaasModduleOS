import { useState, useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, Search, Loader2 } from 'lucide-react';
import { mobileApi } from '../lib/api';

interface TradeAgreement {
  id: string;
  code: string;
  name: string;
  partnerCountry: string;
  partnerName: string;
  active: boolean;
}

const TradeAgreements = () => {
  const navigate = useNavigate();
  const [search, setSearch] = useState('');

  const { data = [], isLoading, isError } = useQuery({
    queryKey: ['mobile-trade-agreements'],
    queryFn: async () => (await mobileApi.tradeAgreements.list()).data as TradeAgreement[],
  });

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return data;
    return data.filter(
      (a) =>
        a.name?.toLowerCase().includes(q) ||
        a.code?.toLowerCase().includes(q) ||
        a.partnerName?.toLowerCase().includes(q) ||
        a.partnerCountry?.toLowerCase().includes(q)
    );
  }, [data, search]);

  return (
    <>
      <div className="header-bar">
        <div className="row" style={{ gap: 8, marginBottom: 12 }}>
          <button onClick={() => navigate(-1)} style={{ background: 'none', border: 'none', cursor: 'pointer' }}>
            <ArrowLeft size={20} />
          </button>
          <h1 className="title" style={{ margin: 0, fontSize: 18 }}>Accords commerciaux</h1>
        </div>
        <div style={{ position: 'relative' }}>
          <Search size={16} color="rgb(var(--c-ink-soft))" style={{ position: 'absolute', left: 12, top: '50%', transform: 'translateY(-50%)' }} />
          <input
            className="input"
            style={{ paddingLeft: 36 }}
            placeholder="Rechercher un accord ou un pays..."
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
        {isError && <p className="error-text">Impossible de charger les accords commerciaux.</p>}
        {!isLoading && filtered.length === 0 && <div className="empty-state">Aucun accord trouvé.</div>}

        {filtered.map((a) => (
          <div key={a.id} className="card">
            <div className="row-between" style={{ marginBottom: 4 }}>
              <span style={{ fontWeight: 700 }}>{a.code}</span>
              {!a.active && <span className="badge badge-warning">Inactif</span>}
            </div>
            <p style={{ margin: '0 0 2px' }}>{a.name}</p>
            <p className="text-sm text-soft" style={{ margin: 0 }}>{a.partnerName || a.partnerCountry}</p>
          </div>
        ))}
      </div>
    </>
  );
};

export default TradeAgreements;
