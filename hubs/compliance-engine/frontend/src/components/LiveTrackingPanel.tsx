import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { incokalkAPI } from '../lib/api';
import toast from 'react-hot-toast';
import { Ship, Plane, Truck, MapPin, RefreshCw, Loader2, Compass, Activity } from 'lucide-react';
import type { TrackingUpdate, LivePosition } from '../types';

const MODE_ICONS: Record<string, React.ReactNode> = {
  MARITIME: <Ship size={18} className="text-ink-soft" />,
  AIR: <Plane size={18} className="text-ink-soft" />,
  ROAD: <Truck size={18} className="text-ink-soft" />,
};

const MODE_LABELS: Record<string, string> = {
  MARITIME: 'Maritime',
  AIR: 'Aérien',
  ROAD: 'Routier',
};

interface LiveTrackingPanelProps {
  shipmentId: string;
  mode?: string;
}

const LiveTrackingPanel = ({ shipmentId, mode = 'ROAD' }: LiveTrackingPanelProps) => {
  const queryClient = useQueryClient();
  const [showPosition, setShowPosition] = useState(false);

  const { data: trackingUpdates = [], isLoading: loadingTracking, error: trackingError, refetch: refetchTracking } = useQuery({
    queryKey: ['tracking', shipmentId],
    queryFn: async () => {
      const res = await incokalkAPI.tracking.getShipment(shipmentId);
      return (res.data as TrackingUpdate[]) || [];
    },
    retry: false,
  });

  const { data: position, refetch: refetchPosition } = useQuery({
    queryKey: ['tracking-position', shipmentId],
    queryFn: async () => {
      const res = await incokalkAPI.tracking.getPosition(shipmentId);
      return res.data as LivePosition | null;
    },
    retry: false,
    enabled: showPosition,
  });

  const syncMutation = useMutation({
    mutationFn: () => incokalkAPI.tracking.sync(shipmentId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tracking', shipmentId] });
      queryClient.invalidateQueries({ queryKey: ['shipments'] });
      toast.success('Suivi synchronisé');
    },
    onError: () => toast.error('Erreur lors de la synchronisation'),
  });

  const icon = MODE_ICONS[mode] || <Truck size={18} className="text-ink-soft" />;
  const label = MODE_LABELS[mode] || mode;

  return (
    <div className="bg-surface border border-line rounded-xl shadow-sm overflow-hidden">
      <div className="bg-surface-2 px-4 py-3 border-b border-line flex items-center justify-between">
        <div className="flex items-center space-x-2">
          {icon}
          <h4 className="font-semibold text-ink text-sm">Suivi Live — {label}</h4>
        </div>
        <div className="flex items-center space-x-2">
          <button
            onClick={() => { setShowPosition(!showPosition); if (!showPosition) refetchPosition(); }}
            className="p-1.5 rounded-md text-ink-soft hover:bg-surface-2 transition-colors"
            title="Position actuelle"
          >
            <Compass size={14} />
          </button>
          <button
            onClick={() => { refetchTracking(); if (showPosition) refetchPosition(); }}
            className="p-1.5 rounded-md text-ink-soft hover:bg-surface-2 transition-colors"
            title="Rafraîchir"
          >
            <RefreshCw size={14} />
          </button>
        </div>
      </div>

      <div className="p-4 space-y-4">
        {loadingTracking ? (
          <div className="flex items-center justify-center py-6">
            <Loader2 className="h-5 w-5 animate-spin text-accent" />
            <span className="ml-2 text-sm text-ink-soft">Chargement du suivi...</span>
          </div>
        ) : trackingError ? (
          <div className="text-center py-4">
            <p className="text-sm text-danger">Erreur lors du chargement du suivi</p>
            <button
              onClick={() => refetchTracking()}
              className="mt-2 text-xs text-accent-strong hover:text-accent-strong underline"
            >
              Réessayer
            </button>
          </div>
        ) : trackingUpdates.length === 0 ? (
          <div className="text-center py-4">
            <Activity size={24} className="mx-auto text-ink-soft mb-2" />
            <p className="text-sm text-ink-soft">Aucune donnée de suivi disponible</p>
            <p className="text-xs text-ink-soft mt-1">Configurez une clé API de suivi pour des données en temps réel</p>
          </div>
        ) : (
          <div className="space-y-3">
            {position && (
              <div className="bg-surface-2 border border-line rounded-lg p-3">
                <div className="flex items-center space-x-2 mb-2">
                  <MapPin size={14} className="text-ink-soft" />
                  <span className="text-xs font-medium text-ink uppercase tracking-wide">Position actuelle</span>
                </div>
                <div className="grid grid-cols-2 gap-3 text-sm">
                  <div>
                    <span className="text-ink-soft">Latitude:</span>
                    <span className="ml-1 font-mono text-ink">{position.latitude?.toFixed(4) ?? '—'}</span>
                  </div>
                  <div>
                    <span className="text-ink-soft">Longitude:</span>
                    <span className="ml-1 font-mono text-ink">{position.longitude?.toFixed(4) ?? '—'}</span>
                  </div>
                  {position.speed != null && (
                    <div>
                      <span className="text-ink-soft">Vitesse:</span>
                      <span className="ml-1 text-ink">{position.speed} {mode === 'MARITIME' ? 'nds' : 'km/h'}</span>
                    </div>
                  )}
                  {position.heading && position.heading !== 'N/A' && (
                    <div>
                      <span className="text-ink-soft">Cap:</span>
                      <span className="ml-1 text-ink">{position.heading}</span>
                    </div>
                  )}
                </div>
                {position.vesselName && (
                  <div className="mt-2 text-xs text-ink-soft">
                    {position.vesselName} — Source: {position.source}
                  </div>
                )}
              </div>
            )}

            <div className="space-y-2">
              {trackingUpdates.slice(0, 5).map((update, idx) => (
                <div key={idx} className="flex items-start space-x-3 py-2 border-b border-line last:border-0">
                  <div className="w-2 h-2 rounded-full bg-accent mt-1.5 flex-shrink-0" />
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center space-x-2">
                      <span className="text-xs font-medium px-2 py-0.5 rounded-full bg-accent-soft text-accent-strong">
                        {update.status}
                      </span>
                      <span className="text-xs text-ink-soft">
                        {update.eventTime ? new Date(update.eventTime).toLocaleString('fr-FR') : '—'}
                      </span>
                    </div>
                    {update.location && (
                      <div className="text-xs text-ink-soft flex items-center space-x-1 mt-1">
                        <MapPin size={10} />
                        <span>{update.location}</span>
                      </div>
                    )}
                    {update.description && (
                      <p className="text-xs text-ink-soft mt-0.5">{update.description}</p>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        <div className="pt-2">
          <button
            onClick={() => syncMutation.mutate()}
            disabled={syncMutation.isPending}
            className="w-full px-3 py-2 bg-accent text-white text-sm rounded-lg hover:bg-accent-strong transition-colors disabled:opacity-50 flex items-center justify-center space-x-2"
          >
            {syncMutation.isPending ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <RefreshCw size={14} />
            )}
            <span>Synchroniser le suivi</span>
          </button>
        </div>
      </div>
    </div>
  );
};

export default LiveTrackingPanel;
