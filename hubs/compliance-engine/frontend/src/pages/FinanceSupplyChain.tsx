import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  DollarSign,
  TrendingDown,
  CheckCircle,
  XCircle,
  Clock,
  Plus,
  ArrowUpRight,
  Loader2,
} from 'lucide-react';
import { incokalkAPI } from '../lib/api';
import { useAuthStore } from '../stores/auth';

interface FinanceStats {
  totalFinanced: number;
  pendingRequests: number;
  fundedAmount: number;
  avgFeePercent: number;
}

interface FinancingRequest {
  id: string;
  invoiceReference: string;
  amount: number;
  fee: number;
  feePercent: number;
  status: 'PENDING' | 'APPROVED' | 'FUNDED' | 'REPAID' | 'REJECTED';
  createdAt: string;
  updatedAt: string;
}

interface EarlyDiscount {
  discountAmount: number;
  discountPercent: number;
  netAmount: number;
}

const STATUS_CONFIG: Record<string, { label: string; icon: typeof Clock; color: string }> = {
  PENDING: { label: 'En attente', icon: Clock, color: 'bg-warning/10 text-warning' },
  APPROVED: { label: 'Approuvé', icon: CheckCircle, color: 'bg-accent-soft text-accent-strong' },
  FUNDED: { label: 'Financé', icon: DollarSign, color: 'bg-success/10 text-success' },
  REPAID: { label: 'Remboursé', icon: CheckCircle, color: 'bg-surface-2 text-ink' },
  REJECTED: { label: 'Rejeté', icon: XCircle, color: 'bg-danger/10 text-danger' },
};

const formatEUR = (value: number) =>
  new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' }).format(value);

