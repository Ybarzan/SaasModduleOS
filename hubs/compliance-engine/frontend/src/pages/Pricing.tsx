import { useState } from 'react';
import { useQuery, useMutation } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { Link } from 'react-router-dom';
import { Check, Zap, Building2, Rocket, ArrowLeft, Sparkles, Scale, ShieldCheck, RefreshCw } from 'lucide-react';
import toast from 'react-hot-toast';
import { incokalkAPI } from '../lib/api';
import { useAuthStore } from '../stores/auth';
import { useLanguageStore } from '../stores/language';
import { formatNumber } from '../lib/formatNumber';
import Seo from '../components/Seo';
import Breadcrumbs from '../components/Breadcrumbs';
import StickyMobileCta from '../components/StickyMobileCta';

// Facturation Stripe toujours en EUR (voir BillingService) -- cette conversion est purement
// d'affichage, à titre indicatif, pour un visiteur hors zone euro. Liste volontairement
// courte plutôt que les ~39 devises supportées par CurrencyExchangeService côté backend :
// une poignée de devises majeures suffit à lever l'ambiguïté "combien ça coûte chez moi"
// sans transformer le sélecteur en recherche interminable.
const CURRENCY_OPTIONS: { code: string; symbol: string; prefix: boolean }[] = [
  { code: 'EUR', symbol: '€', prefix: false },
  { code: 'USD', symbol: '$', prefix: true },
  { code: 'GBP', symbol: '£', prefix: true },
  { code: 'CHF', symbol: 'CHF', prefix: true },
  { code: 'CAD', symbol: 'CA$', prefix: true },
  { code: 'AUD', symbol: 'AU$', prefix: true },
];

function formatPrice(amountInEur: number, currency: string, rates: Record<string, number> | undefined): string {
  const option = CURRENCY_OPTIONS.find((c) => c.code === currency) ?? CURRENCY_OPTIONS[0];
  const rate = currency === 'EUR' ? 1 : rates?.[currency] ?? 1;
  const converted = formatNumber(Math.round(amountInEur * rate));
  return option.prefix ? `${option.symbol}${converted}` : `${converted} ${option.symbol}`;
}

