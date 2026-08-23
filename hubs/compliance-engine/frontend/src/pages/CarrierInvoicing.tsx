import type { AxiosError } from 'axios';
import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  FileText, Plus, Trash2, Loader2, Eye, Send, CheckCircle, XCircle,
  DollarSign, Clock, AlertTriangle, X, Receipt,
} from 'lucide-react';
import toast from 'react-hot-toast';
import { incokalkAPI } from '../lib/api';
import Pagination from '../components/Pagination';

const PAGE_SIZE = 20;

interface CarrierInvoice {
  id: string;
  invoiceNumber: string;
  invoiceDate: string;
  dueDate: string;
  status: string;
  carrierName: string;
  carrierReference: string;
  totalAmount: number;
  currency: string;
  totalAmountEur: number;
  freightAmount: number;
  fuelSurcharge: number;
  securityFee: number;
  handlingFee: number;
  customsFee: number;
  otherCharges: number;
  otherChargesDescription: string;
  shipmentReference: string;
  negotiatedRate: number;
  variance: number;
  variancePercent: number;
  reconciliationNotes: string;
  approvedByUserId: string;
  approvedAt: string;
  paidAt: string;
  disputeReason: string;
  createdAt: string;
}

interface InvoiceStats {
  total: number;
  pending: number;
  approved: number;
  totalAmountEur: number;
}

const STATUS_LABELS: Record<string, string> = {
  DRAFT: 'Brouillon',
  RECEIVED: 'Reçue',
  UNDER_REVIEW: 'En cours de révision',
  APPROVED: 'Approuvée',
  PAID: 'Payée',
  DISPUTED: 'Litige',
  REJECTED: 'Rejetée',
};

const STATUS_COLORS: Record<string, string> = {
  DRAFT: 'bg-surface-2 text-ink',
  RECEIVED: 'bg-accent-soft text-accent-strong',
  UNDER_REVIEW: 'bg-warning/10 text-warning',
  APPROVED: 'bg-success/10 text-success',
  PAID: 'bg-success/10 text-success',
  DISPUTED: 'bg-danger/10 text-danger',
  REJECTED: 'bg-danger/10 text-danger',
};

const emptyForm = {
  invoiceNumber: '',
  invoiceDate: '',
  dueDate: '',
  carrierName: '',
  carrierReference: '',
  totalAmount: 0,
  currency: 'USD',
  totalAmountEur: 0,
  freightAmount: 0,
  fuelSurcharge: 0,
  securityFee: 0,
  handlingFee: 0,
  customsFee: 0,
  otherCharges: 0,
  otherChargesDescription: '',
  shipmentReference: '',
  negotiatedRate: 0,
  reconciliationNotes: '',
};

