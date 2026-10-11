/**
 * Registre des modules de l'écosystème Praxio. Chaque module pointe soit vers
 * une app déployée indépendamment ("live"), soit vers une section qui vit
 * encore dans un autre module en attendant son extraction ("integrated") —
 * voir docs/07-integration-fleet-hub.md pour le principe ("cohabiter, pas
 * fusionner") qui s'applique aussi à Transport et Logistix.
 *
 * Étendre ce fichier (pas le composant) quand un module change de statut ou
 * qu'un nouveau module arrive — App.tsx ne fait que l'afficher.
 */

export type ModuleStatus = 'live' | 'integrated' | 'soon';

export interface PraxioModule {
  id: string;
  name: string;
  tagline: string;
  status: ModuleStatus;
  /** Texte du tag affiché (ex: "autonome", "dans compliance engine"). */
  statusLabel: string;
  href: string;
}

const COMPLIANCE_URL = import.meta.env.VITE_COMPLIANCE_URL ?? 'http://localhost:5173';
const FLEETHUB_URL = import.meta.env.VITE_FLEETHUB_URL ?? 'http://localhost:5199';

export const modules: PraxioModule[] = [
  {
    id: 'compliance',
    name: 'Compliance Engine',
    tagline: 'Douane, Incoterms, TARIC, DAU/DEB/ICS2 — le moteur de conformité Praxio.',
    status: 'live',
    statusLabel: 'autonome',
    href: COMPLIANCE_URL,
  },
  {
    id: 'transport',
    name: 'Transport',
    tagline: 'Transporteurs, booking multimodal, tracking — en cours d’extraction de Compliance Engine.',
    status: 'integrated',
    statusLabel: 'dans compliance engine',
    href: `${COMPLIANCE_URL}/carriers`,
  },
  {
    id: 'logistix',
    name: 'Logistix',
    tagline: 'Entrepôts, réception, inventaire — en cours d’extraction de Compliance Engine.',
    status: 'integrated',
    statusLabel: 'dans compliance engine',
    href: `${COMPLIANCE_URL}/warehouses`,
  },
  {
    id: 'fleethub',
    name: 'Fleet Hub',
    tagline: 'Flotte poids lourds, chauffeurs, tachygraphe, carte temps réel.',
    status: 'live',
    statusLabel: 'autonome',
    href: FLEETHUB_URL,
  },
  {
    id: 'fleetmarket',
    name: 'Fleet Market',
    tagline: 'Marketplace de capacité — partage opt-in de disponibilité véhicule.',
    status: 'integrated',
    statusLabel: 'dans fleet hub',
    href: `${FLEETHUB_URL}/marketplace`,
  },
];
