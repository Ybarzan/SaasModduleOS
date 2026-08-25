import { Link } from 'react-router-dom';
import type { LucideIcon } from 'lucide-react';
import { ArrowRight, Info, Ship, Building2, ShoppingCart, CheckCircle2 } from 'lucide-react';
import Seo from '../components/Seo';
import Breadcrumbs from '../components/Breadcrumbs';
import StickyMobileCta from '../components/StickyMobileCta';

interface UseCase {
  icon: LucideIcon;
  name: string;
  role: string;
  companyProfile: string;
  situation: string;
  modules: string[];
  approach: string;
}

// Trois profils type représentatifs des utilisateurs IncoKalk (voir la doc interne de
// stratégie marketing) -- volontairement PAS présentés comme des clients réels : pas de
// nom d'entreprise, pas de citation attribuée, pas de métrique chiffrée revendiquée comme
// un résultat mesuré. Chaque module cité existe réellement dans le produit (voir NAV_GROUPS
// dans navigation.ts) -- rien n'est inventé sur ce que fait IncoKalk, seule la mise en
// situation est illustrative.
const USE_CASES: UseCase[] = [
  {
    icon: Ship,
    name: 'Sophie',
    role: 'Responsable logistique / ADV export',
    companyProfile: 'PME industrielle, 5 à 50 personnes, 10 à 80 expéditions internationales par mois',
    situation:
      "Sophie gère l'export seule ou en petite équipe : elle compare les transporteurs à la main, ressaisit les mêmes informations pour chaque déclaration douanière, et craint l'erreur de classification qui déclenchera un contrôle.",
    modules: ['Calculateur Incoterms', 'Comparateur de tarifs transporteurs', 'Classification HS', 'Déclarations douane (DEB/Intrastat)', 'Ship Tracker'],
    approach:
      "Elle simule le coût total par Incoterm avant de valider une commande, compare les tarifs transporteurs sans changer d'onglet, et génère sa déclaration douanière directement à partir des expéditions déjà enregistrées plutôt que de ressaisir les mêmes données une deuxième fois.",
  },
  {
    icon: Building2,
    name: 'Marc',
    role: 'Directeur supply chain / opérations',
    companyProfile: 'Entreprise multi-sites, 50 à 500 personnes, 100 à 1000+ expéditions par mois',
    situation:
      "Marc pilote plusieurs filiales dont les données logistiques n'ont pas de vue consolidée. Chaque site a sa propre méthode, un audit ISO approche, et reconstituer un historique fiable prend des jours.",
    modules: ['Multi-branche', 'Reporting financier', "Journal d'audit", 'Intégrations ERP', 'Gestion des rôles'],
    approach:
      "Il consulte une vue consolidée par filiale plutôt que des exports Excel dispersés entre équipes, et s'appuie sur le journal d'audit immuable du compte pour préparer une revue de conformité sans reconstituer l'historique à la main.",
  },
  {
    icon: ShoppingCart,
    name: 'Karim',
    role: 'Fondateur e-commerce cross-border (DTC)',
    companyProfile: 'Marque D2C, 1 à 20 personnes, 50 à 500 colis par mois, majoritairement B2C',
    situation:
      "Karim vend directement à l'international. Ses clients lui écrivent pour savoir où est leur colis, et le calcul des droits de douane au moment de l'achat reste approximatif — source de mauvaises surprises à la livraison.",
    modules: ['Portail client en marque blanche', 'Liens de suivi partageables', 'Landed Cost Calculator', 'Calculateur CO₂', 'Intégrations e-commerce'],
    approach:
      "Il partage un lien de suivi en marque blanche à chaque client plutôt que de répondre lui-même à chaque « où est ma commande », et calcule le coût total à destination (landed cost) en amont pour afficher un prix final fiable dès la commande.",
  },
];