const CarrierInvoicing = () => {
  const queryClient = useQueryClient();
  const [createOpen, setCreateOpen] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [selectedInvoice, setSelectedInvoice] = useState<CarrierInvoice | null>(null);
  const [deleteConfirm, setDeleteConfirm] = useState<string | null>(null);
  const [disputeReason, setDisputeReason] = useState('');
  const [disputeTarget, setDisputeTarget] = useState<string | null>(null);
  const [page, setPage] = useState(0);

  const { data: invoicesData, isLoading: invoicesLoading } = useQuery({
    queryKey: ['carrier-invoices', page],
    queryFn: async () => {
      const res = await incokalkAPI.carrierInvoices.getPage(page, PAGE_SIZE);
      return res.data as CarrierInvoice[] | { content: CarrierInvoice[]; totalPages: number };
    },
  });

  const invoices = Array.isArray(invoicesData) ? invoicesData : invoicesData?.content ?? [];
  const totalPages: number = Array.isArray(invoicesData) ? 1 : invoicesData?.totalPages ?? 1;

  const { data: stats } = useQuery({
    queryKey: ['carrier-invoice-stats'],
    queryFn: async () => {
      const res = await incokalkAPI.carrierInvoices.stats();
      return res.data as InvoiceStats;
    },
  });

  const createMutation = useMutation({
    mutationFn: (data: typeof form) => incokalkAPI.carrierInvoices.create(data),
    onSuccess: () => {
      toast.success('Facture créée avec succès');
      setCreateOpen(false);
      setForm(emptyForm);
      queryClient.invalidateQueries({ queryKey: ['carrier-invoices'] });
      queryClient.invalidateQueries({ queryKey: ['carrier-invoice-stats'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || 'Erreur lors de la création');
    },
  });

  const updateStatusMutation = useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) =>
      incokalkAPI.carrierInvoices.updateStatus(id, { status }),
    onSuccess: () => {
      toast.success('Statut mis à jour');
      queryClient.invalidateQueries({ queryKey: ['carrier-invoices'] });
      queryClient.invalidateQueries({ queryKey: ['carrier-invoice-stats'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || 'Erreur lors de la mise à jour');
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => incokalkAPI.carrierInvoices.delete(id),
    onSuccess: () => {
      toast.success('Facture supprimée');
      setDeleteConfirm(null);
      queryClient.invalidateQueries({ queryKey: ['carrier-invoices'] });
      queryClient.invalidateQueries({ queryKey: ['carrier-invoice-stats'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || 'Erreur lors de la suppression');
    },
  });

  const disputeMutation = useMutation({
    mutationFn: ({ id, status, reason }: { id: string; status: string; reason?: string }) =>
      incokalkAPI.carrierInvoices.updateStatus(id, { status, reason }),
    onSuccess: () => {
      toast.success('Litige signalé');
      setDisputeTarget(null);
      setDisputeReason('');
      queryClient.invalidateQueries({ queryKey: ['carrier-invoices'] });
      queryClient.invalidateQueries({ queryKey: ['carrier-invoice-stats'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || 'Erreur lors du signalement');
    },
  });

  const handleCreate = (e: React.FormEvent) => {
    e.preventDefault();
    createMutation.mutate(form);
  };

  const formatCurrency = (amount: number, currency?: string) => {
    return new Intl.NumberFormat('fr-FR', {
      style: 'currency',
      currency: currency || 'EUR',
    }).format(amount);
  };

  const formatDate = (dateStr: string) => {
    if (!dateStr) return '—';
    return new Date(dateStr).toLocaleDateString('fr-FR');
  };

  return (
    <div className="max-w-7xl mx-auto px-4 py-8">
      {/* Header */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between mb-8 gap-4">
        <div>
          <h1 className="text-2xl font-bold text-ink">Facturation transporteurs</h1>
          <p className="text-ink-soft mt-1">Gestion des factures transporteurs (Accounts Payable)</p>
        </div>
        <button
          onClick={() => setCreateOpen(true)}
          className="flex items-center gap-2 bg-accent text-white px-4 py-2 rounded-lg font-medium hover:bg-accent-strong transition-colors"
        >
          <Plus size={18} />
          Nouvelle facture
        </button>
      </div>

      {/* Stats cards */}
      <div className="grid grid-cols-1 sm:grid-cols-4 gap-4 mb-8">
        <div className="bg-surface rounded-xl border border-line p-5">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-lg bg-accent-soft flex items-center justify-center">
              <FileText size={20} className="text-accent" />
            </div>
            <div>
              <p className="text-sm text-ink-soft">Total factures</p>
              <p className="text-2xl font-bold text-ink">{stats?.total ?? '—'}</p>
            </div>
          </div>
        </div>
        <div className="bg-surface rounded-xl border border-line p-5">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-lg bg-warning/10 flex items-center justify-center">
              <Clock size={20} className="text-warning" />
            </div>
            <div>
              <p className="text-sm text-ink-soft">En attente</p>
              <p className="text-2xl font-bold text-ink">{stats?.pending ?? '—'}</p>
            </div>
          </div>
        </div>
        <div className="bg-surface rounded-xl border border-line p-5">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-lg bg-success/10 flex items-center justify-center">
              <CheckCircle size={20} className="text-success" />
            </div>
            <div>
              <p className="text-sm text-ink-soft">Approuvées</p>
              <p className="text-2xl font-bold text-ink">{stats?.approved ?? '—'}</p>
            </div>
          </div>
        </div>
        <div className="bg-surface rounded-xl border border-line p-5">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-lg bg-accent/10 flex items-center justify-center">
              <DollarSign size={20} className="text-accent" />
            </div>
            <div>
              <p className="text-sm text-ink-soft">Montant total (EUR)</p>
              <p className="text-2xl font-bold text-ink">{stats?.totalAmountEur != null ? formatCurrency(stats.totalAmountEur) : '—'}</p>
            </div>
          </div>
        </div>
      </div>

      {/* Table */}
      <div className="bg-surface rounded-xl border border-line overflow-hidden">
        <div className="px-6 py-4 border-b border-line">
          <h2 className="text-lg font-semibold text-ink">Factures</h2>
        </div>

        {invoicesLoading ? (
          <div className="px-6 py-12 text-center text-ink-soft">
            <Loader2 size={24} className="animate-spin mx-auto mb-2 text-ink-soft" />
            Chargement...
          </div>
        ) : invoices.length === 0 ? (
          <div className="px-6 py-12 text-center text-ink-soft">
            <Receipt size={32} className="mx-auto mb-3 text-ink-soft" />
            <p>Aucune facture</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="bg-surface-2 border-b border-line">
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">N° facture</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Transporteur</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Date</th>
                  <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Montant</th>
                  <th className="text-center text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Devise</th>
                  <th className="text-center text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Statut</th>
                  <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Écart</th>
                  <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {invoices.map((inv: CarrierInvoice) => (
                  <tr
                    key={inv.id}
                    className="hover:bg-surface-2 transition-colors cursor-pointer"
                    onClick={() => setSelectedInvoice(inv)}
                  >
                    <td className="px-6 py-4">
                      <span className="text-sm font-medium text-ink">{inv.invoiceNumber}</span>
                    </td>
                    <td className="px-6 py-4 text-sm text-ink-soft">{inv.carrierName}</td>
                    <td className="px-6 py-4 text-sm text-ink-soft">{formatDate(inv.invoiceDate)}</td>
                    <td className="px-6 py-4 text-sm text-ink text-right font-medium">
                      {formatCurrency(inv.totalAmount, inv.currency)}
                    </td>
                    <td className="px-6 py-4 text-sm text-ink-soft text-center">{inv.currency}</td>
                    <td className="px-6 py-4 text-center">
                      <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${STATUS_COLORS[inv.status] || 'bg-surface-2 text-ink'}`}>
                        {STATUS_LABELS[inv.status] || inv.status}
                      </span>
                    </td>
                    <td className="px-6 py-4 text-sm text-ink-soft text-right">
                      {inv.variance != null ? `${inv.variancePercent > 0 ? '+' : ''}${inv.variancePercent.toFixed(1)}%` : '—'}
                    </td>
                    <td className="px-6 py-4 text-right" onClick={(e) => e.stopPropagation()}>
                      <div className="flex items-center justify-end gap-1">
                        {(inv.status === 'DRAFT' || inv.status === 'RECEIVED') && (
                          <button
                            onClick={() => updateStatusMutation.mutate({ id: inv.id, status: 'UNDER_REVIEW' })}
                            disabled={updateStatusMutation.isPending}
                            className="p-1.5 rounded-lg text-accent hover:text-accent-strong hover:bg-accent-soft transition-colors"
                            title="Soumettre"
                          >
                            <Send size={16} />
                          </button>
                        )}
                        {inv.status === 'UNDER_REVIEW' && (
                          <>
                            <button
                              onClick={() => updateStatusMutation.mutate({ id: inv.id, status: 'APPROVED' })}
                              disabled={updateStatusMutation.isPending}
                              className="p-1.5 rounded-lg text-success hover:text-success hover:bg-success/10 transition-colors"
                              title="Approuver"
                            >
                              <CheckCircle size={16} />
                            </button>
                            <button
                              onClick={() => updateStatusMutation.mutate({ id: inv.id, status: 'REJECTED' })}
                              disabled={updateStatusMutation.isPending}
                              className="p-1.5 rounded-lg text-danger hover:text-danger hover:bg-danger/10 transition-colors"
                              title="Rejeter"
                            >
                              <XCircle size={16} />
                            </button>
                          </>
                        )}
                        {inv.status === 'APPROVED' && (
                          <button
                            onClick={() => updateStatusMutation.mutate({ id: inv.id, status: 'PAID' })}
                            disabled={updateStatusMutation.isPending}
                            className="p-1.5 rounded-lg text-success hover:text-success hover:bg-success/10 transition-colors"
                            title="Marquer payé"
                          >
                            <DollarSign size={16} />
                          </button>
                        )}
                        {inv.status !== 'DRAFT' && inv.status !== 'PAID' && inv.status !== 'REJECTED' && (
                          <button
                            onClick={() => setDisputeTarget(inv.id)}
                            className="p-1.5 rounded-lg text-warning hover:text-warning hover:bg-warning/10 transition-colors"
                            title="Signaler un litige"
                          >
                            <AlertTriangle size={16} />
                          </button>
                        )}
                        {(inv.status === 'DRAFT' || inv.status === 'RECEIVED') && (
                          deleteConfirm === inv.id ? (
                            <div className="flex items-center gap-1">
                              <button
                                onClick={() => deleteMutation.mutate(inv.id)}
                                disabled={deleteMutation.isPending}
                                className="px-2 py-1 text-xs bg-danger text-white rounded hover:bg-danger/90 transition-colors"
                              >
                                {deleteMutation.isPending ? <Loader2 size={12} className="animate-spin" /> : 'Oui'}
                              </button>
                              <button
                                onClick={() => setDeleteConfirm(null)}
                                className="px-2 py-1 text-xs bg-surface-2 text-ink-soft rounded hover:bg-line transition-colors"
                              >
                                Non
                              </button>
                            </div>
                          ) : (
                            <button
                              onClick={() => setDeleteConfirm(inv.id)}
                              className="p-1.5 rounded-lg text-ink-soft hover:text-danger hover:bg-danger/10 transition-colors"
                              title="Supprimer"
                            >
                              <Trash2 size={16} />
                            </button>
                          )
                        )}
                        <button
                          onClick={() => setSelectedInvoice(inv)}
                          className="p-1.5 rounded-lg text-ink-soft hover:text-accent hover:bg-accent-soft transition-colors"
                          title="Voir les détails"
                        >
                          <Eye size={16} />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />

      {/* Create Modal */}
      {createOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div className="absolute inset-0 bg-black/40" onClick={() => setCreateOpen(false)} />
          <div className="relative bg-surface rounded-xl shadow-2xl w-full max-w-2xl mx-4 p-6 max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-lg font-semibold text-ink">Nouvelle facture</h3>
              <button onClick={() => setCreateOpen(false)} className="text-ink-soft hover:text-ink-soft">
                <X size={20} />
              </button>
            </div>
            <form onSubmit={handleCreate} className="space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-ink-soft mb-1">N° facture</label>
                  <input
                    type="text"
                    value={form.invoiceNumber}
                    onChange={(e) => setForm({ ...form, invoiceNumber: e.target.value })}
                    className="w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                    required
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-ink-soft mb-1">Nom transporteur</label>
                  <input
                    type="text"
                    value={form.carrierName}
                    onChange={(e) => setForm({ ...form, carrierName: e.target.value })}
                    className="w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                    required
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-ink-soft mb-1">Date facture</label>
                  <input
                    type="date"
                    value={form.invoiceDate}
                    onChange={(e) => setForm({ ...form, invoiceDate: e.target.value })}
                    className="w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                    required
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-ink-soft mb-1">Échéance</label>
                  <input
                    type="date"
                    value={form.dueDate}
                    onChange={(e) => setForm({ ...form, dueDate: e.target.value })}
                    className="w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-ink-soft mb-1">Référence transporteur</label>
                  <input
                    type="text"
                    value={form.carrierReference}
                    onChange={(e) => setForm({ ...form, carrierReference: e.target.value })}
                    className="w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-ink-soft mb-1">Référence expédition</label>
                  <input
                    type="text"
                    value={form.shipmentReference}
                    onChange={(e) => setForm({ ...form, shipmentReference: e.target.value })}
                    className="w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                  />
                </div>
              </div>

              <div className="border-t border-line pt-4">
                <p className="text-sm font-medium text-ink-soft mb-3">Montants</p>
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                  <div>
                    <label className="block text-xs text-ink-soft mb-1">Montant total</label>
                    <input
                      type="number"
                      step="0.01"
                      value={form.totalAmount || ''}
                      onChange={(e) => setForm({ ...form, totalAmount: parseFloat(e.target.value) || 0 })}
                      className="w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                      required
                    />
                  </div>
                  <div>
                    <label className="block text-xs text-ink-soft mb-1">Devise</label>
                    <select
                      value={form.currency}
                      onChange={(e) => setForm({ ...form, currency: e.target.value })}
                      className="w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                    >
                      <option value="USD">USD</option>
                      <option value="EUR">EUR</option>
                      <option value="GBP">GBP</option>
                      <option value="CHF">CHF</option>
                    </select>
                  </div>
                  <div>
                    <label className="block text-xs text-ink-soft mb-1">Montant EUR</label>
                    <input
                      type="number"
                      step="0.01"
                      value={form.totalAmountEur || ''}
                      onChange={(e) => setForm({ ...form, totalAmountEur: parseFloat(e.target.value) || 0 })}
                      className="w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                    />
                  </div>
                </div>
              </div>

              <div className="border-t border-line pt-4">
                <p className="text-sm font-medium text-ink-soft mb-3">Détail des frais</p>
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                  <div>
                    <label className="block text-xs text-ink-soft mb-1">Fret</label>
                    <input
                      type="number"
                      step="0.01"
                      value={form.freightAmount || ''}
                      onChange={(e) => setForm({ ...form, freightAmount: parseFloat(e.target.value) || 0 })}
                      className="w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                    />
                  </div>
                  <div>
                    <label className="block text-xs text-ink-soft mb-1">Surcharge carburant</label>
                    <input
                      type="number"
                      step="0.01"
                      value={form.fuelSurcharge || ''}
                      onChange={(e) => setForm({ ...form, fuelSurcharge: parseFloat(e.target.value) || 0 })}
                      className="w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                    />
                  </div>
                  <div>
                    <label className="block text-xs text-ink-soft mb-1">Frais sécurité</label>
                    <input
                      type="number"
                      step="0.01"
                      value={form.securityFee || ''}
                      onChange={(e) => setForm({ ...form, securityFee: parseFloat(e.target.value) || 0 })}
                      className="w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                    />
                  </div>
                  <div>
                    <label className="block text-xs text-ink-soft mb-1">Frais manutention</label>
                    <input
                      type="number"
                      step="0.01"
                      value={form.handlingFee || ''}
                      onChange={(e) => setForm({ ...form, handlingFee: parseFloat(e.target.value) || 0 })}
                      className="w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                    />
                  </div>
                  <div>
                    <label className="block text-xs text-ink-soft mb-1">Frais douane</label>
                    <input
                      type="number"
                      step="0.01"
                      value={form.customsFee || ''}
                      onChange={(e) => setForm({ ...form, customsFee: parseFloat(e.target.value) || 0 })}
                      className="w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                    />
                  </div>
                  <div>
                    <label className="block text-xs text-ink-soft mb-1">Autres frais</label>
                    <input
                      type="number"
                      step="0.01"
                      value={form.otherCharges || ''}
                      onChange={(e) => setForm({ ...form, otherCharges: parseFloat(e.target.value) || 0 })}
                      className="w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                    />
                  </div>
                </div>
                {form.otherCharges > 0 && (
                  <div className="mt-3">
                    <label className="block text-xs text-ink-soft mb-1">Description autres frais</label>
                    <input
                      type="text"
                      value={form.otherChargesDescription}
                      onChange={(e) => setForm({ ...form, otherChargesDescription: e.target.value })}
                      className="w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                    />
                  </div>
                )}
              </div>

              <div className="border-t border-line pt-4">
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-medium text-ink-soft mb-1">Taux négocié</label>
                    <input
                      type="number"
                      step="0.01"
                      value={form.negotiatedRate || ''}
                      onChange={(e) => setForm({ ...form, negotiatedRate: parseFloat(e.target.value) || 0 })}
                      className="w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-ink-soft mb-1">Notes</label>
                    <input
                      type="text"
                      value={form.reconciliationNotes}
                      onChange={(e) => setForm({ ...form, reconciliationNotes: e.target.value })}
                      className="w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                    />
                  </div>
                </div>
              </div>

              <div className="flex gap-3 pt-2">
                <button
                  type="button"
                  onClick={() => setCreateOpen(false)}
                  className="flex-1 px-4 py-2 border border-line rounded-lg text-sm font-medium text-ink-soft hover:bg-surface-2 transition-colors"
                >
                  Annuler
                </button>
                <button
                  type="submit"
                  disabled={createMutation.isPending}
                  className="flex-1 px-4 py-2 bg-accent text-white rounded-lg text-sm font-medium hover:bg-accent-strong disabled:opacity-50 transition-colors flex items-center justify-center gap-2"
                >
                  {createMutation.isPending && <Loader2 size={14} className="animate-spin" />}
                  Créer la facture
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Dispute Modal */}
      {disputeTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div className="absolute inset-0 bg-black/40" onClick={() => { setDisputeTarget(null); setDisputeReason(''); }} />
          <div className="relative bg-surface rounded-xl shadow-2xl w-full max-w-md mx-4 p-6">
            <h3 className="text-lg font-semibold text-ink mb-4">Signaler un litige</h3>
            <div>
              <label className="block text-sm font-medium text-ink-soft mb-1">Raison du litige</label>
              <textarea
                value={disputeReason}
                onChange={(e) => setDisputeReason(e.target.value)}
                className="w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                rows={3}
                placeholder="Décrivez le motif du litige..."
              />
            </div>
            <div className="flex gap-3 pt-4">
              <button
                type="button"
                onClick={() => { setDisputeTarget(null); setDisputeReason(''); }}
                className="flex-1 px-4 py-2 border border-line rounded-lg text-sm font-medium text-ink-soft hover:bg-surface-2 transition-colors"
              >
                Annuler
              </button>
              <button
                onClick={() => disputeMutation.mutate({ id: disputeTarget, status: 'DISPUTED', reason: disputeReason })}
                disabled={disputeMutation.isPending}
                className="flex-1 px-4 py-2 bg-warning text-white rounded-lg text-sm font-medium hover:bg-warning/90 disabled:opacity-50 transition-colors flex items-center justify-center gap-2"
              >
                {disputeMutation.isPending && <Loader2 size={14} className="animate-spin" />}
                Signaler le litige
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Detail / Reconciliation Panel */}
      {selectedInvoice && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div className="absolute inset-0 bg-black/40" onClick={() => setSelectedInvoice(null)} />
          <div className="relative bg-surface rounded-xl shadow-2xl w-full max-w-3xl mx-4 max-h-[90vh] overflow-y-auto">
            <div className="sticky top-0 bg-surface border-b border-line px-6 py-4 flex items-center justify-between z-10">
              <div>
                <h3 className="text-lg font-semibold text-ink">
                  Facture {selectedInvoice.invoiceNumber}
                </h3>
                <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium mt-1 ${STATUS_COLORS[selectedInvoice.status] || 'bg-surface-2 text-ink'}`}>
                  {STATUS_LABELS[selectedInvoice.status] || selectedInvoice.status}
                </span>
              </div>
              <button onClick={() => setSelectedInvoice(null)} className="text-ink-soft hover:text-ink-soft">
                <X size={20} />
              </button>
            </div>

            <div className="p-6 space-y-6">
              {/* General Info */}
              <div>
                <h4 className="text-sm font-medium text-ink-soft mb-3">Informations générales</h4>
                <div className="grid grid-cols-2 sm:grid-cols-3 gap-4">
                  <div>
                    <p className="text-xs text-ink-soft">Transporteur</p>
                    <p className="text-sm font-medium text-ink">{selectedInvoice.carrierName}</p>
                  </div>
                  <div>
                    <p className="text-xs text-ink-soft">Réf. transporteur</p>
                    <p className="text-sm font-medium text-ink">{selectedInvoice.carrierReference || '—'}</p>
                  </div>
                  <div>
                    <p className="text-xs text-ink-soft">Réf. expédition</p>
                    <p className="text-sm font-medium text-ink">{selectedInvoice.shipmentReference || '—'}</p>
                  </div>
                  <div>
                    <p className="text-xs text-ink-soft">Date facture</p>
                    <p className="text-sm font-medium text-ink">{formatDate(selectedInvoice.invoiceDate)}</p>
                  </div>
                  <div>
                    <p className="text-xs text-ink-soft">Échéance</p>
                    <p className="text-sm font-medium text-ink">{formatDate(selectedInvoice.dueDate)}</p>
                  </div>
                  <div>
                    <p className="text-xs text-ink-soft">Créée le</p>
                    <p className="text-sm font-medium text-ink">{formatDate(selectedInvoice.createdAt)}</p>
                  </div>
                </div>
              </div>

              {/* Invoice Breakdown */}
              <div className="border-t border-line pt-4">
                <h4 className="text-sm font-medium text-ink-soft mb-3">Ventilation de la facture</h4>
                <div className="bg-surface-2 rounded-lg p-4 space-y-2">
                  <div className="flex justify-between text-sm">
                    <span className="text-ink-soft">Fret</span>
                    <span className="font-medium text-ink">{formatCurrency(selectedInvoice.freightAmount, selectedInvoice.currency)}</span>
                  </div>
                  <div className="flex justify-between text-sm">
                    <span className="text-ink-soft">Surcharge carburant</span>
                    <span className="font-medium text-ink">{formatCurrency(selectedInvoice.fuelSurcharge, selectedInvoice.currency)}</span>
                  </div>
                  <div className="flex justify-between text-sm">
                    <span className="text-ink-soft">Frais sécurité</span>
                    <span className="font-medium text-ink">{formatCurrency(selectedInvoice.securityFee, selectedInvoice.currency)}</span>
                  </div>
                  <div className="flex justify-between text-sm">
                    <span className="text-ink-soft">Frais manutention</span>
                    <span className="font-medium text-ink">{formatCurrency(selectedInvoice.handlingFee, selectedInvoice.currency)}</span>
                  </div>
                  <div className="flex justify-between text-sm">
                    <span className="text-ink-soft">Frais douane</span>
                    <span className="font-medium text-ink">{formatCurrency(selectedInvoice.customsFee, selectedInvoice.currency)}</span>
                  </div>
                  {selectedInvoice.otherCharges > 0 && (
                    <div className="flex justify-between text-sm">
                      <span className="text-ink-soft">Autres frais{selectedInvoice.otherChargesDescription ? ` (${selectedInvoice.otherChargesDescription})` : ''}</span>
                      <span className="font-medium text-ink">{formatCurrency(selectedInvoice.otherCharges, selectedInvoice.currency)}</span>
                    </div>
                  )}
                  <div className="border-t border-line pt-2 flex justify-between text-sm font-semibold">
                    <span className="text-ink">Total</span>
                    <span className="text-ink">{formatCurrency(selectedInvoice.totalAmount, selectedInvoice.currency)}</span>
                  </div>
                  {selectedInvoice.currency !== 'EUR' && (
                    <div className="flex justify-between text-sm text-ink-soft">
                      <span>Montant EUR</span>
                      <span>{formatCurrency(selectedInvoice.totalAmountEur, 'EUR')}</span>
                    </div>
                  )}
                </div>
              </div>

              {/* Reconciliation */}
              <div className="border-t border-line pt-4">
                <h4 className="text-sm font-medium text-ink-soft mb-3">Réconciliation</h4>
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                  <div className="bg-surface-2 rounded-lg p-4">
                    <p className="text-xs text-ink-soft">Taux négocié</p>
                    <p className="text-lg font-bold text-ink">
                      {selectedInvoice.negotiatedRate ? formatCurrency(selectedInvoice.negotiatedRate, selectedInvoice.currency) : '—'}
                    </p>
                  </div>
                  <div className="bg-surface-2 rounded-lg p-4">
                    <p className="text-xs text-ink-soft">Montant facturé</p>
                    <p className="text-lg font-bold text-ink">{formatCurrency(selectedInvoice.totalAmount, selectedInvoice.currency)}</p>
                  </div>
                  <div className="bg-surface-2 rounded-lg p-4">
                    <p className="text-xs text-ink-soft">Écart</p>
                    <p className={`text-lg font-bold ${selectedInvoice.variance > 0 ? 'text-danger' : selectedInvoice.variance < 0 ? 'text-success' : 'text-ink'}`}>
                      {selectedInvoice.variance != null ? (
                        <>
                          {selectedInvoice.variance > 0 ? '+' : ''}{formatCurrency(selectedInvoice.variance, selectedInvoice.currency)}
                          <span className="text-sm font-normal text-ink-soft ml-2">
                            ({selectedInvoice.variancePercent > 0 ? '+' : ''}{selectedInvoice.variancePercent.toFixed(1)}%)
                          </span>
                        </>
                      ) : '—'}
                    </p>
                  </div>
                </div>
                {selectedInvoice.reconciliationNotes && (
                  <div className="mt-4">
                    <p className="text-xs text-ink-soft mb-1">Notes de réconciliation</p>
                    <p className="text-sm text-ink-soft bg-surface-2 rounded-lg p-3">{selectedInvoice.reconciliationNotes}</p>
                  </div>
                )}
              </div>

              {/* Approval / Payment Info */}
              {(selectedInvoice.approvedAt || selectedInvoice.paidAt || selectedInvoice.disputeReason) && (
                <div className="border-t border-line pt-4">
                  <h4 className="text-sm font-medium text-ink-soft mb-3">Historique</h4>
                  <div className="space-y-2">
                    {selectedInvoice.approvedAt && (
                      <div className="flex items-center gap-2 text-sm text-ink-soft">
                        <CheckCircle size={14} className="text-success" />
                        Approuvée le {formatDate(selectedInvoice.approvedAt)}
                      </div>
                    )}
                    {selectedInvoice.paidAt && (
                      <div className="flex items-center gap-2 text-sm text-ink-soft">
                        <DollarSign size={14} className="text-success" />
                        Payée le {formatDate(selectedInvoice.paidAt)}
                      </div>
                    )}
                    {selectedInvoice.disputeReason && (
                      <div className="flex items-start gap-2 text-sm text-danger bg-danger/10 rounded-lg p-3">
                        <AlertTriangle size={14} className="mt-0.5 shrink-0" />
                        <span>Litige : {selectedInvoice.disputeReason}</span>
                      </div>
                    )}
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default CarrierInvoicing;
