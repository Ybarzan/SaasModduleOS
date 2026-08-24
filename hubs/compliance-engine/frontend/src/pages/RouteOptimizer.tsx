import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { Route, Loader2, MapPin, Plus, Trash2, Clock, Fuel, DollarSign } from 'lucide-react';
import { incokalkAPI } from '../lib/api';
import toast from 'react-hot-toast';
import { MapContainer, TileLayer, Marker, Popup, Polyline, useMap } from 'react-leaflet';
import { formatNumber, formatEur } from '../lib/formatNumber';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';

interface RouteStop {
  order: number;
  city: string;
  country: string;
  distanceFromPreviousKm: number;
  cumulativeDistanceKm: number;
}

interface RouteOptimizationResponse {
  totalDistanceKm: number;
  totalStops: number;
  estimatedHours: number;
  estimatedFuelLiters: number;
  estimatedFuelCost: number;
  estimatedTollCost: number;
  orderedStops: RouteStop[];
  recommendation: string;
}

const CITIES = [
  'Paris', 'Lyon', 'Marseille', 'Toulouse', 'Bordeaux', 'Lille', 'Nantes', 'Strasbourg',
  'Bruxelles', 'Amsterdam', 'Francfort', 'Madrid', 'Rome', 'Londres', 'Hamburg',
  'Barcelone', 'Casablanca', 'Alger', 'Tunis', 'Warszawa', 'Prague', 'Vienne',
  'Zurich', 'Rotterdam', 'Anvers', 'Gdansk', 'Le Havre', 'Belfort',
];

const COORDS: Record<string, [number, number]> = {
  Paris: [48.8566, 2.3522], Lyon: [47.7640, 4.8357], Marseille: [43.2965, 5.3698],
  Toulouse: [43.6047, 1.4442], Bordeaux: [44.8378, -0.5792], Lille: [50.6292, 3.0573],
  Nantes: [47.2184, -1.5536], Strasbourg: [48.5734, 7.7521], Bruxelles: [50.8503, 4.3517],
  Amsterdam: [52.3676, 4.9041], Francfort: [50.1109, 8.6821], Madrid: [40.4168, -3.7038],
  Rome: [41.9028, 12.4964], Londres: [51.5074, -0.1278], Hamburg: [53.5511, 9.9937],
  Barcelone: [41.3874, 2.1686], Casablanca: [33.5731, -7.5898], Alger: [36.7538, 3.0588],
  Tunis: [36.8065, 10.1815], Warszawa: [52.2297, 21.0122], Prague: [50.0755, 14.4378],
  Vienne: [48.2082, 16.3738], Zurich: [47.3769, 8.5417], Rotterdam: [51.9244, 4.4777],
  Anvers: [51.2194, 4.4025], Gdansk: [54.3520, 18.6466], 'Le Havre': [49.4944, 0.1079],
  Belfort: [47.6380, 6.8630],
};

const createIcon = (color: string) =>
  L.divIcon({
    className: 'custom-marker',
    html: `<div style="width:24px;height:24px;border-radius:50%;background:${color};border:3px solid white;box-shadow:0 2px 6px rgba(0,0,0,0.3);"></div>`,
    iconSize: [24, 24],
    iconAnchor: [12, 12],
  });

const ORIGIN_ICON = createIcon('#16a34a');
const STOP_ICON = createIcon('#3b82f6');
const DEST_ICON = createIcon('#dc2626');

function FitBounds({ positions }: { positions: [number, number][] }) {
  const map = useMap();
  if (positions.length > 0) {
    map.fitBounds(positions, { padding: [40, 40] });
  }
  return null;
}

