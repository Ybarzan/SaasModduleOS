import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import App from '../App';

describe('App', () => {
  it('affiche le wordmark et tous les modules du registre', () => {
    render(<App />);

    expect(screen.getByText('Praxio')).toBeInTheDocument();
    expect(screen.getByText('Compliance Engine')).toBeInTheDocument();
    expect(screen.getByText('Logistix')).toBeInTheDocument();
    expect(screen.getByText('Fleet Hub')).toBeInTheDocument();
    expect(screen.getByText('Fleet Market')).toBeInTheDocument();
  });

  it('affiche un lien "Ouvrir" par module', () => {
    render(<App />);

    expect(screen.getAllByText('Ouvrir')).toHaveLength(4);
  });
});