// Pilote i18n (voir stores/language.ts) : couvre le "chrome" statique de la
// page. Le contenu des cartes de plan (nom, features, valueProp, hook) vient
// du backend en français uniquement et n'est pas traduit dans cette v1 --
// traduire ça demanderait de rendre l'API elle-même consciente de la langue,
// un chantier bien plus lourd qu'un pilote frontend.
const PAGE_COPY = {
  fr: {
    seoDescription: "Comparez les plans IncoKalk : Gratuit, Starter, Pro et Enterprise. Essai gratuit 14 jours sur tous les plans payants, changement de plan à tout moment.",
    breadcrumb: 'Tarifs',
    back: 'Retour',
    titleStart: 'Le bon niveau,',
    titleAccent: 'au bon moment',
    subtitle: "Import-export et transport multimodal dès le premier jour. Douane, entrepôt et finance s'activent quand votre équipe grandit — pas avant.",
    compareLink: 'Comparez-nous à Cargoson, Easyship et Flexport',
    monthly: 'Mensuel',
    annual: 'Annuel',
    recommended: 'Recommandé',
    free: 'Gratuit',
    perMonth: '/mois',
    perYearSuffix: '/an',
    monthlyBilling: 'Facturation mensuelle',
    save: 'Économisez',
    saveSuffix: '/an',
    currentPlan: 'Plan actuel',
    redirecting: 'Redirection...',
    current: 'Actuel',
    trialButton: 'Essai gratuit 14 jours',
    trustEncryption: 'Données chiffrées en transit (TLS) et au repos',
    trustCancel: 'Sans engagement — changez ou annulez à tout moment',
    currencyLabel: 'Devise',
    currencyDisclaimer: 'Facturation en euros — conversion indicative au taux du jour, non contractuelle.',
    growthTitle1: "Un rôle dédié, pas une taille d'entreprise",
    growthBody1: "Vous n'avez pas besoin de deviner quand upgrader. Le jour où vous créez un rôle dédié à la douane ou à l'entrepôt et l'assignez à quelqu'un, on vous propose le module correspondant — pas avant, pas à toute l'équipe d'un coup.",
    growthTitle2: 'Services à la demande',
    growthBody2: "Financement de factures, assurance cargo et change de devises s'activent au besoin, en dehors de l'abonnement — vous ne payez que ce que vous utilisez réellement, au moment où vous l'utilisez.",
    comparisonLink: 'Comparer IncoKalk aux alternatives (Cargoson, Easyship, Flexport)',
    faqLink: 'Voir toutes les questions fréquentes →',
    faqTitle: 'Questions fréquentes',
    stayFreeToast: 'Vous êtes déjà sur le plan gratuit',
    checkoutErrorToast: 'Erreur lors de la création de la session de paiement',
    faq: [
      {
        q: 'Puis-je changer de plan à tout moment ?',
        a: 'Oui, vous pouvez upgrader ou downgrader à tout moment. Le changement prend effet immédiatement avec un prorata.',
      },
      {
        q: 'Y a-t-il un essai gratuit ?',
        a: "Oui, tous les plans payants (Starter, Croissance, Enterprise) incluent 14 jours d'essai gratuit. Une carte bancaire est demandée à l'inscription mais n'est débitée qu'à l'issue de la période d'essai — vous pouvez annuler avant sans rien payer.",
      },
      {
        q: 'Comment fonctionne la facturation annuelle ?',
        a: 'Avec la facturation annuelle, vous économisez 15% par rapport au paiement mensuel.',
      },
      {
        q: 'Le module douanes est-il inclus ?',
        a: 'Le module douane complet (TARIC, DEB, Intrastat, screening DPS, EUR.1, CMR/DGD) est inclus dans Croissance et au-dessus.',
      },
      {
        q: 'Comment savoir quand passer au plan supérieur ?',
        a: "Pas besoin de calculer un ROI. Le signal, c'est l'organisation elle-même : dès que vous créez un rôle dédié (douane, entrepôt) et l'assignez à quelqu'un, c'est le bon moment pour ce module-là — pas pour tout le reste.",
      },
    ],
  },
  en: {
    seoDescription: "Compare IncoKalk plans: Free, Starter, Pro and Enterprise. 14-day free trial on all paid plans, change plan anytime.",
    breadcrumb: 'Pricing',
    back: 'Back',
    titleStart: 'The right tier,',
    titleAccent: 'at the right time',
    subtitle: "Import-export and multimodal transport from day one. Customs, warehousing and finance switch on as your team grows — not before.",
    compareLink: 'Compare us to Cargoson, Easyship and Flexport',
    monthly: 'Monthly',
    annual: 'Annual',
    recommended: 'Recommended',
    free: 'Free',
    perMonth: '/mo',
    perYearSuffix: '/yr',
    monthlyBilling: 'Billed monthly',
    save: 'Save',
    saveSuffix: '/yr',
    currentPlan: 'Current plan',
    redirecting: 'Redirecting...',
    current: 'Current',
    trialButton: '14-day free trial',
    trustEncryption: 'Data encrypted in transit (TLS) and at rest',
    trustCancel: 'No commitment — change or cancel anytime',
    currencyLabel: 'Currency',
    currencyDisclaimer: 'Billed in euros — indicative conversion at today\'s rate, not contractual.',
    growthTitle1: 'A dedicated role, not a company size',
    growthBody1: "You don't have to guess when to upgrade. The day you create a role dedicated to customs or warehousing and assign it to someone, we surface the matching module — not before, not for the whole team at once.",
    growthTitle2: 'On-demand services',
    growthBody2: "Invoice financing, cargo insurance and currency exchange switch on as needed, outside the subscription — you only pay for what you actually use, when you use it.",
    comparisonLink: 'Compare IncoKalk to the alternatives (Cargoson, Easyship, Flexport)',
    faqLink: 'See all frequently asked questions →',
    faqTitle: 'Frequently asked questions',
    stayFreeToast: "You're already on the free plan",
    checkoutErrorToast: 'Error creating the payment session',
    faq: [
      {
        q: 'Can I change plans at any time?',
        a: 'Yes, you can upgrade or downgrade at any time. The change takes effect immediately with a prorated charge.',
      },
      {
        q: 'Is there a free trial?',
        a: "Yes, all paid plans (Starter, Growth, Enterprise) include a 14-day free trial. A card is requested at signup but isn't charged until the trial ends — you can cancel before then at no cost.",
      },
      {
        q: 'How does annual billing work?',
        a: 'With annual billing, you save 15% compared to paying monthly.',
      },
      {
        q: 'Is the customs module included?',
        a: 'The full customs module (TARIC, DEB, Intrastat, DPS screening, EUR.1, CMR/DGD) is included in Growth and above.',
      },
      {
        q: 'How do I know when to move up a plan?',
        a: "No ROI math needed. The signal is the organization itself: the day you create a dedicated role (customs, warehousing) and assign it to someone, that's the right moment for that specific module — not for everything else.",
      },
    ],
  },
};