const formatPct = (value: number) =>
  new Intl.NumberFormat('fr-FR', { style: 'percent', minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value / 100);

const FinanceSupplyChain = () => {
  const queryClient = useQueryClient();
  const canEdit = useAuthStore((s) => s.canEdit);
  const [invoiceId, setInvoiceId] = useState('');
  const [reqAmount, setReqAmount] = useState('');
  const [discInvoiceId, setDiscInvoiceId] = useState('');
  const [discAmount, setDiscAmount] = useState('');
  const [showForm, setShowForm] = useState(false);

  const { data: stats, isLoading: statsLoading } = useQuery<FinanceStats>({
    queryKey: ['finance-stats'],
    queryFn: async () => {
      const res = await incokalkAPI.finance.stats();
      return res.data;
    },
  });

  const { data: requests = [], isLoading: requestsLoading } = useQuery<FinancingRequest[]>({
    queryKey: ['finance-requests'],
    queryFn: async () => {
      const res = await incokalkAPI.finance.history();
      return res.data;
    },
  });

  const { data: discount, isLoading: discLoading, refetch: calcDiscount } = useQuery<EarlyDiscount>({
    queryKey: ['finance-discount', discInvoiceId, discAmount],
    queryFn: async () => {
      const res = await incokalkAPI.finance.earlyDiscount(discInvoiceId, Number(discAmount));
      return res.data;
    },
    enabled: false,
  });

  const createRequest = useMutation({
    mutationFn: async (data: { invoiceId: string; amount: number }) => {
      const res = await incokalkAPI.finance.request(data);
      return res.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['finance-requests'] });
      queryClient.invalidateQueries({ queryKey: ['finance-stats'] });
      setShowForm(false);
      setInvoiceId('');
      setReqAmount('');
    },
  });

  const approveRequest = useMutation({
    mutationFn: async (id: string) => {
      const res = await incokalkAPI.finance.approve(id);
      return res.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['finance-requests'] });
      queryClient.invalidateQueries({ queryKey: ['finance-stats'] });
    },
  });

  const fundRequest = useMutation({
    mutationFn: async (id: string) => {
      const res = await incokalkAPI.finance.fund(id);
      return res.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['finance-requests'] });
      queryClient.invalidateQueries({ queryKey: ['finance-stats'] });
    },
  });

  const repayRequest = useMutation({
    mutationFn: async (id: string) => {
      const res = await incokalkAPI.finance.repay(id);
      return res.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['finance-requests'] });
      queryClient.invalidateQueries({ queryKey: ['finance-stats'] });
    },
  });

  const handleSubmitRequest = (e: React.FormEvent) => {
    e.preventDefault();
    if (!invoiceId || !reqAmount) return;
    createRequest.mutate({ invoiceId, amount: Number(reqAmount) });
  };

  const handleCalcDiscount = (e: React.FormEvent) => {
    e.preventDefault();
    if (!discInvoiceId || !discAmount) return;
    calcDiscount();
  };

  return (
    <div className="max-w-6xl mx-auto px-4 py-8">
      {/* Header */}
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-ink">Supply Chain Finance</h1>
        <p className="text-ink-soft mt-1">Gestion du financement de factures et escomptes P4.25</p>
      </div>

      {/* Stats cards */}
      {statsLoading ? (
        <div className="flex items-center justify-center py-12 text-ink-soft">
          <Loader2 size={24} className="animate-spin mr-2" />
          Chargement...
        </div>
      ) : stats ? (
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-8">
          <div className="bg-surface rounded-xl border border-line p-5">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-lg bg-accent-soft flex items-center justify-center">
                <DollarSign size={20} className="text-accent" />
              </div>
              <div>
                <p className="text-sm text-ink-soft">Total financé</p>
                <p className="text-xl font-bold text-ink">{formatEUR(stats.totalFinanced)}</p>
              </div>
            </div>
          </div>
          <div className="bg-surface rounded-xl border border-line p-5">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-lg bg-warning/10 flex items-center justify-center">
                <Clock size={20} className="text-warning" />
              </div>
              <div>
                <p className="text-sm text-ink-soft">Demandes en attente</p>
                <p className="text-xl font-bold text-ink">{stats.pendingRequests}</p>
              </div>
            </div>
          </div>
          <div className="bg-surface rounded-xl border border-line p-5">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-lg bg-success/10 flex items-center justify-center">
                <ArrowUpRight size={20} className="text-success" />
              </div>
              <div>
                <p className="text-sm text-ink-soft">Montant décaissé</p>
                <p className="text-xl font-bold text-ink">{formatEUR(stats.fundedAmount)}</p>
              </div>
            </div>
          </div>
          <div className="bg-surface rounded-xl border border-line p-5">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-lg bg-accent/10 flex items-center justify-center">
                <TrendingDown size={20} className="text-accent" />
              </div>
              <div>
                <p className="text-sm text-ink-soft">Frais moyens</p>
                <p className="text-xl font-bold text-ink">{formatPct(stats.avgFeePercent)}</p>
              </div>
            </div>
          </div>
        </div>
      ) : null}

      {/* Financing request form */}
      <div className="bg-surface rounded-xl border border-line p-6 mb-6">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-semibold text-ink">Demande de financement</h2>
          {canEdit() && (
            <button
              onClick={() => setShowForm(!showForm)}
              className="flex items-center gap-2 px-4 py-2 bg-accent text-white text-sm font-medium rounded-lg hover:bg-accent-strong transition-colors"
            >
              <Plus size={16} />
              Nouvelle demande
            </button>
          )}
        </div>
        {showForm && (
          <form onSubmit={handleSubmitRequest} className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-4 p-4 bg-bg rounded-lg">
            <div>
              <label className="block text-sm font-medium text-ink mb-1">Référence facture</label>
              <input
                type="text"
                value={invoiceId}
                onChange={(e) => setInvoiceId(e.target.value)}
                className="w-full px-3 py-2 border border-line rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-accent"
                placeholder="INV-001"
                required
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-ink mb-1">Montant (EUR)</label>
              <input
                type="number"
                value={reqAmount}
                onChange={(e) => setReqAmount(e.target.value)}
                className="w-full px-3 py-2 border border-line rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-accent"
                placeholder="10000"
                min={0}
                step="0.01"
                required
              />
            </div>
            <div className="flex items-end">
              <button
                type="submit"
                disabled={createRequest.isPending}
                className="px-4 py-2 bg-accent text-white text-sm font-medium rounded-lg hover:bg-accent-strong disabled:opacity-50 transition-colors"
              >
                {createRequest.isPending ? 'Envoi...' : 'Soumettre'}
              </button>
            </div>
          </form>
        )}
      </div>

      {/* Financing requests list */}
      <div className="bg-surface rounded-xl border border-line overflow-hidden mb-8">
        <div className="px-6 py-4 border-b border-line">
          <h2 className="text-lg font-semibold text-ink">Demandes de financement</h2>
        </div>
        {requestsLoading ? (
          <div className="flex items-center justify-center py-12 text-ink-soft">
            <Loader2 size={24} className="animate-spin mr-2" />
            Chargement...
          </div>
        ) : requests.length === 0 ? (
          <div className="px-6 py-12 text-center text-ink-soft">
            <DollarSign size={32} className="mx-auto mb-3 text-ink-soft" />
            <p>Aucune demande de financement</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="bg-bg border-b border-line">
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Facture</th>
                  <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Montant</th>
                  <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Frais</th>
                  <th className="text-center text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Statut</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Dates</th>
                  <th className="text-center text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {requests.map((req) => {
                  const cfg = STATUS_CONFIG[req.status] || STATUS_CONFIG.PENDING;
                  const StatusIcon = cfg.icon;
                  return (
                    <tr key={req.id} className="hover:bg-bg transition-colors">
                      <td className="px-6 py-4 text-sm font-medium text-ink">{req.invoiceReference}</td>
                      <td className="px-6 py-4 text-sm text-ink text-right">{formatEUR(req.amount)}</td>
                      <td className="px-6 py-4 text-sm text-ink text-right">{formatEUR(req.fee)} ({req.feePercent}%)</td>
                      <td className="px-6 py-4 text-center">
                        <span className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium ${cfg.color}`}>
                          <StatusIcon size={12} />
                          {cfg.label}
                        </span>
                      </td>
                      <td className="px-6 py-4 text-sm text-ink-soft">
                        <div>Créé: {new Date(req.createdAt).toLocaleDateString('fr-FR')}</div>
                        <div>MAJ: {new Date(req.updatedAt).toLocaleDateString('fr-FR')}</div>
                      </td>
                      <td className="px-6 py-4 text-center">
                        <div className="flex items-center justify-center gap-2">
                          {req.status === 'PENDING' && (
                            <button
                              onClick={() => approveRequest.mutate(req.id)}
                              disabled={approveRequest.isPending}
                              className="px-3 py-1.5 bg-accent text-white text-xs font-medium rounded-lg hover:bg-accent-strong disabled:opacity-50 transition-colors"
                            >
                              Approuver
                            </button>
                          )}
                          {req.status === 'APPROVED' && (
                            <button
                              onClick={() => fundRequest.mutate(req.id)}
                              disabled={fundRequest.isPending}
                              className="px-3 py-1.5 bg-success text-white text-xs font-medium rounded-lg hover:bg-success/90 disabled:opacity-50 transition-colors"
                            >
                              Financer
                            </button>
                          )}
                          {req.status === 'FUNDED' && (
                            <button
                              onClick={() => repayRequest.mutate(req.id)}
                              disabled={repayRequest.isPending}
                              className="px-3 py-1.5 bg-ink-soft text-white text-xs font-medium rounded-lg hover:bg-ink disabled:opacity-50 transition-colors"
                            >
                              Rembourser
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Early payment discount calculator */}
      <div className="bg-surface rounded-xl border border-line p-6">
        <h2 className="text-lg font-semibold text-ink mb-4">Calculateur d'escompte</h2>
        <form onSubmit={handleCalcDiscount} className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-4">
          <div>
            <label className="block text-sm font-medium text-ink mb-1">Facture</label>
            <input
              type="text"
              value={discInvoiceId}
              onChange={(e) => setDiscInvoiceId(e.target.value)}
              className="w-full px-3 py-2 border border-line rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-accent"
              placeholder="INV-001"
              required
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-ink mb-1">Montant (EUR)</label>
            <input
              type="number"
              value={discAmount}
              onChange={(e) => setDiscAmount(e.target.value)}
              className="w-full px-3 py-2 border border-line rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-accent"
              placeholder="10000"
              min={0}
              step="0.01"
              required
            />
          </div>
          <div className="flex items-end">
            <button
              type="submit"
              disabled={discLoading}
              className="flex items-center gap-2 px-4 py-2 bg-accent text-white text-sm font-medium rounded-lg hover:bg-accent-strong disabled:opacity-50 transition-colors"
            >
              {discLoading ? <Loader2 size={16} className="animate-spin" /> : <TrendingDown size={16} />}
              Calculer
            </button>
          </div>
        </form>
        {discount && (
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 p-4 bg-success/10 rounded-lg">
            <div>
              <p className="text-sm text-ink-soft">Montant de l'escompte</p>
              <p className="text-lg font-bold text-success">{formatEUR(discount.discountAmount)}</p>
            </div>
            <div>
              <p className="text-sm text-ink-soft">Taux d'escompte</p>
              <p className="text-lg font-bold text-ink">{formatPct(discount.discountPercent)}</p>
            </div>
            <div>
              <p className="text-sm text-ink-soft">Net à payer</p>
              <p className="text-lg font-bold text-ink">{formatEUR(discount.netAmount)}</p>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default FinanceSupplyChain;
