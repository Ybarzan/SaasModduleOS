import type { AxiosError } from 'axios';
import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { incokalkAPI } from '../lib/api';
import toast from 'react-hot-toast';
import {
  Calendar, Loader2, Plus, Truck, Send, XCircle
} from 'lucide-react';
import type { Carrier, ShipmentOrder } from '../types';
import { formatNumber } from '../lib/formatNumber';

interface CarrierBooking {
  id: string;
  carrierReference: string;
  carrierTrackingNumber: string;
  carrierBookingStatus: 'PENDING' | 'SUBMITTED' | 'CONFIRMED' | 'REJECTED' | 'CANCELLED' | 'COMPLETED' | 'FAILED';
  errorMessage: string;
  serviceType: string;
  specialInstructions: string;
  requestedPickupDate: string;
  estimatedPickupDate: string;
  estimatedTransitDays: number;
  estimatedDeliveryDate: string;
  quotedCost: number;
  quotedCostCurrency: string;
  simulated: boolean;
  createdAt: string;
  shipmentOrder?: { id: string; orderNumber: string };
  carrier?: { id: string; name: string; code: string };
}

const STATUS_CONFIG: Record<string, { label: string; color: string }> = {
  PENDING:    { label: 'En attente',   color: 'bg-warning/10 text-warning' },
  SUBMITTED:  { label: 'Soumise',      color: 'bg-accent-soft text-accent-strong' },
  CONFIRMED:  { label: 'Confirmée',    color: 'bg-success/10 text-success' },
  REJECTED:   { label: 'Rejetée',      color: 'bg-danger/10 text-danger' },
  CANCELLED:  { label: 'Annulée',      color: 'bg-surface-2 text-ink' },
  COMPLETED:  { label: 'Terminée',     color: 'bg-success/10 text-success' },
  FAILED:     { label: 'Échouée',      color: 'bg-danger/10 text-danger' },
};

