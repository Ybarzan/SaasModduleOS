import { useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, Loader2, CheckCircle2, XCircle } from 'lucide-react';
import { mobileApi } from '../lib/api';
import { useAuthStore } from '../stores/auth';

const MANAGER_ROLES = ['OWNER', 'ADMIN', 'MANAGER'];

interface EoriEntry {
  id: string;
  eori: string;
  holderName: string;
  isDefault?: boolean;
}

const EORI_REGEX = /^[A-Z]{2}\d{8,15}$/;

const Eori = () => {
  const navigate = useNavigate();
  const role = useAuthStore((s) => s.user?.role);
  const canView = role ? MANAGER_ROLES.includes(role) : false;
  const [checkValue, setCheckValue] = useState('');

  const { data = [], isLoading, isError } = useQuery({
    queryKey: ['mobile-eori-list'],
    queryFn: async () => {
      const res = await mobileApi.eori.list();
      return (Array.isArray(res.data) ? res.data : res.data?.content || []) as EoriEntry[];
    },
    enabled: canView,
  });

  const validate = useMutation({
    mutationFn: async (eori: string) => (await mobileApi.eori.validate(eori)).data as { valid: boolean },
  });

  const handleCheck = (e: React.FormEvent) => {
    e.preventDefault();
    const upper = checkValue.trim().toUpperCase();
    if (!EORI_REGEX.test(upper)) {
      validate.reset();
      return;
    }
    validate.mutate(upper);
  };

  return (
    <>
      <div className="header-bar row">
        <button onClick={() => navigate(-1)} style={{ background: 'none', border: 'none', cursor: 'pointer' }}>
          <ArrowLeft size={20} />
        </button>
        <h1 className="title" style={{ margin: 0, fontSize: 18 }}>EORI</h1>
      </div>

      <div className="stack" style={{ marginTop: 12 }}>
        {!canView && <div className="empty-state">Réservé aux managers, administrateurs et propriétaires.</div>}
        {canView && (
        <>
        <div className="card">
          <p className="section-label">Vérifier un numéro EORI</p>
          <form onSubmit={handleCheck} className="row" style={{ gap: 8 }}>
            <input
              className="input"
              placeholder="FR12345678901"
              value={checkValue}
              onChange={(e) => { setCheckValue(e.target.value.toUpperCase()); validate.reset(); }}
              style={{ flex: 1, fontFamily: 'monospace' }}
            />
            <button type="submit" className="btn btn-primary" disabled={validate.isPending || !checkValue.trim()}>
              {validate.isPending ? <Loader2 size={16} className="spin" /> : 'Vérifier'}
            </button>
          </form>
          {validate.isSuccess && (
            <p className="row" style={{ gap: 6, marginTop: 10, fontSize: 13, fontWeight: 600 }}>
              {validate.data.valid ? (
                <><CheckCircle2 size={16} color="rgb(var(--c-accent))" /> EORI valide</>
              ) : (
                <><XCircle size={16} color="rgb(var(--c-danger))" /> EORI invalide</>
              )}
            </p>
          )}
          {validate.isError && <p className="error-text" style={{ marginTop: 10 }}>Impossible de vérifier ce numéro.</p>}
        </div>

        <p className="section-label">Numéros EORI enregistrés</p>
        {isLoading && (
          <div className="center-screen" style={{ minHeight: 120 }}>
            <Loader2 className="spin" color="rgb(var(--c-ink-soft))" />
          </div>
        )}
        {isError && <p className="error-text">Impossible de charger les EORI.</p>}
        {!isLoading && data.length === 0 && <div className="empty-state">Aucun EORI enregistré.</div>}

        {data.map((e) => (
          <div key={e.id} className="card row-between">
            <div>
              <p style={{ fontWeight: 700, fontFamily: 'monospace', margin: '0 0 2px' }}>{e.eori}</p>
              <p className="text-sm text-soft" style={{ margin: 0 }}>{e.holderName}</p>
            </div>
            {e.isDefault && <span className="badge badge-accent">Par défaut</span>}
          </div>
        ))}
        </>
        )}
      </div>
    </>
  );
};

export default Eori;
