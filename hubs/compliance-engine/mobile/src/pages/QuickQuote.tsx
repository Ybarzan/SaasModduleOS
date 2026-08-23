import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { Loader2 } from 'lucide-react';
import { mobileApi } from '../lib/api';
import type { QuickQuoteResult } from '../types';

const INCOTERMS = ['EXW', 'FCA', 'FOB', 'CFR', 'CIF', 'CPT', 'CIP', 'DAP', 'DPU', 'DDP'];

const BREAKDOWN_LABELS: Record<string, string> = {
  base_freight: 'Fret de base',
  fuel_surcharge: 'Surcharge carburant',
  security_surcharge: 'Surcharge sécurité',
  handling_fee: 'Frais de manutention',
  documentation_fee: 'Frais documentaires',
};

const QuickQuote = () => {
  const [origin, setOrigin] = useState('');
  const [destination, setDestination] = useState('');
  const [weight, setWeight] = useState('');
  const [incoterm, setIncoterm] = useState('FOB');
  const [validationError, setValidationError] = useState<string | null>(null);

  const quoteMutation = useMutation({
    mutationFn: () =>
      mobileApi.quickQuote({ origin, destination, weight: Number(weight), incoterm }),
  });

  const result = quoteMutation.data?.data as QuickQuoteResult | undefined;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    // Ces champs n'ont pas d'attribut HTML `required` : sans ce message, un
    // clic sur "Calculer" avec un champ manquant ne faisait strictement rien
    // (early-return silencieux) -- pire qu'un blocage natif, qui affiche au
    // moins une bulle d'aide.
    if (!origin.trim() || !destination.trim()) {
      setValidationError("Renseignez l'origine et la destination.");
      return;
    }
    if (!weight || Number(weight) <= 0) {
      setValidationError('Indiquez un poids supérieur à 0.');
      return;
    }
    setValidationError(null);
    quoteMutation.mutate();
  };

  return (
    <>
      <div className="header-bar">
        <h1 className="title" style={{ margin: 0 }}>Devis rapide</h1>
        <p className="subtitle" style={{ marginBottom: 0 }}>Estimation instantanée, hors tarifs transporteurs négociés</p>
      </div>

      <form onSubmit={handleSubmit} className="stack" style={{ marginTop: 16 }}>
        <input className="input" placeholder="Origine (ville ou pays)" value={origin} onChange={(e) => { setOrigin(e.target.value); setValidationError(null); }} />
        <input className="input" placeholder="Destination" value={destination} onChange={(e) => { setDestination(e.target.value); setValidationError(null); }} />
        <input
          className="input"
          type="number"
          min="0"
          step="0.1"
          placeholder="Poids (kg)"
          value={weight}
          onChange={(e) => { setWeight(e.target.value); setValidationError(null); }}
        />
        <select className="input" value={incoterm} onChange={(e) => setIncoterm(e.target.value)}>
          {INCOTERMS.map((i) => (
            <option key={i} value={i}>{i}</option>
          ))}
        </select>
        <button type="submit" className="btn btn-primary btn-block" disabled={quoteMutation.isPending}>
          {quoteMutation.isPending ? <Loader2 size={16} className="spin" /> : 'Calculer'}
        </button>
        {validationError && <p className="error-text">{validationError}</p>}
        {quoteMutation.isError && <p className="error-text">Impossible de calculer le devis.</p>}
      </form>

      {result && (
        <div className="card" style={{ marginTop: 16 }}>
          <div className="row-between" style={{ marginBottom: 12 }}>
            <span className="text-sm text-soft">{result.origin} → {result.destination}</span>
            <span className="badge badge-accent">{result.incoterm}</span>
          </div>
          <div className="stack text-sm">
            {Object.entries(result.breakdown).map(([key, value]) => (
              <div key={key} className="row-between">
                <span className="text-soft">{BREAKDOWN_LABELS[key] || key}</span>
                <span>{value.toFixed(2)} {result.currency}</span>
              </div>
            ))}
          </div>
          <div className="row-between" style={{ marginTop: 14, paddingTop: 14, borderTop: '1px solid rgb(var(--c-line))' }}>
            <span style={{ fontWeight: 700 }}>Total estimé</span>
            <span style={{ fontWeight: 800, fontSize: 18 }}>{result.estimated_total.toFixed(2)} {result.currency}</span>
          </div>
        </div>
      )}
    </>
  );
};

export default QuickQuote;
