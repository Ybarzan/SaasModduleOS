import { render, screen, fireEvent, waitFor } from '@testing-library/react';
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

describe('Dashboard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
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

    expect(await screen.findByText('Compliance Engine')).toBeInTheDocument();
    expect(screen.getByText('5')).toBeInTheDocument();
    expect(screen.getAllByText('Non configuré')).toHaveLength(1);
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
    fireEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));

    await waitFor(() => {
      expect(praxioAPI.updateKeys).toHaveBeenCalledWith('secret', {
        complianceEngineApiKey: undefined,
        fleetHubApiKey: 'fh-key-123',
        logistixApiKey: undefined,
      });
    });
    expect(await screen.findByText('Clés enregistrées')).toBeInTheDocument();
  });
});