function UseCaseCard({ useCase }: { useCase: UseCase }) {
  const Icon = useCase.icon;
  return (
    <div className="bg-surface border border-line rounded-none p-6 sm:p-8">
      <div className="flex items-start gap-4 mb-4">
        <span className="inline-flex items-center justify-center w-12 h-12 rounded-none bg-accent-soft text-accent-strong shrink-0">
          <Icon size={22} />
        </span>
        <div>
          <h3 className="text-lg font-bold text-ink">{useCase.name} — {useCase.role}</h3>
          <p className="text-xs text-ink-soft mt-0.5">{useCase.companyProfile}</p>
        </div>
      </div>

      <p className="text-sm text-ink-soft mb-4">{useCase.situation}</p>

      <div className="mb-4">
        <p className="text-xs font-semibold text-ink uppercase tracking-wide mb-2">Modules IncoKalk mobilisés</p>
        <ul className="flex flex-wrap gap-2">
          {useCase.modules.map((m) => (
            <li key={m} className="inline-flex items-center gap-1.5 text-xs bg-surface-2 text-ink rounded-full px-3 py-1">
              <CheckCircle2 size={12} className="text-success shrink-0" />
              {m}
            </li>
          ))}
        </ul>
      </div>

      <p className="text-sm text-ink leading-relaxed">{useCase.approach}</p>
    </div>
  );
}

export default function UseCases() {
  return (
    <div className="min-h-screen bg-gradient-to-br from-bg to-white pb-24 md:pb-0">
      <Seo
        title="Cas d'usage — Qui utilise IncoKalk et pourquoi"
        description="Trois profils type d'utilisateurs IncoKalk : PME exportatrice, direction supply chain multi-sites, e-commerce cross-border — et les modules qu'ils mobilisent au quotidien."
        path="/cas-usage"
      />
      <div className="container mx-auto px-4 py-10 max-w-5xl">
        <Breadcrumbs items={[{ label: "Cas d'usage" }]} />

        <div className="text-center my-10">
          <h1 className="text-4xl font-extrabold text-ink mb-4">
            Qui utilise <span className="text-accent">IncoKalk</span>, et pourquoi
          </h1>
          <p className="text-lg text-ink-soft max-w-2xl mx-auto">
            Trois profils que l'on retrouve le plus souvent chez nos utilisateurs, et la manière
            dont chacun combine les modules du produit selon son métier.
          </p>
        </div>

        <div className="bg-accent-soft border border-accent/20 rounded-none p-5 mb-10 flex items-start gap-3 max-w-3xl mx-auto">
          <Info size={18} className="text-accent-strong mt-0.5 shrink-0" />
          <p className="text-sm text-ink">
            Sophie, Marc et Karim sont des profils type, pas des clients nommés — ils illustrent des
            situations fréquentes, pas un témoignage ni des métriques mesurées chez un client précis.
          </p>
        </div>

        <div className="space-y-6 mb-12">
          {USE_CASES.map((useCase) => (
            <UseCaseCard key={useCase.name} useCase={useCase} />
          ))}
        </div>

        <div className="text-center bg-surface border border-line rounded-none p-8">
          <h2 className="text-xl font-bold text-ink mb-2">Quel profil vous ressemble le plus ?</h2>
          <p className="text-ink-soft mb-6">
            Découvrez les 7 Hubs métier d'IncoKalk et le plan qui correspond à votre situation.
          </p>
          <div className="flex flex-col sm:flex-row gap-3 justify-center">
            <Link to="/hubs" className="btn-primary inline-flex items-center justify-center gap-2">
              Explorer les Hubs
              <ArrowRight size={18} />
            </Link>
            <Link
              to="/register"
              className="px-6 py-3 rounded-none font-semibold border border-line text-ink hover:bg-surface-2 transition-colors inline-flex items-center justify-center gap-2"
            >
              Essayer gratuitement
            </Link>
          </div>
        </div>
      </div>
      <StickyMobileCta />
    </div>
  );
}
