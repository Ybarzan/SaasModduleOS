import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  praxioAPI,
  UnauthorizedError,
  type CrossHubDashboard,
  type DashboardKeysStatus,
  type HubId,
  type HubStatus,
  type SsoCredentialsStatus,
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
  const [ssoStatus, setSsoStatus] = useState<SsoCredentialsStatus | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [launchError, setLaunchError] = useState('');

  const load = async (pwd: string) => {
    setLoading(true);
    setError('');
    try {
      const [dash, keys, sso] = await Promise.all([
        praxioAPI.getDashboard(pwd),
        praxioAPI.getKeysStatus(pwd),
        praxioAPI.getSsoStatus(pwd),
      ]);
      setDashboard(dash);
      setKeysStatus(keys);
      setSsoStatus(sso);
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
    setSsoStatus(null);
  };

  const launch = async (hub: HubId) => {
    if (!password) return;
    setLaunchError('');
    try {
      const { redirectUrl } = await praxioAPI.launchSso(password, hub);
      window.location.href = redirectUrl;
    } catch (err) {
      setLaunchError(err instanceof Error ? err.message : "Échec de l'ouverture du hub");
    }
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
        {launchError && (
          <div className="border border-danger/40 bg-danger/10 text-danger text-sm px-4 py-3">{launchError}</div>
        )}

        {dashboard && ssoStatus && (
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-5">
            <ComplianceEngineCard
              status={dashboard.complianceEngine}
              ssoConfigured={ssoStatus.complianceEngineConfigured}
              onLaunch={() => launch('complianceEngine')}
            />
            <FleetHubCard
              status={dashboard.fleetHub}
              ssoConfigured={ssoStatus.fleetHubConfigured}
              onLaunch={() => launch('fleetHub')}
            />
            <LogistixCard
              status={dashboard.logistix}
              ssoConfigured={ssoStatus.logistixConfigured}
              onLaunch={() => launch('logistix')}
            />
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

        {ssoStatus && (
          <SsoSettingsPanel
            password={password}
            ssoStatus={ssoStatus}
            onUpdated={(status) => setSsoStatus(status)}
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
  ssoConfigured,
  onLaunch,
  children,
}: {
  title: string;
  status: HubStatus<unknown>['status'];
  ssoConfigured: boolean;
  onLaunch: () => void;
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
      {ssoConfigured && (
        <button
          type="button"
          onClick={onLaunch}
          className="btn-secondary self-start mt-auto"
        >
          &gt; ouvrir
        </button>
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

function ComplianceEngineCard({
  status,
  ssoConfigured,
  onLaunch,
}: {
  status: CrossHubDashboard['complianceEngine'];
  ssoConfigured: boolean;
  onLaunch: () => void;
}) {
  return (
    <HubCardShell title="Compliance Engine" status={status.status} ssoConfigured={ssoConfigured} onLaunch={onLaunch}>
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

function FleetHubCard({
  status,
  ssoConfigured,
  onLaunch,
}: {
  status: CrossHubDashboard['fleetHub'];
  ssoConfigured: boolean;
  onLaunch: () => void;
}) {
  return (
    <HubCardShell title="Fleet Hub" status={status.status} ssoConfigured={ssoConfigured} onLaunch={onLaunch}>
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

function LogistixCard({
  status,
  ssoConfigured,
  onLaunch,
}: {
  status: CrossHubDashboard['logistix'];
  ssoConfigured: boolean;
  onLaunch: () => void;
}) {
  return (
    <HubCardShell title="Logistix" status={status.status} ssoConfigured={ssoConfigured} onLaunch={onLaunch}>
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

function SsoSettingsPanel({
  password,
  ssoStatus,
  onUpdated,
}: {
  password: string;
  ssoStatus: SsoCredentialsStatus;
  onUpdated: (status: SsoCredentialsStatus) => void;
}) {
  const [complianceEngineEmail, setComplianceEngineEmail] = useState('');
  const [complianceEnginePassword, setComplianceEnginePassword] = useState('');
  const [fleetHubEmail, setFleetHubEmail] = useState('');
  const [fleetHubPassword, setFleetHubPassword] = useState('');
  const [logistixEmail, setLogistixEmail] = useState('');
  const [logistixPassword, setLogistixPassword] = useState('');
  const [saving, setSaving] = useState(false);
  const [notice, setNotice] = useState('');
  const [error, setError] = useState('');

  const save = async () => {
    setSaving(true);
    setError('');
    setNotice('');
    try {
      const status = await praxioAPI.updateSsoCredentials(password, {
        complianceEngineEmail: complianceEngineEmail || undefined,
        complianceEnginePassword: complianceEnginePassword || undefined,
        fleetHubEmail: fleetHubEmail || undefined,
        fleetHubPassword: fleetHubPassword || undefined,
        logistixEmail: logistixEmail || undefined,
        logistixPassword: logistixPassword || undefined,
      });
      onUpdated(status);
      setComplianceEngineEmail('');
      setComplianceEnginePassword('');
      setFleetHubEmail('');
      setFleetHubPassword('');
      setLogistixEmail('');
      setLogistixPassword('');
      setNotice('Identifiants SSO enregistrés');
    } catch (err) {
      setError(err instanceof Error ? err.message : "Échec de l'enregistrement");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="bg-surface border border-line overflow-hidden">
      <div className="px-6 py-4 border-b border-line">
        <h2 className="text-lg font-semibold">Paramètres — connexion automatique (SSO)</h2>
        <p className="text-sm text-ink-soft mt-1">
          Les identifiants d'un compte de chaque module, utilisés uniquement pour vous connecter
          automatiquement en un clic (« ouvrir ») depuis ce tableau de bord -- jamais affichés ni
          renvoyés après enregistrement.
        </p>
      </div>
      <div className="px-6 py-4 space-y-5">
        {error && <p className="text-sm text-danger">{error}</p>}
        {notice && <p className="text-sm text-success">{notice}</p>}

        <SsoCredentialField
          label="Compliance Engine"
          configured={ssoStatus.complianceEngineConfigured}
          configuredEmail={ssoStatus.complianceEngineEmail}
          email={complianceEngineEmail}
          onEmailChange={setComplianceEngineEmail}
          password={complianceEnginePassword}
          onPasswordChange={setComplianceEnginePassword}
        />
        <SsoCredentialField
          label="Fleet Hub"
          configured={ssoStatus.fleetHubConfigured}
          configuredEmail={ssoStatus.fleetHubEmail}
          email={fleetHubEmail}
          onEmailChange={setFleetHubEmail}
          password={fleetHubPassword}
          onPasswordChange={setFleetHubPassword}
        />
        <SsoCredentialField
          label="Logistix"
          configured={ssoStatus.logistixConfigured}
          configuredEmail={ssoStatus.logistixEmail}
          email={logistixEmail}
          onEmailChange={setLogistixEmail}
          password={logistixPassword}
          onPasswordChange={setLogistixPassword}
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

function SsoCredentialField({
  label,
  configured,
  configuredEmail,
  email,
  onEmailChange,
  password,
  onPasswordChange,
}: {
  label: string;
  configured: boolean;
  configuredEmail: string | null;
  email: string;
  onEmailChange: (value: string) => void;
  password: string;
  onPasswordChange: (value: string) => void;
}) {
  return (
    <div className="space-y-1.5">
      <div className="flex items-center justify-between text-sm text-ink-soft">
        <span>{label}</span>
        <span className={`tag ${configured ? 'tag-live' : 'tag-soon'}`}>
          <span className="tag-dot" />
          {configured && configuredEmail ? configuredEmail : 'Non configuré'}
        </span>
      </div>
      <div className="flex flex-col sm:flex-row gap-2">
        <input
          type="email"
          value={email}
          onChange={(e) => onEmailChange(e.target.value)}
          placeholder={configured ? "Remplacer l'email…" : 'Email du compte…'}
          className="flex-1 px-3 py-2 border border-line bg-bg text-sm focus:ring-2 focus:ring-accent focus:border-transparent"
        />
        <input
          type="password"
          value={password}
          onChange={(e) => onPasswordChange(e.target.value)}
          placeholder={configured ? 'Remplacer le mot de passe…' : 'Mot de passe…'}
          className="flex-1 px-3 py-2 border border-line bg-bg text-sm font-mono focus:ring-2 focus:ring-accent focus:border-transparent"
        />
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
