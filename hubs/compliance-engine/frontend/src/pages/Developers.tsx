import { Link } from 'react-router-dom';
import {
  ArrowRight,
  KeyRound,
  Code2,
  Package,
  Warehouse,
  Shield,
  DollarSign,
  ShoppingCart,
  BarChart3,
} from 'lucide-react';
import Seo from '../components/Seo';
import Breadcrumbs from '../components/Breadcrumbs';
import StickyMobileCta from '../components/StickyMobileCta';

interface EndpointCategory {
  icon: typeof Package;
  title: string;
  description: string;
  example: string;
}

// Catégories réelles (backend/../controller/*), pas la liste exhaustive de docs/api.md --
// volontairement un aperçu, pas une référence complète : l'énumération détaillée
// (avec les niveaux de rôle exacts par endpoint) reste dans Swagger UI, servi par le
// backend lui-même, pour ne jamais désynchroniser une copie publique du code réel.
const CATEGORIES: EndpointCategory[] = [
  {
    icon: Package,
    title: 'Expéditions',
    description: 'Créer, lister et suivre vos expéditions en temps réel.',
    example: 'GET /api/v1/shipments/{id}/tracking',
  },
  {
    icon: Warehouse,
    title: 'Entrepôt & Inventaire',
    description: 'Articles, codes-barres, soldes de stock et réceptions.',
    example: 'GET /api/v1/inventory/balances',
  },
  {
    icon: Shield,
    title: 'Douane & Conformité',
    description: 'Droits de douane, TARIC, EORI, screening sanctions.',
    example: 'GET /api/v1/compliance/customs',
  },
  {
    icon: DollarSign,
    title: 'Finance',
    description: 'Facturation, landed cost, termes de paiement.',
    example: 'GET /api/v1/financial/landed-cost',
  },
  {
    icon: ShoppingCart,
    title: 'Intégrations',
    description: 'Synchronisation ERP et plateformes e-commerce.',
    example: 'POST /api/v1/config/erp/sync',
  },
  {
    icon: BarChart3,
    title: 'Analytics',
    description: 'Statistiques d\'expéditions et performance transporteurs.',
    example: 'GET /api/v1/analytics/dashboard',
  },
];

const SCHEMA = {
  '@context': 'https://schema.org',
  '@type': 'TechArticle',
  headline: 'API IncoKalk pour développeurs',
  description:
    'Intégrez IncoKalk à votre ERP ou votre plateforme e-commerce via une API REST authentifiée par clé API ou JWT.',
};

export default function Developers() {
  return (
    <div className="min-h-screen bg-gradient-to-br from-bg to-white pb-24 md:pb-0">
      <Seo
        title="API pour développeurs"
        description="Intégrez IncoKalk à votre ERP ou votre plateforme e-commerce via une API REST authentifiée par clé API ou JWT."
        path="/developers"
        jsonLd={SCHEMA}
      />
      <div className="container mx-auto px-4 py-10 max-w-4xl">
        <Breadcrumbs items={[{ label: 'Développeurs' }]} />

        <div className="text-center my-10">
          <h1 className="text-4xl font-extrabold text-ink mb-4">
            <span className="text-accent font-normal" aria-hidden="true">:: </span>
            L'API <span className="text-accent">IncoKalk</span>
          </h1>
          <p className="text-lg text-ink-soft max-w-2xl mx-auto">
            Une API REST pour connecter IncoKalk à votre ERP, votre plateforme e-commerce ou vos
            propres outils internes — les mêmes données que celles affichées dans l'application.
          </p>
        </div>

        <section className="bg-surface border border-line rounded-none p-6 mb-10">
          <div className="flex items-center gap-2 mb-4">
            <KeyRound size={20} className="text-accent" />
            <h2 className="text-xl font-bold text-ink">Authentification</h2>
          </div>
          <p className="text-sm text-ink-soft mb-4">
            Deux méthodes selon votre cas d'usage : un token JWT pour une intégration côté
            utilisateur, ou une clé API dédiée pour une intégration serveur-à-serveur.
          </p>
          <div className="grid sm:grid-cols-2 gap-3">
            <div className="bg-bg border border-line rounded-none p-4 font-mono text-xs text-ink overflow-x-auto">
              Authorization: Bearer &lt;jwt_token&gt;
            </div>
            <div className="bg-bg border border-line rounded-none p-4 font-mono text-xs text-ink overflow-x-auto">
              X-API-Key: &lt;votre_cle_api&gt;
            </div>
          </div>
          <p className="text-xs text-ink-soft mt-3">
            Chaque requête doit également inclure l'en-tête <code className="font-mono">X-Tenant-ID</code>{' '}
            identifiant votre société (architecture multi-tenant).
          </p>
        </section>

        <section className="mb-10">
          <div className="flex items-center gap-2 mb-6">
            <Code2 size={20} className="text-accent" />
            <h2 className="text-xl font-bold text-ink">Ce que vous pouvez intégrer</h2>
          </div>
          <div className="grid md:grid-cols-2 gap-4">
            {CATEGORIES.map((cat) => {
              const Icon = cat.icon;
              return (
                <div key={cat.title} className="bg-surface border border-line rounded-none p-5">
                  <div className="flex items-center gap-3 mb-2">
                    <div className="w-9 h-9 rounded-none bg-accent-soft flex items-center justify-center shrink-0">
                      <Icon size={18} className="text-accent" />
                    </div>
                    <h3 className="font-bold text-ink">{cat.title}</h3>
                  </div>
                  <p className="text-sm text-ink-soft mb-2">{cat.description}</p>
                  <code className="text-xs font-mono text-accent-strong">{cat.example}</code>
                </div>
              );
            })}
          </div>
        </section>

        <div className="text-center bg-surface border border-line rounded-none p-8">
          <h2 className="text-xl font-bold text-ink mb-2">Générez votre clé API</h2>
          <p className="text-ink-soft mb-6">
            La gestion des clés API est accessible depuis votre espace Administration, une fois
            connecté avec un compte administrateur.
          </p>
          <div className="flex flex-col sm:flex-row items-center justify-center gap-3">
            <Link to="/api-keys" className="btn-primary inline-flex items-center gap-2">
              Accéder aux clés API
              <ArrowRight size={18} />
            </Link>
            <Link to="/register" className="btn-outline-white !text-ink !border-ink-soft hover:!bg-bg">
              Créer un compte gratuit
            </Link>
          </div>
        </div>
      </div>
      <StickyMobileCta />
    </div>
  );
}
