import { useState } from 'react';
import { useQuery, useMutation } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import {
  CreditCard, ArrowLeft, ExternalLink, Download, Calendar,
  CheckCircle, XCircle, Clock, AlertTriangle, Settings, Gift, Copy, Check
} from 'lucide-react';
import toast from 'react-hot-toast';
import { incokalkAPI } from '../lib/api';

interface Subscription {
  id: string;
  plan: string;
  status: string;
  billingCycle: string;
  cancelAtPeriodEnd: boolean;
  currentPeriodStart: string;
  currentPeriodEnd: string;
  canceledAt: string;
  createdAt: string;
}

interface Invoice {
  id: string;
  stripeInvoiceId: string;
  amountCents: number;
  currency: string;
  status: string;
  description: string;
  invoiceUrl: string;
  invoicePdfUrl: string;
  periodStart: string;
  periodEnd: string;
  paidAt: string;
  createdAt: string;
}

const statusConfig: Record<string, { color: string; icon: typeof CheckCircle; label: string }> = {
  ACTIVE: { color: 'text-success bg-success/10', icon: CheckCircle, label: 'Actif' },
  PAST_DUE: { color: 'text-warning bg-warning/10', icon: AlertTriangle, label: 'En retard' },
  CANCELED: { color: 'text-danger bg-danger/10', icon: XCircle, label: 'Annulé' },
  UNPAID: { color: 'text-danger bg-danger/10', icon: XCircle, label: 'Impayé' },
  TRIALING: { color: 'text-accent bg-accent-soft', icon: Clock, label: 'Essai' },
};

const planLabels: Record<string, string> = {
  FREE: 'Gratuit',
  STARTER: 'Starter',
  PRO: 'Pro',
  ENTERPRISE: 'Enterprise',
};

