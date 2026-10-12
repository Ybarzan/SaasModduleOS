import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import App from '../App';

describe('App', () => {
  it('affiche le wordmark et tous les modules du registre', () => {
    render(<App />);

    expect(screen.getByText('io')).toBeInTheDocument();
    expect(screen.getByText('Compliance Engine')).toBeInTheDocument();
    expect(screen.getByText('Logistix')).toBeInTheDocument();
    expect(screen.getByText('Fleet Hub')).toBeInTheDocument();
    expect(screen.getByText('Fleet Market')).toBeInTheDocument();
  });

  it('affiche un lien "ouvrir" par module', () => {
    render(<App />);

    expect(screen.getAllByText('> ouvrir')).toHaveLength(4);
  });
});
