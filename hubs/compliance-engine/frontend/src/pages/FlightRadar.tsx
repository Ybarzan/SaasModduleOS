import { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { incokalkAPI } from '../lib/api';
import { Plane, MapPin, RefreshCw, Loader2, Radar, Globe, ArrowLeft } from 'lucide-react';
import L from 'leaflet';

delete (L.Icon.Default.prototype as unknown as { _getIconUrl?: string })._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-icon-2x.png',
  iconUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-icon.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-shadow.png',
});

const DEFAULT_CENTER: [number, number] = [46, 2];
const DEFAULT_ZOOM = 5;

interface Flight {
  icao24: string;
  callsign: string;
  originCountry: string;
  longitude: number;
  latitude: number;
  altitude: number;
  velocity: number;
  heading: number;
  onGround: boolean;
  timePosition: number;
  lastContact: number;
}

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
  trueTrack: number | null,
  verticalRate: number | null,
  sensors: unknown[] | null,
  geoAltitude: number | null,
];

interface FlightData {
  states?: OpenSkyState[];
}

function createPlaneIcon(heading: number, onGround: boolean): L.DivIcon {
  const color = onGround ? '#9CA3AF' : '#8B5CF6';
  return L.divIcon({
    className: 'plane-marker',
    html: `<div style="transform:rotate(${heading}deg);width:22px;height:22px;display:flex;align-items:center;justify-content:center;">
      <svg viewBox="0 0 24 24" width="22" height="22" fill="${color}" stroke="white" stroke-width="1">
        <path d="M21 16v-2l-8-5V3.5c0-.83-.67-1.5-1.5-1.5S10 2.67 10 3.5V9l-8 5v2l8-2.5V19l-2 1.5V22l3.5-1 3.5 1v-1.5L13 19v-5.5l8 2.5z"/>
      </svg>
    </div>`,
    iconSize: [22, 22],
    iconAnchor: [11, 11],
  });
}

function createFlightPopup(flight: Flight): string {
  const alt = flight.altitude ? `${Math.round(flight.altitude)} m` : 'Sol';
  const spd = flight.velocity ? `${Math.round(flight.velocity * 3.6)} km/h` : 'N/A';
  return `<div style="font-family:sans-serif;min-width:200px;">
    <div style="font-weight:700;font-size:14px;margin-bottom:6px;color:#8B5CF6;">
      ✈ ${flight.callsign || flight.icao24}
    </div>
    <div style="font-size:12px;color:#666;line-height:1.6;">
      <div><strong>ICAO24:</strong> ${flight.icao24}</div>
      <div><strong>Pays:</strong> ${flight.originCountry}</div>
      <div><strong>Altitude:</strong> ${alt}</div>
      <div><strong>Vitesse:</strong> ${spd}</div>
      <div><strong>Cap:</strong> ${Math.round(flight.heading || 0)}°</div>
      <div><strong>Position:</strong> ${flight.latitude.toFixed(4)}°, ${flight.longitude.toFixed(4)}°</div>
      <div><strong>Statut:</strong> ${flight.onGround ? 'Au sol' : 'En vol'}</div>
    </div>
  </div>`;
}

