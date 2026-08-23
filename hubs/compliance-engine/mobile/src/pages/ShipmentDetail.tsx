import { useParams, useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, Loader2, AlertTriangle } from 'lucide-react';
import { mobileApi } from '../lib/api';
import { useAuthStore } from '../stores/auth';
import { STATUS_LABELS, STATUS_TRANSITIONS, TRACKING_STATUS_LABELS, CUSTOMS_STATUS_LABELS } from '../types';
import type { ShipmentDetail as ShipmentDetailType, ShipmentItemDTO } from '../types';

const MANAGER_ROLES = ['OWNER', 'ADMIN', 'MANAGER'];

function formatDate(iso?: string): string | null {
  if (!iso) return null;
  return new Date(iso).toLocaleDateString('fr-FR', { day: '2-digit', month: 'short', year: 'numeric' });
}

function formatMoney(value?: number, currency?: string): string | null {
  if (value === undefined || value === null) return null;
  return new Intl.NumberFormat('fr-FR', { style: 'currency', currency: currency || 'EUR' }).format(value);
}

const ShipmentDetail = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const role = useAuthStore((s) => s.user?.role);
  const canUpdateStatus = role ? MANAGER_ROLES.includes(role) : false;

  const { data, isLoading, isError } = useQuery({
    queryKey: ['mobile-shipment', id],
    queryFn: async () => (await mobileApi.shipment.get(id!)).data as ShipmentDetailType,
    enabled: !!id,
  });

  const { data: items = [] } = useQuery({
    queryKey: ['mobile-shipment-items', id],
    queryFn: async () => (await mobileApi.shipment.items(id!)).data as ShipmentItemDTO[],
    enabled: !!id,
  });

  const statusMutation = useMutation({
    mutationFn: (status: string) => mobileApi.shipment.updateStatus(id!, status),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['mobile-shipment', id] });
      queryClient.invalidateQueries({ queryKey: ['mobile-recent-shipments'] });
    },
  });

  const costRows = data
    ? [
        ['Devis', formatMoney(data.quotedCost, data.costCurrency)],
        ['Coût final', formatMoney(data.finalCost, data.costCurrency)],
        ['Acheminement (landed cost)', formatMoney(data.landedCost, data.costCurrency)],
        ['Droits de douane', formatMoney(data.dutyAmount, data.costCurrency)],
        ['TVA', formatMoney(data.vatAmount, data.costCurrency)],
      ].filter(([, v]) => v !== null)
    : [];

  const dateRows = data
    ? [
        ['Enlèvement demandé', formatDate(data.requestedPickupDate)],
        ['Réservé le', formatDate(data.bookedAt)],
        ['Expédié le', formatDate(data.shippedAt)],
        ['Livraison estimée', formatDate(data.estimatedDeliveryDate)],
        ['Livraison réelle', formatDate(data.actualDeliveryDate)],
        ['Créée le', formatDate(data.createdAt)],
      ].filter(([, v]) => v !== null)
    : [];

  const hasCustoms = data && ((data.customsStatus && data.customsStatus !== 'NONE') || data.customsDeclarationNumber);
  const trackingEvents = [...(data?.trackingEvents || [])].sort(
    (a, b) => new Date(b.eventTime).getTime() - new Date(a.eventTime).getTime()
  );

  return (
    <>
      <div className="header-bar row">
        <button onClick={() => navigate(-1)} style={{ background: 'none', border: 'none', cursor: 'pointer' }}>
          <ArrowLeft size={20} />
        </button>
        <h1 className="title" style={{ margin: 0, fontSize: 18 }}>{data?.orderNumber || 'Expédition'}</h1>
      </div>

      <div className="stack" style={{ marginTop: 12 }}>
        {isLoading && (
          <div className="center-screen" style={{ minHeight: 200 }}>
            <Loader2 className="spin" color="rgb(var(--c-ink-soft))" />
          </div>
        )}

        {isError && <p className="error-text">Impossible de charger cette expédition.</p>}

        {data && (
          <>
            <div className="card">
              <div className="row-between" style={{ marginBottom: 12 }}>
                <span className="badge badge-accent">{STATUS_LABELS[data.status] || data.status}</span>
                <div className="row" style={{ gap: 6 }}>
                  {data.dangerous && (
                    <span className="badge badge-danger">
                      <AlertTriangle size={11} style={{ marginRight: 4 }} />
                      Marchandise dangereuse
                    </span>
                  )}
                  {data.incotermCode && <span className="text-sm text-soft">{data.incotermCode}</span>}
                </div>
              </div>
              <div className="stack text-sm">
                <div>
                  <p className="text-soft" style={{ margin: '0 0 2px' }}>Expéditeur</p>
                  <p style={{ margin: 0 }}>{data.shipperName || '—'} ({data.shipperCity || data.shipperCountry || '—'})</p>
                </div>
                <div>
                  <p className="text-soft" style={{ margin: '0 0 2px' }}>Destinataire</p>
                  <p style={{ margin: 0 }}>{data.consigneeName || '—'} ({data.consigneeCity || data.consigneeCountry || '—'})</p>
                </div>
                {data.goodsDescription && (
                  <div>
                    <p className="text-soft" style={{ margin: '0 0 2px' }}>Marchandises</p>
                    <p style={{ margin: 0 }}>{data.goodsDescription}</p>
                  </div>
                )}
                <div className="row-between">
                  <span>{data.weightKg ? `${data.weightKg} kg` : '—'}</span>
                  <span>{data.volumeM3 ? `${data.volumeM3} m³` : '—'}</span>
                  <span>{data.hsCode || '—'}</span>
                </div>
                <div className="row-between">
                  <span>{data.packagesCount ? `${data.packagesCount} colis` : '—'}</span>
                  <span>{data.goodsValue ? formatMoney(data.goodsValue, data.currency) : '—'}</span>
                  <span>{data.countryOfOrigin ? `Origine : ${data.countryOfOrigin}` : '—'}</span>
                </div>
                {(data.carrierName || data.transportMode) && (
                  <div>
                    <p className="text-soft" style={{ margin: '0 0 2px' }}>Transporteur</p>
                    <p style={{ margin: 0 }}>
                      {data.carrierName || '—'}
                      {data.transportMode ? ` · ${data.transportMode}` : ''}
                    </p>
                  </div>
                )}
                {data.containerType && (
                  <div>
                    <p className="text-soft" style={{ margin: '0 0 2px' }}>Conteneur</p>
                    <p style={{ margin: 0 }}>{data.containerType.replace(/_/g, ' ')}</p>
                  </div>
                )}
              </div>
            </div>

            {items.length > 0 && (
              <div className="card">
                <p className="section-label">Articles ({items.length})</p>
                <div className="stack">
                  {items.map((item) => (
                    <div key={item.id} className="row-between text-sm">
                      <div>
                        <p style={{ margin: '0 0 2px', fontWeight: 600 }}>{item.name || item.sku || 'Article'}</p>
                        {item.hsCode && <p className="text-soft" style={{ margin: 0 }}>{item.hsCode}</p>}
                      </div>
                      <div style={{ textAlign: 'right' }}>
                        <p style={{ margin: '0 0 2px' }}>{item.quantity} {item.unit}</p>
                        {item.unitPrice > 0 && (
                          <p className="text-soft" style={{ margin: 0 }}>{formatMoney(item.unitPrice, data.currency)} / unité</p>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {costRows.length > 0 && (
              <div className="card">
                <p className="section-label">Coûts</p>
                {costRows.map(([label, value]) => (
                  <div key={label} className="kv-row">
                    <span className="text-soft">{label}</span>
                    <span style={{ fontWeight: 600 }}>{value}</span>
                  </div>
                ))}
              </div>
            )}

            {dateRows.length > 0 && (
              <div className="card">
                <p className="section-label">Dates clés</p>
                {dateRows.map(([label, value]) => (
                  <div key={label} className="kv-row">
                    <span className="text-soft">{label}</span>
                    <span>{value}</span>
                  </div>
                ))}
              </div>
            )}

            {hasCustoms && (
              <div className="card">
                <p className="section-label">Douane</p>
                <div className="kv-row">
                  <span className="text-soft">Statut</span>
                  <span>{CUSTOMS_STATUS_LABELS[data.customsStatus || ''] || data.customsStatus}</span>
                </div>
                {data.customsDeclarationNumber && (
                  <div className="kv-row">
                    <span className="text-soft">N° déclaration</span>
                    <span>{data.customsDeclarationNumber}</span>
                  </div>
                )}
              </div>
            )}

            {trackingEvents.length > 0 && (
              <div className="card">
                <p className="section-label">Suivi</p>
                <div className="timeline">
                  {trackingEvents.map((ev, idx) => (
                    <div key={ev.id} className={`timeline-item${idx === 0 ? ' active' : ''}`}>
                      <div className="timeline-dot" />
                      <div className="row-between">
                        <span style={{ fontWeight: 600, fontSize: 13 }}>{TRACKING_STATUS_LABELS[ev.status] || STATUS_LABELS[ev.status] || ev.status}</span>
                        <span className="text-sm text-soft">{formatDate(ev.eventTime)}</span>
                      </div>
                      {ev.location && <p className="text-sm text-soft" style={{ margin: '2px 0 0' }}>{ev.location}</p>}
                      {ev.description && <p className="text-sm text-soft" style={{ margin: '2px 0 0' }}>{ev.description}</p>}
                    </div>
                  ))}
                </div>
              </div>
            )}

            {canUpdateStatus && (STATUS_TRANSITIONS[data.status] || []).length > 0 && (
              <div className="card">
                <p style={{ fontWeight: 700, margin: '0 0 10px' }}>Changer le statut</p>
                <div className="stack">
                  {STATUS_TRANSITIONS[data.status].map((next) => (
                    <button
                      key={next}
                      className="btn btn-primary btn-block"
                      disabled={statusMutation.isPending}
                      onClick={() => statusMutation.mutate(next)}
                    >
                      {statusMutation.isPending ? <Loader2 size={16} className="spin" /> : `Passer à « ${STATUS_LABELS[next]} »`}
                    </button>
                  ))}
                </div>
                {statusMutation.isError && (
                  <p className="error-text" style={{ marginTop: 8 }}>Échec de la mise à jour du statut.</p>
                )}
              </div>
            )}
          </>
        )}
      </div>
    </>
  );
};

export default ShipmentDetail;
