import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, Loader2, Plane, RefreshCw } from 'lucide-react';
import { mobileApi } from '../lib/api';

type OpenSkyState = [
  icao24: string,
  callsign: string | null,
  originCountry: string | null,
  timePosition: number | null,
  lastContact: number | null,
  longitude: number | null,
  latitude: number | null,
  baroAltitude: number | null,
  onGround: boolean,
  velocity: number | null,
];

// Pas de recherche par indicatif côté API (uniquement une zone géographique) --
// zone par défaut centrée sur la France/Europe de l'Ouest, comme la carte web
// (DEFAULT_CENTER [46, 2] dans FlightRadar.tsx).
const DEFAULT_BBOX = '41,51,-5,9';

const FlightRadar = () => {
  const navigate = useNavigate();

  const { data, isLoading, isError, refetch, isFetching } = useQuery({
    queryKey: ['mobile-flight-radar'],
    queryFn: async () => {
      const res = await mobileApi.trackingMap.getFlights(DEFAULT_BBOX);
      return (res.data?.states || []) as OpenSkyState[];
    },
    staleTime: 15_000,
  });

  const flights = data || [];

  return (
    <>
      <div className="header-bar row-between">
        <div className="row" style={{ gap: 8 }}>
          <button onClick={() => navigate(-1)} style={{ background: 'none', border: 'none', cursor: 'pointer' }}>
            <ArrowLeft size={20} />
          </button>
          <h1 className="title" style={{ margin: 0, fontSize: 18 }}>Flight Radar</h1>
        </div>
        <button onClick={() => refetch()} disabled={isFetching} style={{ background: 'none', border: 'none', cursor: 'pointer' }}>
          <RefreshCw size={18} className={isFetching ? 'spin' : ''} color="rgb(var(--c-ink-soft))" />
        </button>
      </div>

      <div className="stack" style={{ marginTop: 12 }}>
        <p className="text-sm text-soft">Vols au-dessus de la France et de l'Europe de l'Ouest</p>

        {isLoading && (
          <div className="center-screen" style={{ minHeight: 200 }}>
            <Loader2 className="spin" color="rgb(var(--c-ink-soft))" />
          </div>
        )}
        {isError && <p className="error-text">Impossible de charger les vols en direct.</p>}
        {!isLoading && flights.length === 0 && <div className="empty-state">Aucun vol détecté dans cette zone.</div>}

        {flights.slice(0, 50).map((f) => {
          const [icao24, callsign, originCountry, , , , , altitude, onGround, velocity] = f;
          return (
            <div key={icao24} className="card row-between">
              <div className="row" style={{ gap: 8 }}>
                <Plane size={16} color={onGround ? 'rgb(var(--c-ink-soft))' : 'rgb(var(--c-accent))'} />
                <div>
                  <p style={{ fontWeight: 700, margin: 0 }}>{callsign?.trim() || icao24}</p>
                  <p className="text-sm text-soft" style={{ margin: 0 }}>{originCountry || '—'}</p>
                </div>
              </div>
              <div style={{ textAlign: 'right' }}>
                <p className="text-sm" style={{ margin: 0 }}>{onGround ? 'Au sol' : `${Math.round((altitude || 0) * 3.281)} ft`}</p>
                {!onGround && velocity != null && (
                  <p className="text-sm text-soft" style={{ margin: 0 }}>{Math.round(velocity * 3.6)} km/h</p>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </>
  );
};

export default FlightRadar;
