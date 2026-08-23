import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, Loader2, ShieldAlert, ShieldCheck } from 'lucide-react';
import { mobileApi } from '../lib/api';
import { useAuthStore } from '../stores/auth';

const MANAGER_ROLES = ['OWNER', 'ADMIN', 'MANAGER'];

interface DpsCheck {
  riskLevel: 'NONE' | 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  matchedListName: string | null;
  matchedEntryDetails: string | null;
}

const RISK_LABEL: Record<string, string> = {
  NONE: 'Aucun risque',
  LOW: 'Risque faible',
  MEDIUM: 'Risque moyen',
  HIGH: 'Risque élevé',
  CRITICAL: 'Risque critique',
};

const RISK_BADGE: Record<string, string> = {
  NONE: 'badge-accent',
  LOW: 'badge-accent',
  MEDIUM: 'badge-warning',
  HIGH: 'badge-danger',
  CRITICAL: 'badge-danger',
};

const Dps = () => {
  const navigate = useNavigate();
  const role = useAuthStore((s) => s.user?.role);
  const canScreen = role ? MANAGER_ROLES.includes(role) : false;
  const [name, setName] = useState('');
  const [countryCode, setCountryCode] = useState('');

  const screen = useMutation({
    mutationFn: async () =>
      (await mobileApi.dps.screen({ name: name.trim(), countryCode: countryCode.trim() || undefined })).data as DpsCheck,
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;
    screen.mutate();
  };

  const risk = screen.data?.riskLevel;
  const clear = risk === 'NONE' || risk === 'LOW';

  return (
    <>
      <div className="header-bar row">
        <button onClick={() => navigate(-1)} style={{ background: 'none', border: 'none', cursor: 'pointer' }}>
          <ArrowLeft size={20} />
        </button>
        <h1 className="title" style={{ margin: 0, fontSize: 18 }}>Screening parties</h1>
      </div>

      {!canScreen && <div className="empty-state" style={{ marginTop: 12 }}>Réservé aux managers, administrateurs et propriétaires.</div>}

      {canScreen && (
      <form onSubmit={handleSubmit} className="stack" style={{ marginTop: 12 }}>
        <input className="input" placeholder="Nom de la partie / entreprise" value={name} onChange={(e) => setName(e.target.value)} />
        <input className="input" placeholder="Code pays (optionnel)" value={countryCode} onChange={(e) => setCountryCode(e.target.value.toUpperCase())} />
        <button type="submit" className="btn btn-primary btn-block" disabled={screen.isPending || !name.trim()}>
          {screen.isPending ? <Loader2 size={16} className="spin" /> : 'Vérifier'}
        </button>
        {screen.isError && <p className="error-text">Erreur lors du screening.</p>}
      </form>
      )}

      {canScreen && screen.data && risk && (
        <div className="card" style={{ marginTop: 16 }}>
          <div className="row" style={{ gap: 8, marginBottom: 8 }}>
            {clear ? (
              <ShieldCheck size={20} color="rgb(var(--c-accent))" />
            ) : (
              <ShieldAlert size={20} color="rgb(var(--c-danger))" />
            )}
            <span className={`badge ${RISK_BADGE[risk]}`}>{RISK_LABEL[risk]}</span>
          </div>
          {screen.data.matchedListName && (
            <p className="text-sm text-soft" style={{ margin: 0 }}>Liste : {screen.data.matchedListName}</p>
          )}
        </div>
      )}
    </>
  );
};

export default Dps;
