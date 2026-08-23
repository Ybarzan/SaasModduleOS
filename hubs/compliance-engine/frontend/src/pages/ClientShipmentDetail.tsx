import { useQuery } from '@tanstack/react-query';
import { useParams, Link } from 'react-router-dom';
import { incokalkAPI, downloadAuthedFile } from '../lib/api';
import type { ClientShipment } from '../types';
import {
  ArrowLeft,
  MapPin, Clock, Weight, Box, Hash, FileText, DollarSign,
  Download
} from 'lucide-react';
import { CLIENT_STATUS_CONFIG, TRACKING_STATUS_COLORS } from '@/lib/constants';
import { formatNumber } from '../lib/formatNumber';

const ClientShipmentDetail = () => {
  const { id } = useParams<{ id: string }>();

  const { data: shipment, isLoading } = useQuery<ClientShipment>({
    queryKey: ['client-shipment', id],
    queryFn: async () => {
      const res = await incokalkAPI.clientPortal.shipmentDetail(id!);
      return res.data;
    },
    enabled: !!id,
    refetchInterval: 30000,
  });

  if (isLoading) {
    return (
      <div className="min-h-screen bg-bg flex items-center justify-center">
        <p className="text-ink-soft">Chargement...</p>
      </div>
    );
  }

  if (!shipment) {
    return (
      <div className="min-h-screen bg-bg flex items-center justify-center">
        <p className="text-ink-soft">Expédition introuvable</p>
      </div>
    );
  }

  const statusCfg = CLIENT_STATUS_CONFIG[shipment.status] || CLIENT_STATUS_CONFIG.DRAFT;
  const StatusIcon = statusCfg.icon;
  const trackingEvents = shipment.trackingEvents || [];
  const displayCost = shipment.finalCost || shipment.quotedCost;

  return (
    <div className="min-h-screen bg-bg">
      {/* Header */}
      <div className="bg-surface border-b border-line">
        <div className="max-w-4xl mx-auto px-4 py-4">
          <Link to="/client/dashboard" className="flex items-center gap-2 text-sm text-accent hover:text-accent-strong mb-3">
            <ArrowLeft className="w-4 h-4" /> Retour au tableau de bord
          </Link>
          <div className="flex items-center justify-between">
            <div>
              <h1 className="text-xl font-bold text-ink font-mono">{shipment.orderNumber}</h1>
              <div className="flex items-center gap-2 mt-1">
                <span className={`px-2.5 py-0.5 rounded-full text-xs font-medium ${statusCfg.color}`}>
                  <StatusIcon className="w-3 h-3 inline mr-1" />
                  {statusCfg.label}
                </span>
                {shipment.incotermCode && (
                  <span className="px-2 py-0.5 rounded-full text-xs font-medium bg-surface-2 text-ink-soft">
                    {shipment.incotermCode}
                  </span>
                )}
              </div>
            </div>
            {shipment.carrierName && (
              <div className="text-right">
                <p className="text-sm font-medium text-ink">{shipment.carrierName}</p>
                <p className="text-xs text-ink-soft">Transporteur</p>
              </div>
            )}
          </div>
        </div>
      </div>

      <div className="max-w-4xl mx-auto px-4 py-6 space-y-6">
        {/* Route Card */}
        <div className="bg-surface rounded-xl border border-line p-5">
          <h2 className="text-sm font-semibold text-ink mb-3 uppercase tracking-wide">Itinéraire</h2>
          <div className="flex items-center justify-between">
            <div className="flex-1">
              <p className="text-xs text-ink-soft mb-0.5">Origine</p>
              <p className="font-semibold text-ink">{shipment.shipperCity || '—'}</p>
              {shipment.shipperCountry && <p className="text-xs text-ink-soft">{shipment.shipperCountry}</p>}
            </div>
            <div className="px-4">
              <div className="w-12 h-0.5 bg-line relative">
                <div className="absolute right-0 top-1/2 -translate-y-1/2 w-0 h-0 border-t-4 border-b-4 border-l-6 border-transparent border-l-line" />
              </div>
            </div>
            <div className="flex-1 text-right">
              <p className="text-xs text-ink-soft mb-0.5">Destination</p>
              <p className="font-semibold text-ink">{shipment.consigneeCity || '—'}</p>
              {shipment.consigneeCountry && <p className="text-xs text-ink-soft">{shipment.consigneeCountry}</p>}
            </div>
          </div>
        </div>

        {/* Cost Summary */}
        {displayCost && (
          <div className="bg-surface rounded-xl border border-line p-5">
            <h2 className="text-sm font-semibold text-ink mb-3 uppercase tracking-wide">Coût</h2>
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-lg bg-success/10 flex items-center justify-center">
                  <DollarSign className="w-5 h-5 text-success" />
                </div>
                <div>
                  <p className="text-xs text-ink-soft">Coût total</p>
                  <p className="text-xl font-bold text-ink">
                    {formatNumber(displayCost)} {shipment.costCurrency || 'EUR'}
                  </p>
                </div>
              </div>
              {shipment.quotedCost && shipment.finalCost && shipment.finalCost !== shipment.quotedCost && (
                <div className="text-right">
                  <p className="text-xs text-ink-soft">Devis initial</p>
                  <p className="text-sm text-ink-soft line-through">
                    {formatNumber(shipment.quotedCost)} {shipment.costCurrency || 'EUR'}
                  </p>
                </div>
              )}
            </div>
          </div>
        )}

        {/* Cargo Info */}
        <div className="bg-surface rounded-xl border border-line p-5">
          <h2 className="text-sm font-semibold text-ink mb-3 uppercase tracking-wide">Marchandise</h2>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
            {shipment.goodsDescription && (
              <div className="col-span-2 sm:col-span-4">
                <p className="text-xs text-ink-soft flex items-center gap-1"><FileText className="w-3 h-3" /> Description</p>
                <p className="text-sm text-ink mt-0.5">{shipment.goodsDescription}</p>
              </div>
            )}
            <div>
              <p className="text-xs text-ink-soft flex items-center gap-1"><Weight className="w-3 h-3" /> Poids</p>
              <p className="text-sm font-medium text-ink mt-0.5">{shipment.weightKg ? `${shipment.weightKg} kg` : '—'}</p>
            </div>
            <div>
              <p className="text-xs text-ink-soft flex items-center gap-1"><Box className="w-3 h-3" /> Volume</p>
              <p className="text-sm font-medium text-ink mt-0.5">{shipment.volumeM3 ? `${shipment.volumeM3} m³` : '—'}</p>
            </div>
            <div>
              <p className="text-xs text-ink-soft flex items-center gap-1"><Hash className="w-3 h-3" /> Colis</p>
              <p className="text-sm font-medium text-ink mt-0.5">{shipment.packagesCount || '—'}</p>
            </div>
            <div>
              <p className="text-xs text-ink-soft flex items-center gap-1"><Clock className="w-3 h-3" /> Livraison est.</p>
              <p className="text-sm font-medium text-ink mt-0.5">
                {shipment.estimatedDeliveryDate
                  ? new Date(shipment.estimatedDeliveryDate).toLocaleDateString('fr-FR')
                  : '—'}
              </p>
            </div>
          </div>
        </div>

        {/* Documents Section */}
        <div className="bg-surface rounded-xl border border-line p-5">
          <h2 className="text-sm font-semibold text-ink mb-3 uppercase tracking-wide">Documents</h2>
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
            {[
              { label: 'Étiquette d\'expédition', type: 'label' },
              { label: 'CMR', type: 'cmr' },
              { label: 'Certificat d\'origine', type: 'certificate-of-origin' },
            ].map((doc) => (
              <button
                key={doc.type}
                onClick={() => {
                  downloadAuthedFile(`/v1/client/shipments/${id}/documents/${doc.type}`, `${doc.type}-${shipment.orderNumber}.pdf`)
                    .catch(() => {});
                }}
                className="flex items-center gap-2 p-3 rounded-lg border border-line hover:border-accent/40 hover:bg-accent-soft transition-colors text-left"
              >
                <Download className="w-4 h-4 text-accent flex-shrink-0" />
                <span className="text-sm text-ink">{doc.label}</span>
              </button>
            ))}
          </div>
        </div>

        {/* Tracking Timeline */}
        <div className="bg-surface rounded-xl border border-line p-5">
          <h2 className="text-sm font-semibold text-ink mb-4 uppercase tracking-wide">Suivi en temps réel</h2>
          {trackingEvents.length === 0 ? (
            <div className="text-center py-8">
              <MapPin className="w-10 h-10 text-ink-soft mx-auto mb-2" />
              <p className="text-sm text-ink-soft">Aucun événement de suivi disponible</p>
            </div>
          ) : (
            <div className="relative ml-3">
              <div className="absolute left-0 top-2 bottom-2 w-0.5 bg-line" />
              <div className="space-y-4">
                {trackingEvents.map((event, idx) => {
                  const tcColor = TRACKING_STATUS_COLORS[event.status] || 'bg-surface-2 text-ink-soft';
                  return (
                    <div key={event.id || idx} className="relative pl-6">
                      <div className={`absolute left-0 top-1.5 w-3 h-3 rounded-full border-2 border-surface ${idx === 0 ? 'bg-accent' : 'bg-line'}`} />
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
      </div>
    </div>
  );
};

export default ClientShipmentDetail;
