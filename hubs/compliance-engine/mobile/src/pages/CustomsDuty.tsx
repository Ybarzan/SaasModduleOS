import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, Loader2 } from 'lucide-react';
import { mobileApi } from '../lib/api';

interface DutyResult {
  dutyRate: number;
  dutyAmount: number;
}

const CustomsDuty = () => {
  const navigate = useNavigate();
  const [hsCode, setHsCode] = useState('');
  const [origin, setOrigin] = useState('');
  const [dest, setDest] = useState('');
  const [goodsValue, setGoodsValue] = useState('');
  const [validationError, setValidationError] = useState<string | null>(null);

  const calc = useMutation({
    mutationFn: async () =>
      (await mobileApi.customs.getDuty(hsCode.trim(), origin.trim(), dest.trim(), Number(goodsValue))).data as DutyResult,
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!hsCode.trim() || !origin.trim() || !dest.trim()) {
      setValidationError('Renseignez le code SH, le pays d’origine et de destination.');
      return;
    }
    if (!goodsValue || Number(goodsValue) <= 0) {
      setValidationError('Indiquez une valeur des marchandises supérieure à 0.');
      return;
    }
    setValidationError(null);
    calc.mutate();
  };

  return (
    <>
      <div className="header-bar row">
        <button onClick={() => navigate(-1)} style={{ background: 'none', border: 'none', cursor: 'pointer' }}>
          <ArrowLeft size={20} />
        </button>
        <h1 className="title" style={{ margin: 0, fontSize: 18 }}>Droits de douane</h1>
      </div>

      <form onSubmit={handleSubmit} className="stack" style={{ marginTop: 12 }}>
        <input className="input" placeholder="Code SH (ex: 8471.30)" value={hsCode} onChange={(e) => { setHsCode(e.target.value); setValidationError(null); }} />
        <div className="row" style={{ gap: 8 }}>
          <input className="input" placeholder="Origine (code pays)" value={origin} onChange={(e) => { setOrigin(e.target.value); setValidationError(null); }} />
          <input className="input" placeholder="Destination" value={dest} onChange={(e) => { setDest(e.target.value); setValidationError(null); }} />
        </div>
        <input
          className="input"
          type="number"
          min="0"
          placeholder="Valeur des marchandises (€)"
          value={goodsValue}
          onChange={(e) => { setGoodsValue(e.target.value); setValidationError(null); }}
        />
        <button type="submit" className="btn btn-primary btn-block" disabled={calc.isPending}>
          {calc.isPending ? <Loader2 size={16} className="spin" /> : 'Calculer'}
        </button>
        {validationError && <p className="error-text">{validationError}</p>}
        {calc.isError && <p className="error-text">Impossible de calculer les droits de douane.</p>}
      </form>

      {calc.data && (
        <div className="card stack" style={{ marginTop: 16 }}>
          <div className="kv-row">
            <span className="text-soft">Taux de droit</span>
            <span style={{ fontWeight: 700 }}>{calc.data.dutyRate > 0 ? calc.data.dutyRate.toFixed(1) : '0'}%</span>
          </div>
          <div className="kv-row">
            <span className="text-soft">Montant estimé</span>
            <span style={{ fontWeight: 800, fontSize: 18 }}>{calc.data.dutyAmount.toFixed(2)} €</span>
          </div>
        </div>
      )}
    </>
  );
};

export default CustomsDuty;
