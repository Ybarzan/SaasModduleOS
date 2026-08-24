import type { AxiosError } from 'axios';
import { Fragment, useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Zap,
  Check,
  X,
  Clock,
  Loader2,
  ChevronDown,
  ChevronUp,
  History,
} from 'lucide-react';
import toast from 'react-hot-toast';
import { incokalkAPI } from '../lib/api';

interface OrchestrationSuggestion {
  id: string;
  ruleName: string | null;
  shipmentId: string | null;
  actionType: string;
  status: 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED' | 'EXECUTED' | 'FAILED';
  contextJson: string | null;
  createdAt: string;
  decidedAt: string | null;
  decidedByUserId: string | null;
  decisionNote: string | null;
  executionResult: string | null;
}

const ACTION_TYPE_LABELS: Record<string, string> = {
  SUGGEST_ERP_ORDER_ADJUSTMENT: 'Ajustement commande ERP',
};

const STATUS_CONFIG: Record<OrchestrationSuggestion['status'], { label: string; className: string }> = {
  PENDING_APPROVAL: { label: 'EN ATTENTE', className: 'text-warning border-warning/40' },
  APPROVED: { label: 'APPROUVÉ', className: 'text-accent border-accent/40' },
  REJECTED: { label: 'REJETÉ', className: 'text-ink-soft border-line' },
  EXECUTED: { label: 'EXÉCUTÉ', className: 'text-success border-success/40' },
  FAILED: { label: 'ÉCHOUÉ', className: 'text-danger border-danger/40' },
};

function StatusTag({ status }: { status: OrchestrationSuggestion['status'] }) {
  const cfg = STATUS_CONFIG[status];
  return (
    <span
      className={`inline-block text-[10px] font-medium uppercase tracking-wide border rounded-none px-1.5 py-0.5 ${cfg.className}`}
    >
      [{cfg.label}]
    </span>
  );
}

function formatContextJson(raw: string | null): string {
  if (!raw) return '—';
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch {
    return raw;
  }
}

