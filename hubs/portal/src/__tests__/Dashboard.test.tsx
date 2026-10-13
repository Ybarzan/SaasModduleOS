import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import Dashboard from '../pages/Dashboard';
import { praxioAPI, UnauthorizedError } from '../lib/praxioApi';

vi.mock('../lib/praxioApi', async () => {
  const actual = await vi.importActual<typeof import('../lib/praxioApi')>('../lib/praxioApi');
  return {
    ...actual,
    praxioAPI: {
      getDashboard: vi.fn(),
      getKeysStatus: vi.fn(),
      updateKeys: vi.fn(),
      getSsoStatus: vi.fn(),
      updateSsoCredentials: vi.fn(),
      launchSso: vi.fn(),
    },
  };
});

function renderDashboard() {
  return render(
    <MemoryRouter>
      <Dashboard />
    </MemoryRouter>
  );
}

const notConfiguredDashboard = {
  complianceEngine: { status: 'NOT_CONFIGURED' as const, data: null },
  fleetHub: { status: 'NOT_CONFIGURED' as const, data: null },
  logistix: { status: 'NOT_CONFIGURED' as const, data: null },
};

const allUnconfiguredKeys = {
  complianceEngineConfigured: false,
  fleetHubConfigured: false,
  logistixConfigured: false,
};

const allUnconfiguredSso = {
  complianceEngineConfigured: false,
  complianceEngineEmail: null,
  fleetHubConfigured: false,
  fleetHubEmail: null,
  logistixConfigured: false,
  logistixEmail: null,
};

