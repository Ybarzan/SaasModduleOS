import { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { incokalkAPI } from '../lib/api';
import { Search, Ship, MapPin, RefreshCw, Loader2, Anchor, ArrowLeft, AlertTriangle } from 'lucide-react';
import L from 'leaflet';

delete (L.Icon.Default.prototype as unknown as { _getIconUrl?: string })._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-icon-2x.png',
  iconUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-icon.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-shadow.png',
});

const DEFAULT_CENTER: [number, number] = [30, 0];
const DEFAULT_ZOOM = 3;

interface MapShip {
  id: string;
  name: string;
  lat: number;
  lng: number;
  heading: number;
  speed: number;
  status: string;
  source: string;
}

interface TrackerShipment {
  id: string;
  status: string;
  orderNumber?: string;
  consigneeCity?: string;
  consigneeCountry?: string;
  carrier?: { transportModes?: string[] };
}

interface Vessel {
  name?: string;
  callsign?: string;
  shipname?: string;
  mmsi?: string;
  icao24?: string;
  latitude?: number;
  longitude?: number;
  lat?: number;
  lon?: number;
  lng?: number;
  true_track?: number;
  heading?: number;
  course?: number;
  velocity?: number;
  speed?: number;
}

interface LiveVessel {
  mmsi: string;
  shipName?: string;
  latitude?: number;
  longitude?: number;
  speedKnots?: number;
  course?: number;
  heading?: number;
  updatedAt?: string;
}

interface LiveVesselsResponse {
  configured: boolean;
  connected: boolean;
  vessels: LiveVessel[];
}

function createShipIcon(heading: number): L.DivIcon {
  return L.divIcon({
    className: 'ship-marker',
    html: `<div style="transform:rotate(${heading}deg);width:24px;height:24px;display:flex;align-items:center;justify-content:center;">
      <svg viewBox="0 0 24 24" width="24" height="24" fill="#1B4965" stroke="white" stroke-width="1.5">
        <path d="M12 2L4 14h16L12 2z"/>
        <circle cx="12" cy="18" r="3" fill="#3B82B5"/>
      </svg>
    </div>`,
    iconSize: [24, 24],
    iconAnchor: [12, 12],
  });
}

function createLiveShipIcon(heading: number): L.DivIcon {
  return L.divIcon({
    className: 'ship-marker-live',
    html: `<div style="transform:rotate(${heading}deg);width:22px;height:22px;display:flex;align-items:center;justify-content:center;">
      <svg viewBox="0 0 24 24" width="22" height="22" fill="#0F9D58" stroke="white" stroke-width="1.5">
        <path d="M12 2L4 14h16L12 2z"/>
        <circle cx="12" cy="18" r="3" fill="#34C28C"/>
      </svg>
    </div>`,
    iconSize: [22, 22],
    iconAnchor: [11, 11],
  });
}

function createLiveShipPopup(v: LiveVessel): string {
  const name = v.shipName?.trim() || `MMSI ${v.mmsi}`;
  return `<div style="font-family:sans-serif;min-width:180px;">
    <div style="font-weight:700;font-size:14px;margin-bottom:6px;color:#0F9D58;">${name}</div>
    <div style="font-size:12px;color:#666;line-height:1.6;">
      <div>MMSI: ${v.mmsi}</div>
      <div>Lat: ${v.latitude?.toFixed(4)}° | Lng: ${v.longitude?.toFixed(4)}°</div>
      <div>Cap: ${v.course ?? v.heading ?? 0}° | Vitesse: ${v.speedKnots ?? 0} kts</div>
      <div>Source: AISStream.io (direct)</div>
    </div>
  </div>`;
}

function createShipPopup(ship: MapShip): string {
  return `<div style="font-family:sans-serif;min-width:180px;">
    <div style="font-weight:700;font-size:14px;margin-bottom:6px;color:#1B4965;">${ship.name}</div>
    <div style="font-size:12px;color:#666;line-height:1.6;">
      <div>Lat: ${ship.lat.toFixed(4)}° | Lng: ${ship.lng.toFixed(4)}°</div>
      <div>Cap: ${ship.heading}° | Vitesse: ${ship.speed} kts</div>
      <div>Source: ${ship.source}</div>
    </div>
  </div>`;
}

