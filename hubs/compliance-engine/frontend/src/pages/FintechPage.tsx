import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Building2,
  Plus,
  Loader2,
  Trash2,
  Plug,
  RefreshCw,
  Eye,
  XCircle,
  CheckCircle2,
  Landmark,
  ArrowLeftRight,
  Receipt,
} from 'lucide-react';
import { incokalkAPI } from '../lib/api';
import toast from 'react-hot-toast';

interface FintechConnection {
  id: string;
  provider: 'QONTO' | 'SPENDESK';
  name: string;
  apiKey?: string | null;
  apiSecret?: string | null;
  active: boolean;
  lastSyncAt?: string | null;
  createdAt: string;
}

interface FintechData {
  accounts?: Record<string, unknown>[];
  transactions?: Record<string, unknown>[];
  expenses?: Record<string, unknown>[];
  [key: string]: unknown;
}

const PROVIDER_META: Record<string, { label: string; color: string; desc: string }> = {
  QONTO: { label: 'Qonto', color: 'bg-accent-soft text-accent-strong', desc: 'Comptes professionnels, transactions et pièces comptables' },
  SPENDESK: { label: 'Spendesk', color: 'bg-accent/10 text-accent-strong', desc: 'Dépenses d\'entreprise, cartes et justificatifs' },
};

const formatDate = (value?: string | null) =>
  value ? new Date(value).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' }) : 'Jamais';