const RouteOptimizer = () => {
  const [origin, setOrigin] = useState('Paris');
  const [destination, setDestination] = useState('Lyon');
  const [stops, setStops] = useState<string[]>(['Bordeaux']);
  const [newStop, setNewStop] = useState('');

  const optimizeMutation = useMutation({
    mutationFn: (data: { originCountry: string; destinationCountry: string; stops: { city: string }[] }) =>
      incokalkAPI.logistics.optimizeRoute(data).then(res => res.data as RouteOptimizationResponse),
    onError: () => toast.error("Erreur lors de l'optimisation"),
  });

  const addStop = () => {
    if (!newStop) return;
    if (stops.includes(newStop)) { toast.error('Stop déjà ajouté'); return; }
    setStops([...stops, newStop]);
    setNewStop('');
  };

  const removeStop = (idx: number) => setStops(stops.filter((_, i) => i !== idx));

  const handleOptimize = () => {
    optimizeMutation.mutate({
      originCountry: origin,
      destinationCountry: destination,
      stops: stops.map(s => ({ city: s })),
    });
  };

  const result = optimizeMutation.data;

  const allCityNames = [origin, ...stops, destination];
  const polylinePositions: [number, number][] = allCityNames
    .map(c => COORDS[c])
    .filter(Boolean) as [number, number][];

  const mapMarkers = allCityNames
    .filter(c => COORDS[c])
    .map((city, idx) => ({
      city,
      pos: COORDS[city] as [number, number],
      isFirst: idx === 0,
      isLast: idx === allCityNames.length - 1,
    }));

  return (
    <div className="min-h-screen bg-gradient-to-b from-success via-white to-success">
      <div className="max-w-5xl mx-auto px-4 py-12">
        <div className="text-center mb-10">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-success/10 mb-4">
            <Route size={32} className="text-success" />
          </div>
          <h1 className="text-3xl sm:text-4xl font-extrabold text-ink mb-3">
            <span className="text-accent font-normal" aria-hidden="true">:: </span>
            Optimisation d'itinéraire
          </h1>
          <p className="text-ink-soft max-w-xl mx-auto">Planifiez un itinéraire multi-stops avec estimation de carburant et péages.</p>
        </div>

        <div className="grid lg:grid-cols-2 gap-6">
          <div className="bg-surface rounded-none shadow-sm border border-line p-6 space-y-5">
            <h2 className="text-sm font-semibold text-ink-soft uppercase tracking-wider">Itinéraire</h2>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="text-xs text-ink-soft mb-1 block">Départ</label>
                <select value={origin} onChange={e => setOrigin(e.target.value)} className="w-full px-3 py-2.5 border border-line rounded-none text-sm bg-bg">
                  {CITIES.map(c => <option key={c} value={c}>{c}</option>)}
                </select>
              </div>
              <div>
                <label className="text-xs text-ink-soft mb-1 block">Arrivée</label>
                <select value={destination} onChange={e => setDestination(e.target.value)} className="w-full px-3 py-2.5 border border-line rounded-none text-sm bg-bg">
                  {CITIES.map(c => <option key={c} value={c}>{c}</option>)}
                </select>
              </div>
            </div>

            <div>
              <label className="text-xs text-ink-soft mb-1 block">Stops intermédiaires</label>
              {stops.length > 0 && (
                <div className="space-y-1.5 mb-2">
                  {stops.map((stop, idx) => (
                    <div key={idx} className="flex items-center gap-2 bg-bg rounded-none px-3 py-2 text-sm border border-line">
                      <MapPin size={14} className="text-success flex-shrink-0" />
                      <span className="flex-1 font-medium">{stop}</span>
                      <span className="text-[10px] text-ink-soft">Stop {idx + 1}</span>
                      <button onClick={() => removeStop(idx)} className="text-ink-soft hover:text-danger"><Trash2 size={12} /></button>
                    </div>
                  ))}
                </div>
              )}
              <div className="flex gap-2">
                <select value={newStop} onChange={e => setNewStop(e.target.value)} className="flex-1 px-3 py-2 border border-line rounded-none text-sm bg-bg">
                  <option value="">Ajouter un stop...</option>
                  {CITIES.filter(c => c !== origin && c !== destination && !stops.includes(c)).map(c => <option key={c} value={c}>{c}</option>)}
                </select>
                <button onClick={addStop} className="bg-success text-white px-3 py-2 rounded-none hover:bg-success/90 transition-colors"><Plus size={16} /></button>
              </div>
            </div>

            <button onClick={handleOptimize} disabled={optimizeMutation.isPending} className="w-full bg-success text-white py-3 rounded-none font-semibold hover:bg-success/90 disabled:opacity-50 flex items-center justify-center gap-2 text-sm transition-colors">
              {optimizeMutation.isPending ? <><Loader2 className="animate-spin" /> Calcul...</> : 'Optimiser l\'itinéraire'}
            </button>
          </div>

          <div className="space-y-4">
            {result ? (
              <>
                <div className="relative bg-surface rounded-none shadow-sm border border-line p-6">
                  <span className="hud-corner hud-corner-tl" aria-hidden="true" />
                  <span className="hud-corner hud-corner-tr" aria-hidden="true" />
                  <span className="hud-corner hud-corner-bl" aria-hidden="true" />
                  <span className="hud-corner hud-corner-br" aria-hidden="true" />
                  <h2 className="text-sm font-semibold text-ink-soft uppercase tracking-wider mb-4">Résumé</h2>
                  <div className="grid grid-cols-2 gap-3">
                    <div className="bg-success/10 rounded-none p-4 text-center">
                      <div className="text-3xl font-bold text-success">{formatNumber(result.totalDistanceKm)}</div>
                      <div className="text-xs text-ink-soft">km</div>
                    </div>
                    <div className="bg-warning/10 rounded-none p-4 text-center">
                      <div className="text-3xl font-bold text-warning">{result.estimatedHours}h</div>
                      <div className="text-xs text-ink-soft">temps estimé</div>
                    </div>
                    <div className="bg-accent/10 rounded-none p-4 text-center">
                      <div className="text-2xl font-bold text-accent-strong">{result.estimatedFuelLiters} L</div>
                      <div className="text-xs text-ink-soft">carburant</div>
                    </div>
                    <div className="bg-danger/10 rounded-none p-4 text-center">
                      <div className="text-2xl font-bold text-danger">{formatEur(result.estimatedFuelCost + result.estimatedTollCost)}</div>
                      <div className="text-xs text-ink-soft">coût total</div>
                    </div>
                  </div>
                </div>

                <div className="bg-surface rounded-none shadow-sm border border-line p-5">
                  <h3 className="text-xs font-semibold text-ink-soft uppercase tracking-wider mb-3">Détail des coûts</h3>
                  <div className="space-y-2 text-sm">
                    <div className="flex justify-between items-center py-1">
                      <span className="flex items-center gap-2 text-ink-soft"><Fuel size={14} /> Carburant</span>
                      <span className="font-medium">{formatEur(result.estimatedFuelCost)}</span>
                    </div>
                    <div className="flex justify-between items-center py-1">
                      <span className="flex items-center gap-2 text-ink-soft"><DollarSign size={14} /> Péages</span>
                      <span className="font-medium">{formatEur(result.estimatedTollCost)}</span>
                    </div>
                    <div className="flex justify-between items-center py-1">
                      <span className="flex items-center gap-2 text-ink-soft"><Clock size={14} /> Temps</span>
                      <span className="font-medium">{result.estimatedHours}h de conduite</span>
                    </div>
                  </div>
                </div>

                <div className="bg-surface rounded-none shadow-sm border border-line p-5">
                  <h3 className="text-xs font-semibold text-ink-soft uppercase tracking-wider mb-3">Étapes ({result.orderedStops.length})</h3>
                  <div className="space-y-0">
                    {result.orderedStops.map((stop, idx) => (
                      <div key={stop.order} className="flex items-start gap-3 relative">
                        <div className="flex flex-col items-center">
                          <div className={`w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold ${idx === 0 ? 'bg-success text-white' : idx === result.orderedStops.length - 1 ? 'bg-danger text-white' : 'bg-surface-2 text-ink-soft'}`}>
                            {idx + 1}
                          </div>
                          {idx < result.orderedStops.length - 1 && <div className="w-0.5 h-8 bg-surface-2" />}
                        </div>
                        <div className="pb-4 flex-1">
                          <div className="font-medium text-sm text-ink">{stop.city}</div>
                          {stop.country && <div className="text-[10px] text-ink-soft">{stop.country}</div>}
                          {stop.distanceFromPreviousKm > 0 && (
                            <div className="text-[11px] text-ink-soft mt-0.5">+{stop.distanceFromPreviousKm} km (total: {stop.cumulativeDistanceKm} km)</div>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                </div>

                {result.recommendation && (
                  <div className="bg-success/10 border border-success/40 rounded-none p-4 text-sm text-success font-medium">
                    {result.recommendation}
                  </div>
                )}
              </>
            ) : (
              <div className="bg-surface rounded-none shadow-sm border border-line p-12 text-center">
                <Route className="h-10 w-10 mx-auto mb-3 text-ink-soft" />
                <p className="text-sm text-ink-soft">Ajoutez des stops et cliquez sur « Optimiser »</p>
              </div>
            )}
          </div>
        </div>

        {polylinePositions.length > 0 && (
          <div className="mt-6 bg-surface rounded-none shadow-sm border border-line p-5">
            <h3 className="text-sm font-semibold text-ink-soft uppercase tracking-wider mb-4">Visualisation de l'itinéraire</h3>
            <div style={{ height: 400 }} className="rounded-none overflow-hidden">
              <MapContainer center={[48.8566, 2.3522]} zoom={5} style={{ height: '100%', width: '100%' }} scrollWheelZoom={false}>
                <TileLayer attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>' url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />
                <FitBounds positions={polylinePositions} />
                <Polyline positions={polylinePositions} pathOptions={{ color: '#65a30d', weight: 4, opacity: 0.8 }} />
                {mapMarkers.map((m, idx) => (
                  <Marker key={m.city} position={m.pos} icon={m.isFirst ? ORIGIN_ICON : m.isLast ? DEST_ICON : STOP_ICON}>
                    <Popup>{m.city}{m.isFirst ? ' (Départ)' : m.isLast ? ' (Arrivée)' : ` (Stop ${idx})`}</Popup>
                  </Marker>
                ))}
              </MapContainer>
            </div>
            <div className="flex items-center gap-4 mt-3 text-xs text-ink-soft">
              <span className="flex items-center gap-1.5"><span className="w-3 h-3 rounded-full bg-success inline-block" /> Départ</span>
              <span className="flex items-center gap-1.5"><span className="w-3 h-3 rounded-full bg-accent inline-block" /> Stop</span>
              <span className="flex items-center gap-1.5"><span className="w-3 h-3 rounded-full bg-danger inline-block" /> Arrivée</span>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default RouteOptimizer;
