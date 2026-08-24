import type { LucideIcon } from 'lucide-react';
import { ROLE_HIERARCHY, hasMinimumPlan, type UserRole, type PlanId } from '../stores/auth';
import {
  LayoutDashboard, Truck, Search, Package, Wifi, Bell, Users, Database,
  ScrollText, Calculator, Link2, Ship, Plane, Leaf, Shield, DollarSign, Scale, Umbrella,
  Route, FileText, CreditCard, Globe, BookOpen, FileCheck, ShieldAlert, Tag, Receipt,
  BarChart3, Clock, CheckSquare, TrendingDown, Calendar, ShoppingCart, Building2,
  GraduationCap, Palette, Mail, KeyRound, Webhook, Euro, Warehouse, Boxes, ClipboardList,
  ScanLine, Target, Zap,
} from 'lucide-react';

export interface NavItem {
  to: string;
  icon: LucideIcon;
  label: string;
  /** Undefined = visible to any authenticated user. Must mirror the route's actual
   * ProtectedRoute gating in App.tsx (requiredRole="MANAGER" or requireAdmin) exactly —
   * a mismatch means a user sees a link they get redirected away from on click, or a
   * link that should be visible to them is hidden. */
  requiredRole?: Extract<UserRole, 'MANAGER' | 'ADMIN'>;
  /** Undefined = pas de palier minimum (item de plateforme, jamais vendu seul).
   * Sinon : plan minimum requis (voir BillingService.getPlans côté backend, source
   * de vérité des features par plan). Contrairement à requiredRole, un item sous
   * son palier n'est PAS caché : il reste visible mais rendu verrouillé (voir
   * Sidebar/MobileNavDrawer) pour servir d'appel à l'upgrade. */
  requiredPlan?: PlanId;
}

export interface NavGroup {
  id: string;
  label: string;
  icon: LucideIcon;
  items: NavItem[];
}

export interface StandaloneNavItem extends NavItem {
  highlight?: boolean;
}

/**
 * Items pinned above the collapsible groups, always visible, same level.
 * "Calculateur Incoterms" is the core product — it must never be buried
 * inside a dropdown/group again (see plan whimsical-snuggling-ullman.md).
 */
export const STANDALONE_ITEMS: StandaloneNavItem[] = [
  { to: '/dashboard', icon: LayoutDashboard, label: 'Tableau de bord' },
  { to: '/simulation', icon: Calculator, label: 'Calculateur Incoterms', highlight: true },
  { to: '/eta-predictions', icon: Clock, label: 'Prédictions ETA', highlight: true, requiredRole: 'MANAGER' },
];

/**
 * Regroupement par Hub commercial (voir modèle de commercialisation en mémoire projet),
 * pas par département technique interne. Ordre = échelle de maturité : Import-Export et
 * Transport Multimodal (le bundle Starter PME) en tête, Plateforme & Intégrations
 * (jamais vendu seul) en dernier.
 */
