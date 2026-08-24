import type { AxiosError } from 'axios';
import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  FileText, Plus, Trash2, Loader2, Send, CheckCircle, XCircle,
  Clock, AlertTriangle, X, Receipt, CreditCard,
} from 'lucide-react';
import toast from 'react-hot-toast';
import { incokalkAPI } from '../lib/api';

interface ClientInvoice {
  id: string;
  invoiceNumber: string;
  invoiceDate: string;
  dueDate: string;
  status: string;
  clientName: string;
  clientEmail: string;
  subtotal: number;
  vatAmount: number;
  totalAmount: number;
  amountPaid: number;
  balanceDue: number;
  currency: string;
  earlyPaymentDiscountAmount: number;
  earlyPaymentDiscountDeadline: string;
  lateFeeApplied: boolean;
  paymentReference: string;
  paidAt: string;
  notes: string;
  createdAt: string;
}

interface InvoiceStats {
  total: number;
  sent: number;
  paid: number;
  overdue: number;
  totalRevenue: number;
  totalPaid: number;
  totalOutstanding: number;
}

const STATUS_LABELS: Record<string, string> = {
  DRAFT: 'Brouillon',
  SENT: 'Envoyée',
  VIEWED: 'Consultée',
  PAID: 'Payée',
  PARTIALLY_PAID: 'Partiellement payée',
  OVERDUE: 'En retard',
  CANCELLED: 'Annulée',
};

const STATUS_COLORS: Record<string, string> = {
  DRAFT: 'bg-surface-2 text-ink',
  SENT: 'bg-accent-soft text-accent-strong',
  VIEWED: 'bg-success/10 text-success',
  PAID: 'bg-success/10 text-success',
  PARTIALLY_PAID: 'bg-warning/10 text-warning',
  OVERDUE: 'bg-danger/10 text-danger',
  CANCELLED: 'bg-surface-2 text-ink',
};

const emptyForm = {
  invoiceNumber: '',
  invoiceDate: '',
  dueDate: '',
  clientName: '',
  clientEmail: '',
  subtotal: 0,
  vatAmount: 0,
  totalAmount: 0,
  currency: 'EUR',
  earlyPaymentDiscountAmount: 0,
  earlyPaymentDiscountDeadline: '',
  notes: '',
};

