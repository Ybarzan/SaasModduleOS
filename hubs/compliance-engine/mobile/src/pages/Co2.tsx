import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, Leaf } from 'lucide-react';

// Mêmes facteurs que le calculateur web (frontend/src/pages/Co2Calculator.tsx) --
// calcul 100% local, pas d'appel réseau, cohérent avec un usage terrain hors ligne.
const EMISSION_FACTORS: Record<string, { label: string; factor: number }> = {
  SEA: { label: 'Maritime', factor: 0.016 },
  AIR: { label: 'Aérien', factor: 0.255 },
  ROAD: { label: 'Routier', factor: 0.062 },
};

const Co2 = () => {
  const navigate = useNavigate();
  const [weight, setWeight] = useState('1000');
  const [distance, setDistance] = useState('10000');

  const w = Number(weight) || 0;
  const d = Number(distance) || 0;

  return (
    <>
      <div className="header-bar row">
        <button onClick={() => navigate(-1)} style={{ background: 'none', border: 'none', cursor: 'pointer' }}>
          <ArrowLeft size={20} />
        </button>
        <h1 className="title" style={{ margin: 0, fontSize: 18 }}>Émissions CO₂</h1>
      </div>

      <div className="stack" style={{ marginTop: 12 }}>
        <div className="card stack">
          <div>
            <p className="section-label">Poids (kg)</p>
            <input className="input" type="number" min="0" value={weight} onChange={(e) => setWeight(e.target.value)} />
          </div>
          <div>
            <p className="section-label">Distance (km)</p>
            <input className="input" type="number" min="0" value={distance} onChange={(e) => setDistance(e.target.value)} />
          </div>
        </div>

        <div className="stack">
          {Object.entries(EMISSION_FACTORS).map(([key, { label, factor }]) => {
            const co2Kg = Math.round((w * d * factor) / 1000 * 100) / 100;
            return (
              <div key={key} className="card row-between">
                <span className="row" style={{ gap: 8 }}>
                  <Leaf size={16} color="rgb(var(--c-accent))" />
                  {label}
                </span>
                <span style={{ fontWeight: 800 }}>{co2Kg.toLocaleString('fr-FR')} kg CO₂</span>
              </div>
            );
          })}
        </div>
      </div>
    </>
  );
};

export default Co2;
