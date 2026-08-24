import type { AxiosError } from 'axios';
import { Fragment, useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  FileText,
  Plus,
  Loader2,
  ChevronDown,
  ChevronUp,
  X,
  Printer,
} from 'lucide-react';
import toast from 'react-hot-toast';
import { incokalkAPI } from '../lib/api';
import type { CustomsInvoice, ShipmentOrder } from '../types';

function formatMoney(amount: number | undefined, currency: string | undefined) {
  if (amount == null) return '—';
  return `${amount.toLocaleString('fr-FR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} ${currency || 'EUR'}`;
}

function formatDate(d?: string) {
  if (!d) return '—';
  return new Date(d).toLocaleDateString('fr-FR', { day: '2-digit', month: 'short', year: 'numeric' });
}

const CustomsInvoices = () => {
  const queryClient = useQueryClient();
  const [showModal, setShowModal] = useState(false);
  const [selectedShipmentId, setSelectedShipmentId] = useState('');
  const [expanded, setExpanded] = useState<string | null>(null);

  const { data, isLoading } = useQuery({
    queryKey: ['customs-invoices'],
    queryFn: async () => {
      const res = await incokalkAPI.customsInvoices.list();
      return (res.data as CustomsInvoice[]) || [];
    },
  });

  const invoices = data ?? [];

  const { data: shipmentsData } = useQuery({
    queryKey: ['shipments-for-customs-invoice'],
    queryFn: async () => {
      const res = await incokalkAPI.shipments.getAll();
      return (res.data as ShipmentOrder[]) || [];
    },
    enabled: showModal,
  });

  const shipments = shipmentsData ?? [];
  const invoicedShipmentIds = new Set(invoices.map((i) => i.shipmentId).filter(Boolean));
  const availableShipments = shipments.filter((s) => !invoicedShipmentIds.has(s.id));

  const generateMutation = useMutation({
    mutationFn: (shipmentId: string) => incokalkAPI.customsInvoices.generate(shipmentId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['customs-invoices'] });
      toast.success('Facture douanière générée');
      setShowModal(false);
      setSelectedShipmentId('');
    },
    onError: (err: AxiosError<{ message?: string }>) => {
      toast.error(err.response?.data?.message || 'Erreur lors de la génération');
    },
  });

  return (
    <div className="max-w-5xl mx-auto px-4 py-8">
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-2xl font-bold text-ink">
            <span className="text-accent font-normal" aria-hidden="true">:: </span>
            Factures douanières
          </h1>
          <p className="text-ink-soft mt-1">
            Droits de douane et TVA calculés par ligne d'article à partir des données TARIC.
          </p>
        </div>
        <button
          onClick={() => setShowModal(true)}
          className="flex items-center gap-2 bg-accent text-white px-4 py-2 rounded-none hover:bg-accent-strong transition-colors"
        >
          <Plus size={18} />
          Générer une facture
        </button>
      </div>

      {isLoading ? (
        <div className="px-6 py-12 text-center text-ink-soft">
          <Loader2 size={24} className="animate-spin mx-auto mb-2 text-ink-soft" />
          Chargement...
        </div>
      ) : invoices.length === 0 ? (
        <div className="bg-surface rounded-none border border-line px-6 py-12 text-center text-ink-soft">
          <FileText size={32} className="mx-auto mb-3 text-ink-soft" />
          <p>Aucune facture douanière</p>
          <p className="text-sm text-ink-soft mt-1">Générez-en une depuis une expédition existante.</p>
        </div>
      ) : (
        <div className="bg-surface rounded-none border border-line overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="bg-bg border-b border-line">
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">N° Facture</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Date</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Destinataire</th>
                  <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Droits + TVA</th>
                  <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Total</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Statut</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {invoices.map((inv) => {
                  const isExpanded = expanded === inv.id;
                  return (
                    <Fragment key={inv.id}>
                      <tr
                        className="hover:bg-bg transition-colors cursor-pointer"
                        onClick={() => setExpanded(isExpanded ? null : inv.id)}
                      >
                        <td className="px-6 py-4">
                          <div className="flex items-center gap-2">
                            {isExpanded ? <ChevronUp size={14} className="text-ink-soft shrink-0" /> : <ChevronDown size={14} className="text-ink-soft shrink-0" />}
                            <span className="text-sm font-mono font-medium text-ink">{inv.invoiceNumber}</span>
                          </div>
                        </td>
                        <td className="px-6 py-4 text-sm text-ink-soft">{formatDate(inv.invoiceDate)}</td>
                        <td className="px-6 py-4 text-sm text-ink-soft">{inv.consigneeName || '—'}</td>
                        <td className="px-6 py-4 text-sm text-ink-soft text-right">
                          {formatMoney((inv.totalDuty || 0) + (inv.totalVat || 0), inv.currency)}
                        </td>
                        <td className="px-6 py-4 text-sm font-medium text-ink text-right">
                          {formatMoney(inv.totalAmount, inv.currency)}
                        </td>
                        <td className="px-6 py-4">
                          <span className="inline-block text-[10px] font-medium uppercase tracking-wide border rounded-none px-1.5 py-0.5 text-ink-soft border-line">
                            [{inv.status}]
                          </span>
                        </td>
                      </tr>
                      {isExpanded && (
                        <tr>
                          <td colSpan={6} className="px-6 py-5 bg-bg">
                            <div className="flex items-center justify-between mb-4">
                              <div className="grid grid-cols-1 md:grid-cols-2 gap-6 flex-1">
                                <div>
                                  <p className="text-xs font-medium text-ink-soft uppercase tracking-wider mb-1">Expéditeur</p>
                                  <p className="text-sm text-ink">{inv.shipperName || '—'}</p>
                                  <p className="text-xs text-ink-soft">
                                    {[inv.shipperAddress, inv.shipperCity, inv.shipperCountry].filter(Boolean).join(', ') || '—'}
                                  </p>
                                </div>
                                <div>
                                  <p className="text-xs font-medium text-ink-soft uppercase tracking-wider mb-1">Destinataire</p>
                                  <p className="text-sm text-ink">{inv.consigneeName || '—'}</p>
                                  <p className="text-xs text-ink-soft">
                                    {[inv.consigneeAddress, inv.consigneeCity, inv.consigneeCountry].filter(Boolean).join(', ') || '—'}
                                  </p>
                                </div>
                              </div>
                              <button
                                onClick={(e) => { e.stopPropagation(); window.print(); }}
                                className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium border border-line rounded-none text-ink-soft hover:text-ink hover:bg-surface transition-colors shrink-0 ml-4"
                                title="Imprimer cette facture (aucun PDF téléchargeable n'est généré côté serveur)"
                              >
                                <Printer size={12} />
                                Imprimer
                              </button>
                            </div>

                            <div className="flex flex-wrap gap-x-6 gap-y-1 text-xs text-ink-soft mb-4">
                              {inv.eoriNumber && <span>EORI : <span className="font-mono text-ink">{inv.eoriNumber}</span></span>}
                              {inv.incotermCode && <span>Incoterm : <span className="text-ink">{inv.incotermCode}</span></span>}
                              <span>Poids total : <span className="text-ink">{inv.totalWeightKg} kg</span></span>
                              <span>Colis : <span className="text-ink">{inv.totalPackages}</span></span>
                            </div>

                            {inv.items.length > 0 && (
                              <div className="overflow-x-auto border border-line bg-surface">
                                <table className="w-full text-xs">
                                  <thead>
                                    <tr className="border-b border-line">
                                      <th className="text-left px-3 py-2 font-medium text-ink-soft uppercase tracking-wider">Article</th>
                                      <th className="text-left px-3 py-2 font-medium text-ink-soft uppercase tracking-wider">Code SH</th>
                                      <th className="text-left px-3 py-2 font-medium text-ink-soft uppercase tracking-wider">Origine</th>
                                      <th className="text-right px-3 py-2 font-medium text-ink-soft uppercase tracking-wider">Qté</th>
                                      <th className="text-right px-3 py-2 font-medium text-ink-soft uppercase tracking-wider">Valeur</th>
                                      <th className="text-right px-3 py-2 font-medium text-ink-soft uppercase tracking-wider">Droit</th>
                                      <th className="text-right px-3 py-2 font-medium text-ink-soft uppercase tracking-wider">TVA</th>
                                    </tr>
                                  </thead>
                                  <tbody className="divide-y divide-line">
                                    {inv.items.map((item) => (
                                      <tr key={item.id}>
                                        <td className="px-3 py-2 text-ink">{item.name || item.sku || '—'}</td>
                                        <td className="px-3 py-2 font-mono text-ink-soft">{item.hsCode || '—'}</td>
                                        <td className="px-3 py-2 text-ink-soft">
                                          {item.countryOfOrigin || '—'}
                                          {item.isPreferential && (
                                            <span className="ml-1 text-[10px] text-success">(préf.)</span>
                                          )}
                                        </td>
                                        <td className="px-3 py-2 text-right text-ink-soft">{item.quantity} {item.unit}</td>
                                        <td className="px-3 py-2 text-right text-ink-soft">{formatMoney(item.totalValue, inv.currency)}</td>
                                        <td className="px-3 py-2 text-right text-ink-soft">
                                          {formatMoney(item.dutyAmount, inv.currency)}
                                          <span className="text-ink-soft/70"> ({item.dutyRate}%)</span>
                                        </td>
                                        <td className="px-3 py-2 text-right text-ink-soft">
                                          {formatMoney(item.vatAmount, inv.currency)}
                                          <span className="text-ink-soft/70"> ({item.vatRate}%)</span>
                                        </td>
                                      </tr>
                                    ))}
                                  </tbody>
                                </table>
                              </div>
                            )}

                            <div className="flex justify-end gap-6 mt-3 text-sm">
                              <span className="text-ink-soft">Marchandises : <span className="text-ink">{formatMoney(inv.totalGoodsValue, inv.currency)}</span></span>
                              <span className="text-ink-soft">Droits : <span className="text-ink">{formatMoney(inv.totalDuty, inv.currency)}</span></span>
                              <span className="text-ink-soft">TVA : <span className="text-ink">{formatMoney(inv.totalVat, inv.currency)}</span></span>
                              <span className="font-medium text-ink">Total : {formatMoney(inv.totalAmount, inv.currency)}</span>
                            </div>
                          </td>
                        </tr>
                      )}
                    </Fragment>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Generate modal */}
      {showModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div className="absolute inset-0 bg-black/40" onClick={() => setShowModal(false)} />
          <div className="relative bg-surface rounded-none border border-line shadow-2xl w-full max-w-md mx-4 p-6">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-lg font-semibold text-ink">Générer une facture douanière</h3>
              <button onClick={() => setShowModal(false)} className="p-1.5 text-ink-soft hover:bg-surface-2 rounded-none transition-colors">
                <X size={18} />
              </button>
            </div>
            <label className="block text-sm font-medium text-ink mb-1">Expédition</label>
            <select
              value={selectedShipmentId}
              onChange={(e) => setSelectedShipmentId(e.target.value)}
              className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm mb-4"
            >
              <option value="">Sélectionner une expédition</option>
              {availableShipments.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.orderNumber} — {s.consigneeCity || s.consigneeName || 'destinataire inconnu'}
                </option>
              ))}
            </select>
            {availableShipments.length === 0 && (
              <p className="text-xs text-ink-soft mb-4">
                Toutes les expéditions ont déjà une facture douanière, ou aucune expédition n'existe encore.
              </p>
            )}
            <div className="flex gap-3">
              <button
                onClick={() => setShowModal(false)}
                className="flex-1 px-4 py-2 border border-line rounded-none text-sm font-medium text-ink hover:bg-bg transition-colors"
              >
                Annuler
              </button>
              <button
                onClick={() => selectedShipmentId && generateMutation.mutate(selectedShipmentId)}
                disabled={!selectedShipmentId || generateMutation.isPending}
                className="flex-1 px-4 py-2 bg-accent text-white rounded-none text-sm font-medium hover:bg-accent-strong disabled:opacity-50 transition-colors flex items-center justify-center gap-2"
              >
                {generateMutation.isPending && <Loader2 size={14} className="animate-spin" />}
                Générer
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default CustomsInvoices;