const CarrierBookings = () => {
  const queryClient = useQueryClient();
  const [showCreate, setShowCreate] = useState(false);
  const [page, setPage] = useState(0);
  const [form, setForm] = useState({
    shipmentOrderId: '',
    carrierId: '',
    serviceType: '',
    specialInstructions: '',
    requestedPickupDate: '',
  });

  const { data, isLoading } = useQuery({
    queryKey: ['carrier-bookings', page],
    queryFn: async () => {
      const res = await incokalkAPI.carrierBookings.list(page, 20);
      return res.data;
    },
  });

  const { data: carriersData } = useQuery({
    queryKey: ['carriers-all'],
    queryFn: async () => {
      const res = await incokalkAPI.carriers.getAll();
      return res.data as Carrier[];
    },
  });

  const { data: shipmentsData } = useQuery({
    queryKey: ['shipments-all'],
    queryFn: async () => {
      const res = await incokalkAPI.shipments.getAll();
      return res.data as ShipmentOrder[];
    },
  });

  const bookings: CarrierBooking[] = Array.isArray(data) ? data : (data?.content ?? []);
  const totalPages: number = Array.isArray(data) ? 1 : (data?.totalPages ?? 1);
  const carriers: Carrier[] = Array.isArray(carriersData) ? carriersData : [];
  const shipments: ShipmentOrder[] = Array.isArray(shipmentsData) ? shipmentsData : [];

  const createMutation = useMutation({
    mutationFn: (d: typeof form) => incokalkAPI.carrierBookings.create(d),
    onSuccess: () => {
      toast.success('Réservation créée');
      setShowCreate(false);
      setForm({ shipmentOrderId: '', carrierId: '', serviceType: '', specialInstructions: '', requestedPickupDate: '' });
      queryClient.invalidateQueries({ queryKey: ['carrier-bookings'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => toast.error(err.response?.data?.message || 'Erreur création'),
  });

  const submitMutation = useMutation({
    mutationFn: (id: string) => incokalkAPI.carrierBookings.submit(id),
    onSuccess: () => {
      toast.success('Réservation soumise au transporteur');
      queryClient.invalidateQueries({ queryKey: ['carrier-bookings'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => toast.error(err.response?.data?.message || 'Erreur soumission'),
  });

  const cancelMutation = useMutation({
    mutationFn: (id: string) => incokalkAPI.carrierBookings.cancel(id),
    onSuccess: () => {
      toast.success('Réservation annulée');
      queryClient.invalidateQueries({ queryKey: ['carrier-bookings'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => toast.error(err.response?.data?.message || 'Erreur annulation'),
  });

  return (
    <div className="max-w-7xl mx-auto px-4 py-8">
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-2xl font-bold text-ink">
            <span className="text-accent font-normal" aria-hidden="true">:: </span>
            Réservations transporteurs
          </h1>
          <p className="text-ink-soft mt-1">Gérez les réservations auprès de vos transporteurs</p>
        </div>
        <button
          onClick={() => setShowCreate(true)}
          className="flex items-center gap-2 bg-accent text-white px-4 py-2.5 rounded-none font-medium hover:bg-accent-strong transition-colors"
        >
          <Plus size={18} />
          Nouvelle réservation
        </button>
      </div>

      {/* Create Modal */}
      {showCreate && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30">
          <div className="bg-surface rounded-none shadow-xl w-full max-w-lg mx-4 p-6">
            <h2 className="text-lg font-semibold mb-4">Nouvelle réservation</h2>
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-ink mb-1">Expédition</label>
                <select
                  value={form.shipmentOrderId}
                  onChange={(e) => setForm({ ...form, shipmentOrderId: e.target.value })}
                  className="w-full px-3 py-2 border border-line rounded-none text-sm"
                >
                  <option value="">Sélectionner...</option>
                  {shipments.map((s) => (
                    <option key={s.id} value={s.id}>{s.orderNumber} — {s.goodsDescription?.slice(0, 50)}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-ink mb-1">Transporteur</label>
                <select
                  value={form.carrierId}
                  onChange={(e) => setForm({ ...form, carrierId: e.target.value })}
                  className="w-full px-3 py-2 border border-line rounded-none text-sm"
                >
                  <option value="">Sélectionner...</option>
                  {carriers.filter((c) => c.active).map((c) => (
                    <option key={c.id} value={c.id}>{c.name} ({c.code})</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-ink mb-1">Type de service</label>
                <input
                  type="text"
                  value={form.serviceType}
                  onChange={(e) => setForm({ ...form, serviceType: e.target.value })}
                  className="w-full px-3 py-2 border border-line rounded-none text-sm"
                  placeholder="EXPRESS, STANDARD, ECONOMY..."
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-ink mb-1">Date d'enlèvement souhaitée</label>
                <input
                  type="date"
                  value={form.requestedPickupDate}
                  onChange={(e) => setForm({ ...form, requestedPickupDate: e.target.value })}
                  className="w-full px-3 py-2 border border-line rounded-none text-sm"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-ink mb-1">Instructions spéciales</label>
                <textarea
                  value={form.specialInstructions}
                  onChange={(e) => setForm({ ...form, specialInstructions: e.target.value })}
                  rows={3}
                  className="w-full px-3 py-2 border border-line rounded-none text-sm resize-none"
                />
              </div>
            </div>
            <div className="flex items-center justify-end gap-3 mt-6">
              <button
                onClick={() => setShowCreate(false)}
                className="px-4 py-2 text-sm font-medium text-ink-soft hover:bg-surface-2 rounded-none transition-colors"
              >
                Annuler
              </button>
              <button
                onClick={() => createMutation.mutate(form)}
                disabled={createMutation.isPending || !form.shipmentOrderId || !form.carrierId}
                className="flex items-center gap-2 bg-accent text-white px-4 py-2 rounded-none font-medium text-sm hover:bg-accent-strong disabled:opacity-50 transition-colors"
              >
                {createMutation.isPending ? <Loader2 size={16} className="animate-spin" /> : <Plus size={16} />}
                Créer
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Stats bar */}
      <div className="grid grid-cols-3 sm:grid-cols-7 gap-3 mb-6">
        {Object.entries(STATUS_CONFIG).map(([key, cfg]) => {
          const count = bookings.filter((b) => b.carrierBookingStatus === key).length;
          return (
            <div key={key} className="bg-surface rounded-none border border-line p-3 text-center">
              <span className={`inline-block text-xs font-medium px-2 py-0.5 rounded-full ${cfg.color}`}>{cfg.label}</span>
              <p className="text-xl font-bold text-ink mt-1">{count}</p>
            </div>
          );
        })}
      </div>

      {/* List */}
      {isLoading ? (
        <div className="text-center py-12 text-ink-soft"><Loader2 size={24} className="animate-spin mx-auto mb-2" />Chargement...</div>
      ) : bookings.length === 0 ? (
        <div className="text-center py-12 text-ink-soft">
          <Truck size={48} className="mx-auto mb-3 text-ink-soft" />
          <p>Aucune réservation</p>
        </div>
      ) : (
        <div className="bg-surface rounded-none border border-line overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="bg-bg border-b border-line">
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Statut</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Transporteur</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Expédition</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Référence</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Coût estimé</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {bookings.map((b) => (
                  <tr key={b.id} className="hover:bg-bg transition-colors">
                    <td className="px-6 py-4">
                      <span className={`inline-flex items-center gap-1.5 text-xs font-medium px-2.5 py-1 rounded-full ${STATUS_CONFIG[b.carrierBookingStatus]?.color || 'bg-surface-2 text-ink'}`}>
                        {STATUS_CONFIG[b.carrierBookingStatus]?.label || b.carrierBookingStatus}
                      </span>
                    </td>
                    <td className="px-6 py-4 text-sm font-medium text-ink">{b.carrier?.name || '—'}</td>
                    <td className="px-6 py-4 text-sm text-ink-soft">{b.shipmentOrder?.orderNumber || '—'}</td>
                    <td className="px-6 py-4 text-sm font-mono text-ink-soft">
                      <div className="flex items-center gap-2">
                        <span>{b.carrierReference || '—'}</span>
                        {b.simulated && (
                          <span
                            title="Réponse simulée : aucune clé API transporteur configurée, ces données sont indicatives"
                            className="text-[10px] uppercase tracking-wide text-ink-soft/70 border border-line rounded px-1.5 py-0.5 shrink-0"
                          >
                            Simulé
                          </span>
                        )}
                      </div>
                    </td>
                    <td className="px-6 py-4 text-sm text-ink">
                      {b.quotedCost != null ? `${formatNumber(b.quotedCost, { minimumFractionDigits: 2 })} ${b.quotedCostCurrency || 'EUR'}` : '—'}
                    </td>
                    <td className="px-6 py-4">
                      <div className="flex items-center gap-2">
                        {b.carrierBookingStatus === 'PENDING' && (
                          <button
                            onClick={() => submitMutation.mutate(b.id)}
                            disabled={submitMutation.isPending}
                            className="flex items-center gap-1.5 text-xs font-medium text-accent hover:text-accent-strong px-2.5 py-1.5 rounded-none hover:bg-accent-soft transition-colors"
                          >
                            <Send size={14} />
                            Soumettre
                          </button>
                        )}
                        {(b.carrierBookingStatus === 'PENDING' || b.carrierBookingStatus === 'SUBMITTED') && (
                          <button
                            onClick={() => cancelMutation.mutate(b.id)}
                            disabled={cancelMutation.isPending}
                            className="flex items-center gap-1.5 text-xs font-medium text-danger hover:text-danger px-2.5 py-1.5 rounded-none hover:bg-danger/10 transition-colors"
                          >
                            <XCircle size={14} />
                            Annuler
                          </button>
                        )}
                        {b.carrierBookingStatus === 'CONFIRMED' && b.estimatedDeliveryDate && (
                          <span className="flex items-center gap-1.5 text-xs text-ink-soft">
                            <Calendar size={14} />
                            Livraison {new Date(b.estimatedDeliveryDate).toLocaleDateString('fr-FR')}
                          </span>
                        )}
                        {b.carrierBookingStatus === 'FAILED' && b.errorMessage && (
                          <span className="text-xs text-danger max-w-[150px] truncate" title={b.errorMessage}>
                            {b.errorMessage}
                          </span>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {!Array.isArray(data) && totalPages > 1 && (
            <div className="flex items-center justify-between px-6 py-4 border-t border-line">
              <span className="text-sm text-ink-soft">Page {page + 1} / {totalPages}</span>
              <div className="flex gap-2">
                <button disabled={page <= 0} onClick={() => setPage(page - 1)} className="px-3 py-1.5 text-sm border border-line rounded-none disabled:opacity-50 hover:bg-bg">Précédent</button>
                <button disabled={page >= totalPages - 1} onClick={() => setPage(page + 1)} className="px-3 py-1.5 text-sm border border-line rounded-none disabled:opacity-50 hover:bg-bg">Suivant</button>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default CarrierBookings;
