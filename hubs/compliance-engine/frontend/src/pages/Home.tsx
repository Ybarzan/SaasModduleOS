import { Link } from 'react-router-dom';
import Seo, { SITE_URL } from '../components/Seo';
import StickyMobileCta from '../components/StickyMobileCta';
import HeroShowcase from '../components/HeroShowcase';
import { useLanguageStore } from '../stores/language';
import {
  Calculator,
  Truck,
  Globe,
  BarChart3,
  Shield,
  Zap,
  ArrowRight,
  TrendingDown,
  ChevronRight,
  Users,
  Package,
  Leaf,
} from 'lucide-react';

const FEATURES = [
  {
    icon: Calculator,
    title: 'Calculateur Incoterms',
    description:
      'Simulez instantanément les coûts totaux pour chaque Incoterm 2020. Frais de transport, droits de douane, assurance, TVA — tout est calculé automatiquement.',
  },
  {
    icon: TrendingDown,
    title: 'Comparaison de devis',
    description:
      'Demandez des devis à plusieurs transporteurs simultanément et comparez tarifs, délais et services en un clin d\'œil.',
  },
  {
    icon: Globe,
    title: 'Couverture mondiale',
    description:
      'Support complet des 11 Incoterms 2020 avec calculs adaptés à plus de 190 pays et toutes les modes de transport.',
  },
  {
    icon: Shield,
    title: 'Conformité automatisée',
    description:
      'Alertes en temps réel sur les incompatibilités Incoterm/transport et vérification automatique des règles commerciales.',
  },
  {
    icon: BarChart3,
    title: 'Tableau de bord intelligent',
    description:
      'Visualisez vos coûts logistiques avec des graphiques interactifs, suivez l\'évolution de vos simulations et identifiez les opportunités d\'économie.',
  },
  {
    icon: Zap,
    title: 'Intégrations ERP',
    description:
      'Connectez IncoKalk à votre ERP existant (Odoo, SAP, Dynamics) pour une synchronisation automatique des données logistiques.',
  },
];

const STEPS = [
  {
    number: '01',
    title: 'Configurez votre shipment',
    description:
      'Saisissez la valeur des marchandises, le poids, le mode de transport et les pays d\'origine/destination.',
    icon: Package,
  },
  {
    number: '02',
    title: 'Analysez les coûts',
    description:
      'IncoKalk calcule instantanément tous les coûts pour chaque Incoterm : transport, douane, assurance, manutention.',
    icon: Calculator,
  },
  {
    number: '03',
    title: 'Comparez et décidez',
    description:
      'Comparez les scénarios, pinez les meilleurs options et partagez les résultats avec votre équipe.',
    icon: BarChart3,
  },
];

const SOFTWARE_APPLICATION_SCHEMA = {
  '@context': 'https://schema.org',
  '@type': 'SoftwareApplication',
  name: 'IncoKalk',
  applicationCategory: 'BusinessApplication',
  operatingSystem: 'Web',
  url: SITE_URL,
  description:
    'Simulateur Incoterms 2020, comparaison de devis transport et gestion des expéditions internationales pour exportateurs et importateurs.',
  offers: {
    '@type': 'Offer',
    price: '0',
    priceCurrency: 'EUR',
    description: 'Plan gratuit disponible, essai sans carte bancaire',
  },
  // Pas d'aggregateRating : Google interdit les données d'avis fictives dans le
  // balisage structuré (risque de pénalité manuelle). À ajouter uniquement une
  // fois de vrais avis clients collectés (Trustpilot, G2, avis internes...).
};

// Pilote i18n (voir stores/language.ts) : seul le hero est traduit, pas le
// reste de la page -- le contenu au-delà reste français pour cette v1.
const HERO_COPY = {
  fr: {
    eyebrow: "L'infrastructure du commerce international pour les PME",
    title: <>Le bon outil, pour la bonne route et la <span className="text-accent">bonne décision</span></>,
    subtitle: (
      <>
        Pilotez vos coûts logistiques avec sérénité.{' '}
        <span className="text-ink font-semibold">Vos tarifs. Vos transporteurs. Pas d'intermédiaire.</span>
      </>
    ),
    ctaPrimary: 'Voir le calculateur',
    ctaSecondary: 'Créer un compte gratuit',
    stat1: 'Incoterms 2020',
    stat2: 'Pays supportés',
    stat3: 'Par devis complet',
  },
  en: {
    eyebrow: 'International trade infrastructure for small and mid-sized businesses',
    title: <>The right tool, for the right route and the <span className="text-accent">right decision</span></>,
    subtitle: (
      <>
        Manage your logistics costs with confidence.{' '}
        <span className="text-ink font-semibold">Your rates. Your carriers. No middleman.</span>
      </>
    ),
    ctaPrimary: 'See the calculator',
    ctaSecondary: 'Create a free account',
    stat1: 'Incoterms 2020',
    stat2: 'Countries supported',
    stat3: 'Per full quote',
  },
};

