import type { AxiosError } from 'axios';
import { Fragment, useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  GitBranch,
  Plus,
  Trash2,
  ChevronDown,
  ChevronUp,
  Loader2,
  Check,
  X,
  Clock,
  FileText,
  Filter,
  Eye,
  ArrowRight,
} from 'lucide-react';
import toast from 'react-hot-toast';
import { incokalkAPI } from '../lib/api';
import { useAuthStore } from '../stores/auth';
import { formatNumber, formatEur } from '../lib/formatNumber';

interface WorkflowStep {
  id?: string;
  name: string;
  approverRole: string;
  required: boolean;
  order?: number;
}

interface WorkflowDefinition {
  id: string;
  name: string;
  description: string;
  entityType: string;
  thresholdAmount: number;
  steps: WorkflowStep[];
  active: boolean;
  createdAt: string;
}

interface ApprovalRequest {
  id: string;
  reference: string;
  entityType: string;
  amount: number;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED' | 'EXPIRED';
  requestedBy: string;
  requestedByName?: string;
  createdAt: string;
  workflowId: string;
  currentStep: number;
  notes?: string;
}

interface ApprovalHistoryEntry {
  id: string;
  step: string;
  approver: string;
  approverName?: string;
  action: string;
  notes?: string;
  createdAt: string;
}

interface ApprovalStats {
  total: number;
  pending: number;
  approved: number;
  rejected: number;
}

const ENTITY_TYPES = [
  { value: 'QUOTE', label: 'Devis' },
  { value: 'CARRIER_INVOICE', label: 'Facture transporteur' },
  { value: 'PURCHASE_ORDER', label: 'Commande d\'achat' },
  { value: 'EXPENSE_REPORT', label: 'Note de frais' },
  { value: 'CUSTOM', label: 'Personnalisé' },
];

const STATUS_CONFIG: Record<string, { label: string; color: string; bg: string }> = {
  PENDING: { label: 'En attente', color: 'text-warning', bg: 'bg-warning/10' },
  APPROVED: { label: 'Approuvé', color: 'text-success', bg: 'bg-success/10' },
  REJECTED: { label: 'Rejeté', color: 'text-danger', bg: 'bg-danger/10' },
  CANCELLED: { label: 'Annulé', color: 'text-ink-soft', bg: 'bg-surface-2' },
  EXPIRED: { label: 'Expiré', color: 'text-ink-soft', bg: 'bg-surface-2' },
};

const emptyWorkflowForm = {
  name: '',
  description: '',
  entityType: 'QUOTE',
  thresholdAmount: 0,
  steps: [{ name: '', approverRole: 'ADMIN', required: true }] as WorkflowStep[],
};