describe('Dashboard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
    vi.mocked(praxioAPI.getSsoStatus).mockResolvedValue(allUnconfiguredSso);
  });

  it('shows a login gate when no password is stored', () => {
    renderDashboard();

    expect(screen.getByPlaceholderText('Mot de passe administrateur')).toBeInTheDocument();
  });

  it('logs in and shows hub cards with their status', async () => {
    vi.mocked(praxioAPI.getDashboard).mockResolvedValue({
      complianceEngine: {
        status: 'OK',
        data: { simulationsThisMonth: 5, openDeclarations: 2, overdueInvoicesCount: 1, overdueInvoicesAmount: 100 },
      },
      fleetHub: { status: 'NOT_CONFIGURED', data: null },
      logistix: { status: 'UNREACHABLE', data: null },
    });
    vi.mocked(praxioAPI.getKeysStatus).mockResolvedValue(allUnconfiguredKeys);
    renderDashboard();

    fireEvent.change(screen.getByPlaceholderText('Mot de passe administrateur'), {
      target: { value: 'secret' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Entrer' }));

    expect(await screen.findByRole('heading', { name: 'Compliance Engine' })).toBeInTheDocument();
    expect(screen.getByText('5')).toBeInTheDocument();
    const fleetHubHeading = screen.getByRole('heading', { name: 'Fleet Hub' });
    expect(within(fleetHubHeading.parentElement!).getByText('Non configuré')).toBeInTheDocument();
    expect(screen.getByText('Inaccessible')).toBeInTheDocument();
  });

  it('shows an error and stays on the login gate when the password is wrong', async () => {
    vi.mocked(praxioAPI.getDashboard).mockRejectedValue(new UnauthorizedError());
    vi.mocked(praxioAPI.getKeysStatus).mockResolvedValue(allUnconfiguredKeys);
    renderDashboard();

    fireEvent.change(screen.getByPlaceholderText('Mot de passe administrateur'), {
      target: { value: 'wrong' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Entrer' }));

    expect(await screen.findByText('Mot de passe administrateur incorrect')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('Mot de passe administrateur')).toBeInTheDocument();
  });

  it('saves a key from the settings panel', async () => {
    vi.mocked(praxioAPI.getDashboard).mockResolvedValue(notConfiguredDashboard);
    vi.mocked(praxioAPI.getKeysStatus).mockResolvedValue(allUnconfiguredKeys);
    vi.mocked(praxioAPI.updateKeys).mockResolvedValue({
      ...allUnconfiguredKeys,
      fleetHubConfigured: true,
    });
    renderDashboard();

    fireEvent.change(screen.getByPlaceholderText('Mot de passe administrateur'), {
      target: { value: 'secret' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Entrer' }));

    await screen.findByText('Fleet Hub (X-Internal-Integration-Key)');
    const [, fleetHubInput] = screen.getAllByPlaceholderText('Coller la clé…');
    fireEvent.change(fleetHubInput, { target: { value: 'fh-key-123' } });
    const [apiKeysSaveButton] = screen.getAllByRole('button', { name: 'Enregistrer' });
    fireEvent.click(apiKeysSaveButton);

    await waitFor(() => {
      expect(praxioAPI.updateKeys).toHaveBeenCalledWith('secret', {
        complianceEngineApiKey: undefined,
        fleetHubApiKey: 'fh-key-123',
        logistixApiKey: undefined,
      });
    });
    expect(await screen.findByText('Clés enregistrées')).toBeInTheDocument();
  });

  it('saves SSO credentials from the SSO settings panel', async () => {
    vi.mocked(praxioAPI.getDashboard).mockResolvedValue(notConfiguredDashboard);
    vi.mocked(praxioAPI.getKeysStatus).mockResolvedValue(allUnconfiguredKeys);
    vi.mocked(praxioAPI.updateSsoCredentials).mockResolvedValue({
      ...allUnconfiguredSso,
      fleetHubConfigured: true,
      fleetHubEmail: 'ops@acme.fr',
    });
    renderDashboard();

    fireEvent.change(screen.getByPlaceholderText('Mot de passe administrateur'), {
      target: { value: 'secret' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Entrer' }));

    await screen.findByText('Paramètres — connexion automatique (SSO)');
    const [, fleetHubEmailInput] = screen.getAllByPlaceholderText('Email du compte…');
    const [, fleetHubPasswordInput] = screen.getAllByPlaceholderText('Mot de passe…');
    fireEvent.change(fleetHubEmailInput, { target: { value: 'ops@acme.fr' } });
    fireEvent.change(fleetHubPasswordInput, { target: { value: 'secret-pw' } });
    const [, ssoSaveButton] = screen.getAllByRole('button', { name: 'Enregistrer' });
    fireEvent.click(ssoSaveButton);

    await waitFor(() => {
      expect(praxioAPI.updateSsoCredentials).toHaveBeenCalledWith('secret', {
        complianceEngineEmail: undefined,
        complianceEnginePassword: undefined,
        fleetHubEmail: 'ops@acme.fr',
        fleetHubPassword: 'secret-pw',
        logistixEmail: undefined,
        logistixPassword: undefined,
      });
    });
    expect(await screen.findByText('Identifiants SSO enregistrés')).toBeInTheDocument();
    expect(screen.getByText('ops@acme.fr')).toBeInTheDocument();
  });

  it('shows an "ouvrir" button only for hubs with SSO configured, and redirects on click', async () => {
    const originalLocation = window.location;
    Object.defineProperty(window, 'location', {
      writable: true,
      value: { ...originalLocation, href: '' },
    });

    vi.mocked(praxioAPI.getDashboard).mockResolvedValue(notConfiguredDashboard);
    vi.mocked(praxioAPI.getKeysStatus).mockResolvedValue(allUnconfiguredKeys);
    vi.mocked(praxioAPI.getSsoStatus).mockResolvedValue({
      ...allUnconfiguredSso,
      fleetHubConfigured: true,
      fleetHubEmail: 'ops@acme.fr',
    });
    vi.mocked(praxioAPI.launchSso).mockResolvedValue({
      redirectUrl: 'http://fh-frontend.test/sso-entry#payload',
    });
    renderDashboard();

    fireEvent.change(screen.getByPlaceholderText('Mot de passe administrateur'), {
      target: { value: 'secret' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Entrer' }));

    await screen.findByRole('heading', { name: 'Fleet Hub' });
    const launchButtons = await screen.findAllByRole('button', { name: '> ouvrir' });
    expect(launchButtons).toHaveLength(1);
    fireEvent.click(launchButtons[0]);

    await waitFor(() => {
      expect(praxioAPI.launchSso).toHaveBeenCalledWith('secret', 'fleetHub');
    });
    await waitFor(() => {
      expect(window.location.href).toBe('http://fh-frontend.test/sso-entry#payload');
    });

    Object.defineProperty(window, 'location', { writable: true, value: originalLocation });
  });
});
