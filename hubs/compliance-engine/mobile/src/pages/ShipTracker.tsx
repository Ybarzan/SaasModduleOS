import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, Search, Loader2, Ship } from 'lucide-react';
import { mobileApi } from '../lib/api';

interface Vessel {
  name?: string;
  shipname?: string;
  mmsi?: string;
  latitude?: number;
  longitude?: number;
  lat?: number;
  lon?: number;
  speed?: number;
  velocity?: number;
}

// Recherche par nom/MMSI plutôt qu'une carte de flotte mondiale (comme sur web) :
// sur mobile, l'usage terrain est "où est CE navire", pas parcourir une carte.
const ShipTracker = () => {
  const navigate = useNavigate();
  const [query, setQuery] = useState('');

  const search = useMutation({
    mutationFn: async (q: string) => {
      const res = await mobileApi.trackingMap.searchVessels(q);
      return (res.data || []) as Vessel[];
    },
  });

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    if (!query.trim()) return;
    search.mutate(query.trim());
  };

  const results = search.data || [];

  return (
    <>
      <div className="header-bar row">
        <button onClick={() => navigate(-1)} style={{ background: 'none', border: 'none', cursor: 'pointer' }}>
          <ArrowLeft size={20} />
        </button>
        <h1 className="title" style={{ margin: 0, fontSize: 18 }}>Ship Tracker</h1>
      </div>

      <form onSubmit={handleSearch} className="stack" style={{ marginTop: 12 }}>
        <div style={{ position: 'relative' }}>
          <Search size={16} color="rgb(var(--c-ink-soft))" style={{ position: 'absolute', left: 12, top: '50%', transform: 'translateY(-50%)' }} />
          <input
            className="input"
            style={{ paddingLeft: 36 }}
            placeholder="Nom du navire ou MMSI"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </div>
        <button type="submit" className="btn btn-primary btn-block" disabled={search.isPending || !query.trim()}>
          {search.isPending ? <Loader2 size={16} className="spin" /> : 'Rechercher'}
        </button>
      </form>

      <div className="stack" style={{ marginTop: 16 }}>
        {search.isError && <p className="error-text">Recherche impossible — service de suivi indisponible.</p>}
        {search.isSuccess && results.length === 0 && (
          <div className="empty-state">Aucun navire trouvé pour « {query} ».</div>
        )}

        {results.map((v, i) => {
          const lat = v.latitude ?? v.lat;
          const lon = v.longitude ?? v.lon;
          const speed = v.speed ?? v.velocity;
          return (
            <div key={v.mmsi || i} className="card">
              <div className="row" style={{ gap: 8, marginBottom: 6 }}>
                <Ship size={16} color="rgb(var(--c-accent))" />
                <span style={{ fontWeight: 700 }}>{v.name || v.shipname || `MMSI ${v.mmsi}`}</span>
              </div>
              <div className="kv-row">
                <span className="text-soft">Position</span>
                <span>{lat != null && lon != null ? `${lat.toFixed(4)}°, ${lon.toFixed(4)}°` : 'Inconnue'}</span>
              </div>
              {speed != null && (
                <div className="kv-row">
                  <span className="text-soft">Vitesse</span>
                  <span>{speed.toFixed(1)} nds</span>
                </div>
              )}
            </div>
          );
        })}
      </div>
    </>
  );
};

export default ShipTracker;
