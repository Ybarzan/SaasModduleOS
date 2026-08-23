import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';

// Même formule que le web (frontend/src/pages/VolumetricWeight.tsx) : poids
// volumétrique (kg) = volume (cm³) / diviseur selon le mode de transport.
const DIVISORS: Record<string, { divisor: number; label: string }> = {
  air: { divisor: 6000, label: 'Aérien (÷ 6000)' },
  sea: { divisor: 1000, label: 'Maritime (÷ 1000)' },
  road: { divisor: 3000, label: 'Routier (÷ 3000)' },
};

const VolumetricWeight = () => {
  const navigate = useNavigate();
  const [length, setLength] = useState('50');
  const [width, setWidth] = useState('40');
  const [height, setHeight] = useState('30');
  const [actualWeight, setActualWeight] = useState('10');
  const [mode, setMode] = useState('air');

  const volumeCm3 = (Number(length) || 0) * (Number(width) || 0) * (Number(height) || 0);
  const divisor = DIVISORS[mode]?.divisor ?? 6000;
  const volumetricWeight = volumeCm3 / divisor;
  const chargeableWeight = Math.max(Number(actualWeight) || 0, volumetricWeight);

  return (
    <>
      <div className="header-bar row">
        <button onClick={() => navigate(-1)} style={{ background: 'none', border: 'none', cursor: 'pointer' }}>
          <ArrowLeft size={20} />
        </button>
        <h1 className="title" style={{ margin: 0, fontSize: 18 }}>Poids volumétrique</h1>
      </div>

      <div className="stack" style={{ marginTop: 12 }}>
        <div className="card stack">
          <div className="row" style={{ gap: 8 }}>
            <input className="input" type="number" min="0" placeholder="L (cm)" value={length} onChange={(e) => setLength(e.target.value)} />
            <input className="input" type="number" min="0" placeholder="l (cm)" value={width} onChange={(e) => setWidth(e.target.value)} />
            <input className="input" type="number" min="0" placeholder="H (cm)" value={height} onChange={(e) => setHeight(e.target.value)} />
          </div>
          <div>
            <p className="section-label">Poids réel (kg)</p>
            <input className="input" type="number" min="0" value={actualWeight} onChange={(e) => setActualWeight(e.target.value)} />
          </div>
          <div>
            <p className="section-label">Mode de transport</p>
            <select className="input" value={mode} onChange={(e) => setMode(e.target.value)}>
              {Object.entries(DIVISORS).map(([key, { label }]) => (
                <option key={key} value={key}>{label}</option>
              ))}
            </select>
          </div>
        </div>

        <div className="card stack">
          <div className="kv-row">
            <span className="text-soft">Poids volumétrique</span>
            <span style={{ fontWeight: 700 }}>{volumetricWeight.toFixed(2)} kg</span>
          </div>
          <div className="kv-row">
            <span className="text-soft">Poids taxable (le plus élevé)</span>
            <span style={{ fontWeight: 800, fontSize: 18 }}>{chargeableWeight.toFixed(2)} kg</span>
          </div>
        </div>
      </div>
    </>
  );
};

export default VolumetricWeight;