const Home = () => {
  const language = useLanguageStore((s) => s.language);
  const t = HERO_COPY[language];

  return (
    <div className="overflow-hidden pb-20 md:pb-0">
      <Seo
        title="IncoKalk — Simulateur Incoterms 2020 & gestion logistique internationale"
        description="Calculez vos coûts logistiques par Incoterm 2020, comparez les devis transporteurs et pilotez vos expéditions internationales depuis une seule plateforme."
        path="/"
        jsonLd={SOFTWARE_APPLICATION_SCHEMA}
      />
      {/* ========== HERO SECTION ========== */}
      <section className="bg-bg section-padding">
        <div className="container-narrow mx-auto px-4">
          <div className="grid lg:grid-cols-2 gap-16 items-center">
            <div className="text-center lg:text-left">
              <span className="eyebrow inline-flex items-center gap-2">
                <span className="w-1.5 h-1.5 rounded-full bg-accent" />
                {t.eyebrow}
              </span>

              <h1 className="text-5xl md:text-7xl lg:text-6xl font-extrabold text-ink mt-6 mb-6 leading-[1.03] text-balance">
                {t.title}
              </h1>

              <p className="text-xl md:text-2xl text-ink-soft mb-10 max-w-2xl mx-auto lg:mx-0 leading-relaxed">
                {t.subtitle}
              </p>

              <div className="flex flex-col sm:flex-row items-center lg:items-start justify-center lg:justify-start gap-4 mb-16">
                <Link to="/simulation" className="btn-primary text-lg inline-flex items-center gap-2">
                  {t.ctaPrimary}
                  <ArrowRight size={20} />
                </Link>
                <Link to="/register" className="btn-secondary text-lg">
                  {t.ctaSecondary}
                </Link>
              </div>

              {/* Stats bar */}
              <div className="grid grid-cols-3 gap-8 max-w-xl mx-auto lg:mx-0">
                <div>
                  <div className="text-3xl font-extrabold text-ink">11</div>
                  <div className="text-sm text-ink-soft font-medium">{t.stat1}</div>
                </div>
                <div>
                  <div className="text-3xl font-extrabold text-ink">190+</div>
                  <div className="text-sm text-ink-soft font-medium">{t.stat2}</div>
                </div>
                <div>
                  <div className="text-3xl font-extrabold text-ink">5min</div>
                  <div className="text-sm text-ink-soft font-medium">{t.stat3}</div>
                </div>
              </div>
            </div>

            <HeroShowcase />
          </div>
        </div>
      </section>

      {/* ========== FEATURES GRID ========== */}
      <section className="bg-surface section-padding">
        <div className="container-narrow mx-auto px-4">
          <div className="text-center mb-16">
            <p className="eyebrow mb-4">Fonctionnalités</p>
            <h2 className="text-4xl md:text-5xl font-extrabold text-ink mb-4">
              Tout ce qu'il faut pour vos{' '}
              <span className="gradient-text">routes internationales</span>
            </h2>
            <p className="text-xl text-ink-soft max-w-2xl mx-auto">
              Une plateforme complète pour calculer, comparer et optimiser tous vos coûts logistiques internationaux.
            </p>
          </div>

          <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-6">
            {FEATURES.map((feature) => {
              const Icon = feature.icon;
              return (
                <div
                  key={feature.title}
                  className="card-moroccan p-8 group"
                >
                  <div className="w-14 h-14 rounded-2xl bg-accent-soft flex items-center justify-center mb-6 group-hover:scale-110 transition-transform">
                    <Icon size={28} className="text-accent" />
                  </div>
                  <h3 className="text-xl font-bold text-ink mb-3">{feature.title}</h3>
                  <p className="text-ink-soft leading-relaxed">{feature.description}</p>
                </div>
              );
            })}
          </div>
        </div>
      </section>

      {/* ========== HOW IT WORKS ========== */}
      <section className="bg-bg section-padding">
        <div className="container-narrow mx-auto px-4">
          <div className="text-center mb-16">
            <p className="eyebrow mb-4">Processus</p>
            <h2 className="text-4xl md:text-5xl font-extrabold text-ink mb-4">
              En 3 étapes, de la demande à la décision
            </h2>
            <p className="text-xl text-ink-soft max-w-2xl mx-auto">
              Obtenez un calcul complet de vos coûts logistiques en un rien de temps.
            </p>
          </div>

          <div className="grid md:grid-cols-3 gap-8">
            {STEPS.map((step) => {
              const Icon = step.icon;
              return (
                <div key={step.number} className="relative">
                  <div className="bg-surface rounded-2xl border border-line p-8 h-full hover:shadow-xl hover:shadow-accent/8 hover:-translate-y-1 transition-all duration-300">
                    <div className="text-7xl font-extrabold text-surface-2 absolute top-4 right-6 select-none">
                      {step.number}
                    </div>
                    <div className="relative z-10">
                      <div className="w-14 h-14 rounded-2xl bg-accent-soft border border-accent/15 flex items-center justify-center mb-6">
                        <Icon size={26} className="text-accent" />
                      </div>
                      <h3 className="text-xl font-bold text-ink mb-3">{step.title}</h3>
                      <p className="text-ink-soft leading-relaxed">{step.description}</p>
                    </div>
                  </div>
                  {step.number !== '03' && (
                    <div className="hidden md:flex absolute top-1/2 -right-4 -translate-y-1/2 z-20">
                      <ChevronRight size={24} className="text-accent/50" />
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      </section>

      {/* ========== DASHBOARD PREVIEW ========== */}
      <section className="bg-surface section-padding">
        <div className="container-narrow mx-auto px-4">
          <div className="text-center mb-16">
            <p className="eyebrow mb-4">Tableau de bord</p>
            <h2 className="text-4xl md:text-5xl font-extrabold text-ink mb-4">
              Prenez des décisions de transport{' '}
              <span className="gradient-text">judicieuses</span>, instantanément
            </h2>
            <p className="text-xl text-ink-soft max-w-3xl mx-auto">
              Comparez les tarifs, réservez le transporteur que vous préférez, suivez vos expéditions, surveillez la collecte et l'arrivée des marchandises — tout cela dans un seul tableau de bord.
            </p>
          </div>

          <div className="grid lg:grid-cols-2 gap-12 items-start">
            {/* Dashboard mockup */}
            <div className="bg-bg rounded-3xl border border-line p-6 shadow-xl shadow-accent/5">
              <div className="bg-surface rounded-2xl p-6 space-y-4 border border-line">
                <div className="flex items-center justify-between mb-6">
                  <h3 className="text-ink font-bold">Tableau de bord</h3>
                  <div className="flex gap-2">
                    <div className="w-3 h-3 rounded-full bg-accent" />
                    <div className="w-3 h-3 rounded-full bg-accent/60" />
                    <div className="w-3 h-3 rounded-full bg-accent/30" />
                  </div>
                </div>
                {/* Mock KPI cards */}
                <div className="grid grid-cols-3 gap-3">
                  <div className="bg-bg rounded-xl p-3 border border-line">
                    <div className="text-xs text-ink-soft font-medium">Simulations</div>
                    <div className="text-xl font-bold text-ink">247</div>
                  </div>
                  <div className="bg-bg rounded-xl p-3 border border-line">
                    <div className="text-xs text-ink-soft font-medium">Économies</div>
                    <div className="text-xl font-bold text-success">€12.4k</div>
                  </div>
                  <div className="bg-bg rounded-xl p-3 border border-line">
                    <div className="text-xs text-ink-soft font-medium">Transporteurs</div>
                    <div className="text-xl font-bold text-ink">18</div>
                  </div>
                </div>
                {/* Mock chart bars */}
                <div className="space-y-2">
                  {['Fret maritime', 'Fret aérien', 'Route', 'Ferroviaire'].map((mode, i) => (
                    <div key={mode} className="flex items-center gap-3">
                      <span className="text-xs text-ink-soft w-24 font-medium">{mode}</span>
                      <div className="flex-1 bg-surface-2 rounded-full h-2 overflow-hidden">
                        <div
                          className="h-full bg-accent rounded-full"
                          style={{ width: `${[75, 45, 60, 20][i]}%` }}
                        />
                      </div>
                      <span className="text-xs text-ink-soft w-8 text-right">{[75, 45, 60, 20][i]}%</span>
                    </div>
                  ))}
                </div>
              </div>
            </div>

            {/* Feature list */}
            <div className="space-y-6">
              {[
                {
                  icon: BarChart3,
                  title: 'Tableau de bord intelligent',
                  desc: 'Toutes les informations de transport, tarifs, délais de transit, suivi et statistiques dans un seul logiciel cloud.',
                },
                {
                  icon: Truck,
                  title: 'Vos transporteurs existants',
                  desc: 'Gardez tous vos transporteurs actuels et ajoutez-en d\'autres selon vos besoins. Plus de 1 000 intégrations plug&play.',
                },
                {
                  icon: Zap,
                  title: 'Incroyablement facile',
                  desc: 'Nous configurons tout pour vous. Après l\'initialisation, IncoKalk révolutionne votre gestion logistique.',
                },
                {
                  icon: Users,
                  title: 'Données automatisées',
                  desc: 'Notifications personnalisées pour les achats, les ventes, l\'entrepôt et vos clients. Tout le monde est informé.',
                },
              ].map((item) => {
                const Icon = item.icon;
                return (
                  <div key={item.title} className="flex gap-4">
                    <div className="w-12 h-12 rounded-2xl bg-accent-soft border border-accent/15 flex items-center justify-center shrink-0">
                      <Icon size={22} className="text-accent" />
                    </div>
                    <div>
                      <h3 className="text-lg font-bold text-ink mb-1">{item.title}</h3>
                      <p className="text-ink-soft text-sm leading-relaxed">{item.desc}</p>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      </section>

      {/* ========== CO2 SECTION ========== */}
      <section className="bg-bg section-padding">
        <div className="container-narrow mx-auto px-4">
          <div className="bg-success/5 rounded-3xl border border-success/20 p-8 md:p-12 relative overflow-hidden">
            <div className="grid lg:grid-cols-2 gap-8 items-center relative z-10">
              <div>
                <div className="inline-flex items-center gap-2 bg-success/10 border border-success/20 rounded-full px-4 py-2 mb-6">
                  <Leaf size={16} className="text-success" />
                  <span className="text-sm text-success font-semibold">Durable</span>
                </div>
                <h2 className="text-3xl md:text-4xl font-extrabold text-ink mb-4">
                  Réduisez votre empreinte CO₂ avec chaque décision intelligente
                </h2>
                <p className="text-lg text-ink-soft mb-6 leading-relaxed">
                  Estimation pré-calculée des émissions de CO₂ pour chaque expédition. Si la réduction de l'empreinte
                  carbone est importante pour votre entreprise, ne cherchez pas plus loin.
                </p>
                <Link to="/co2" className="btn-primary inline-flex items-center gap-2 !bg-success hover:!bg-accent-strong">
                  Faire un calcul rapide
                  <ArrowRight size={18} />
                </Link>
              </div>
              <div className="flex justify-center">
                <div className="relative">
                  <div className="w-48 h-48 rounded-full border-[12px] border-success/20 flex items-center justify-center bg-surface/50">
                    <div className="text-center">
                      <div className="text-4xl font-extrabold text-success">-94%</div>
                      <div className="text-sm text-ink-soft mt-1 font-medium leading-tight">
                        d'émissions en choisissant<br />le maritime plutôt que l'aérien
                      </div>
                    </div>
                  </div>
                  <div className="absolute -top-4 -right-4 w-16 h-16 rounded-2xl bg-success/10 border border-success/20 flex items-center justify-center animate-float">
                    <Leaf size={28} className="text-success" />
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* ========== FINAL CTA ========== */}
      <section className="bg-surface section-padding">
        <div className="container-narrow mx-auto px-4">
          <div className="bg-accent rounded-3xl p-12 md:p-20 text-center">
            <h2 className="text-4xl md:text-5xl font-extrabold text-white mb-6">
              Découvrez comment booster votre logistique quotidienne
            </h2>
            <p className="text-xl text-white/70 mb-10 max-w-2xl mx-auto">
              Commencez gratuitement ou réservez une démo personnalisée avec notre équipe.
            </p>
            <div className="flex flex-col sm:flex-row items-center justify-center gap-4">
              <Link to="/register" className="bg-surface text-accent-strong px-8 py-3.5 rounded-xl font-bold hover:bg-surface/90 transition-all duration-300 shadow-xl shadow-black/10 hover:-translate-y-0.5 text-lg">
                Créer un compte
              </Link>
              <Link to="/simulation" className="btn-outline-white text-lg">
                Essayer le calculateur
              </Link>
            </div>
          </div>
        </div>
      </section>

      <StickyMobileCta />
    </div>
  );
};

export default Home;