export default function Billing() {
  const navigate = useNavigate();
  const [copied, setCopied] = useState(false);

  const { data: subData, isLoading: subLoading } = useQuery<{ stripeConfigured: boolean; subscription: Subscription }>({
    queryKey: ['billing-status'],
    queryFn: async () => {
      const res = await incokalkAPI.billing.status();
      return res.data;
    },
  });

  const { data: companyData } = useQuery<{ referralCode: string }>({
    queryKey: ['company-me'],
    queryFn: async () => {
      const res = await incokalkAPI.companies.me();
      return res.data;
    },
  });

  const { data: invoices = [], isLoading: invLoading } = useQuery<Invoice[]>({
    queryKey: ['billing-invoices'],
    queryFn: async () => {
      const res = await incokalkAPI.billing.invoices();
      return res.data;
    },
  });

  const portalMutation = useMutation({
    mutationFn: async () => {
      const res = await incokalkAPI.billing.portal();
      return res.data;
    },
    onSuccess: (data: { url: string }) => {
      window.location.href = data.url;
    },
    onError: () => {
      toast.error('Erreur lors de l\'ouverture du portail de facturation');
    },
  });

  const subscription = subData?.subscription;
  const stripeConfigured = subData?.stripeConfigured ?? false;
  const referralCode = companyData?.referralCode;
  const referralLink = referralCode ? `${window.location.origin}/register?ref=${referralCode}` : '';

  const copyReferralLink = async () => {
    try {
      await navigator.clipboard.writeText(referralLink);
      setCopied(true);
      toast.success('Lien de parrainage copié !');
      setTimeout(() => setCopied(false), 2000);
    } catch {
      toast.error('Impossible de copier le lien');
    }
  };

  const formatDate = (dateStr: string) => {
    if (!dateStr) return '—';
    return new Date(dateStr).toLocaleDateString('fr-FR', {
      day: 'numeric',
      month: 'long',
      year: 'numeric',
    });
  };

  const formatAmount = (cents: number, currency: string) => {
    return new Intl.NumberFormat('fr-FR', {
      style: 'currency',
      currency: currency || 'EUR',
    }).format(cents / 100);
  };

  if (subLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-accent"></div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-bg">
      <div className="container mx-auto px-4 py-12 max-w-4xl">
        {/* Header */}
        <div className="mb-8">
          <button
            onClick={() => navigate(-1)}
            className="inline-flex items-center gap-2 text-ink-soft hover:text-ink mb-4 transition-colors"
          >
            <ArrowLeft size={16} />
            Retour
          </button>
          <h1 className="text-3xl font-extrabold text-ink">
            <CreditCard className="inline mr-3 text-accent" size={28} />
            Facturation
          </h1>
        </div>

        {/* Stripe not configured */}
        {!stripeConfigured && (
          <div className="bg-warning/10 border border-warning/20 rounded-xl p-6 mb-8">
            <div className="flex items-start gap-3">
              <AlertTriangle className="text-warning shrink-0 mt-0.5" size={20} />
              <div>
                <h3 className="font-semibold text-warning">Stripe non configuré</h3>
                <p className="text-sm text-warning mt-1">
                  Le système de paiement n'est pas encore configuré. Contactez l'administrateur pour activer la facturation.
                </p>
              </div>
            </div>
          </div>
        )}

        {/* Current Plan */}
        <div className="bg-surface rounded-2xl border border-line p-8 mb-8">
          <h2 className="text-xl font-bold text-ink mb-6 flex items-center gap-2">
            <Settings size={20} className="text-ink-soft" />
            Plan actuel
          </h2>

          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div>
              <div className="flex items-center gap-3 mb-2">
                <span className="text-2xl font-extrabold text-ink">
                  {planLabels[subscription?.plan || 'FREE'] || subscription?.plan}
                </span>
                {subscription?.status && (
                  <span className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-semibold ${statusConfig[subscription.status]?.color || 'text-ink-soft bg-surface-2'}`}>
                    {(() => {
                      const StatusIcon = statusConfig[subscription.status]?.icon || CheckCircle;
                      return <StatusIcon size={12} />;
                    })()}
                    {statusConfig[subscription.status]?.label || subscription.status}
                  </span>
                )}
              </div>
              <p className="text-sm text-ink-soft">
                {subscription?.billingCycle === 'ANNUAL' ? 'Facturation annuelle' : 'Facturation mensuelle'}
                {subscription?.currentPeriodEnd && ` — Renouvellement le ${formatDate(subscription.currentPeriodEnd)}`}
              </p>
              {subscription?.cancelAtPeriodEnd && (
                <p className="text-sm text-warning mt-1 font-medium">
                  ⚠️ Annulation prévue le {formatDate(subscription?.currentPeriodEnd || '')}
                </p>
              )}
            </div>

            <div className="flex gap-2">
              {subscription?.plan !== 'FREE' && (
                <button
                  onClick={() => portalMutation.mutate()}
                  disabled={portalMutation.isPending}
                  className="px-4 py-2 rounded-xl text-sm font-semibold border border-line text-ink-soft hover:bg-surface-2 transition-colors inline-flex items-center gap-2"
                >
                  <ExternalLink size={14} />
                  {portalMutation.isPending ? 'Ouverture...' : 'Gérer l\'abonnement'}
                </button>
              )}
              <button
                onClick={() => navigate('/pricing')}
                className="px-4 py-2 rounded-xl text-sm font-semibold bg-accent text-white hover:bg-accent-strong transition-colors"
              >
                {subscription?.plan === 'FREE' ? 'Upgrade' : 'Changer de plan'}
              </button>
            </div>
          </div>
        </div>

        {/* Referral program */}
        {referralCode && (
          <div className="bg-surface rounded-2xl border border-line p-8 mb-8">
            <h2 className="text-xl font-bold text-ink mb-2 flex items-center gap-2">
              <Gift size={20} className="text-accent" />
              Parrainez une entreprise
            </h2>
            <p className="text-sm text-ink-soft mb-4">
              1 mois offert pour vous et pour l'entreprise que vous parrainez, dès son premier abonnement payant.
            </p>
            <div className="flex flex-col sm:flex-row gap-2">
              <input
                type="text"
                readOnly
                value={referralLink}
                onFocus={(e) => e.target.select()}
                className="flex-1 px-4 py-2 border border-line rounded-xl bg-surface-2 text-sm text-ink-soft"
              />
              <button
                onClick={copyReferralLink}
                className="px-4 py-2 rounded-xl text-sm font-semibold bg-accent text-white hover:bg-accent-strong transition-colors inline-flex items-center justify-center gap-2 shrink-0"
              >
                {copied ? <Check size={14} /> : <Copy size={14} />}
                {copied ? 'Copié' : 'Copier le lien'}
              </button>
            </div>
          </div>
        )}

        {/* Invoices */}
        <div className="bg-surface rounded-2xl border border-line p-8">
          <h2 className="text-xl font-bold text-ink mb-6 flex items-center gap-2">
            <Calendar size={20} className="text-ink-soft" />
            Historique de facturation
          </h2>

          {invLoading ? (
            <div className="text-center py-8">
              <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-accent mx-auto"></div>
            </div>
          ) : invoices.length === 0 ? (
            <div className="text-center py-12 text-ink-soft">
              <CreditCard size={40} className="mx-auto mb-3 opacity-30" />
              <p className="text-sm">Aucune facture pour le moment</p>
              <p className="text-xs mt-1">Vos factures apparaîtront ici une fois que vous aurez souscrit à un plan payant.</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-line">
                    <th className="text-left py-3 px-4 font-semibold text-ink-soft">Date</th>
                    <th className="text-left py-3 px-4 font-semibold text-ink-soft">Description</th>
                    <th className="text-left py-3 px-4 font-semibold text-ink-soft">Montant</th>
                    <th className="text-left py-3 px-4 font-semibold text-ink-soft">Statut</th>
                    <th className="text-right py-3 px-4 font-semibold text-ink-soft">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {invoices.map((inv) => (
                    <tr key={inv.id} className="border-b border-line hover:bg-surface-2/50 transition-colors">
                      <td className="py-3 px-4 text-ink-soft">
                        {formatDate(inv.paidAt || inv.createdAt)}
                      </td>
                      <td className="py-3 px-4 text-ink font-medium">
                        {inv.description || `Facture ${inv.stripeInvoiceId?.slice(-8) || ''}`}
                      </td>
                      <td className="py-3 px-4 font-semibold text-ink">
                        {formatAmount(inv.amountCents, inv.currency)}
                      </td>
                      <td className="py-3 px-4">
                        <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-semibold ${
                          inv.status === 'PAID' ? 'text-success bg-success/10' : 'text-warning bg-warning/10'
                        }`}>
                          {inv.status === 'PAID' ? 'Payé' : inv.status}
                        </span>
                      </td>
                      <td className="py-3 px-4 text-right">
                        {inv.invoicePdfUrl && (
                          <a
                            href={inv.invoicePdfUrl}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="inline-flex items-center gap-1 text-accent hover:text-accent-strong transition-colors"
                          >
                            <Download size={14} />
                            PDF
                          </a>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