export const NAV_GROUPS: NavGroup[] = [
  {
    id: 'import-export',
    label: 'Import-Export',
    icon: Globe,
    items: [
      { to: '/landed-cost', icon: DollarSign, label: 'Landed Cost', requiredRole: 'MANAGER', requiredPlan: 'STARTER' },
      { to: '/poids-volumetrique', icon: Scale, label: 'Poids volumétrique', requiredPlan: 'STARTER' },
      { to: '/douane', icon: Shield, label: 'Droits de douane', requiredPlan: 'STARTER' },
      { to: '/customs', icon: Calculator, label: 'Simulateur douane', requiredRole: 'MANAGER', requiredPlan: 'STARTER' },
      { to: '/hs-classification', icon: Tag, label: 'Classification HS', requiredPlan: 'STARTER' },
      { to: '/trade-agreements', icon: BookOpen, label: 'Accords commerciaux', requiredPlan: 'STARTER' },
      { to: '/taric', icon: Database, label: 'Données TARIC', requiredRole: 'MANAGER', requiredPlan: 'PRO' },
      { to: '/declarations', icon: FileCheck, label: 'Déclarations douane', requiredRole: 'MANAGER', requiredPlan: 'PRO' },
      { to: '/eur1', icon: FileText, label: 'Certificats EUR.1', requiredRole: 'MANAGER', requiredPlan: 'PRO' },
      { to: '/customs-invoices', icon: Receipt, label: 'Factures douanières', requiredRole: 'MANAGER', requiredPlan: 'PRO' },
      { to: '/eori', icon: Globe, label: 'Configuration EORI', requiredRole: 'MANAGER', requiredPlan: 'PRO' },
      { to: '/dps', icon: ShieldAlert, label: 'Screening parties', requiredRole: 'MANAGER', requiredPlan: 'PRO' },
      { to: '/french-fiscal', icon: Euro, label: 'Fiscalité FR', requiredRole: 'ADMIN', requiredPlan: 'PRO' },
      { to: '/compliance', icon: Shield, label: 'Conformité', requiredRole: 'ADMIN', requiredPlan: 'PRO' },
    ],
  },
  {
    id: 'transport',
    label: 'Transport Multimodal',
    icon: Truck,
    items: [
      { to: '/quotes', icon: Search, label: 'Devis', requiredPlan: 'STARTER' },
      { to: '/shipments', icon: Package, label: 'Expéditions', requiredPlan: 'STARTER' },
      { to: '/logistics', icon: Package, label: 'Logistique', requiredPlan: 'STARTER' },
      { to: '/carriers', icon: Truck, label: 'Transporteurs', requiredPlan: 'STARTER' },
      { to: '/carrier-bookings', icon: Calendar, label: 'Réservations', requiredPlan: 'STARTER' },
      { to: '/rate-comparison', icon: Scale, label: 'Comparateur tarifs', requiredPlan: 'STARTER' },
      { to: '/shipping-rates', icon: DollarSign, label: 'Gestion tarifs', requiredRole: 'MANAGER', requiredPlan: 'STARTER' },
      { to: '/groupage', icon: Boxes, label: 'Groupage & Co-loading', requiredRole: 'MANAGER', requiredPlan: 'STARTER' },
      { to: '/optimisation-itineraire', icon: Route, label: 'Optimisation itinéraire', requiredPlan: 'STARTER' },
      { to: '/ship-tracker', icon: Ship, label: 'Ship Tracker', requiredPlan: 'STARTER' },
      { to: '/flight-radar', icon: Plane, label: 'Flight Radar', requiredPlan: 'STARTER' },
      { to: '/assurance-cargo', icon: Umbrella, label: 'Assurance cargo', requiredPlan: 'STARTER' },
      { to: '/co2', icon: Leaf, label: 'Émissions CO₂', requiredPlan: 'STARTER' },
      { to: '/quality', icon: Target, label: 'Qualité Six Sigma', requiredRole: 'MANAGER', requiredPlan: 'STARTER' },
      { to: '/approvals', icon: CheckSquare, label: 'Workflows approbation', requiredRole: 'MANAGER', requiredPlan: 'STARTER' },
    ],
  },
  {
    id: 'docs',
    label: 'Documents & Automatisation',
    icon: FileText,
    items: [
      { to: '/documents', icon: FileText, label: 'Documents', requiredPlan: 'STARTER' },
      { to: '/email-intake', icon: Mail, label: 'Import Email', requiredRole: 'ADMIN', requiredPlan: 'STARTER' },
      { to: '/orchestration-suggestions', icon: Zap, label: "Suggestions d'action", requiredRole: 'MANAGER', requiredPlan: 'PRO' },
    ],
  },
  {
    id: 'warehouse',
    label: 'Entrepôt & Stock',
    icon: Warehouse,
    items: [
      { to: '/warehouses', icon: Warehouse, label: 'Entrepôts', requiredPlan: 'ENTERPRISE' },
      { to: '/inventory-items', icon: Package, label: 'Catalogue articles', requiredPlan: 'ENTERPRISE' },
      { to: '/inventory', icon: Boxes, label: 'Stock', requiredPlan: 'ENTERPRISE' },
      { to: '/receivings', icon: ClipboardList, label: 'Bons de réception', requiredPlan: 'ENTERPRISE' },
      { to: '/scan-receiving', icon: ScanLine, label: 'Scanner réception', requiredPlan: 'ENTERPRISE' },
    ],
  },
  {
    id: 'finance',
    label: 'Finance & Trésorerie',
    icon: DollarSign,
    items: [
      { to: '/carrier-invoices', icon: Receipt, label: 'Facturation transporteurs', requiredRole: 'MANAGER', requiredPlan: 'ENTERPRISE' },
      { to: '/client-invoices', icon: Receipt, label: 'Facturation clients', requiredRole: 'ADMIN', requiredPlan: 'ENTERPRISE' },
      { to: '/payment-terms', icon: Calendar, label: 'Paiements', requiredRole: 'MANAGER', requiredPlan: 'ENTERPRISE' },
      { to: '/change', icon: DollarSign, label: 'Taux de change', requiredPlan: 'ENTERPRISE' },
      { to: '/finance', icon: TrendingDown, label: 'Finance SCF', requiredRole: 'ADMIN', requiredPlan: 'ENTERPRISE' },
      { to: '/fintech', icon: Building2, label: 'Intégrations fintech', requiredRole: 'ADMIN', requiredPlan: 'ENTERPRISE' },
      { to: '/financials', icon: BarChart3, label: 'Reporting financier', requiredRole: 'ADMIN', requiredPlan: 'ENTERPRISE' },
      { to: '/carbon', icon: Leaf, label: 'Dashboard carbone', requiredRole: 'MANAGER', requiredPlan: 'ENTERPRISE' },
      { to: '/csrd', icon: FileText, label: 'Reporting CSRD', requiredRole: 'ADMIN', requiredPlan: 'ENTERPRISE' },
    ],
  },
  {
    id: 'client-portal',
    label: 'Portail Client',
    icon: Link2,
    items: [
      { to: '/clients', icon: Users, label: 'Clients', requiredRole: 'ADMIN', requiredPlan: 'STARTER' },
      { to: '/shared-links', icon: Link2, label: 'Liens de suivi', requiredRole: 'ADMIN', requiredPlan: 'STARTER' },
      { to: '/branding', icon: Palette, label: 'Portail White-Label', requiredRole: 'ADMIN', requiredPlan: 'STARTER' },
    ],
  },
  {
    id: 'platform',
    label: 'Plateforme & Intégrations',
    icon: Database,
    items: [
      { to: '/team', icon: Users, label: 'Équipe' },
      { to: '/roles', icon: Shield, label: 'Rôles', requiredRole: 'ADMIN' },
      { to: '/company-settings', icon: Building2, label: 'Mon entreprise', requiredRole: 'ADMIN' },
      { to: '/branches', icon: Building2, label: 'Multi-branche', requiredRole: 'ADMIN' },
      { to: '/billing', icon: CreditCard, label: 'Facturation', requiredRole: 'ADMIN' },
      { to: '/audit', icon: ScrollText, label: 'Journal', requiredRole: 'ADMIN' },
      { to: '/api-keys', icon: KeyRound, label: 'Clés API', requiredRole: 'ADMIN' },
      { to: '/webhooks', icon: Webhook, label: 'Webhooks', requiredRole: 'ADMIN' },
      { to: '/erp', icon: Database, label: 'Intégrations ERP' },
      { to: '/fleethub', icon: Truck, label: 'Fleet Hub', requiredRole: 'MANAGER' },
      { to: '/ecommerce', icon: ShoppingCart, label: 'E-Commerce', requiredRole: 'MANAGER' },
      { to: '/providers', icon: Wifi, label: 'Fournisseurs' },
      { to: '/notifications', icon: Bell, label: 'Notifications' },
      { to: '/notification-rules', icon: Bell, label: 'Règles de notification', requiredRole: 'MANAGER' },
      { to: '/academy', icon: GraduationCap, label: 'Academy' },
    ],
  },
];