const ApprovalWorkflows = () => {
  const queryClient = useQueryClient();
  const user = useAuthStore((s) => s.user);

  const [activeTab, setActiveTab] = useState<'workflows' | 'demandes'>('workflows');
  const [demandeView, setDemandeView] = useState<'toutes' | 'en_attente' | 'mes'>('toutes');
  const [createOpen, setCreateOpen] = useState(false);
  const [form, setForm] = useState(emptyWorkflowForm);
  const [expandedRequest, setExpandedRequest] = useState<string | null>(null);
  const [deleteConfirm, setDeleteConfirm] = useState<string | null>(null);

  // ── Queries ──────────────────────────────────────────────
  const { data: workflowsData, isLoading: workflowsLoading } = useQuery({
    queryKey: ['approval-workflows'],
    queryFn: async () => {
      const res = await incokalkAPI.approvals.listWorkflows();
      return res.data as WorkflowDefinition[] | { workflows: WorkflowDefinition[] };
    },
  });

  const workflows = Array.isArray(workflowsData)
    ? workflowsData
    : workflowsData?.workflows || [];

  const { data: allRequestsData, isLoading: allLoading } = useQuery({
    queryKey: ['approval-requests', 'all'],
    queryFn: async () => {
      const res = await incokalkAPI.approvals.listRequests();
      return res.data as ApprovalRequest[] | { requests: ApprovalRequest[] };
    },
    enabled: activeTab === 'demandes',
  });

  const { data: pendingData, isLoading: pendingLoading } = useQuery({
    queryKey: ['approval-requests', 'pending'],
    queryFn: async () => {
      const res = await incokalkAPI.approvals.pendingApprovals();
      return res.data as ApprovalRequest[] | { requests: ApprovalRequest[] };
    },
    enabled: activeTab === 'demandes',
  });

  const { data: myData, isLoading: myLoading } = useQuery({
    queryKey: ['approval-requests', 'my'],
    queryFn: async () => {
      const res = await incokalkAPI.approvals.myRequests();
      return res.data as ApprovalRequest[] | { requests: ApprovalRequest[] };
    },
    enabled: activeTab === 'demandes',
  });

  const { data: stats } = useQuery({
    queryKey: ['approval-stats'],
    queryFn: async () => {
      const res = await incokalkAPI.approvals.stats();
      return res.data as ApprovalStats;
    },
  });

  const { data: historyData, isLoading: historyLoading } = useQuery({
    queryKey: ['approval-history', expandedRequest],
    queryFn: async () => {
      const res = await incokalkAPI.approvals.getHistory(expandedRequest!);
      return res.data as ApprovalHistoryEntry[] | { history: ApprovalHistoryEntry[] };
    },
    enabled: !!expandedRequest,
  });

  const history = Array.isArray(historyData)
    ? historyData
    : historyData?.history || [];

  const unwrapRequests = (d: ApprovalRequest[] | { requests: ApprovalRequest[] } | undefined): ApprovalRequest[] =>
    Array.isArray(d) ? d : d?.requests || [];

  const currentRequests =
    demandeView === 'toutes'
      ? unwrapRequests(allRequestsData)
      : demandeView === 'en_attente'
        ? unwrapRequests(pendingData)
        : unwrapRequests(myData);

  const isLoading =
    demandeView === 'toutes'
      ? allLoading
      : demandeView === 'en_attente'
        ? pendingLoading
        : myLoading;

  // ── Mutations ────────────────────────────────────────────
  const createWorkflow = useMutation({
    mutationFn: (data: typeof form) => incokalkAPI.approvals.createWorkflow(data),
    onSuccess: () => {
      toast.success('Workflow créé avec succès');
      setCreateOpen(false);
      setForm(emptyWorkflowForm);
      queryClient.invalidateQueries({ queryKey: ['approval-workflows'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || 'Erreur lors de la création');
    },
  });

  const toggleActive = useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) =>
      incokalkAPI.approvals.updateWorkflow(id, { active }),
    onSuccess: () => {
      toast.success('Workflow mis à jour');
      queryClient.invalidateQueries({ queryKey: ['approval-workflows'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || 'Erreur lors de la mise à jour');
    },
  });

  const deleteWorkflow = useMutation({
    mutationFn: (id: string) => incokalkAPI.approvals.deleteWorkflow(id),
    onSuccess: () => {
      toast.success('Workflow supprimé');
      setDeleteConfirm(null);
      queryClient.invalidateQueries({ queryKey: ['approval-workflows'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || 'Erreur lors de la suppression');
    },
  });

  const approveRequest = useMutation({
    mutationFn: (id: string) => incokalkAPI.approvals.approve(id, { notes: '' }),
    onSuccess: () => {
      toast.success('Demande approuvée');
      queryClient.invalidateQueries({ queryKey: ['approval-requests'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || "Erreur lors de l'approbation");
    },
  });

  const rejectRequest = useMutation({
    mutationFn: (id: string) => incokalkAPI.approvals.reject(id, { notes: '' }),
    onSuccess: () => {
      toast.success('Demande rejetée');
      queryClient.invalidateQueries({ queryKey: ['approval-requests'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || 'Erreur lors du rejet');
    },
  });

  const cancelRequest = useMutation({
    mutationFn: (id: string) => incokalkAPI.approvals.cancel(id),
    onSuccess: () => {
      toast.success('Demande annulée');
      queryClient.invalidateQueries({ queryKey: ['approval-requests'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || "Erreur lors de l'annulation");
    },
  });

  // ── Helpers ──────────────────────────────────────────────
  const addStep = () =>
    setForm({
      ...form,
      steps: [...form.steps, { name: '', approverRole: 'ADMIN', required: true }],
    });

  const removeStep = (index: number) =>
    setForm({ ...form, steps: form.steps.filter((_, i) => i !== index) });

  const updateStep = (index: number, field: keyof WorkflowStep, value: string | boolean) =>
    setForm({
      ...form,
      steps: form.steps.map((s, i) => (i === index ? { ...s, [field]: value } : s)),
    });

  const handleSubmitWorkflow = (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.name.trim()) {
      toast.error('Le nom est requis');
      return;
    }
    if (form.steps.length === 0 || form.steps.some((s) => !s.name.trim())) {
      toast.error('Chaque étape doit avoir un nom');
      return;
    }
    createWorkflow.mutate(form);
  };

  const entityTypeLabel = (v: string) =>
    ENTITY_TYPES.find((e) => e.value === v)?.label || v;

  const canCancel = (req: ApprovalRequest) =>
    req.status === 'PENDING' && user && (req.requestedBy === user.id || req.requestedByName === [user.firstName, user.lastName].filter(Boolean).join(' '));

  return (
    <div className="max-w-6xl mx-auto px-4 py-8">
      {/* Header */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between mb-8 gap-4">
        <div>
          <h1 className="text-2xl font-bold text-ink">
            <span className="text-accent font-normal" aria-hidden="true">:: </span>
            Workflows d'approbation
          </h1>
          <p className="text-ink-soft mt-1">Gestion des chaînes d'approbation multi-étapes</p>
        </div>
        {activeTab === 'workflows' && (
          <button
            onClick={() => { setCreateOpen(true); setForm(emptyWorkflowForm); }}
            className="flex items-center gap-2 bg-accent text-white px-4 py-2 rounded-none font-medium hover:bg-accent-strong transition-colors"
          >
            <Plus size={18} />
            Nouveau workflow
          </button>
        )}
      </div>

      {/* Stats */}
      <div className="grid grid-cols-1 sm:grid-cols-4 gap-4 mb-8">
        <div className="bg-surface rounded-none border border-line p-5">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-none bg-accent-soft flex items-center justify-center">
              <GitBranch size={20} className="text-accent" />
            </div>
            <div>
              <p className="text-sm text-ink-soft">Workflows</p>
              <p className="text-2xl font-bold text-ink">{stats?.total ?? workflows.length}</p>
            </div>
          </div>
        </div>
        <div className="bg-surface rounded-none border border-line p-5">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-none bg-warning/15 flex items-center justify-center">
              <Clock size={20} className="text-warning" />
            </div>
            <div>
              <p className="text-sm text-ink-soft">En attente</p>
              <p className="text-2xl font-bold text-ink">{stats?.pending ?? '—'}</p>
            </div>
          </div>
        </div>
        <div className="bg-surface rounded-none border border-line p-5">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-none bg-success/15 flex items-center justify-center">
              <Check size={20} className="text-success" />
            </div>
            <div>
              <p className="text-sm text-ink-soft">Approuvées</p>
              <p className="text-2xl font-bold text-ink">{stats?.approved ?? '—'}</p>
            </div>
          </div>
        </div>
        <div className="bg-surface rounded-none border border-line p-5">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-none bg-danger/15 flex items-center justify-center">
              <X size={20} className="text-danger" />
            </div>
            <div>
              <p className="text-sm text-ink-soft">Rejetées</p>
              <p className="text-2xl font-bold text-ink">{stats?.rejected ?? '—'}</p>
            </div>
          </div>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 bg-surface-2 p-1 rounded-none mb-6 w-fit">
        <button
          onClick={() => setActiveTab('workflows')}
          className={`px-4 py-2 rounded-none text-sm font-medium transition-colors ${
            activeTab === 'workflows'
              ? 'bg-surface text-ink shadow-sm'
              : 'text-ink-soft hover:text-ink'
          }`}
        >
          Workflows
        </button>
        <button
          onClick={() => setActiveTab('demandes')}
          className={`px-4 py-2 rounded-none text-sm font-medium transition-colors ${
            activeTab === 'demandes'
              ? 'bg-surface text-ink shadow-sm'
              : 'text-ink-soft hover:text-ink'
          }`}
        >
          Demandes
        </button>
      </div>

      {/* ═══════════════════════════════════════════════════════
          TAB: WORKFLOWS
          ═══════════════════════════════════════════════════════ */}
      {activeTab === 'workflows' && (
        <>
          {workflowsLoading ? (
            <div className="px-6 py-12 text-center text-ink-soft">
              <Loader2 size={24} className="animate-spin mx-auto mb-2 text-ink-soft" />
              Chargement...
            </div>
          ) : workflows.length === 0 ? (
            <div className="bg-surface rounded-none border border-line px-6 py-12 text-center text-ink-soft">
              <GitBranch size={32} className="mx-auto mb-3 text-ink-soft" />
              <p>Aucun workflow configuré</p>
              <p className="text-sm text-ink-soft mt-1">Créez votre premier workflow d'approbation</p>
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {workflows.map((wf: WorkflowDefinition) => (
                <div
                  key={wf.id}
                  className="bg-surface rounded-none border border-line p-5 hover:shadow-sm transition-shadow"
                >
                  <div className="flex items-start justify-between mb-3">
                    <div className="flex-1 min-w-0">
                      <h3 className="text-base font-semibold text-ink truncate">{wf.name}</h3>
                      <p className="text-sm text-ink-soft mt-0.5 line-clamp-2">{wf.description || '—'}</p>
                    </div>
                    <span
                      className={`ml-3 shrink-0 text-xs font-medium px-2 py-1 rounded-full ${
                        wf.active ? 'bg-success/15 text-success' : 'bg-surface-2 text-ink-soft'
                      }`}
                    >
                      {wf.active ? 'Actif' : 'Inactif'}
                    </span>
                  </div>

                  <div className="flex flex-wrap gap-2 mb-3">
                    <span className="text-xs font-medium px-2 py-1 rounded-full bg-accent-soft text-accent-strong">
                      {entityTypeLabel(wf.entityType)}
                    </span>
                    <span className="text-xs font-medium px-2 py-1 rounded-full bg-accent/10 text-accent-strong">
                      {wf.steps.length} étape{wf.steps.length !== 1 ? 's' : ''}
                    </span>
                    {wf.thresholdAmount > 0 && (
                      <span className="text-xs font-medium px-2 py-1 rounded-full bg-warning/10 text-warning">
                        Seuil : {formatEur(wf.thresholdAmount)}
                      </span>
                    )}
                  </div>

                  <div className="flex items-center gap-2 pt-3 border-t border-line">
                    <button
                      onClick={() =>
                        toggleActive.mutate({ id: wf.id, active: !wf.active })
                      }
                      disabled={toggleActive.isPending}
                      className={`px-3 py-1.5 rounded-none text-xs font-medium transition-colors ${
                        wf.active
                          ? 'bg-surface-2 text-ink-soft hover:bg-line'
                          : 'bg-success/15 text-success hover:bg-success/25'
                      }`}
                    >
                      {wf.active ? 'Désactiver' : 'Activer'}
                    </button>
                    {deleteConfirm === wf.id ? (
                      <div className="flex items-center gap-1 ml-auto">
                        <span className="text-xs text-ink-soft mr-1">Confirmer ?</span>
                        <button
                          onClick={() => deleteWorkflow.mutate(wf.id)}
                          disabled={deleteWorkflow.isPending}
                          className="px-2 py-1 text-xs bg-danger text-white rounded hover:bg-danger/90 transition-colors"
                        >
                          {deleteWorkflow.isPending ? (
                            <Loader2 size={12} className="animate-spin" />
                          ) : (
                            'Oui'
                          )}
                        </button>
                        <button
                          onClick={() => setDeleteConfirm(null)}
                          className="px-2 py-1 text-xs bg-surface-2 text-ink-soft rounded hover:bg-line transition-colors"
                        >
                          Non
                        </button>
                      </div>
                    ) : (
                      <button
                        onClick={() => setDeleteConfirm(wf.id)}
                        className="p-1.5 rounded-none text-ink-soft hover:text-danger hover:bg-danger/10 transition-colors ml-auto"
                        title="Supprimer"
                      >
                        <Trash2 size={16} />
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </>
      )}

      {/* ═══════════════════════════════════════════════════════
          TAB: DEMANDES
          ═══════════════════════════════════════════════════════ */}
      {activeTab === 'demandes' && (
        <>
          {/* Sub-view tabs */}
          <div className="flex gap-1 bg-surface-2 p-1 rounded-none mb-6 w-fit">
            {([
              { key: 'toutes', label: 'Toutes', icon: Filter },
              { key: 'en_attente', label: 'En attente', icon: Clock },
              { key: 'mes', label: 'Mes demandes', icon: Eye },
            ] as const).map(({ key, label, icon: Icon }) => (
              <button
                key={key}
                onClick={() => setDemandeView(key)}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-none text-sm font-medium transition-colors ${
                  demandeView === key
                    ? 'bg-surface text-ink shadow-sm'
                    : 'text-ink-soft hover:text-ink'
                }`}
              >
                <Icon size={14} />
                {label}
              </button>
            ))}
          </div>

          {isLoading ? (
            <div className="px-6 py-12 text-center text-ink-soft">
              <Loader2 size={24} className="animate-spin mx-auto mb-2 text-ink-soft" />
              Chargement...
            </div>
          ) : currentRequests.length === 0 ? (
            <div className="bg-surface rounded-none border border-line px-6 py-12 text-center text-ink-soft">
              <FileText size={32} className="mx-auto mb-3 text-ink-soft" />
              <p>Aucune demande</p>
            </div>
          ) : (
            <div className="bg-surface rounded-none border border-line overflow-hidden">
              <div className="overflow-x-auto">
                <table className="w-full">
                  <thead>
                    <tr className="bg-bg border-b border-line">
                      <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">
                        Référence
                      </th>
                      <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">
                        Type
                      </th>
                      <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">
                        Montant
                      </th>
                      <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">
                        Statut
                      </th>
                      <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">
                        Demandé par
                      </th>
                      <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">
                        Date
                      </th>
                      <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">
                        Actions
                      </th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-line">
                    {currentRequests.map((req: ApprovalRequest) => {
                      const st = STATUS_CONFIG[req.status] || STATUS_CONFIG.PENDING;
                      const isExpanded = expandedRequest === req.id;
                      return (
                        <Fragment key={req.id}>
                          <tr
                            className="hover:bg-bg transition-colors cursor-pointer"
                            onClick={() =>
                              setExpandedRequest(isExpanded ? null : req.id)
                            }
                          >
                            <td className="px-6 py-4">
                              <div className="flex items-center gap-2">
                                {isExpanded ? (
                                  <ChevronUp size={14} className="text-ink-soft shrink-0" />
                                ) : (
                                  <ChevronDown size={14} className="text-ink-soft shrink-0" />
                                )}
                                <span className="text-sm font-medium text-ink">
                                  {req.reference}
                                </span>
                              </div>
                            </td>
                            <td className="px-6 py-4 text-sm text-ink-soft">
                              {entityTypeLabel(req.entityType)}
                            </td>
                            <td className="px-6 py-4 text-sm text-ink text-right font-medium">
                              {req.amount != null ? formatNumber(req.amount) : ''} €
                            </td>
                            <td className="px-6 py-4">
                              <span
                                className={`inline-flex items-center text-xs font-medium px-2.5 py-0.5 rounded-full ${st.bg} ${st.color}`}
                              >
                                {st.label}
                              </span>
                            </td>
                            <td className="px-6 py-4 text-sm text-ink-soft">
                              {req.requestedByName || req.requestedBy}
                            </td>
                            <td className="px-6 py-4 text-sm text-ink-soft">
                              {new Date(req.createdAt).toLocaleDateString('fr-FR')}
                            </td>
                            <td className="px-6 py-4 text-right">
                              <div
                                className="flex items-center justify-end gap-2"
                                onClick={(e) => e.stopPropagation()}
                              >
                                {req.status === 'PENDING' && (
                                  <>
                                    <button
                                      onClick={() => approveRequest.mutate(req.id)}
                                      disabled={approveRequest.isPending}
                                      className="inline-flex items-center gap-1 px-2.5 py-1 text-xs font-medium bg-success text-white rounded-none hover:bg-success/90 transition-colors"
                                    >
                                      <Check size={12} />
                                      Approuver
                                    </button>
                                    <button
                                      onClick={() => rejectRequest.mutate(req.id)}
                                      disabled={rejectRequest.isPending}
                                      className="inline-flex items-center gap-1 px-2.5 py-1 text-xs font-medium bg-danger text-white rounded-none hover:bg-danger/90 transition-colors"
                                    >
                                      <X size={12} />
                                      Rejeter
                                    </button>
                                  </>
                                )}
                                {canCancel(req) && (
                                  <button
                                    onClick={() => cancelRequest.mutate(req.id)}
                                    disabled={cancelRequest.isPending}
                                    className="inline-flex items-center gap-1 px-2.5 py-1 text-xs font-medium bg-surface-2 text-ink-soft rounded-none hover:bg-line transition-colors"
                                  >
                                    Annuler
                                  </button>
                                )}
                              </div>
                            </td>
                          </tr>
                          {isExpanded && (
                            <tr>
                              <td colSpan={7} className="px-6 py-4 bg-bg">
                                <p className="text-xs font-medium text-ink-soft uppercase tracking-wider mb-3">
                                  Historique d'approbation
                                </p>
                                {historyLoading ? (
                                  <div className="text-center py-4">
                                    <Loader2
                                      size={16}
                                      className="animate-spin mx-auto text-ink-soft"
                                    />
                                  </div>
                                ) : history.length === 0 ? (
                                  <p className="text-sm text-ink-soft">Aucun historique</p>
                                ) : (
                                  <div className="space-y-3">
                                    {history.map((h: ApprovalHistoryEntry, idx: number) => {
                                      const actionColor =
                                        h.action === 'APPROVED'
                                          ? 'text-success'
                                          : h.action === 'REJECTED'
                                            ? 'text-danger'
                                            : h.action === 'CANCELLED'
                                              ? 'text-ink-soft'
                                              : 'text-accent';
                                      return (
                                        <div key={h.id} className="flex items-start gap-3">
                                          <div className="flex flex-col items-center">
                                            <div
                                              className={`w-2 h-2 rounded-full mt-1.5 ${
                                                h.action === 'APPROVED'
                                                  ? 'bg-success'
                                                  : h.action === 'REJECTED'
                                                    ? 'bg-danger'
                                                    : h.action === 'CANCELLED'
                                                      ? 'bg-surface-2'
                                                      : 'bg-accent'
                                              }`}
                                            />
                                            {idx < history.length - 1 && (
                                              <div className="w-px h-full min-h-[24px] bg-surface-2 mt-1" />
                                            )}
                                          </div>
                                          <div className="flex-1">
                                            <div className="flex items-center gap-2">
                                              <span className="text-sm font-medium text-ink">
                                                {h.approverName || h.approver}
                                              </span>
                                              <ArrowRight size={12} className="text-ink-soft" />
                                              <span className={`text-sm font-medium ${actionColor}`}>
                                                {h.action === 'APPROVED'
                                                  ? 'Approuvé'
                                                  : h.action === 'REJECTED'
                                                    ? 'Rejeté'
                                                    : h.action === 'CANCELLED'
                                                      ? 'Annulé'
                                                      : h.action}
                                              </span>
                                              {h.step && (
                                                <span className="text-xs text-ink-soft">
                                                  — {h.step}
                                                </span>
                                              )}
                                            </div>
                                            {h.notes && (
                                              <p className="text-xs text-ink-soft mt-0.5">
                                                {h.notes}
                                              </p>
                                            )}
                                            <p className="text-xs text-ink-soft mt-0.5">
                                              {new Date(h.createdAt).toLocaleString('fr-FR')}
                                            </p>
                                          </div>
                                        </div>
                                      );
                                    })}
                                  </div>
                                )}
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
        </>
      )}

      {/* ═══════════════════════════════════════════════════════
          CREATE WORKFLOW MODAL
          ═══════════════════════════════════════════════════════ */}
      {createOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div
            className="absolute inset-0 bg-black/40"
            onClick={() => setCreateOpen(false)}
          />
          <div className="relative bg-surface rounded-none shadow-2xl w-full max-w-lg mx-4 p-6 max-h-[90vh] overflow-y-auto">
            <h3 className="text-lg font-semibold text-ink mb-4">
              Nouveau workflow
            </h3>
            <form onSubmit={handleSubmitWorkflow} className="space-y-4">
              {/* Name */}
              <div>
                <label className="block text-sm font-medium text-ink mb-1">
                  Nom
                </label>
                <input
                  type="text"
                  value={form.name}
                  onChange={(e) => setForm({ ...form, name: e.target.value })}
                  className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                  placeholder="Approbation devis > 10k€"
                  required
                />
              </div>

              {/* Description */}
              <div>
                <label className="block text-sm font-medium text-ink mb-1">
                  Description
                </label>
                <textarea
                  value={form.description}
                  onChange={(e) =>
                    setForm({ ...form, description: e.target.value })
                  }
                  className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                  rows={2}
                  placeholder="Workflow pour les devis dépassant 10 000 €"
                />
              </div>

              {/* Entity Type */}
              <div>
                <label className="block text-sm font-medium text-ink mb-1">
                  Type d'entité
                </label>
                <select
                  value={form.entityType}
                  onChange={(e) =>
                    setForm({ ...form, entityType: e.target.value })
                  }
                  className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                >
                  {ENTITY_TYPES.map((et) => (
                    <option key={et.value} value={et.value}>
                      {et.label}
                    </option>
                  ))}
                </select>
              </div>

              {/* Threshold */}
              <div>
                <label className="block text-sm font-medium text-ink mb-1">
                  Seuil (€)
                </label>
                <input
                  type="number"
                  min={0}
                  value={form.thresholdAmount}
                  onChange={(e) =>
                    setForm({
                      ...form,
                      thresholdAmount: Number(e.target.value),
                    })
                  }
                  className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                  placeholder="0"
                />
              </div>

              {/* Steps */}
              <div>
                <div className="flex items-center justify-between mb-2">
                  <label className="text-sm font-medium text-ink">
                    Étapes d'approbation
                  </label>
                  <button
                    type="button"
                    onClick={addStep}
                    className="flex items-center gap-1 text-xs font-medium text-accent hover:text-accent-strong transition-colors"
                  >
                    <Plus size={14} />
                    Ajouter
                  </button>
                </div>
                <div className="space-y-3">
                  {form.steps.map((step, i) => (
                    <div
                      key={i}
                      className="flex items-start gap-2 p-3 bg-bg rounded-none border border-line"
                    >
                      <div className="flex-1 space-y-2">
                        <input
                          type="text"
                          value={step.name}
                          onChange={(e) => updateStep(i, 'name', e.target.value)}
                          className="w-full px-2 py-1.5 border border-line rounded-none text-sm focus:ring-2 focus:ring-accent focus:border-transparent"
                          placeholder={`Étape ${i + 1}`}
                        />
                        <div className="flex items-center gap-2">
                          <select
                            value={step.approverRole}
                            onChange={(e) =>
                              updateStep(i, 'approverRole', e.target.value)
                            }
                            className="flex-1 px-2 py-1.5 border border-line rounded-none text-sm focus:ring-2 focus:ring-accent focus:border-transparent"
                          >
                            <option value="ADMIN">Administrateur</option>
                            <option value="MANAGER">Manager</option>
                            <option value="USER">Utilisateur</option>
                          </select>
                          <label className="flex items-center gap-1.5 text-xs text-ink-soft shrink-0">
                            <input
                              type="checkbox"
                              checked={step.required}
                              onChange={(e) =>
                                updateStep(i, 'required', e.target.checked)
                              }
                              className="rounded"
                            />
                            Requis
                          </label>
                        </div>
                      </div>
                      {form.steps.length > 1 && (
                        <button
                          type="button"
                          onClick={() => removeStep(i)}
                          className="p-1 rounded text-ink-soft hover:text-danger hover:bg-danger/10 transition-colors mt-1"
                        >
                          <Trash2 size={14} />
                        </button>
                      )}
                    </div>
                  ))}
                </div>
              </div>

              <div className="flex gap-3 pt-2">
                <button
                  type="button"
                  onClick={() => setCreateOpen(false)}
                  className="flex-1 px-4 py-2 border border-line rounded-none text-sm font-medium text-ink hover:bg-bg transition-colors"
                >
                  Annuler
                </button>
                <button
                  type="submit"
                  disabled={createWorkflow.isPending}
                  className="flex-1 px-4 py-2 bg-accent text-white rounded-none text-sm font-medium hover:bg-accent-strong disabled:opacity-50 transition-colors flex items-center justify-center gap-2"
                >
                  {createWorkflow.isPending && (
                    <Loader2 size={14} className="animate-spin" />
                  )}
                  Créer le workflow
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default ApprovalWorkflows;