const OrchestrationSuggestions = () => {
  const queryClient = useQueryClient();
  const [tab, setTab] = useState<'pending' | 'history'>('pending');
  const [expanded, setExpanded] = useState<string | null>(null);
  const [decision, setDecision] = useState<{ id: string; action: 'approve' | 'reject' } | null>(null);
  const [note, setNote] = useState('');

  const { data, isLoading } = useQuery({
    queryKey: ['orchestration-suggestions'],
    queryFn: async () => {
      const res = await incokalkAPI.orchestrationSuggestions.list();
      return res.data as OrchestrationSuggestion[];
    },
  });

  const all = data || [];
  const pending = all.filter((s) => s.status === 'PENDING_APPROVAL');
  const history = all.filter((s) => s.status !== 'PENDING_APPROVAL');
  const rows = tab === 'pending' ? pending : history;

  const decide = useMutation({
    mutationFn: ({ id, action, note }: { id: string; action: 'approve' | 'reject'; note: string }) =>
      action === 'approve'
        ? incokalkAPI.orchestrationSuggestions.approve(id, note || undefined)
        : incokalkAPI.orchestrationSuggestions.reject(id, note || undefined),
    onSuccess: (_res, vars) => {
      toast.success(vars.action === 'approve' ? 'Suggestion approuvée' : 'Suggestion rejetée');
      setDecision(null);
      setNote('');
      queryClient.invalidateQueries({ queryKey: ['orchestration-suggestions'] });
    },
    onError: (err: AxiosError<{ message?: string }>) => {
      toast.error(err.response?.data?.message || 'Erreur lors de la décision');
    },
  });

  const openDecision = (id: string, action: 'approve' | 'reject') => {
    setDecision({ id, action });
    setNote('');
  };

  const confirmDecision = () => {
    if (!decision) return;
    decide.mutate({ id: decision.id, action: decision.action, note });
  };

  return (
    <div className="max-w-6xl mx-auto px-4 py-8">
      {/* Header */}
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-ink">
          <span className="text-accent font-normal" aria-hidden="true">:: </span>
          Suggestions d'action
        </h1>
        <p className="text-ink-soft mt-1">
          Propositions du moteur de règles à valider avant exécution réelle. Approuver déclenche
          immédiatement l'action (ex. synchronisation ERP) — la décision n'est pas réversible.
        </p>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 bg-surface-2 p-1 rounded-none border border-line mb-6 w-fit">
        <button
          onClick={() => setTab('pending')}
          className={`flex items-center gap-1.5 px-4 py-2 rounded-none text-sm font-medium transition-colors ${
            tab === 'pending' ? 'bg-surface text-ink shadow-sm' : 'text-ink-soft hover:text-ink'
          }`}
        >
          <Clock size={14} />
          En attente {pending.length > 0 && `(${pending.length})`}
        </button>
        <button
          onClick={() => setTab('history')}
          className={`flex items-center gap-1.5 px-4 py-2 rounded-none text-sm font-medium transition-colors ${
            tab === 'history' ? 'bg-surface text-ink shadow-sm' : 'text-ink-soft hover:text-ink'
          }`}
        >
          <History size={14} />
          Historique
        </button>
      </div>

      {isLoading ? (
        <div className="px-6 py-12 text-center text-ink-soft">
          <Loader2 size={24} className="animate-spin mx-auto mb-2 text-ink-soft" />
          Chargement...
        </div>
      ) : rows.length === 0 ? (
        <div className="bg-surface rounded-none border border-line px-6 py-12 text-center text-ink-soft">
          <Zap size={32} className="mx-auto mb-3 text-ink-soft" />
          <p>{tab === 'pending' ? 'Aucune suggestion en attente' : 'Aucune suggestion décidée'}</p>
        </div>
      ) : (
        <div className="bg-surface rounded-none border border-line overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="bg-bg border-b border-line">
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Règle</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Action</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Statut</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Créée le</th>
                  <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {rows.map((s) => {
                  const isExpanded = expanded === s.id;
                  return (
                    <Fragment key={s.id}>
                      <tr
                        className="hover:bg-bg transition-colors cursor-pointer"
                        onClick={() => setExpanded(isExpanded ? null : s.id)}
                      >
                        <td className="px-6 py-4">
                          <div className="flex items-center gap-2">
                            {isExpanded ? (
                              <ChevronUp size={14} className="text-ink-soft shrink-0" />
                            ) : (
                              <ChevronDown size={14} className="text-ink-soft shrink-0" />
                            )}
                            <span className="text-sm font-medium text-ink">{s.ruleName || '—'}</span>
                          </div>
                        </td>
                        <td className="px-6 py-4 text-sm text-ink-soft">
                          {ACTION_TYPE_LABELS[s.actionType] || s.actionType}
                        </td>
                        <td className="px-6 py-4">
                          <StatusTag status={s.status} />
                        </td>
                        <td className="px-6 py-4 text-sm text-ink-soft">
                          {new Date(s.createdAt).toLocaleString('fr-FR')}
                        </td>
                        <td className="px-6 py-4 text-right">
                          {s.status === 'PENDING_APPROVAL' && (
                            <div className="flex items-center justify-end gap-2" onClick={(e) => e.stopPropagation()}>
                              <button
                                onClick={() => openDecision(s.id, 'approve')}
                                className="inline-flex items-center gap-1 px-2.5 py-1 text-xs font-medium bg-success text-white rounded-none hover:bg-success/90 transition-colors"
                              >
                                <Check size={12} />
                                Approuver
                              </button>
                              <button
                                onClick={() => openDecision(s.id, 'reject')}
                                className="inline-flex items-center gap-1 px-2.5 py-1 text-xs font-medium bg-danger text-white rounded-none hover:bg-danger/90 transition-colors"
                              >
                                <X size={12} />
                                Rejeter
                              </button>
                            </div>
                          )}
                        </td>
                      </tr>
                      {isExpanded && (
                        <tr>
                          <td colSpan={5} className="px-6 py-4 bg-bg">
                            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                              <div>
                                <p className="text-xs font-medium text-ink-soft uppercase tracking-wider mb-2">
                                  Contexte au déclenchement
                                </p>
                                <pre className="text-xs font-mono text-ink-soft bg-surface border border-line rounded-none p-3 overflow-x-auto">
                                  {formatContextJson(s.contextJson)}
                                </pre>
                                <p className="text-xs text-ink-soft mt-2">
                                  ID expédition : <span className="font-mono">{s.shipmentId || '—'}</span>
                                </p>
                              </div>
                              {s.status !== 'PENDING_APPROVAL' && (
                                <div>
                                  <p className="text-xs font-medium text-ink-soft uppercase tracking-wider mb-2">
                                    Décision
                                  </p>
                                  <p className="text-sm text-ink-soft">
                                    Décidée le {s.decidedAt ? new Date(s.decidedAt).toLocaleString('fr-FR') : '—'}
                                  </p>
                                  {s.decisionNote && (
                                    <p className="text-sm text-ink mt-1">Note : {s.decisionNote}</p>
                                  )}
                                  {s.executionResult && (
                                    <p className="text-sm text-ink mt-2">
                                      <span className="text-ink-soft">Résultat d'exécution : </span>
                                      {s.executionResult}
                                    </p>
                                  )}
                                </div>
                              )}
                            </div>
                          </td>
                        </tr>
                      )}
                    </Fragment>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Decision modal */}
      {decision && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div className="absolute inset-0 bg-black/40" onClick={() => setDecision(null)} />
          <div className="relative bg-surface rounded-none border border-line shadow-2xl w-full max-w-md mx-4 p-6">
            <h3 className="text-lg font-semibold text-ink mb-2">
              {decision.action === 'approve' ? 'Approuver la suggestion' : 'Rejeter la suggestion'}
            </h3>
            <p className="text-sm text-ink-soft mb-4">
              {decision.action === 'approve'
                ? "Cette décision déclenche immédiatement l'action réelle correspondante (ex. synchronisation ERP)."
                : "La suggestion sera rejetée définitivement, aucune action ne sera exécutée."}
            </p>
            <label className="block text-sm font-medium text-ink mb-1">Note (optionnelle)</label>
            <textarea
              value={note}
              onChange={(e) => setNote(e.target.value)}
              className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
              rows={3}
              placeholder="Motif de la décision..."
            />
            <div className="flex gap-3 pt-4">
              <button
                onClick={() => setDecision(null)}
                className="flex-1 px-4 py-2 border border-line rounded-none text-sm font-medium text-ink hover:bg-bg transition-colors"
              >
                Annuler
              </button>
              <button
                onClick={confirmDecision}
                disabled={decide.isPending}
                className={`flex-1 px-4 py-2 rounded-none text-sm font-medium text-white transition-colors flex items-center justify-center gap-2 disabled:opacity-50 ${
                  decision.action === 'approve' ? 'bg-success hover:bg-success/90' : 'bg-danger hover:bg-danger/90'
                }`}
              >
                {decide.isPending && <Loader2 size={14} className="animate-spin" />}
                {decision.action === 'approve' ? "Confirmer l'approbation" : 'Confirmer le rejet'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default OrchestrationSuggestions;
