import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  praxioAPI,
  UnauthorizedError,
  type CrossHubBilling,
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
  const [billing, setBilling] = useState<CrossHubBilling | null>(null);
  const [keysStatus, setKeysStatus] = useState<DashboardKeysStatus | null>(null);
  const [ssoStatus, setSsoStatus] = useState<SsoCredentialsStatus | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [launchError, setLaunchError] = useState('');

  const load = async (pwd: string) => {
    setLoading(true);
    setError('');
    try {
      const [dash, bill, keys, sso] = await Promise.all([
        praxioAPI.getDashboard(pwd),
        praxioAPI.getBilling(pwd),
        praxioAPI.getKeysStatus(pwd),
        praxioAPI.getSsoStatus(pwd),
      ]);
      setDashboard(dash);
      setBilling(bill);
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
    setBilling(null);
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

        {billing && ssoStatus && <BillingSection billing={billing} ssoStatus={ssoStatus} onLaunch={launch} />}

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
    status === 'OK'
      ? 'Connecté'
      : status === 'NOT_CONFIGURED'
        ? 'Non configuré'
        : status === 'NOT_APPLICABLE'
          ? 'Non applicable'
          : 'Inaccessible';
  const cls =
    status === 'OK'
      ? 'tag-live'
      : status === 'NOT_CONFIGURED' || status === 'NOT_APPLICABLE'
        ? 'tag-soon'
        : 'tag-sim';
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

function formatAmount(amount: number, currency: string): string {
  return new Intl.NumberFormat('fr-FR', { style: 'currency', currency }).format(amount);
}

function formatDate(value: string | null): string | null {
  return value ? new Date(value).toLocaleDateString('fr-FR') : null;
}

function monthlyEquivalent(amount: number, billingCycle: string): number {
  return billingCycle === 'ANNUAL' ? amount / 12 : amount;
}

interface BillingRowData {
  hub: HubId;
  title: string;
  status: HubStatus<unknown>['status'];
  plan: string | null;
  planStatus: string | null;
  amount: number | null;
  currency: string | null;
  cycleLabel: string | null;
  nextBillingDate: string | null;
  ssoConfigured: boolean;
}

function BillingSection({
  billing,
  ssoStatus,
  onLaunch,
}: {
  billing: CrossHubBilling;
  ssoStatus: SsoCredentialsStatus;
  onLaunch: (hub: HubId) => void;
}) {
  const ce = billing.complianceEngine.data;
  const fh = billing.fleetHub.data;

  const rows: BillingRowData[] = [
    {
      hub: 'complianceEngine',
      title: 'Compliance Engine',
      status: billing.complianceEngine.status,
      plan: ce?.plan ?? null,
      planStatus: ce?.status ?? null,
      amount: ce?.amount ?? null,
      currency: ce?.currency ?? null,
      cycleLabel: ce ? (ce.billingCycle === 'ANNUAL' ? '/an' : '/mois') : null,
      nextBillingDate: ce?.nextBillingDate ?? null,
      ssoConfigured: ssoStatus.complianceEngineConfigured,
    },
    {
      hub: 'fleetHub',
      title: 'Fleet Hub',
      status: billing.fleetHub.status,
      plan: fh?.plan ?? null,
      planStatus: fh?.status ?? null,
      amount: fh?.amount ?? null,
      currency: fh?.currency ?? null,
      cycleLabel: fh?.amount != null ? '/mois' : null,
      nextBillingDate: fh?.nextBillingDate ?? null,
      ssoConfigured: ssoStatus.fleetHubConfigured,
    },
    {
      hub: 'logistix',
      title: 'Logistix',
      status: billing.logistix.status,
      plan: null,
      planStatus: null,
      amount: null,
      currency: null,
      cycleLabel: null,
      nextBillingDate: null,
      ssoConfigured: ssoStatus.logistixConfigured,
    },
  ];

  const total = (ce ? monthlyEquivalent(ce.amount, ce.billingCycle) : 0) + (fh?.amount ?? 0);

  return (
    <div className="bg-surface border border-line overflow-hidden">
      <div className="px-6 py-4 border-b border-line flex items-start justify-between gap-6 flex-wrap">
        <div>
          <h2 className="text-lg font-semibold">Facturation groupée</h2>
          <p className="text-sm text-ink-soft mt-1 max-w-md">
            Vue de lecture seule : chaque hub facture et encaisse de façon indépendante. Utilisez
            « gérer » pour payer ou changer de plan directement sur le hub concerné.
          </p>
        </div>
        <div className="text-right">
          <div className="text-xs text-ink-soft uppercase tracking-wide">Estimé / mois</div>
          <div className="text-2xl font-bold">{formatAmount(total, 'EUR')}</div>
        </div>
      </div>
      <div className="divide-y divide-line">
        {rows.map((row) => (
          <BillingRow key={row.hub} row={row} onLaunch={() => onLaunch(row.hub)} />
        ))}
      </div>
    </div>
  );
}

function BillingRow({ row, onLaunch }: { row: BillingRowData; onLaunch: () => void }) {
  const nextBillingDate = formatDate(row.nextBillingDate);
  return (
    <div className="px-6 py-4 flex items-center justify-between gap-4 flex-wrap">
      <div>
        <div className="flex items-center gap-2">
          <span className="font-semibold">{row.title}</span>
          <StatusBadge status={row.status} />
        </div>
        {row.status === 'OK' && row.plan && (
          <p className="text-sm text-ink-soft mt-1">
            Plan {row.plan.toLowerCase()}
            {row.planStatus && ` — ${row.planStatus.toLowerCase()}`}
            {nextBillingDate && ` — prochaine échéance le ${nextBillingDate}`}
          </p>
        )}
        {row.status === 'NOT_APPLICABLE' && (
          <p className="text-sm text-ink-soft mt-1">Pas de facturation propre à ce module.</p>
        )}
        {row.status === 'NOT_CONFIGURED' && (
          <p className="text-sm text-ink-soft mt-1">Aucune clé enregistrée pour ce hub.</p>
        )}
        {row.status === 'UNREACHABLE' && (
          <p className="text-sm text-ink-soft mt-1">Le hub n'a pas répondu.</p>
        )}
      </div>
      <div className="flex items-center gap-4">
        {row.amount != null && row.currency && (
          <span className="text-lg font-semibold">
            {formatAmount(row.amount, row.currency)}
            <span className="text-sm text-ink-soft font-normal">{row.cycleLabel}</span>
          </span>
        )}
        {row.ssoConfigured && row.status !== 'NOT_APPLICABLE' && (
          <button type="button" onClick={onLaunch} className="btn-secondary">
            &gt; gérer
          </button>
        )}
      </div>
    </div>
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
