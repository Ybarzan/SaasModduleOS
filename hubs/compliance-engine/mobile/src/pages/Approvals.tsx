import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, Check, Loader2, X } from 'lucide-react';
import { mobileApi } from '../lib/api';
import { useAuthStore } from '../stores/auth';
import { APPROVAL_ENTITY_LABELS } from '../types';
import type { ApprovalRequest } from '../types';

const MANAGER_ROLES = ['OWNER', 'ADMIN', 'MANAGER'];

function formatDate(iso?: string): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString('fr-FR', { day: '2-digit', month: 'short', year: 'numeric' });
}

function formatAmount(amount?: number, currency?: string): string | null {
  if (amount === undefined || amount === null) return null;
  return new Intl.NumberFormat('fr-FR', { style: 'currency', currency: currency || 'EUR' }).format(amount);
}

const Approvals = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const role = useAuthStore((s) => s.user?.role);
  const canDecide = role ? MANAGER_ROLES.includes(role) : false;
  const [decidingId, setDecidingId] = useState<string | null>(null);

  const { data = [], isLoading, isError } = useQuery({
    queryKey: ['mobile-approvals-pending'],
    queryFn: async () => (await mobileApi.approvals.pending()).data as ApprovalRequest[],
    enabled: canDecide,
  });

  const decide = useMutation({
    mutationFn: ({ id, action }: { id: string; action: 'approve' | 'reject' }) =>
      action === 'approve' ? mobileApi.approvals.approve(id) : mobileApi.approvals.reject(id),
    onMutate: ({ id }) => setDecidingId(id),
    onSettled: () => {
      setDecidingId(null);
      queryClient.invalidateQueries({ queryKey: ['mobile-approvals-pending'] });
    },
  });

  return (
    <>
      <div className="header-bar row">
        <button onClick={() => navigate(-1)} style={{ background: 'none', border: 'none', cursor: 'pointer' }}>
          <ArrowLeft size={20} />
        </button>
        <h1 className="title" style={{ margin: 0, fontSize: 18 }}>Approbations</h1>
      </div>

      <div className="stack" style={{ marginTop: 12 }}>
        {!canDecide && (
          <div className="empty-state">Réservé aux managers, administrateurs et propriétaires.</div>
        )}

        {canDecide && isLoading && (
          <div className="center-screen" style={{ minHeight: 200 }}>
            <Loader2 className="spin" color="rgb(var(--c-ink-soft))" />
          </div>
        )}

        {canDecide && isError && <p className="error-text">Impossible de charger les approbations.</p>}

        {canDecide && !isLoading && data.length === 0 && (
          <div className="empty-state">Aucune approbation en attente.</div>
        )}

        {data.map((req) => {
          const amount = formatAmount(req.amount, req.currency);
          const busy = decidingId === req.id && decide.isPending;
          return (
            <div key={req.id} className="card">
              <div className="row-between" style={{ marginBottom: 8 }}>
                <span className="badge badge-accent">
                  {APPROVAL_ENTITY_LABELS[req.entityType] || req.entityType}
                </span>
                {req.totalSteps && req.totalSteps > 1 && (
                  <span className="text-sm text-soft">Étape {req.currentStep}/{req.totalSteps}</span>
                )}
              </div>
              <p style={{ fontWeight: 700, margin: '0 0 4px' }}>{req.entityReference || '—'}</p>
              <div className="row-between text-sm" style={{ marginBottom: 4 }}>
                <span className="text-soft">Demandé le {formatDate(req.requestedAt)}</span>
                {amount && <span style={{ fontWeight: 600 }}>{amount}</span>}
              </div>
              {req.notes && <p className="text-sm text-soft" style={{ margin: '4px 0 0' }}>{req.notes}</p>}

              <div className="row" style={{ marginTop: 12, gap: 8 }}>
                <button
                  className="btn btn-primary"
                  style={{ flex: 1, background: 'rgb(var(--c-danger))' }}
                  disabled={busy}
                  onClick={() => decide.mutate({ id: req.id, action: 'reject' })}
                >
                  {busy && decide.variables?.action === 'reject' ? <Loader2 size={16} className="spin" /> : <X size={16} />}
                  Rejeter
                </button>
                <button
                  className="btn btn-primary"
                  style={{ flex: 1 }}
                  disabled={busy}
                  onClick={() => decide.mutate({ id: req.id, action: 'approve' })}
                >
                  {busy && decide.variables?.action === 'approve' ? <Loader2 size={16} className="spin" /> : <Check size={16} />}
                  Approuver
                </button>
              </div>
            </div>
          );
        })}
      </div>
    </>
  );
};

export default Approvals;