const FlightRadar = () => {
  const navigate = useNavigate();
  const mapRef = useRef<HTMLDivElement>(null);
  const mapInstance = useRef<L.Map | null>(null);
  const markersRef = useRef<Map<string, L.Marker>>(new Map());
  const [mapReady, setMapReady] = useState(false);
  const [selectedFlight, setSelectedFlight] = useState<Flight | null>(null);
  const [bbox, setBbox] = useState('35,55,-5,15');

  const { data: flightData, isLoading, refetch } = useQuery({
    queryKey: ['flights', bbox],
    queryFn: async () => {
      const res = await incokalkAPI.trackingMap.getFlights(bbox);
      return res.data as FlightData;
    },
    refetchInterval: 15000,
    retry: false,
  });

  const flightCount = flightData?.states
    ? flightData.states.filter((s) => s[5] != null && s[6] != null && !s[8]).length
    : 0;

  useEffect(() => {
    if (!mapRef.current || mapInstance.current) return;

    const timer = setTimeout(() => {
      if (!mapRef.current || mapInstance.current) return;
      const map = L.map(mapRef.current, {
        center: DEFAULT_CENTER,
        zoom: DEFAULT_ZOOM,
        zoomControl: true,
        attributionControl: true,
      });
      L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
        attribution: '&copy; CartoDB &copy; OpenStreetMap',
        maxZoom: 18,
      }).addTo(map);

      const updateBbox = () => {
        const b = map.getBounds();
        setBbox(`${b.getSouth()},${b.getNorth()},${b.getWest()},${b.getEast()}`);
      };

      map.on('moveend', updateBbox);
      mapInstance.current = map;
      setMapReady(true);
      map.invalidateSize();
      updateBbox();
    }, 100);

    return () => {
      clearTimeout(timer);
      if (mapInstance.current) {
        mapInstance.current.remove();
        mapInstance.current = null;
      }
    };
  }, []);

  const updateFlightMarkers = useCallback((states: OpenSkyState[]) => {
    if (!mapInstance.current || !states) return;
    const map = mapInstance.current;
    const flights: Flight[] = states
      .filter((s) => s[5] != null && s[6] != null && !s[8])
      .map((s) => ({
        icao24: s[0] || '',
        callsign: (s[1] || '').trim(),
        originCountry: s[2] || '',
        longitude: s[5] ?? 0,
        latitude: s[6] ?? 0,
        altitude: s[7] || s[13] || 0,
        velocity: s[9] || 0,
        heading: s[10] || 0,
        onGround: s[8] || false,
        timePosition: s[3] || 0,
        lastContact: s[4] || 0,
      }));

    const newIds = new Set(flights.map(f => f.icao24));
    markersRef.current.forEach((marker, id) => {
      if (!newIds.has(id)) {
        map.removeLayer(marker);
        markersRef.current.delete(id);
      }
    });

    flights.forEach(flight => {
      const existing = markersRef.current.get(flight.icao24);
      if (existing) {
        existing.setLatLng([flight.latitude, flight.longitude]);
        existing.setIcon(createPlaneIcon(flight.heading, flight.onGround));
      } else {
        const marker = L.marker([flight.latitude, flight.longitude], {
          icon: createPlaneIcon(flight.heading, flight.onGround),
        })
          .bindPopup(createFlightPopup(flight))
          .addTo(map);
        marker.on('click', () => setSelectedFlight(flight));
        markersRef.current.set(flight.icao24, marker);
      }
    });
  }, []);

  useEffect(() => {
    if (flightData?.states) {
      updateFlightMarkers(flightData.states);
    }
  }, [flightData, updateFlightMarkers]);

  return (
    <div className="h-screen flex flex-col overflow-hidden bg-ink">
      <div className="bg-ink border-b border-line px-4 py-3 flex-shrink-0">
        <div className="max-w-7xl mx-auto flex items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <button
              onClick={() => navigate('/dashboard')}
              className="p-2 text-ink-soft hover:text-white hover:bg-ink-soft rounded-none transition-colors flex-shrink-0"
              title="Retour au tableau de bord"
            >
              <ArrowLeft size={20} />
            </button>
            <div className="w-10 h-10 rounded-none bg-accent flex items-center justify-center flex-shrink-0">
              <Plane size={20} className="text-white" />
            </div>
            <div>
              <h1 className="text-lg font-extrabold text-white">
                <span className="text-accent font-normal" aria-hidden="true">:: </span>
                Flight Radar
              </h1>
              <p className="text-xs text-ink-soft">Vols en temps réel — OpenSky Network</p>
            </div>
          </div>
          <div className="flex items-center gap-4">
            <div className="flex items-center gap-2 bg-ink-soft rounded-none px-3 py-2">
              <Radar size={14} className="text-accent animate-pulse" />
              <span className="text-sm font-semibold text-white">{flightCount}</span>
              <span className="text-xs text-ink-soft">vols affichés</span>
            </div>
            <button
              onClick={() => refetch()}
              className="p-2 text-ink-soft hover:text-white hover:bg-ink-soft rounded-none transition-colors"
              title="Rafraîchir"
            >
              <RefreshCw size={18} />
            </button>
          </div>
        </div>
      </div>

      <div className="flex-1 relative min-h-0">
        <div ref={mapRef} className="absolute inset-0 z-0" />

        <div className="absolute top-4 left-4 z-[1000] bg-ink/90 backdrop-blur-sm rounded-none shadow-lg border border-line p-3">
          <span className="hud-corner hud-corner-tl" aria-hidden="true" />
          <span className="hud-corner hud-corner-tr" aria-hidden="true" />
          <span className="hud-corner hud-corner-bl" aria-hidden="true" />
          <span className="hud-corner hud-corner-br" aria-hidden="true" />
          <div className="flex items-center gap-2 mb-1">
            <Globe size={14} className="text-accent" />
            <span className="text-xs font-bold text-white uppercase tracking-wide">Filtres</span>
          </div>
          <p className="text-[10px] text-ink-soft">Déplacez la carte pour charger les vols</p>
          <button
            onClick={() => refetch()}
            className="mt-2 w-full px-3 py-1.5 bg-accent text-white rounded-none text-xs font-semibold hover:bg-accent-strong transition-colors"
          >
            Actualiser cette zone
          </button>
        </div>

        <div className="absolute bottom-4 left-4 z-[1000] bg-ink/90 backdrop-blur-sm rounded-none shadow border border-line px-3 py-2">
          <div className="flex items-center gap-4 text-[10px] text-ink-soft">
            <div className="flex items-center gap-1">
              <div className="w-2 h-2 rounded-full bg-accent" />
              <span>En vol</span>
            </div>
            <div className="flex items-center gap-1">
              <div className="w-2 h-2 rounded-full bg-surface-2" />
              <span>Au sol</span>
            </div>
            <div className="flex items-center gap-1">
              <MapPin size={10} />
              <span>OpenSky Network</span>
            </div>
          </div>
        </div>

        {selectedFlight && (
          <div className="absolute top-4 right-4 z-[1000] bg-ink/95 backdrop-blur-sm rounded-none shadow-lg border border-line p-4 w-72">
            <div className="flex items-center justify-between mb-3">
              <div className="flex items-center gap-2">
                <Plane size={16} className="text-accent" />
                <span className="text-sm font-bold text-white">
                  {selectedFlight.callsign || selectedFlight.icao24}
                </span>
              </div>
              <button
                onClick={() => setSelectedFlight(null)}
                className="text-ink-soft hover:text-white text-xs"
              >
                ✕
              </button>
            </div>
            <div className="space-y-1.5 text-xs text-ink-soft">
              <div className="flex justify-between">
                <span className="text-ink-soft">ICAO24</span>
                <span className="font-mono">{selectedFlight.icao24}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-ink-soft">Pays d'origine</span>
                <span>{selectedFlight.originCountry}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-ink-soft">Altitude</span>
                <span>{selectedFlight.altitude ? `${Math.round(selectedFlight.altitude)} m` : 'Sol'}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-ink-soft">Vitesse</span>
                <span>{selectedFlight.velocity ? `${Math.round(selectedFlight.velocity * 3.6)} km/h` : 'N/A'}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-ink-soft">Cap</span>
                <span>{Math.round(selectedFlight.heading || 0)}°</span>
              </div>
              <div className="flex justify-between">
                <span className="text-ink-soft">Statut</span>
                <span className={selectedFlight.onGround ? 'text-ink-soft' : 'text-accent'}>
                  {selectedFlight.onGround ? 'Au sol' : 'En vol'}
                </span>
              </div>
            </div>
          </div>
        )}

        {isLoading && flightCount === 0 && (
          <div className="absolute inset-0 flex items-center justify-center bg-ink/50 z-[1001]">
            <div className="text-center">
              <Loader2 size={32} className="animate-spin text-accent mx-auto mb-2" />
              <p className="text-sm text-ink-soft">Chargement des vols...</p>
            </div>
          </div>
        )}

        {!mapReady && (
          <div className="absolute inset-0 flex items-center justify-center bg-ink z-[1001]">
            <div className="text-center">
              <Loader2 size={32} className="animate-spin text-accent mx-auto mb-2" />
              <p className="text-sm text-ink-soft">Initialisation du radar...</p>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default FlightRadar;
