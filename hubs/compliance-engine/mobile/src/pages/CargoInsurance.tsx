import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, Loader2, Umbrella, FileCheck2 } from 'lucide-react';
import { mobileApi } from '../lib/api';

interface InsuranceQuote {
  id: string;
  goodsValue: number;
  premiumAmount: number;
  policyNumber?: string | null;
}

const CargoInsurance = () => {
  const navigate = useNavigate();

  const { data = [], isLoading, isError } = useQuery({
    queryKey: ['mobile-insurance-quotes'],
    queryFn: async () => (await mobileApi.insurance.listQuotes()).data as InsuranceQuote[],
  });

  return (
    <>
      <div className="header-bar row">
        <button onClick={() => navigate(-1)} style={{ background: 'none', border: 'none', cursor: 'pointer' }}>
          <ArrowLeft size={20} />
        </button>
        <h1 className="title" style={{ margin: 0, fontSize: 18 }}>Assurance cargo</h1>
      </div>

      <div className="stack" style={{ marginTop: 12 }}>
        {isLoading && (
          <div className="center-screen" style={{ minHeight: 200 }}>
            <Loader2 className="spin" color="rgb(var(--c-ink-soft))" />
          </div>
        )}
        {isError && <p className="error-text">Impossible de charger les polices d'assurance.</p>}
        {!isLoading && data.length === 0 && <div className="empty-state">Aucune police ou devis d'assurance.</div>}

        {data.map((q) => (
          <div key={q.id} className="card row-between">
            <div className="row" style={{ gap: 8 }}>
              <Umbrella size={16} color="rgb(var(--c-accent))" />
              <div>
                <p style={{ fontWeight: 700, margin: 0 }}>{q.goodsValue.toFixed(0)} € couverts</p>
                <p className="text-sm text-soft" style={{ margin: 0 }}>Prime {q.premiumAmount.toFixed(2)} €</p>
              </div>
            </div>
            {q.policyNumber && (
              <span className="badge badge-accent row" style={{ gap: 4 }}>
                <FileCheck2 size={12} />
                {q.policyNumber}
              </span>
            )}
          </div>
        ))}
      </div>
    </>
  );
};

export default CargoInsurance;
