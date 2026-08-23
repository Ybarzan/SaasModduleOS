import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, Loader2, Truck } from 'lucide-react';
import { mobileApi } from '../lib/api';

interface RateComparisonResult {
  carrierName: string;
  estimatedCost: number;
  transitDaysAvg: number;
}

const MODES = [
  { value: 'SEA', label: 'Maritime' },
  { value: 'AIR', label: 'Aérien' },
  { value: 'ROAD', label: 'Routier' },
];

const RateComparison = () => {
  const navigate = useNavigate();
  const [origin, setOrigin] = useState('');
  const [destination, setDestination] = useState('');
  const [mode, setMode] = useState('SEA');
  const [weight, setWeight] = useState('');

  const compare = useMutation({
    mutationFn: async () =>
      (await mobileApi.shippingRates.compare(origin.trim(), destination.trim(), mode, weight ? Number(weight) : undefined))
        .data as RateComparisonResult[],
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!origin.trim() || !destination.trim()) return;
    compare.mutate();
  };

  const results = [...(compare.data || [])].sort((a, b) => a.estimatedCost - b.estimatedCost);

  return (
    <>
      <div className="header-bar row">
        <button onClick={() => navigate(-1)} style={{ background: 'none', border: 'none', cursor: 'pointer' }}>
          <ArrowLeft size={20} />
        </button>
        <h1 className="title" style={{ margin: 0, fontSize: 18 }}>Comparateur tarifs</h1>
      </div>

      <form onSubmit={handleSubmit} className="stack" style={{ marginTop: 12 }}>
        <div className="row" style={{ gap: 8 }}>
          <input className="input" placeholder="Origine (pays)" value={origin} onChange={(e) => setOrigin(e.target.value)} />
          <input className="input" placeholder="Destination" value={destination} onChange={(e) => setDestination(e.target.value)} />
        </div>
        <select className="input" value={mode} onChange={(e) => setMode(e.target.value)}>
          {MODES.map((m) => (
            <option key={m.value} value={m.value}>{m.label}</option>
          ))}
        </select>
        <input className="input" type="number" min="0" placeholder="Poids (kg, optionnel)" value={weight} onChange={(e) => setWeight(e.target.value)} />
        <button type="submit" className="btn btn-primary btn-block" disabled={compare.isPending || !origin.trim() || !destination.trim()}>
          {compare.isPending ? <Loader2 size={16} className="spin" /> : 'Comparer'}
        </button>
        {compare.isError && <p className="error-text">Impossible de comparer les tarifs.</p>}
      </form>

      {compare.isSuccess && (
        <div className="stack" style={{ marginTop: 16 }}>
          {results.length === 0 && <div className="empty-state">Aucun tarif trouvé pour ce trajet.</div>}
          {results.map((r, i) => (
            <div key={`${r.carrierName}-${i}`} className="card row-between">
              <div className="row" style={{ gap: 8 }}>
                <Truck size={16} color="rgb(var(--c-accent))" />
                <div>
                  <p style={{ fontWeight: 700, margin: 0 }}>{r.carrierName}</p>
                  <p className="text-sm text-soft" style={{ margin: 0 }}>{r.transitDaysAvg} j de transit</p>
                </div>
              </div>
              <span style={{ fontWeight: 800 }}>{r.estimatedCost.toFixed(0)} €</span>
            </div>
          ))}
        </div>
      )}
    </>
  );
};

export default RateComparison;
