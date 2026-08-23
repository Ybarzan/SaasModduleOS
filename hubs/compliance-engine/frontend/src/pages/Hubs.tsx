import { Link } from 'react-router-dom';
import type { LucideIcon } from 'lucide-react';
import { ArrowRight, CheckCircle2 } from 'lucide-react';
import Seo from '../components/Seo';
import Breadcrumbs from '../components/Breadcrumbs';
import StickyMobileCta from '../components/StickyMobileCta';
import { NAV_GROUPS, type NavGroup } from '../config/navigation';
import { PLAN_HIERARCHY, type PlanId } from '../stores/auth';

/** Même nom commercial que Sidebar.tsx (PLAN_DISPLAY_NAME) — à garder synchronisé. */
const PLAN_DISPLAY_NAME: Record<PlanId, string> = {
  FREE: 'Découverte',
  STARTER: 'Starter',
  PRO: 'Croissance',
  ENTERPRISE: 'Suite',
};

// Pas de description marketing dans navigation.ts (source technique de la nav produit) :
// ce texte est le seul ajout éditorial, tout le reste (modules, palier de plan) est dérivé
// de NAV_GROUPS pour ne jamais désynchroniser cette page de la vraie nav applicative.
const HUB_PITCH: Record<string, string> = {
  'import-export': "Simulez et sécurisez vos opérations douanières : droits de douane, classification HS, données TARIC, certificats EUR.1, screening des parties sensibles.",
  'transport': "Réservez, comparez et suivez vos expéditions tous modes — mer, air, route : devis, transporteurs, tracking temps réel.",
  'docs': "Générez et centralisez vos documents d'expédition, importez vos factures fournisseurs automatiquement par email.",
  'warehouse': "Pilotez vos entrepôts de bout en bout : catalogue articles, stock, bons de réception, scan.",
  'finance': "Facturation transporteurs et clients, taux de change, financement de facture, reporting carbone et CSRD.",
  'client-portal': "Donnez à vos clients un accès de suivi en marque blanche, sans exposer votre back-office.",
  'platform': "Équipe, rôles, intégrations ERP/e-commerce, clés API, webhooks — le socle technique inclus avec chaque abonnement.",
};

function planBadge(group: NavGroup): string {
  const plans = Array.from(new Set(group.items.map((item) => item.requiredPlan)));
  if (plans.every((p) => p === undefined)) return 'Inclus dans tous les plans';

  const defined = plans.filter((p): p is PlanId => p !== undefined);
  const sorted = defined.sort((a, b) => PLAN_HIERARCHY[a] - PLAN_HIERARCHY[b]);
  const min = sorted[0];
  const max = sorted[sorted.length - 1];

  if (min === max) return `À partir du plan ${PLAN_DISPLAY_NAME[min]}`;
  return `Du plan ${PLAN_DISPLAY_NAME[min]} au plan ${PLAN_DISPLAY_NAME[max]}`;
}

function HubCard({ group }: { group: NavGroup }) {
  const Icon = group.icon as LucideIcon;
  const preview = group.items.slice(0, 5);
  const remaining = group.items.length - preview.length;

  return (
    <div className="bg-surface border border-line rounded-2xl p-6 flex flex-col h-full">
      <div className="flex items-center gap-3 mb-3">
        <span className="inline-flex items-center justify-center w-11 h-11 rounded-xl bg-accent-soft text-accent-strong shrink-0">
          <Icon size={20} />
        </span>
        <div>
          <h3 className="font-bold text-ink">{group.label}</h3>
          <span className="text-xs font-semibold text-accent-strong">{planBadge(group)}</span>
        </div>
      </div>

      <p className="text-sm text-ink-soft mb-4">{HUB_PITCH[group.id]}</p>

      <ul className="space-y-1.5 text-sm mb-4 flex-1">
        {preview.map((item) => (
          <li key={item.to} className="flex items-center gap-2 text-ink">
            <CheckCircle2 size={14} className="text-success shrink-0" />
            {item.label}
          </li>
        ))}
      </ul>
      {remaining > 0 && (
        <p className="text-xs text-ink-soft">+ {remaining} autre{remaining > 1 ? 's' : ''} module{remaining > 1 ? 's' : ''}</p>
      )}
    </div>
  );
}

export default function Hubs() {
  return (
    <div className="min-h-screen bg-gradient-to-br from-bg to-white pb-24 md:pb-0">
      <Seo
        title="Les 7 Hubs métier IncoKalk"
        description="Import-Export, Transport Multimodal, Documents, Entrepôt, Finance, Portail Client, Plateforme — 7 Hubs métier réunis dans un seul abonnement IncoKalk."
        path="/hubs"
      />
      <div className="container mx-auto px-4 py-10 max-w-6xl">
        <Breadcrumbs items={[{ label: 'Hubs' }]} />

        <div className="text-center my-10">
          <h1 className="text-4xl font-extrabold text-ink mb-4">
            7 Hubs métier, <span className="text-accent">un seul abonnement</span>
          </h1>
          <p className="text-lg text-ink-soft max-w-2xl mx-auto">
            IncoKalk n'est pas un calculateur isolé : c'est une plateforme organisée par métier.
            Chaque Hub regroupe les modules dont un service a besoin — vous débloquez les Hubs
            au fur et à mesure que votre plan grandit avec vous.
          </p>
        </div>

        <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-6 mb-12">
          {NAV_GROUPS.map((group) => (
            <HubCard key={group.id} group={group} />
          ))}
        </div>

        <div className="text-center bg-surface border border-line rounded-2xl p-8">
          <h2 className="text-xl font-bold text-ink mb-2">Quel plan débloque quels Hubs ?</h2>
          <p className="text-ink-soft mb-6">
            Comparez les 4 plans IncoKalk et le détail des modules inclus dans chacun.
          </p>
          <div className="flex flex-col sm:flex-row gap-3 justify-center">
            <Link to="/pricing" className="btn-primary inline-flex items-center justify-center gap-2">
              Voir les plans et tarifs
              <ArrowRight size={18} />
            </Link>
            <Link
              to="/register"
              className="px-6 py-3 rounded-lg font-semibold border border-line text-ink hover:bg-surface-2 transition-colors inline-flex items-center justify-center gap-2"
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