interface Plan {
  id: string;
  name: string;
  priceMonthly: number;
  priceAnnual: number;
  currency: string;
  features: string[];
  valueProp?: string;
  hook?: string;
  recommended?: boolean;
  limits: Record<string, number>;
}

const planIcons: Record<string, typeof Zap> = {
  free: Zap,
  starter: Rocket,
  pro: Sparkles,
  enterprise: Building2,
};

const planColors: Record<string, string> = {
  free: 'from-surface-2 to-surface-2',
  starter: 'from-accent to-accent',
  pro: 'from-accent to-accent-strong',
  enterprise: 'from-ink-soft to-ink',
};

type BillingCycle = 'monthly' | 'annual';

export default function Pricing() {
  const navigate = useNavigate();
  const [cycle, setCycle] = useState<BillingCycle>('annual');
  const [currency, setCurrency] = useState('EUR');
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated());
  const user = useAuthStore((s) => s.user);
  const language = useLanguageStore((s) => s.language);
  const t = PAGE_COPY[language];

  const { data: plans = [], isLoading } = useQuery<Plan[]>({
    queryKey: ['billing-plans'],
    queryFn: async () => {
      const res = await incokalkAPI.billing.getPlans();
      return res.data;
    },
  });

  // Taux mis en cache côté backend (CurrencyExchangeService) -- staleTime long ici aussi,
  // pas besoin de re-fetch à chaque changement de devise dans le sélecteur.
  const { data: fxData } = useQuery<{ rates: Record<string, number> }>({
    queryKey: ['fx-rates', 'EUR'],
    queryFn: async () => {
      const res = await incokalkAPI.currency.getRates('EUR');
      return res.data;
    },
    staleTime: 60 * 60 * 1000,
  });

  const checkoutMutation = useMutation({
    mutationFn: async ({ planId, billingCycle }: { planId: string; billingCycle: string }) => {
      const res = await incokalkAPI.billing.checkout({ planId, billingCycle });
      return res.data;
    },
    onSuccess: (data: { url: string }) => {
      window.location.href = data.url;
    },
    onError: () => {
      toast.error(t.checkoutErrorToast);
    },
  });

  const handleSubscribe = (planId: string, billingCycle: BillingCycle) => {
    if (!isAuthenticated) {
      navigate('/register');
      return;
    }
    if (planId === 'free') {
      toast(t.stayFreeToast, { icon: 'ℹ️' });
      return;
    }
    checkoutMutation.mutate({ planId, billingCycle });
  };

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-accent"></div>
      </div>
    );
  }

  const annualSavings = (plan: Plan) =>
    plan.priceMonthly > 0 ? plan.priceMonthly * 12 - plan.priceAnnual : 0;

  const displayPrice = (plan: Plan) =>
    cycle === 'annual' ? plan.priceAnnual / 12 : plan.priceMonthly;

  return (
    <div className="min-h-screen bg-gradient-to-br from-bg to-white pb-24 md:pb-0">
      <Seo
        title="Tarifs — Plans et abonnements"
        description={t.seoDescription}
        path="/pricing"
      />
      <div className="container mx-auto px-4 py-12 max-w-7xl">
        <Breadcrumbs items={[{ label: t.breadcrumb }]} />

        {/* Header */}
        <div className="text-center mb-10 mt-8">
          <button
            onClick={() => navigate(-1)}
            className="inline-flex items-center gap-2 text-ink-soft hover:text-ink mb-6 transition-colors"
          >
            <ArrowLeft size={16} />
            {t.back}
          </button>
          <h1 className="text-4xl font-extrabold text-ink mb-4">
            {t.titleStart} <span className="text-accent">{t.titleAccent}</span>
          </h1>
          <p className="text-lg text-ink-soft max-w-2xl mx-auto">
            {t.subtitle}
          </p>

          <Link
            to="/pricing-comparison"
            className="inline-flex items-center gap-2 mt-4 px-4 py-2 bg-accent-soft text-accent-strong rounded-full text-sm font-semibold hover:bg-accent/20 transition-colors"
          >
            <Scale size={15} />
            {t.compareLink}
          </Link>

          {/* Billing cycle toggle */}
          <div className="inline-flex bg-surface border border-line rounded-xl p-1 mt-8">
            <button
              onClick={() => setCycle('monthly')}
              className={`px-5 py-2 text-sm font-semibold rounded-lg transition-colors ${
                cycle === 'monthly'
                  ? 'bg-ink text-white'
                  : 'text-ink-soft hover:bg-bg'
              }`}
            >
              {t.monthly}
            </button>
            <button
              onClick={() => setCycle('annual')}
              className={`px-5 py-2 text-sm font-semibold rounded-lg transition-colors inline-flex items-center gap-2 ${
                cycle === 'annual'
                  ? 'bg-ink text-white'
                  : 'text-ink-soft hover:bg-bg'
              }`}
            >
              {t.annual}
              <span className="text-[10px] font-bold bg-accent text-white px-1.5 py-0.5 rounded-full">
                -15%
              </span>
            </button>
          </div>

          {/* Devise d'affichage -- conversion indicative uniquement, la facturation Stripe
              reste en EUR (voir BillingService) */}
          <div className="mt-4 flex flex-col items-center gap-1.5">
            <label className="inline-flex items-center gap-2 text-sm text-ink-soft">
              {t.currencyLabel}
              <select
                value={currency}
                onChange={(e) => setCurrency(e.target.value)}
                className="border border-line rounded-lg px-2 py-1 text-sm bg-surface text-ink focus:ring-2 focus:ring-accent focus:border-transparent"
              >
                {CURRENCY_OPTIONS.filter((c) => c.code === 'EUR' || fxData?.rates?.[c.code]).map((c) => (
                  <option key={c.code} value={c.code}>{c.code}</option>
                ))}
              </select>
            </label>
            {currency !== 'EUR' && (
              <p className="text-xs text-ink-soft">{t.currencyDisclaimer}</p>
            )}
          </div>
        </div>

        {/* Plans Grid */}
        <div className="grid md:grid-cols-2 xl:grid-cols-4 gap-6 max-w-6xl mx-auto">
          {plans.map((plan) => {
            const Icon = planIcons[plan.id] || Zap;
            const isCurrent = user?.company?.toLowerCase() === plan.name.toLowerCase();
            const isPopular = plan.recommended === true;

            return (
              <div
                key={plan.id}
                className={`relative bg-surface rounded-2xl border-2 p-7 transition-all hover:shadow-xl flex flex-col ${
                  isPopular
                    ? 'border-accent shadow-lg shadow-accent/10 scale-[1.03]'
                    : 'border-line hover:border-accent/60'
                }`}
              >
                {isPopular && (
                  <div className="absolute -top-4 left-1/2 -translate-x-1/2">
                    <span className="bg-accent text-white text-xs font-bold px-4 py-1.5 rounded-full shadow-md">
                      {t.recommended}
                    </span>
                  </div>
                )}

                <div className={`w-12 h-12 rounded-2xl bg-gradient-to-br ${planColors[plan.id]} flex items-center justify-center mb-5 shadow-lg`}>
                  <Icon size={22} className="text-white" />
                </div>

                <h3 className="text-xl font-bold text-ink mb-1">{plan.name}</h3>
                {plan.valueProp && (
                  <p className="text-xs font-semibold text-accent-strong mb-1 leading-snug">
                    {plan.valueProp}
                  </p>
                )}
                {plan.hook && (
                  <p className="text-xs text-ink-soft leading-snug mb-4">{plan.hook}</p>
                )}

                <div className="mb-6">
                  <div className="flex items-baseline gap-1">
                    <span className="text-4xl font-extrabold text-ink">
                      {plan.priceMonthly === 0 ? t.free : formatPrice(displayPrice(plan), currency, fxData?.rates)}
                    </span>
                    {plan.priceMonthly > 0 && (
                      <span className="text-ink-soft text-sm">{t.perMonth}</span>
                    )}
                  </div>
                  {plan.priceMonthly > 0 && (
                    <>
                      <p className="text-sm text-ink-soft mt-1">
                        {cycle === 'annual' ? `${formatPrice(plan.priceAnnual, currency, fxData?.rates)}${t.perYearSuffix}` : t.monthlyBilling}
                      </p>
                      {cycle === 'annual' && annualSavings(plan) > 0 && (
                        <p className="text-xs font-semibold text-success mt-0.5">
                          {t.save} {formatPrice(annualSavings(plan), currency, fxData?.rates)}{t.saveSuffix}
                        </p>
                      )}
                    </>
                  )}
                </div>

                <ul className="space-y-2.5 mb-8 flex-1">
                  {plan.features.map((feature, i) => (
                    <li key={i} className="flex items-start gap-2.5">
                      <Check size={15} className="text-success mt-0.5 shrink-0" />
                      <span className="text-sm text-ink-soft">{feature}</span>
                    </li>
                  ))}
                </ul>

                <button
                  onClick={() => handleSubscribe(plan.id, cycle)}
                  disabled={isCurrent || checkoutMutation.isPending}
                  className={`w-full py-3 px-6 rounded-xl font-semibold text-sm transition-all ${
                    isCurrent
                      ? 'bg-surface-2 text-ink-soft cursor-not-allowed'
                      : isPopular
                      ? 'bg-accent text-white hover:bg-accent-strong shadow-md shadow-accent/20'
                      : 'bg-ink text-white hover:bg-ink'
                  }`}
                >
                  {isCurrent
                    ? t.currentPlan
                    : checkoutMutation.isPending
                    ? t.redirecting
                    : plan.id === 'free'
                    ? t.current
                    : t.trialButton}
                </button>
              </div>
            );
          })}
        </div>

        {/* Réassurance au point de décision — mêmes faits que la FAQ plus bas, mais
            affichés ici, juste après les boutons d'abonnement, pas seulement en bas
            de page où l'hésitation à l'achat est déjà passée. */}
        <div className="flex flex-wrap items-center justify-center gap-x-8 gap-y-3 mt-10 text-sm text-ink-soft">
          <div className="flex items-center gap-2">
            <ShieldCheck size={16} className="text-success shrink-0" />
            {t.trustEncryption}
          </div>
          <div className="flex items-center gap-2">
            <RefreshCw size={16} className="text-success shrink-0" />
            {t.trustCancel}
          </div>
        </div>

        {/* Mécanique de croissance */}
        <div className="mt-16 max-w-4xl mx-auto grid sm:grid-cols-2 gap-4">
          <div className="bg-surface rounded-xl border border-line p-6">
            <h3 className="font-semibold text-ink mb-2">{t.growthTitle1}</h3>
            <p className="text-sm text-ink-soft leading-relaxed">
              {t.growthBody1}
            </p>
          </div>
          <div className="bg-surface rounded-xl border border-line p-6">
            <h3 className="font-semibold text-ink mb-2">{t.growthTitle2}</h3>
            <p className="text-sm text-ink-soft leading-relaxed">
              {t.growthBody2}
            </p>
          </div>
        </div>

        {/* Comparison link */}
        <div className="text-center mt-12 flex flex-col sm:flex-row items-center justify-center gap-2 sm:gap-6">
          <Link
            to="/pricing-comparison"
            className="inline-flex items-center gap-2 text-ink-soft hover:text-ink transition-colors text-sm font-medium"
          >
            <Scale size={16} />
            {t.comparisonLink}
          </Link>
          <Link
            to="/faq"
            className="text-sm font-medium text-ink-soft hover:text-ink transition-colors"
          >
            {t.faqLink}
          </Link>
        </div>

        {/* FAQ */}
        <div className="mt-16 max-w-3xl mx-auto">
          <h2 className="text-2xl font-bold text-ink text-center mb-8">{t.faqTitle}</h2>
          <div className="space-y-4">
            {t.faq.map((faq, i) => (
              <div key={i} className="bg-surface rounded-xl border border-line p-6">
                <h4 className="font-semibold text-ink mb-2">{faq.q}</h4>
                <p className="text-sm text-ink-soft">{faq.a}</p>
              </div>
            ))}
          </div>
        </div>
      </div>
      <StickyMobileCta label={t.trialButton} to="/register" />
    </div>
  );
}
