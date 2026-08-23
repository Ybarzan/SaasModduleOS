import { Link } from 'react-router-dom';
import {
  ChevronRight, Ship, Plane, Clock, ScanLine, Calendar, Scale, Leaf,
  ClipboardList, Bell, Percent, Ruler, Tag, Target, ShieldAlert, Mail,
  Umbrella, IdCard, FileText,
} from 'lucide-react';
import { useAuthStore } from '../stores/auth';

const MANAGER_ROLES = ['OWNER', 'ADMIN', 'MANAGER'];
const ADMIN_ROLES = ['OWNER', 'ADMIN'];

interface MoreItem {
  to: string;
  icon: typeof Ship;
  label: string;
  requiredRoles?: string[];
}

interface MoreSection {
  label: string;
  items: MoreItem[];
}

// Regroupement librement inspiré des 7 Hubs du web (frontend/src/config/navigation.ts)
// mais volontairement réduit à ce qui a du sens sur mobile -- lookup/terrain, pas de
// back-office (facturation, ERP, multi-filiales...). Voir la recherche de cadrage :
// 9 candidats "terrain" + 11 ambigus retenus en lecture seule.
const SECTIONS: MoreSection[] = [
  {
    label: 'Transport & suivi',
    items: [
      { to: '/ship-tracker', icon: Ship, label: 'Ship Tracker' },
      { to: '/flight-radar', icon: Plane, label: 'Flight Radar' },
      { to: '/eta-predictions', icon: Clock, label: 'Prédictions ETA', requiredRoles: MANAGER_ROLES },
      { to: '/carrier-bookings', icon: Calendar, label: 'Réservations' },
      { to: '/rate-comparison', icon: Scale, label: 'Comparateur tarifs' },
      { to: '/co2', icon: Leaf, label: 'Émissions CO₂' },
      { to: '/assurance-cargo', icon: Umbrella, label: 'Assurance cargo' },
    ],
  },
  {
    label: 'Entrepôt',
    items: [
      { to: '/receivings', icon: ClipboardList, label: 'Bons de réception' },
      { to: '/scan-receiving', icon: ScanLine, label: 'Scanner réception' },
    ],
  },
  {
    label: 'Douane & conformité',
    items: [
      { to: '/customs-duty', icon: Percent, label: 'Droits de douane' },
      { to: '/volumetric-weight', icon: Ruler, label: 'Poids volumétrique' },
      { to: '/hs-classification', icon: Tag, label: 'Classification HS' },
      { to: '/trade-agreements', icon: FileText, label: 'Accords commerciaux' },
      { to: '/eori', icon: IdCard, label: 'EORI', requiredRoles: MANAGER_ROLES },
      { to: '/dps', icon: ShieldAlert, label: 'Screening parties', requiredRoles: MANAGER_ROLES },
    ],
  },
  {
    label: 'Qualité & alertes',
    items: [
      { to: '/quality', icon: Target, label: 'Qualité Six Sigma', requiredRoles: MANAGER_ROLES },
      { to: '/notification-rules', icon: Bell, label: 'Règles de notification', requiredRoles: MANAGER_ROLES },
      { to: '/email-intake', icon: Mail, label: 'Suivi email entrant', requiredRoles: ADMIN_ROLES },
    ],
  },
];

const More = () => {
  const role = useAuthStore((s) => s.user?.role);

  return (
    <>
      <div className="header-bar">
        <h1 className="title" style={{ margin: 0 }}>Plus</h1>
      </div>

      <div className="stack" style={{ marginTop: 12 }}>
        {SECTIONS.map((section) => {
          const visibleItems = section.items.filter(
            (item) => !item.requiredRoles || (role ? item.requiredRoles.includes(role) : false)
          );
          if (visibleItems.length === 0) return null;

          return (
            <div key={section.label}>
              <p className="section-label">{section.label}</p>
              <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
                {visibleItems.map((item, i) => {
                  const Icon = item.icon;
                  return (
                    <Link
                      key={item.to}
                      to={item.to}
                      className="row-between"
                      style={{
                        padding: '14px 16px',
                        borderBottom: i < visibleItems.length - 1 ? '1px solid rgb(var(--c-line))' : 'none',
                      }}
                    >
                      <span className="row" style={{ gap: 10 }}>
                        <Icon size={18} color="rgb(var(--c-accent))" />
                        {item.label}
                      </span>
                      <ChevronRight size={16} color="rgb(var(--c-ink-soft))" />
                    </Link>
                  );
                })}
              </div>
            </div>
          );
        })}
      </div>
    </>
  );
};

export default More;