const FintechPage = () => {
  const queryClient = useQueryClient();
  const [showCreate, setShowCreate] = useState(false);
  const [dataFor, setDataFor] = useState<string | null>(null);
  const [testResults, setTestResults] = useState<Record<string, { ok: boolean; message: string }>>({});

  const { data: connections = [], isLoading } = useQuery<FintechConnection[]>({
    queryKey: ['fintech-connections'],
    queryFn: async () => (await incokalkAPI.fintech.listConnections()).data,
  });

  const { data: dataView } = useQuery<FintechData>({
    queryKey: ['fintech-data', dataFor],
    queryFn: async () => {
      if (!dataFor) throw new Error('no selection');
      return (await incokalkAPI.fintech.fetchData(dataFor)).data;
    },
    enabled: !!dataFor,
  });

  const createMutation = useMutation({
    mutationFn: (data: Parameters<typeof incokalkAPI.fintech.createConnection>[0]) =>
      incokalkAPI.fintech.createConnection(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['fintech-connections'] });
      setShowCreate(false);
      toast.success('Connexion fintech créée');
    },
    onError: () => toast.error('Erreur lors de la création'),
  });

  const testMutation = useMutation({
    mutationFn: (id: string) => incokalkAPI.fintech.testConnection(id),
    onSuccess: (res, id) => {
      const data = res.data as { ok?: boolean; message?: string };
      setTestResults((prev) => ({ ...prev, [id]: { ok: data?.ok ?? false, message: data?.message ?? 'Connexion réussie' } }));
      toast.success(data?.ok ? 'Connexion validée' : 'Échec du test');
    },
    onError: (_err, id) => {
      setTestResults((prev) => ({ ...prev, [id]: { ok: false, message: 'Erreur réseau' } }));
      toast.error('Test impossible');
    },
  });

  const syncMutation = useMutation({
    mutationFn: (id: string) => incokalkAPI.fintech.syncConnection(id),
    onSuccess: (res) => {
      const data = res.data as { message?: string };
      queryClient.invalidateQueries({ queryKey: ['fintech-connections'] });
      toast.success(data?.message ?? 'Synchronisation terminée');
    },
    onError: () => toast.error('Synchronisation impossible'),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => incokalkAPI.fintech.deleteConnection(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['fintech-connections'] });
      toast.success('Connexion supprimée');
    },
    onError: () => toast.error('Suppression impossible'),
  });

  const toggleMutation = useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) =>
      incokalkAPI.fintech.updateConnection(id, { active }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['fintech-connections'] });
    },
  });

  return (
    <div className="max-w-6xl mx-auto px-4 py-8">
      <div className="flex flex-wrap items-start justify-between gap-4 mb-8">
        <div>
          <h1 className="text-2xl font-bold text-ink flex items-center gap-2">
            <Building2 size={24} className="text-accent" />
            <span className="text-accent font-normal" aria-hidden="true">:: </span>
            Intégrations fintech
          </h1>
          <p className="text-ink-soft mt-1">Connectez Qonto ou Spendesk pour synchroniser comptes, transactions et dépenses.</p>
        </div>
        <button
          onClick={() => setShowCreate(true)}
          className="flex items-center gap-2 px-4 py-2.5 bg-accent text-white text-sm font-medium rounded-none hover:bg-accent-strong transition-colors"
        >
          <Plus size={16} />
          Connecter un fournisseur
        </button>
      </div>

      {isLoading ? (
        <div className="flex items-center justify-center py-16 text-ink-soft">
          <Loader2 size={24} className="animate-spin mr-2" /> Chargement...
        </div>
      ) : connections.length === 0 ? (
        <div className="bg-surface rounded-none border border-line p-16 text-center text-ink-soft">
          <Building2 size={40} className="mx-auto mb-3 text-ink-soft" />
          <p>Aucune connexion fintech. Connectez votre banque ou outil de dépenses.</p>
        </div>
      ) : (
        <div className="grid md:grid-cols-2 gap-4">
          {connections.map((conn) => {
            const meta = PROVIDER_META[conn.provider] ?? PROVIDER_META.QONTO;
            const test = testResults[conn.id];
            return (
              <div key={conn.id} className={`bg-surface rounded-none border p-5 ${conn.active ? 'border-line' : 'border-line opacity-70'}`}>
                <div className="flex items-start justify-between gap-3">
                  <div className="flex items-center gap-3">
                    <div className={`w-11 h-11 rounded-none ${meta.color} flex items-center justify-center`}>
                      <Landmark size={22} />
                    </div>
                    <div>
                      <div className="flex items-center gap-2">
                        <span className="font-semibold text-ink">{conn.name}</span>
                        <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${meta.color}`}>
                          {meta.label}
                        </span>
                      </div>
                      <p className="text-xs text-ink-soft mt-0.5">{meta.desc}</p>
                      <p className="text-xs text-ink-soft mt-0.5">Dernière synchro : {formatDate(conn.lastSyncAt)}</p>
                    </div>
                  </div>
                  <button
                    onClick={() => {
                      if (window.confirm(`Supprimer ${conn.name} ?`)) deleteMutation.mutate(conn.id);
                    }}
                    className="p-2 text-ink-soft hover:text-danger rounded-none hover:bg-danger/10 transition-colors"
                    title="Supprimer"
                  >
                    <Trash2 size={16} />
                  </button>
                </div>

                {test && (
                  <div className={`mt-3 text-xs flex items-center gap-2 rounded-none px-3 py-2 ${test.ok ? 'bg-success/10 text-success' : 'bg-danger/10 text-danger'}`}>
                    {test.ok ? <CheckCircle2 size={14} /> : <XCircle size={14} />} {test.message}
                  </div>
                )}

                <div className="flex flex-wrap items-center gap-2 mt-4">
                  <button
                    onClick={() => testMutation.mutate(conn.id)}
                    disabled={testMutation.isPending}
                    className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-ink bg-surface-2 rounded-none hover:bg-surface-2 disabled:opacity-50 transition-colors"
                  >
                    {testMutation.isPending ? <Loader2 size={13} className="animate-spin" /> : <Plug size={13} />} Tester
                  </button>
                  <button
                    onClick={() => syncMutation.mutate(conn.id)}
                    disabled={syncMutation.isPending}
                    className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-white bg-accent rounded-none hover:bg-accent-strong disabled:opacity-50 transition-colors"
                  >
                    {syncMutation.isPending ? <Loader2 size={13} className="animate-spin" /> : <RefreshCw size={13} />} Synchroniser
                  </button>
                  <button
                    onClick={() => setDataFor(conn.id)}
                    className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-accent bg-accent/10 rounded-none hover:bg-accent-soft transition-colors"
                  >
                    <Eye size={13} /> Données
                  </button>
                  <button
                    onClick={() => toggleMutation.mutate({ id: conn.id, active: !conn.active })}
                    className={`ml-auto px-3 py-1.5 text-xs font-medium rounded-none transition-colors ${conn.active ? 'text-ink-soft bg-surface-2 hover:bg-surface-2' : 'text-success bg-success/10 hover:bg-success/20'}`}
                  >
                    {conn.active ? 'Désactiver' : 'Activer'}
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Create modal */}
      {showCreate && (
        <CreateConnectionModal
          onClose={() => setShowCreate(false)}
          onSubmit={(data) => createMutation.mutate(data)}
          pending={createMutation.isPending}
        />
      )}

      {/* Data modal */}
      {dataFor && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50" onClick={() => setDataFor(null)}>
          <div className="bg-surface rounded-none shadow-xl w-full max-w-4xl max-h-[90vh] overflow-y-auto" onClick={(e) => e.stopPropagation()}>
            <div className="flex items-center justify-between px-6 py-4 border-b border-line">
              <h2 className="text-lg font-bold text-ink">Données synchronisées</h2>
              <button onClick={() => setDataFor(null)} className="text-ink-soft hover:text-ink-soft"><XCircle size={22} /></button>
            </div>
            <div className="px-6 py-4 space-y-6">
              <DataSection title="Comptes bancaires" icon={<Landmark size={15} />} rows={dataView?.accounts} />
              <DataSection title="Transactions" icon={<ArrowLeftRight size={15} />} rows={dataView?.transactions} />
              <DataSection title="Dépenses / pièces à traiter" icon={<Receipt size={15} />} rows={dataView?.expenses} />
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

const DataSection = ({ title, icon, rows }: { title: string; icon: React.ReactNode; rows?: Record<string, unknown>[] }) => {
  if (!rows || rows.length === 0) return null;
  const keys = Object.keys(rows[0] ?? {}).slice(0, 6);
  return (
    <div>
      <h3 className="text-sm font-semibold text-ink mb-2 flex items-center gap-2">{icon} {title} <span className="text-ink-soft font-normal">({rows.length})</span></h3>
      <div className="overflow-x-auto rounded-none border border-line">
        <table className="w-full text-sm">
          <thead>
            <tr className="bg-bg border-b border-line">
              {keys.map((k) => (
                <th key={k} className="text-left px-3 py-2 text-xs font-semibold text-ink-soft uppercase">{k}</th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-line">
            {rows.slice(0, 15).map((row, i) => (
              <tr key={i}>
                {keys.map((k) => (
                  <td key={k} className="px-3 py-2 text-ink whitespace-nowrap">
                    {typeof row[k] === 'object' && row[k] !== null ? JSON.stringify(row[k]) : String(row[k] ?? '—')}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};

const CreateConnectionModal = ({
  onClose,
  onSubmit,
  pending,
}: {
  onClose: () => void;
  onSubmit: (data: Parameters<typeof incokalkAPI.fintech.createConnection>[0]) => void;
  pending: boolean;
}) => {
  const [form, setForm] = useState({ provider: 'QONTO', name: '', apiKey: '', apiSecret: '' });

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.name) return;
    onSubmit({
      provider: form.provider,
      name: form.name,
      apiKey: form.apiKey || undefined,
      apiSecret: form.apiSecret || undefined,
    });
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50" onClick={onClose}>
      <form onSubmit={submit} className="bg-surface rounded-none shadow-xl w-full max-w-md p-6" onClick={(e) => e.stopPropagation()}>
        <h2 className="text-lg font-bold text-ink mb-4">Connecter un fournisseur</h2>
        <div className="space-y-3">
          <div className="grid grid-cols-2 gap-2">
            {(['QONTO', 'SPENDESK'] as const).map((p) => (
              <button key={p} type="button" onClick={() => setForm({ ...form, provider: p })}
                className={`py-2.5 rounded-none text-sm font-medium transition-colors ${
                  form.provider === p ? 'bg-accent text-white' : 'bg-surface-2 text-ink-soft hover:bg-surface-2'
                }`}>
                {PROVIDER_META[p].label}
              </button>
            ))}
          </div>
          <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })}
            placeholder="Nom de la connexion *" required className="w-full px-3 py-2.5 border border-line rounded-none text-sm" />
          <input value={form.apiKey} onChange={(e) => setForm({ ...form, apiKey: e.target.value })}
            placeholder={form.provider === 'QONTO' ? 'Clé API (slug:secret) ou login' : 'Clé API'}
            className="w-full px-3 py-2.5 border border-line rounded-none text-sm" />
          <input value={form.apiSecret} onChange={(e) => setForm({ ...form, apiSecret: e.target.value })}
            placeholder="Secret (optionnel)" className="w-full px-3 py-2.5 border border-line rounded-none text-sm" />
          <p className="text-xs text-ink-soft">
            {form.provider === 'QONTO'
              ? 'Format Qonto : "login:secretKey" ou "slug:secretKey".'
              : 'Format Spendesk : clé API Bearer.'}
          </p>
        </div>
        <div className="flex justify-end gap-3 mt-6">
          <button type="button" onClick={onClose} className="px-4 py-2 text-sm font-medium text-ink-soft hover:bg-surface-2 rounded-none transition-colors">
            Annuler
          </button>
          <button type="submit" disabled={pending}
            className="flex items-center gap-2 px-4 py-2 bg-accent text-white text-sm font-medium rounded-none hover:bg-accent-strong disabled:opacity-50 transition-colors">
            {pending ? <Loader2 size={15} className="animate-spin" /> : <Plus size={15} />} Connecter
          </button>
        </div>
      </form>
    </div>
  );
};

export default FintechPage;
