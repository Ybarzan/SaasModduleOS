import { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { incokalkAPI } from '../lib/api';
import type { SharedTrackingData } from '../types';
import {
  Package, AlertCircle, MapPin,
  ExternalLink, Loader2
} from 'lucide-react';
import { CLIENT_STATUS_CONFIG, TRACKING_STATUS_COLORS } from '@/lib/constants';

const SharedTracking = () => {
  const { token } = useParams<{ token: string }>();
  const [data, setData] = useState<SharedTrackingData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!token) return;
    incokalkAPI.sharedTracking.access(token)
      .then((res) => setData(res.data))
      .catch((err) => setError(err.response?.data?.message || 'Lien invalide ou expiré'))
      .finally(() => setLoading(false));
  }, [token]);

  if (loading) {
    return (
      <div className="min-h-screen bg-bg flex items-center justify-center">
        <Loader2 className="w-8 h-8 text-accent animate-spin" />
      </div>
    );
  }

  if (error || !data) {
    return (
      <div className="min-h-screen bg-bg flex items-center justify-center px-4">
        <div className="text-center">
          <AlertCircle className="w-16 h-16 text-danger mx-auto mb-4" />
          <h1 className="text-xl font-bold text-ink mb-2">Lien invalide</h1>
          <p className="text-ink-soft">{error || 'Ce lien de suivi n\'est plus disponible'}</p>
        </div>
      </div>
    );
  }

  const { shipment, trackingEvents, companyName, companyLogo, label } = data;
  const statusCfg = CLIENT_STATUS_CONFIG[shipment.status] || CLIENT_STATUS_CONFIG.DRAFT;
  const StatusIcon = statusCfg.icon;

  return (
    <div className="min-h-screen bg-bg">
      {/* Brand header */}
      <div className="bg-surface border-b border-line">
        <div className="max-w-3xl mx-auto px-4 py-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            {companyLogo ? (
              <img src={companyLogo} alt={companyName} className="w-10 h-10 rounded-none object-cover" />
            ) : (
              <div className="w-10 h-10 bg-accent-soft rounded-none flex items-center justify-center">
                <Package className="w-5 h-5 text-accent" />
              </div>
            )}
            <div>
              <h1 className="font-bold text-ink">{companyName}</h1>
              {label && <p className="text-xs text-ink-soft">{label}</p>}
            </div>
          </div>
          <a href="/" className="text-xs text-accent hover:underline flex items-center gap-1">
            IncoKalk <ExternalLink className="w-3 h-3" />
          </a>
        </div>
      </div>

      <div className="max-w-3xl mx-auto px-4 py-6 space-y-6">
        {/* Status + Route */}
        <div className="bg-surface rounded-none border border-line p-6">
          <div className="flex items-center gap-3 mb-4">
            <div className={`w-12 h-12 rounded-none flex items-center justify-center ${statusCfg.color}`}>
              <StatusIcon className="w-6 h-6" />
            </div>
            <div>
              <p className="text-xs text-ink-soft uppercase tracking-wide">Statut</p>
              <p className="font-bold text-lg text-ink">{statusCfg.label}</p>
            </div>
          </div>

          <div className="flex items-center justify-between bg-bg rounded-none p-4">
            <div className="text-center flex-1">
              <p className="text-xs text-ink-soft mb-0.5">Origine</p>
              <p className="font-semibold text-ink">{shipment.shipperCity || '—'}</p>
              <p className="text-xs text-ink-soft">{shipment.shipperCountry}</p>
            </div>
            <div className="px-4">
              <div className="w-16 h-0.5 bg-line relative">
                <div className="absolute right-0 top-1/2 -translate-y-1/2 w-0 h-0 border-t-4 border-b-4 border-l-6 border-transparent border-l-ink-soft" />
              </div>
            </div>
            <div className="text-center flex-1">
              <p className="text-xs text-ink-soft mb-0.5">Destination</p>
              <p className="font-semibold text-ink">{shipment.consigneeCity || '—'}</p>
              <p className="text-xs text-ink-soft">{shipment.consigneeCountry}</p>
            </div>
          </div>

          <div className="grid grid-cols-3 gap-3 mt-4 text-center">
            {shipment.carrierName && (
              <div>
                <p className="text-xs text-ink-soft">Transporteur</p>
                <p className="text-sm font-medium text-ink">{shipment.carrierName}</p>
              </div>
            )}
            {shipment.weightKg && (
              <div>
                <p className="text-xs text-ink-soft">Poids</p>
                <p className="text-sm font-medium text-ink">{shipment.weightKg} kg</p>
              </div>
            )}
            {shipment.estimatedDeliveryDate && (
              <div>
                <p className="text-xs text-ink-soft">Livraison est.</p>
                <p className="text-sm font-medium text-ink">
                  {new Date(shipment.estimatedDeliveryDate).toLocaleDateString('fr-FR')}
                </p>
              </div>
            )}
          </div>
        </div>

        {/* Tracking Timeline */}
        <div className="bg-surface rounded-none border border-line p-6">
          <h2 className="text-sm font-semibold text-ink mb-4 uppercase tracking-wide">Historique de suivi</h2>
          {trackingEvents.length === 0 ? (
            <div className="text-center py-8">
              <MapPin className="w-10 h-10 text-line mx-auto mb-2" />
              <p className="text-sm text-ink-soft">Aucun événement de suivi disponible</p>
            </div>
          ) : (
            <div className="relative ml-3">
              <div className="absolute left-0 top-2 bottom-2 w-0.5 bg-line" />
              <div className="space-y-5">
                {trackingEvents.map((event, idx) => {
                  const tcColor = TRACKING_STATUS_COLORS[event.status] || 'bg-surface-2 text-ink-soft';
                  return (
                    <div key={event.id || idx} className="relative pl-6">
                      <div className={`absolute left-0 top-1.5 w-3 h-3 rounded-full border-2 border-white ${idx === 0 ? 'bg-accent' : 'bg-line'}`} />
                      <div>
                        <div className="flex items-center gap-2">
                          <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${tcColor}`}>
                            {event.status}
                          </span>
                          {event.source && (
                            <span className="text-xs text-ink-soft">via {event.source}</span>
                          )}
                        </div>
                        {event.description && (
                          <p className="text-sm text-ink mt-1">{event.description}</p>
                        )}
                        {event.location && (
                          <p className="text-xs text-ink-soft flex items-center gap-1 mt-0.5">
                            <MapPin className="w-3 h-3" /> {event.location}
                          </p>
                        )}
                        <p className="text-xs text-ink-soft mt-0.5">
                          {new Date(event.eventTime).toLocaleString('fr-FR')}
                        </p>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          )}
        </div>

        {/* Footer */}
        <p className="text-center text-xs text-ink-soft py-4">
          Suivi propulsé par <span className="font-semibold text-accent">IncoKalk</span>
        </p>
      </div>
    </div>
  );
};

export default SharedTracking;
