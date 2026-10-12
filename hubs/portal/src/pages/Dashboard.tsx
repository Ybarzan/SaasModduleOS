import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  praxioAPI,
  UnauthorizedError,
  type CrossHubDashboard,
  type DashboardKeysStatus,
  type HubStatus,
} from '../lib/praxioApi';

const STORAGE_KEY = 'praxio-admin-password';

function readStoredPassword(): string | null {
  try {
    return sessionStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}

function storePassword(password: string) {
  try {
    sessionStorage.setItem(STORAGE_KEY, password);
  } catch {
    // stockage indisponible (navigation privée, etc.) -- la session ne survivra pas à un refresh
  }
}

function clearStoredPassword() {
  try {
    sessionStorage.removeItem(STORAGE_KEY);
  } catch {
    // rien à faire
  }
}

export default function Dashboard() {
  const [password, setPassword] = useState<string | null>(null);
  const [dashboard, setDashboard] = useState<CrossHubDashboard | null>(null);
  const [keysStatus, setKeysStatus] = useState<DashboardKeysStatus | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const load = async (pwd: string) => {
    setLoading(true);
    setError('');
    try {
      const [dash, keys] = await Promise.all([praxioAPI.getDashboard(pwd), praxioAPI.getKeysStatus(pwd)]);
      setDashboard(dash);
      setKeysStatus(keys);
      setPassword(pwd);
      storePassword(pwd);
    } catch (err) {
      if (err instanceof UnauthorizedError) {
        clearStoredPassword();
        setPassword(null);
        setError(err.message);
      } else {
        setError(err instanceof Error ? err.message : 'Erreur inconnue');
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    const stored = readStoredPassword();
    if (stored) {
      // Déféré en microtâche : `load` met à jour l'état dès sa première ligne
      // (avant le premier await), ce qui déclencherait un setState synchrone
      // dans l'effet si on l'appelait directement ici.
      Promise.resolve().then(() => load(stored));
    }
  }, []);

  const logout = () => {
    clearStoredPassword();
    setPassword(null);
    setDashboard(null);
    setKeysStatus(null);
  };

  if (!password) {
    return <LoginGate loading={loading} error={error} onSubmit={load} />;
  }

  return (
    <div className="min-h-screen bg-bg text-ink">
      <header className="border-b border-line">
        <div className="max-w-5xl mx-auto px-6 py-10 flex items-start justify-between gap-6">
          <div>
            <Link to="/" className="eyebrow hover:text-accent transition-colors">
              &larr; prax<b className="font-bold">io</b>
            </Link>
            <h1 className="text-3xl font-extralight tracking-tight mt-2">Tableau de bord cross-hub</h1>
          </div>
          <button
            type="button"
            onClick={logout}
            className="text-xs text-ink-soft hover:text-ink transition-colors underline"
          >
            Se déconnecter
          </button>
        </div>
      </header>

      <main className="max-w-5xl mx-auto px-6 py-12 space-y-10">
        {error && <div className="border border-danger/40 bg-danger/10 text-danger text-sm px-4 py-3">{error}</div>}

        {dashboard && (
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-5">
            <ComplianceEngineCard status={dashboard.complianceEngine} />
            <FleetHubCard status={dashboard.fleetHub} />
            <LogistixCard status={dashboard.logistix} />
          </div>
        )}

        {keysStatus && (
          <SettingsPanel
            password={password}
            keysStatus={keysStatus}
            onUpdated={(status) => setKeysStatus(status)}
            onReload={() => load(password)}
          />
        )}
      </main>
    </div>
  );
}

function LoginGate({
  loading,
  error,
  onSubmit,
}: {
  loading: boolean;
  error: string;
  onSubmit: (password: string) => void;
}) {
  const [input, setInput] = useState('');

  return (
    <div className="min-h-screen bg-bg text-ink flex items-center justify-center px-6">
      <form
        onSubmit={(e) => {
          e.preventDefault();
          if (input.trim()) onSubmit(input.trim());
        }}
        className="w-full max-w-sm border border-line bg-surface p-8 space-y-4"
      >
        <div className="eyebrow">portail — tableau de bord</div>
        <h1 className="text-2xl font-extralight tracking-tight">
          prax<b className="font-bold">io</b>
        </h1>
        <p className="text-sm text-ink-soft">Accès administrateur requis.</p>
        {error && <p className="text-sm text-danger">{error}</p>}
        <input
          type="password"
          autoFocus
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="Mot de passe administrateur"
          className="w-full px-3 py-2 border border-line bg-bg text-sm font-mono focus:ring-2 focus:ring-accent focus:border-transparent"
        />
        <button
          type="submit"
          disabled={loading || !input.trim()}
          className="btn-primary w-full justify-center disabled:opacity-50"
        >
          {loading ? 'Connexion…' : 'Entrer'}
        </button>
      </form>
    </div>
  );
}

function StatusBadge({ status }: { status: HubStatus<unknown>['status'] }) {
  const label =
    status === 'OK' ? 'Connecté' : status === 'NOT_CONFIGURED' ? 'Non configuré' : 'Inaccessible';
  const cls =
    status === 'OK' ? 'tag-live' : status === 'NOT_CONFIGURED' ? 'tag-soon' : 'tag-sim';
  return (
    <span className={`tag ${cls}`}>
      <span className="tag-dot" />
      {label}
    </span>
  );
}

function HubCardShell({
  title,
  status,
  children,
}: {
  title: string;
  status: HubStatus<unknown>['status'];
  children?: React.ReactNode;
}) {
  return (
    <div className="bg-surface border border-line p-6 flex flex-col gap-4">
      <div className="flex items-start justify-between gap-3">
        <h2 className="text-base font-bold uppercase tracking-[0.02em]">{title}</h2>
        <StatusBadge status={status} />
      </div>
      {status === 'OK' ? (
        children
      ) : status === 'NOT_CONFIGURED' ? (
        <p className="text-sm text-ink-soft">
          Aucune clé enregistrée pour ce hub — voir « Paramètres » ci-dessous.
        </p>
      ) : (
        <p className="text-sm text-ink-soft">Le hub n'a pas répondu. Réessayez plus tard.</p>
      )}
    </div>
  );
}

function Kpi({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="flex items-baseline justify-between gap-3">
      <span className="text-sm text-ink-soft">{label}</span>
      <span className="text-lg font-semibold">{value}</span>
    </div>
  );
}

function ComplianceEngineCard({ status }: { status: CrossHubDashboard['complianceEngine'] }) {
  return (
    <HubCardShell title="Compliance Engine" status={status.status}>
      {status.data && (
        <div className="space-y-2">
          <Kpi label="Simulations ce mois" value={status.data.simulationsThisMonth} />
          <Kpi label="Déclarations ouvertes" value={status.data.openDeclarations} />
          <Kpi label="Factures en retard" value={status.data.overdueInvoicesCount} />
        </div>
      )}
    </HubCardShell>
  );
}

function FleetHubCard({ status }: { status: CrossHubDashboard['fleetHub'] }) {
  return (
    <HubCardShell title="Fleet Hub" status={status.status}>
      {status.data && (
        <div className="space-y-2">
          <Kpi label="Expéditions actives" value={status.data.activeShipments} />
          <Kpi label="Camions disponibles" value={status.data.availableTrucks} />
          <Kpi label="Réservations ouvertes" value={status.data.openCarrierBookings} />
        </div>
      )}
    </HubCardShell>
  );
}

function LogistixCard({ status }: { status: CrossHubDashboard['logistix'] }) {
  return (
    <HubCardShell title="Logistix" status={status.status}>
      {status.data && (
        <div className="space-y-2">
          <Kpi label="Entrepôts actifs" value={status.data.activeWarehouses} />
          <Kpi label="Réceptions ouvertes" value={status.data.openReceivingOrders} />
          <Kpi label="Écarts ouverts" value={status.data.openDiscrepancies} />
        </div>
      )}
    </HubCardShell>
  );
}

function SettingsPanel({
  password,
  keysStatus,
  onUpdated,
  onReload,
}: {
  password: string;
  keysStatus: DashboardKeysStatus;
  onUpdated: (status: DashboardKeysStatus) => void;
  onReload: () => void;
}) {
  const [complianceEngineApiKey, setComplianceEngineApiKey] = useState('');
  const [fleetHubApiKey, setFleetHubApiKey] = useState('');
  const [logistixApiKey, setLogistixApiKey] = useState('');
  const [saving, setSaving] = useState(false);
  const [notice, setNotice] = useState('');
  const [error, setError] = useState('');

  const save = async () => {
    setSaving(true);
    setError('');
    setNotice('');
    try {
      const status = await praxioAPI.updateKeys(password, {
        complianceEngineApiKey: complianceEngineApiKey || undefined,
        fleetHubApiKey: fleetHubApiKey || undefined,
        logistixApiKey: logistixApiKey || undefined,
      });
      onUpdated(status);
      setComplianceEngineApiKey('');
      setFleetHubApiKey('');
      setLogistixApiKey('');
      setNotice('Clés enregistrées');
      onReload();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Échec de l'enregistrement");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="bg-surface border border-line overflow-hidden">
      <div className="px-6 py-4 border-b border-line">
        <h2 className="text-lg font-semibold">Paramètres — clés d'intégration</h2>
        <p className="text-sm text-ink-soft mt-1">
          Collez ici les clés générées dans chaque module (laissez vide pour ne pas changer une
          clé déjà enregistrée).
        </p>
      </div>
      <div className="px-6 py-4 space-y-4">
        {error && <p className="text-sm text-danger">{error}</p>}
        {notice && <p className="text-sm text-success">{notice}</p>}

        <KeyField
          label="Compliance Engine (clé API ic_live_…)"
          configured={keysStatus.complianceEngineConfigured}
          value={complianceEngineApiKey}
          onChange={setComplianceEngineApiKey}
        />
        <KeyField
          label="Fleet Hub (X-Internal-Integration-Key)"
          configured={keysStatus.fleetHubConfigured}
          value={fleetHubApiKey}
          onChange={setFleetHubApiKey}
        />
        <KeyField
          label="Logistix (X-Internal-Integration-Key)"
          configured={keysStatus.logistixConfigured}
          value={logistixApiKey}
          onChange={setLogistixApiKey}
        />

        <button
          type="button"
          onClick={save}
          disabled={saving}
          className="btn-primary disabled:opacity-50"
        >
          {saving ? 'Enregistrement…' : 'Enregistrer'}
        </button>
      </div>
    </div>
  );
}

function KeyField({
  label,
  configured,
  value,
  onChange,
}: {
  label: string;
  configured: boolean;
  value: string;
  onChange: (value: string) => void;
}) {
  return (
    <div className="space-y-1">
      <label className="flex items-center justify-between text-sm text-ink-soft">
        <span>{label}</span>
        <span className={`tag ${configured ? 'tag-live' : 'tag-soon'}`}>
          <span className="tag-dot" />
          {configured ? 'Configurée' : 'Non configurée'}
        </span>
      </label>
      <input
        type="password"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={configured ? 'Remplacer la clé existante…' : 'Coller la clé…'}
        className="w-full px-3 py-2 border border-line bg-bg text-sm font-mono focus:ring-2 focus:ring-accent focus:border-transparent"
      />
    </div>
  );
}