const ClientInvoicing = () => {
  const queryClient = useQueryClient();
  const [createOpen, setCreateOpen] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [deleteConfirm, setDeleteConfirm] = useState<string | null>(null);
  const [paymentTarget, setPaymentTarget] = useState<ClientInvoice | null>(null);
  const [paymentAmount, setPaymentAmount] = useState<number>(0);
  const [paymentReference, setPaymentReference] = useState('');

  const { data: invoicesData, isLoading: invoicesLoading } = useQuery({
    queryKey: ['client-invoices'],
    queryFn: async () => {
      const res = await incokalkAPI.clientInvoices.list();
      return res.data as ClientInvoice[] | { invoices: ClientInvoice[] };
    },
  });

  const invoices = Array.isArray(invoicesData) ? invoicesData : invoicesData?.invoices ?? [];

  const { data: stats } = useQuery({
    queryKey: ['client-invoice-stats'],
    queryFn: async () => {
      const res = await incokalkAPI.clientInvoices.stats();
      return res.data as InvoiceStats;
    },
  });

  const createMutation = useMutation({
    mutationFn: (data: typeof form) => incokalkAPI.clientInvoices.create(data),
    onSuccess: () => {
      toast.success('Facture créée avec succès');
      setCreateOpen(false);
      setForm(emptyForm);
      queryClient.invalidateQueries({ queryKey: ['client-invoices'] });
      queryClient.invalidateQueries({ queryKey: ['client-invoice-stats'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || 'Erreur lors de la création');
    },
  });

  const updateStatusMutation = useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) =>
      incokalkAPI.clientInvoices.updateStatus(id, { status }),
    onSuccess: () => {
      toast.success('Statut mis à jour');
      queryClient.invalidateQueries({ queryKey: ['client-invoices'] });
      queryClient.invalidateQueries({ queryKey: ['client-invoice-stats'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || 'Erreur lors de la mise à jour');
    },
  });

  const recordPaymentMutation = useMutation({
    mutationFn: ({ id, amount, reference }: { id: string; amount: number; reference: string }) =>
      incokalkAPI.clientInvoices.recordPayment(id, { amount, reference }),
    onSuccess: () => {
      toast.success('Paiement enregistré');
      setPaymentTarget(null);
      setPaymentAmount(0);
      setPaymentReference('');
      queryClient.invalidateQueries({ queryKey: ['client-invoices'] });
      queryClient.invalidateQueries({ queryKey: ['client-invoice-stats'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || "Erreur lors de l'enregistrement du paiement");
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => incokalkAPI.clientInvoices.delete(id),
    onSuccess: () => {
      toast.success('Facture supprimée');
      setDeleteConfirm(null);
      queryClient.invalidateQueries({ queryKey: ['client-invoices'] });
      queryClient.invalidateQueries({ queryKey: ['client-invoice-stats'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || 'Erreur lors de la suppression');
    },
  });

  const handleCreate = (e: React.FormEvent) => {
    e.preventDefault();
    createMutation.mutate(form);
  };

  const handleRecordPayment = () => {
    if (!paymentTarget || paymentAmount <= 0) return;
    recordPaymentMutation.mutate({
      id: paymentTarget.id,
      amount: paymentAmount,
      reference: paymentReference,
    });
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
          <h1 className="text-2xl font-bold text-ink">
            <span className="text-accent font-normal" aria-hidden="true">:: </span>
            Facturation clients
          </h1>
          <p className="text-ink-soft mt-1">Comptabilité et facturation client (Accounts Receivable)</p>
        </div>
        <button
          onClick={() => setCreateOpen(true)}
          className="flex items-center gap-2 bg-accent text-white px-4 py-2 rounded-none font-medium hover:bg-accent-strong transition-colors"
        >
          <Plus size={18} />
          Nouvelle facture
        </button>
      </div>

      {/* Stats cards */}
      <div className="grid grid-cols-1 sm:grid-cols-4 gap-4 mb-8">
        <div className="bg-surface rounded-none border border-line p-5">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-none bg-accent-soft flex items-center justify-center">
              <FileText size={20} className="text-accent" />
            </div>
            <div>
              <p className="text-sm text-ink-soft">Total factures</p>
              <p className="text-2xl font-bold text-ink">{stats?.total ?? '—'}</p>
            </div>
          </div>
        </div>
        <div className="bg-surface rounded-none border border-line p-5">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-none bg-warning/10 flex items-center justify-center">
              <Clock size={20} className="text-warning" />
            </div>
            <div>
              <p className="text-sm text-ink-soft">En attente</p>
              <p className="text-2xl font-bold text-ink">{stats?.sent ?? '—'}</p>
            </div>
          </div>
        </div>
        <div className="bg-surface rounded-none border border-line p-5">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-none bg-success/10 flex items-center justify-center">
              <CheckCircle size={20} className="text-success" />
            </div>
            <div>
              <p className="text-sm text-ink-soft">Payées</p>
              <p className="text-2xl font-bold text-ink">{stats?.paid ?? '—'}</p>
            </div>
          </div>
        </div>
        <div className="bg-surface rounded-none border border-line p-5">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-none bg-danger/10 flex items-center justify-center">
              <AlertTriangle size={20} className="text-danger" />
            </div>
            <div>
              <p className="text-sm text-ink-soft">En retard</p>
              <p className="text-2xl font-bold text-ink">{stats?.overdue ?? '—'}</p>
            </div>
          </div>
        </div>
      </div>

      {/* Table */}
      <div className="bg-surface rounded-none border border-line overflow-hidden">
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
                <tr className="bg-bg border-b border-line">
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">N° facture</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Client</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Date facture</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Échéance</th>
                  <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Montant</th>
                  <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Payé</th>
                  <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Solde</th>
                  <th className="text-center text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Statut</th>
                  <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {invoices.map((inv: ClientInvoice) => (
                  <tr key={inv.id} className="hover:bg-bg transition-colors">
                    <td className="px-6 py-4">
                      <span className="text-sm font-medium text-ink">{inv.invoiceNumber}</span>
                    </td>
                    <td className="px-6 py-4">
                      <div>
                        <p className="text-sm font-medium text-ink">{inv.clientName}</p>
                        <p className="text-xs text-ink-soft">{inv.clientEmail}</p>
                      </div>
                    </td>
                    <td className="px-6 py-4 text-sm text-ink-soft">{formatDate(inv.invoiceDate)}</td>
                    <td className="px-6 py-4 text-sm text-ink-soft">{formatDate(inv.dueDate)}</td>
                    <td className="px-6 py-4 text-sm text-ink text-right font-medium">
                      {formatCurrency(inv.totalAmount, inv.currency)}
                    </td>
                    <td className="px-6 py-4 text-sm text-ink-soft text-right">
                      {formatCurrency(inv.amountPaid, inv.currency)}
                    </td>
                    <td className="px-6 py-4 text-sm text-right font-medium">
                      <span className={inv.balanceDue > 0 ? 'text-danger' : 'text-success'}>
                        {formatCurrency(inv.balanceDue, inv.currency)}
                      </span>
                    </td>
                    <td className="px-6 py-4 text-center">
                      <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${STATUS_COLORS[inv.status] || 'bg-surface-2 text-ink'}`}>
                        {STATUS_LABELS[inv.status] || inv.status}
                      </span>
                    </td>
                    <td className="px-6 py-4 text-right">
                      <div className="flex items-center justify-end gap-1">
                        {inv.status === 'DRAFT' && (
                          <button
                            onClick={() => updateStatusMutation.mutate({ id: inv.id, status: 'SENT' })}
                            disabled={updateStatusMutation.isPending}
                            className="px-2 py-1 text-xs font-medium rounded bg-accent text-white hover:bg-accent-strong transition-colors disabled:opacity-50 flex items-center gap-1"
                            title="Envoyer"
                          >
                            <Send size={12} />
                            Envoyer
                          </button>
                        )}
                        {(inv.status === 'SENT' || inv.status === 'VIEWED') && (
                          <button
                            onClick={() => {
                              setPaymentTarget(inv);
                              setPaymentAmount(inv.balanceDue);
                              setPaymentReference('');
                            }}
                            disabled={recordPaymentMutation.isPending}
                            className="px-2 py-1 text-xs font-medium rounded bg-success text-white hover:bg-success/90 transition-colors disabled:opacity-50 flex items-center gap-1"
                            title="Enregistrer paiement"
                          >
                            <CreditCard size={12} />
                            Enregistrer paiement
                          </button>
                        )}
                        {inv.status !== 'CANCELLED' && inv.status !== 'PAID' && inv.status !== 'DRAFT' && (
                          <button
                            onClick={() => updateStatusMutation.mutate({ id: inv.id, status: 'CANCELLED' })}
                            disabled={updateStatusMutation.isPending}
                            className="p-1.5 rounded-none text-ink-soft hover:text-danger hover:bg-danger/10 transition-colors"
                            title="Annuler"
                          >
                            <XCircle size={16} />
                          </button>
                        )}
                        {inv.status === 'DRAFT' && (
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
                              className="p-1.5 rounded-none text-ink-soft hover:text-danger hover:bg-danger/10 transition-colors"
                              title="Supprimer"
                            >
                              <Trash2 size={16} />
                            </button>
                          )
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Create Modal */}
      {createOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div className="absolute inset-0 bg-black/40" onClick={() => setCreateOpen(false)} />
          <div className="relative bg-surface rounded-none shadow-2xl w-full max-w-2xl mx-4 p-6 max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-lg font-semibold text-ink">Nouvelle facture</h3>
              <button onClick={() => setCreateOpen(false)} className="text-ink-soft hover:text-ink-soft">
                <X size={20} />
              </button>
            </div>
            <form onSubmit={handleCreate} className="space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">N° facture</label>
                  <input
                    type="text"
                    value={form.invoiceNumber}
                    onChange={(e) => setForm({ ...form, invoiceNumber: e.target.value })}
                    className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                    required
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">Devise</label>
                  <select
                    value={form.currency}
                    onChange={(e) => setForm({ ...form, currency: e.target.value })}
                    className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                  >
                    <option value="EUR">EUR</option>
                    <option value="USD">USD</option>
                    <option value="GBP">GBP</option>
                    <option value="CHF">CHF</option>
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">Date facture</label>
                  <input
                    type="date"
                    value={form.invoiceDate}
                    onChange={(e) => setForm({ ...form, invoiceDate: e.target.value })}
                    className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                    required
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">Date échéance</label>
                  <input
                    type="date"
                    value={form.dueDate}
                    onChange={(e) => setForm({ ...form, dueDate: e.target.value })}
                    className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                    required
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">Nom client</label>
                  <input
                    type="text"
                    value={form.clientName}
                    onChange={(e) => setForm({ ...form, clientName: e.target.value })}
                    className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                    required
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">Email client</label>
                  <input
                    type="email"
                    value={form.clientEmail}
                    onChange={(e) => setForm({ ...form, clientEmail: e.target.value })}
                    className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                    required
                  />
                </div>
              </div>

              <div className="border-t border-line pt-4">
                <p className="text-sm font-medium text-ink mb-3">Montants</p>
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                  <div>
                    <label className="block text-xs text-ink-soft mb-1">Sous-total</label>
                    <input
                      type="number"
                      step="0.01"
                      value={form.subtotal || ''}
                      onChange={(e) => {
                        const subtotal = parseFloat(e.target.value) || 0;
                        setForm({ ...form, subtotal, totalAmount: subtotal + form.vatAmount });
                      }}
                      className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                      required
                    />
                  </div>
                  <div>
                    <label className="block text-xs text-ink-soft mb-1">TVA</label>
                    <input
                      type="number"
                      step="0.01"
                      value={form.vatAmount || ''}
                      onChange={(e) => {
                        const vatAmount = parseFloat(e.target.value) || 0;
                        setForm({ ...form, vatAmount, totalAmount: form.subtotal + vatAmount });
                      }}
                      className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                    />
                  </div>
                  <div>
                    <label className="block text-xs text-ink-soft mb-1">Montant total</label>
                    <input
                      type="number"
                      step="0.01"
                      value={form.totalAmount || ''}
                      onChange={(e) => setForm({ ...form, totalAmount: parseFloat(e.target.value) || 0 })}
                      className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                      required
                    />
                  </div>
                </div>
              </div>

              <div className="border-t border-line pt-4">
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-medium text-ink mb-1">Condition de paiement</label>
                    <select
                      value={''}
                      onChange={(e) => setForm({ ...form, notes: e.target.value ? `Condition: ${e.target.value}\n${form.notes}` : form.notes })}
                      className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                    >
                      <option value="">—</option>
                      <option value="Net 30">Net 30 jours</option>
                      <option value="Net 60">Net 60 jours</option>
                      <option value="Net 90">Net 90 jours</option>
                      <option value="Comptant">Comptant</option>
                      <option value="Acompte 50%">Acompte 50%</option>
                    </select>
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-ink mb-1">Remise anticipée (€)</label>
                    <input
                      type="number"
                      step="0.01"
                      value={form.earlyPaymentDiscountAmount || ''}
                      onChange={(e) => setForm({ ...form, earlyPaymentDiscountAmount: parseFloat(e.target.value) || 0 })}
                      className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                    />
                  </div>
                  <div className="sm:col-span-2">
                    <label className="block text-sm font-medium text-ink mb-1">Date limite remise anticipée</label>
                    <input
                      type="date"
                      value={form.earlyPaymentDiscountDeadline}
                      onChange={(e) => setForm({ ...form, earlyPaymentDiscountDeadline: e.target.value })}
                      className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                    />
                  </div>
                  <div className="sm:col-span-2">
                    <label className="block text-sm font-medium text-ink mb-1">Notes</label>
                    <textarea
                      value={form.notes}
                      onChange={(e) => setForm({ ...form, notes: e.target.value })}
                      className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                      rows={3}
                      placeholder="Notes ou commentaires..."
                    />
                  </div>
                </div>
              </div>

              <div className="flex gap-3 pt-2">
                <button
                  type="button"
                  onClick={() => setCreateOpen(false)}
                  className="flex-1 px-4 py-2 border border-line rounded-none text-sm font-medium text-ink hover:bg-bg transition-colors"
                >
                  Annuler
                </button>
                <button
                  type="submit"
                  disabled={createMutation.isPending}
                  className="flex-1 px-4 py-2 bg-accent text-white rounded-none text-sm font-medium hover:bg-accent-strong disabled:opacity-50 transition-colors flex items-center justify-center gap-2"
                >
                  {createMutation.isPending && <Loader2 size={14} className="animate-spin" />}
                  Créer la facture
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Record Payment Modal */}
      {paymentTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div className="absolute inset-0 bg-black/40" onClick={() => { setPaymentTarget(null); setPaymentAmount(0); setPaymentReference(''); }} />
          <div className="relative bg-surface rounded-none shadow-2xl w-full max-w-md mx-4 p-6">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-lg font-semibold text-ink">Enregistrer un paiement</h3>
              <button onClick={() => { setPaymentTarget(null); setPaymentAmount(0); setPaymentReference(''); }} className="text-ink-soft hover:text-ink-soft">
                <X size={20} />
              </button>
            </div>
            <div className="bg-bg rounded-none p-4 mb-4">
              <p className="text-sm text-ink-soft">Facture</p>
              <p className="text-sm font-medium text-ink">{paymentTarget.invoiceNumber} — {paymentTarget.clientName}</p>
              <p className="text-sm text-ink-soft mt-1">Solde dû : <span className="font-medium text-danger">{formatCurrency(paymentTarget.balanceDue, paymentTarget.currency)}</span></p>
            </div>
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-ink mb-1">Montant</label>
                <input
                  type="number"
                  step="0.01"
                  min="0"
                  value={paymentAmount || ''}
                  onChange={(e) => setPaymentAmount(parseFloat(e.target.value) || 0)}
                  className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-ink mb-1">Référence paiement</label>
                <input
                  type="text"
                  value={paymentReference}
                  onChange={(e) => setPaymentReference(e.target.value)}
                  className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                  placeholder="Ex: VIR-2024-001"
                />
              </div>
            </div>
            <div className="flex gap-3 pt-4">
              <button
                type="button"
                onClick={() => { setPaymentTarget(null); setPaymentAmount(0); setPaymentReference(''); }}
                className="flex-1 px-4 py-2 border border-line rounded-none text-sm font-medium text-ink hover:bg-bg transition-colors"
              >
                Annuler
              </button>
              <button
                onClick={handleRecordPayment}
                disabled={recordPaymentMutation.isPending || paymentAmount <= 0}
                className="flex-1 px-4 py-2 bg-success text-white rounded-none text-sm font-medium hover:bg-success/90 disabled:opacity-50 transition-colors flex items-center justify-center gap-2"
              >
                {recordPaymentMutation.isPending && <Loader2 size={14} className="animate-spin" />}
                Enregistrer le paiement
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default ClientInvoicing;