export function isItemVisible(item: NavItem, userRole?: UserRole): boolean {
  if (!item.requiredRole) return true;
  if (!userRole) return false;
  return ROLE_HIERARCHY[userRole] <= ROLE_HIERARCHY[item.requiredRole];
}

/** Verrouillage commercial (distinct du rôle) : true si le plan de l'entreprise
 * n'atteint pas requiredPlan. Un item verrouillé reste dans la nav (voir NavItem). */
export function isItemLocked(item: NavItem, userPlan?: PlanId): boolean {
  if (!item.requiredPlan) return false;
  return !hasMinimumPlan(userPlan, item.requiredPlan);
}

/** Derives a page title from the nav config so app pages don't need to declare one manually. */
export function getPageLabel(pathname: string): string | null {
  const standalone = STANDALONE_ITEMS.find((item) => item.to === pathname);
  if (standalone) return standalone.label;
  for (const group of NAV_GROUPS) {
    const match = group.items.find((item) => item.to === pathname);
    if (match) return match.label;
  }
  return null;
}

/** Même principe que getPageLabel : source unique de vérité pour le plan requis
 * par une route, consommée par ProtectedRoute (App.tsx) pour fermer côté route
 * ce que le menu ne fait que masquer visuellement. */
export function getRequiredPlan(pathname: string): PlanId | undefined {
  const standalone = STANDALONE_ITEMS.find((item) => item.to === pathname);
  if (standalone) return standalone.requiredPlan;
  for (const group of NAV_GROUPS) {
    const match = group.items.find((item) => item.to === pathname);
    if (match) return match.requiredPlan;
  }
  return undefined;
}
