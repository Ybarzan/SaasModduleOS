import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { LogOut, Loader2, Package, Clock, CheckSquare, DollarSign, Leaf, ScanLine, ClipboardCheck } from 'lucide-react';
import { mobileApi } from '../lib/api';
import { useAuthStore } from '../stores/auth';

const MANAGER_ROLES = ['OWNER', 'ADMIN', 'MANAGER'];

interface DashboardData {
  total_shipments: number;
  active_shipments: number;
  pending_approvals: number;
  total_cost_month: number;
  carbon_offset_month: number;
  recent_activity: Array<{ order_number?: string; status?: string; origin?: string; destination?: string }>;
}

const KPIS: Array<{ key: keyof DashboardData; label: string; icon: typeof Package; format?: (v: number) => string }> = [
  { key: 'total_shipments', label: 'Expéditions totales', icon: Package },
  { key: 'active_shipments', label: 'En cours', icon: Clock },
  { key: 'pending_approvals', label: 'Approbations en attente', icon: CheckSquare },
  { key: 'total_cost_month', label: 'Coût du mois', icon: DollarSign, format: (v) => `${v.toFixed(0)} €` },
  { key: 'carbon_offset_month', label: 'CO₂ compensé (kg)', icon: Leaf, format: (v) => v.toFixed(1) },
];

const Dashboard = () => {
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);
  const canManage = user?.role ? MANAGER_ROLES.includes(user.role) : false;

  const { data, isLoading, isError } = useQuery({
    queryKey: ['mobile-dashboard'],
    queryFn: async () => (await mobileApi.dashboard()).data as DashboardData,
  });

  return (
    <>
      <div className="header-bar row-between">
        <div>
          <p className="text-sm text-soft" style={{ margin: 0 }}>Bonjour</p>
          <h1 className="title" style={{ margin: 0 }}>{user?.firstName || 'Bienvenue'}</h1>
        </div>
        <button
          onClick={logout}
          aria-label="Déconnexion"
          style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'rgb(var(--c-ink-soft))' }}
        >
          <LogOut size={20} />
        </button>
      </div>

      <div className="stack" style={{ marginTop: 16 }}>
        {isLoading && (
          <div className="center-screen" style={{ minHeight: 200 }}>
            <Loader2 className="spin" color="rgb(var(--c-ink-soft))" />
          </div>
        )}

        {isError && <p className="error-text">Impossible de charger le tableau de bord.</p>}

        {data && (
          <>
            <div className="kpi-grid">
              {KPIS.map(({ key, label, icon: Icon, format }) => {
                const raw = data[key];
                const value = typeof raw === 'number' ? raw : 0;
                return (
                  <div key={key} className="card">
                    <Icon size={16} color="rgb(var(--c-accent))" style={{ marginBottom: 6 }} />
                    <div className="kpi-value">{format ? format(value) : value}</div>
                    <div className="kpi-label">{label}</div>
                  </div>
                );
              })}
            </div>

            {canManage && (
              <div className="card">
                <p style={{ fontWeight: 700, margin: '0 0 10px' }}>Raccourcis</p>
                <div className="row" style={{ gap: 8 }}>
                  <Link to="/scan" className="btn btn-primary" style={{ flex: 1, textDecoration: 'none' }}>
                    <ScanLine size={16} />
                    Scanner
                  </Link>
                  <Link
                    to="/approvals"
                    className="btn btn-primary"
                    style={{ flex: 1, textDecoration: 'none', background: 'rgb(var(--c-ink-soft))' }}
                  >
                    <ClipboardCheck size={16} />
                    Approbations
                  </Link>
                </div>
              </div>
            )}

            {data.recent_activity?.length > 0 && (
              <div className="card">
                <p style={{ fontWeight: 700, margin: '0 0 10px' }}>Activité récente</p>
                <div className="stack">
                  {data.recent_activity.slice(0, 5).map((item, i) => (
                    <div key={i} className="row-between text-sm">
                      <span>{item.order_number || '—'}</span>
                      <span className="text-soft">{item.origin} → {item.destination}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </>
        )}
      </div>
    </>
  );
};

export default Dashboard;