const ShipTracker = () => {
  const navigate = useNavigate();
  const mapRef = useRef<HTMLDivElement>(null);
  const mapInstance = useRef<L.Map | null>(null);
  const markersRef = useRef<Map<string, L.Marker>>(new Map());
  const liveMarkersRef = useRef<Map<string, L.Marker>>(new Map());
  const [searchQuery, setSearchQuery] = useState('');
  const [submitQuery, setSubmitQuery] = useState('');
  const [mapReady, setMapReady] = useState(false);
  const [bbox, setBbox] = useState('35,55,-5,15');

  const { data: liveData, isLoading: liveLoading, refetch: refetchLive } = useQuery({
    queryKey: ['live-vessels', bbox],
    queryFn: async () => {
      const res = await incokalkAPI.trackingMap.getLiveVessels(bbox);
      return res.data as LiveVesselsResponse;
    },
    refetchInterval: 15000,
    retry: false,
  });

  const { data: vessels, isLoading, refetch } = useQuery({
    queryKey: ['vessel-search', submitQuery],
    queryFn: async () => {
      if (!submitQuery) return [];
      const res = await incokalkAPI.trackingMap.searchVessels(submitQuery);
      return (res.data as Vessel[]) || [];
    },
    enabled: !!submitQuery,
    refetchInterval: 30000,
    retry: false,
  });

  const liveVesselCount = liveData?.vessels?.filter((v) => v.latitude != null && v.longitude != null).length ?? 0;

  const { data: myShipments = [] } = useQuery({
    queryKey: ['shipments-maritime'],
    queryFn: async () => {
      const res = await incokalkAPI.shipments.getAll();
      return (res.data as TrackerShipment[]) || [];
    },
    refetchInterval: 30000,
    retry: false,
  });

  const maritimeShipments = myShipments.filter(
    (s: TrackerShipment) => s.status === 'IN_TRANSIT' && s.carrier?.transportModes?.includes('SEA')
  );

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
      L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '&copy; OpenStreetMap contributors',
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

  const updateMarkers = useCallback((ships: MapShip[]) => {
    if (!mapInstance.current) return;
    const map = mapInstance.current;
    const newIds = new Set(ships.map(s => s.id));

    markersRef.current.forEach((marker, id) => {
      if (!newIds.has(id)) {
        map.removeLayer(marker);
        markersRef.current.delete(id);
      }
    });

    ships.forEach(ship => {
      const existing = markersRef.current.get(ship.id);
      if (existing) {
        existing.setLatLng([ship.lat, ship.lng]);
      } else {
        const marker = L.marker([ship.lat, ship.lng], { icon: createShipIcon(ship.heading) })
          .bindPopup(createShipPopup(ship))
          .addTo(map);
        markersRef.current.set(ship.id, marker);
      }
    });
  }, []);

  useEffect(() => {
    if (!vessels || !Array.isArray(vessels)) return;
    const ships: MapShip[] = vessels
      .filter((v: Vessel) => v.latitude && v.longitude)
      .map((v: Vessel) => ({
        id: v.icao24 || v.mmsi || v.name || Math.random().toString(),
        name: v.name || v.callsign || v.shipname || 'Navire inconnu',
        lat: v.latitude || v.lat || 0,
        lng: v.longitude || v.lon || v.lng || 0,
        heading: v.true_track || v.heading || v.course || 0,
        speed: v.velocity || v.speed || 0,
        status: 'En mer',
        source: 'AIS',
      }));
    updateMarkers(ships);
    if (ships.length > 0 && mapInstance.current) {
      const bounds = L.latLngBounds(ships.map(s => [s.lat, s.lng]));
      mapInstance.current.fitBounds(bounds, { padding: [50, 50] });
    }
  }, [vessels, updateMarkers]);

  const updateLiveMarkers = useCallback((liveVessels: LiveVessel[]) => {
    if (!mapInstance.current) return;
    const map = mapInstance.current;
    const newIds = new Set(liveVessels.map(v => v.mmsi));

    liveMarkersRef.current.forEach((marker, mmsi) => {
      if (!newIds.has(mmsi)) {
        map.removeLayer(marker);
        liveMarkersRef.current.delete(mmsi);
      }
    });

    liveVessels.forEach(v => {
      if (v.latitude == null || v.longitude == null) return;
      const heading = v.heading ?? v.course ?? 0;
      const existing = liveMarkersRef.current.get(v.mmsi);
      if (existing) {
        existing.setLatLng([v.latitude, v.longitude]);
        existing.setIcon(createLiveShipIcon(heading));
        existing.setPopupContent(createLiveShipPopup(v));
      } else {
        const marker = L.marker([v.latitude, v.longitude], { icon: createLiveShipIcon(heading) })
          .bindPopup(createLiveShipPopup(v))
          .addTo(map);
        liveMarkersRef.current.set(v.mmsi, marker);
      }
    });
  }, []);

  useEffect(() => {
    if (!liveData?.vessels) return;
    updateLiveMarkers(liveData.vessels);
  }, [liveData, updateLiveMarkers]);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    if (searchQuery.trim()) setSubmitQuery(searchQuery.trim());
  };

  return (
    <div className="h-screen flex flex-col overflow-hidden">
      <div className="bg-surface border-b border-line px-4 py-3 flex-shrink-0">
        <div className="max-w-7xl mx-auto flex items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <button
              onClick={() => navigate('/dashboard')}
              className="p-2 text-ink-soft hover:text-ink-soft hover:bg-surface-2 rounded-lg transition-colors flex-shrink-0"
              title="Retour au tableau de bord"
            >
              <ArrowLeft size={20} />
            </button>
            <div className="w-10 h-10 rounded-xl bg-ink-soft flex items-center justify-center flex-shrink-0">
              <Ship size={20} className="text-white" />
            </div>
            <div>
              <h1 className="text-lg font-extrabold text-ink">Ship Tracker</h1>
              <p className="text-xs text-ink-soft">Suivi maritime en temps réel</p>
            </div>
            {liveData?.configured && liveData.connected && (
              <div className="hidden sm:flex items-center gap-1.5 px-2.5 py-1 bg-accent-soft text-accent-strong rounded-full text-xs font-semibold flex-shrink-0">
                <span className="relative flex h-2 w-2">
                  <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-accent opacity-75" />
                  <span className="relative inline-flex rounded-full h-2 w-2 bg-accent" />
                </span>
                {liveVesselCount} navire{liveVesselCount !== 1 ? 's' : ''} en direct
              </div>
            )}
            {liveData?.configured && !liveData.connected && (
              <div className="hidden sm:flex items-center gap-1.5 px-2.5 py-1 bg-danger/10 text-danger rounded-full text-xs font-semibold flex-shrink-0">
                <span className="h-2 w-2 rounded-full bg-danger" />
                Flux AIS déconnecté
              </div>
            )}
          </div>
          <form onSubmit={handleSearch} className="flex items-center gap-2 flex-1 max-w-md">
            <div className="relative flex-1">
              <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-soft" />
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Rechercher un navire (nom, MMSI)..."
                className="w-full pl-9 pr-4 py-2 border border-line rounded-xl text-sm focus:ring-2 focus:ring-ink-soft focus:border-transparent"
              />
            </div>
            <button
              type="submit"
              className="px-4 py-2 bg-ink-soft text-white rounded-xl text-sm font-semibold hover:bg-ink-soft transition-colors flex-shrink-0"
            >
              Rechercher
            </button>
          </form>
          <button
            onClick={() => {
              refetch();
              refetchLive();
            }}
            className="p-2 text-ink-soft hover:text-ink-soft hover:bg-surface-2 rounded-lg transition-colors flex-shrink-0"
            title="Rafraîchir"
          >
            <RefreshCw size={18} className={liveLoading ? 'animate-spin' : ''} />
          </button>
        </div>
      </div>

      <div className="flex-1 relative min-h-0">
        <div ref={mapRef} className="absolute inset-0 z-0" />

        {liveData && !liveData.configured && (
          <div className="absolute top-4 left-1/2 -translate-x-1/2 z-[1000] bg-warning/10 border border-warning/30 text-warning rounded-xl shadow-lg px-4 py-2 flex items-center gap-2 max-w-md">
            <AlertTriangle size={16} className="flex-shrink-0" />
            <span className="text-xs font-medium">
              Suivi en direct non configuré — inscrivez-vous sur AISStream.io et renseignez la clé API (AISSTREAM_API_KEY) pour afficher les navires en temps réel.
            </span>
          </div>
        )}

        {liveData && liveData.configured && !liveData.connected && (
          <div className="absolute top-4 left-1/2 -translate-x-1/2 z-[1000] bg-danger/10 border border-danger/30 text-danger rounded-xl shadow-lg px-4 py-2 flex items-center gap-2 max-w-md">
            <AlertTriangle size={16} className="flex-shrink-0" />
            <span className="text-xs font-medium">
              Clé API AISStream.io configurée mais la connexion au flux échoue — la clé est probablement invalide ou expirée. Générez-en une nouvelle sur aisstream.io.
            </span>
          </div>
        )}

        <div className="absolute top-4 left-4 z-[1000] bg-surface rounded-xl shadow-lg border border-line p-3 max-w-xs">
          <div className="flex items-center gap-2 mb-2">
            <Anchor size={14} className="text-ink-soft" />
            <span className="text-xs font-bold text-ink uppercase tracking-wide">Expéditions maritimes</span>
          </div>
          {maritimeShipments.length === 0 ? (
            <p className="text-xs text-ink-soft">Aucune expédition en transit</p>
          ) : (
            <div className="space-y-1 max-h-40 overflow-y-auto">
              {maritimeShipments.slice(0, 5).map((s: TrackerShipment) => (
                <div key={s.id} className="flex items-center gap-2 text-xs text-ink-soft py-1 px-2 rounded-lg hover:bg-surface-2">
                  <Ship size={12} className="text-ink-soft" />
                  <span className="truncate">{s.orderNumber}</span>
                  <span className="text-ink-soft ml-auto">{s.consigneeCity || s.consigneeCountry}</span>
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="absolute bottom-4 left-4 z-[1000] bg-surface/90 backdrop-blur-sm rounded-xl shadow border border-line px-3 py-2">
          <div className="flex items-center gap-4 text-[10px] text-ink-soft">
            <div className="flex items-center gap-1">
              <div className="w-2 h-2 rounded-full bg-ink-soft" />
              <span>Résultat de recherche</span>
            </div>
            {liveData?.configured && liveData.connected && (
              <div className="flex items-center gap-1">
                <div className="w-2 h-2 rounded-full bg-accent" />
                <span>Navire en direct</span>
              </div>
            )}
            <div className="flex items-center gap-1">
              <MapPin size={10} />
              <span>Carte OpenStreetMap</span>
            </div>
          </div>
        </div>

        {isLoading && (
          <div className="absolute top-4 right-4 z-[1000] bg-surface rounded-xl shadow-lg border border-line p-3 flex items-center gap-2">
            <Loader2 size={16} className="animate-spin text-ink-soft" />
            <span className="text-xs text-ink-soft">Recherche en cours...</span>
          </div>
        )}

        {!mapReady && (
          <div className="absolute inset-0 flex items-center justify-center bg-bg z-[1001]">
            <div className="text-center">
              <Loader2 size={32} className="animate-spin text-ink-soft mx-auto mb-2" />
              <p className="text-sm text-ink-soft">Chargement de la carte...</p>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default ShipTracker;
