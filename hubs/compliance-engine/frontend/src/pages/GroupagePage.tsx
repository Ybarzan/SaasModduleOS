import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Package,
  Boxes,
  Plus,
  Loader2,
  Trash2,
  ArrowRight,
  CheckCircle2,
  XCircle,
  Ship,
  Truck,
  Plane,
  Scale,
  Ruler,
  Users,
  CalendarDays,
} from 'lucide-react';
import { incokalkAPI } from '../lib/api';
import toast from 'react-hot-toast';

interface Groupage {
  id: string;
  reference: string;
  name: string;
  status: 'PLANNED' | 'FORMING' | 'BOOKED' | 'DEPARTED' | 'DELIVERED' | 'CANCELLED';
  transportMode?: string | null;
  carrierName?: string | null;
  origin?: string | null;
  destination?: string | null;
  capacityWeightKg?: number | null;
  capacityVolumeM3?: number | null;
  bookedWeightKg: number;
  bookedVolumeM3: number;
  plannedDeparture?: string | null;
  plannedArrival?: string | null;
  createdAt: string;
}

interface GroupageMember {
  id: string;
  groupageId: string;
  shipmentOrderId?: string | null;
  externalCompany?: string | null;
  reference?: string | null;
  weightKg: number;
  volumeM3: number;
  createdAt: string;
}

interface GroupageDetail extends Groupage {
  memberCount: number;
  weightUtilizationPct: number;
  volumeUtilizationPct: number;
  members: GroupageMember[];
}

const STATUS_CONFIG: Record<string, { label: string; color: string }> = {
  PLANNED: { label: 'Planifié', color: 'bg-surface-2 text-ink' },
  FORMING: { label: 'En constitution', color: 'bg-accent-soft text-accent-strong' },
  BOOKED: { label: 'Réservé', color: 'bg-warning/10 text-warning' },
  DEPARTED: { label: 'Départ', color: 'bg-accent-soft text-accent-strong' },
  DELIVERED: { label: 'Livré', color: 'bg-success/10 text-success' },
  CANCELLED: { label: 'Annulé', color: 'bg-danger/10 text-danger' },
};

const STATUS_FLOW: Groupage['status'][] = ['PLANNED', 'FORMING', 'BOOKED', 'DEPARTED', 'DELIVERED'];

const MODE_ICON: Record<string, typeof Ship> = {
  SEA: Ship,
  AIR: Plane,
  ROAD: Truck,
};

const formatDate = (value?: string | null) =>
  value ? new Date(value).toLocaleDateString('fr-FR') : '—';

const GroupagePage = () => {
  const queryClient = useQueryClient();
  const [showCreate, setShowCreate] = useState(false);
  const [detailId, setDetailId] = useState<string | null>(null);
  const [memberForm, setMemberForm] = useState({ shipmentOrderId: '', externalCompany: '', reference: '', weightKg: '', volumeM3: '' });

  const { data: groupages = [], isLoading } = useQuery<Groupage[]>({
    queryKey: ['groupages'],
    queryFn: async () => (await incokalkAPI.groupages.list()).data,
  });

  const { data: stats } = useQuery<Record<string, number>>({
    queryKey: ['groupages-stats'],
    queryFn: async () => (await incokalkAPI.groupages.stats()).data,
  });

  const { data: detail, refetch: refetchDetail } = useQuery<GroupageDetail>({
    queryKey: ['groupage-detail', detailId],
    queryFn: async () => {
      if (!detailId) throw new Error('no detail');
      return (await incokalkAPI.groupages.detail(detailId)).data;
    },
    enabled: !!detailId,
  });

  const createMutation = useMutation({
    mutationFn: (data: Parameters<typeof incokalkAPI.groupages.create>[0]) =>
      incokalkAPI.groupages.create(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['groupages'] });
      queryClient.invalidateQueries({ queryKey: ['groupages-stats'] });
      setShowCreate(false);
      toast.success('Groupage créé');
    },
    onError: () => toast.error("Erreur lors de la création"),
  });

  const statusMutation = useMutation({
    mutationFn: ({ id, status }: { id: string; status: Groupage['status'] }) =>
      incokalkAPI.groupages.updateStatus(id, status),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['groupages'] });
      queryClient.invalidateQueries({ queryKey: ['groupage-detail', detailId] });
      toast.success('Statut mis à jour');
    },
    onError: () => toast.error('Transition de statut impossible'),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => incokalkAPI.groupages.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['groupages'] });
      queryClient.invalidateQueries({ queryKey: ['groupages-stats'] });
      toast.success('Groupage supprimé');
    },
    onError: () => toast.error('Suppression impossible (statut engagé)'),
  });

  const addMemberMutation = useMutation({
    mutationFn: (data: Parameters<typeof incokalkAPI.groupages.addMember>[1]) =>
      incokalkAPI.groupages.addMember(detailId!, data),
    onSuccess: () => {
      refetchDetail();
      queryClient.invalidateQueries({ queryKey: ['groupages'] });
      setMemberForm({ shipmentOrderId: '', externalCompany: '', reference: '', weightKg: '', volumeM3: '' });
      toast.success('Expédition ajoutée au groupage');
    },
    onError: () => toast.error('Capacité dépassée ou données invalides'),
  });

  const removeMemberMutation = useMutation({
    mutationFn: (memberId: string) => incokalkAPI.groupages.removeMember(detailId!, memberId),
    onSuccess: () => {
      refetchDetail();
      queryClient.invalidateQueries({ queryKey: ['groupages'] });
      toast.success('Expédition retirée');
    },
    onError: () => toast.error('Retrait impossible'),
  });

  const submitMember = (e: React.FormEvent) => {
    e.preventDefault();
    if (!memberForm.weightKg || !memberForm.volumeM3) return;
    addMemberMutation.mutate({
      shipmentOrderId: memberForm.shipmentOrderId || undefined,
      externalCompany: memberForm.externalCompany || undefined,
      reference: memberForm.reference || undefined,
      weightKg: Number(memberForm.weightKg),
      volumeM3: Number(memberForm.volumeM3),
    });
  };

  const nextStatus = (current: Groupage['status']): Groupage['status'] | null => {
    if (current === 'CANCELLED') return null;
    const idx = STATUS_FLOW.indexOf(current);
    return idx >= 0 && idx < STATUS_FLOW.length - 1 ? STATUS_FLOW[idx + 1] : null;
  };

  const utilization = (booked: number, capacity?: number | null) =>
    capacity && capacity > 0 ? Math.min((booked / capacity) * 100, 100) : 0;

  return (
    <div className="max-w-7xl mx-auto px-4 py-8">
      {/* Header */}
      <div className="flex flex-wrap items-start justify-between gap-4 mb-8">
        <div>
          <h1 className="text-2xl font-bold text-ink flex items-center gap-2">
            <Boxes size={24} className="text-accent" />
            <span className="text-accent font-normal" aria-hidden="true">:: </span>
            Groupage & Co-loading
          </h1>
          <p className="text-ink-soft mt-1">Consolidez plusieurs exportateurs sur un même transport pour mutualiser les coûts.</p>
        </div>
        <button
          onClick={() => setShowCreate(true)}
          className="flex items-center gap-2 px-4 py-2.5 bg-accent text-white text-sm font-medium rounded-none hover:bg-accent-strong transition-colors"
        >
          <Plus size={16} />
          Nouveau groupage
        </button>
      </div>

      {/* Stats */}
      {stats && (
        <div className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-7 gap-3 mb-8">
          {(['total', 'PLANNED', 'FORMING', 'BOOKED', 'DEPARTED', 'DELIVERED', 'CANCELLED'] as const).map((key) => (
            <div key={key} className="bg-surface rounded-none border border-line p-4">
              <p className="text-xs text-ink-soft uppercase tracking-wider">
                {key === 'total' ? 'Total' : (STATUS_CONFIG[key]?.label ?? key)}
              </p>
              <p className="text-2xl font-bold text-ink mt-1">{stats[key] ?? 0}</p>
            </div>
          ))}
        </div>
      )}

      {/* List */}
      {isLoading ? (
        <div className="flex items-center justify-center py-16 text-ink-soft">
          <Loader2 size={24} className="animate-spin mr-2" /> Chargement...
        </div>
      ) : groupages.length === 0 ? (
        <div className="bg-surface rounded-none border border-line p-16 text-center text-ink-soft">
          <Boxes size={40} className="mx-auto mb-3 text-ink-soft" />
          <p>Aucun groupage. Créez un premier groupage pour consolider vos expéditions.</p>
        </div>
      ) : (
        <div className="grid gap-4">
          {groupages.map((g) => {
            const cfg = STATUS_CONFIG[g.status];
            const ModeIcon = g.transportMode ? MODE_ICON[g.transportMode] : Truck;
            const wPct = utilization(g.bookedWeightKg, g.capacityWeightKg);
            const vPct = utilization(g.bookedVolumeM3, g.capacityVolumeM3);
            return (
              <div key={g.id} className="bg-surface rounded-none border border-line p-5 hover:shadow-sm transition-shadow">
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <div className="flex items-center gap-3">
                    <div className="w-11 h-11 rounded-none bg-accent-soft flex items-center justify-center">
                      <ModeIcon size={22} className="text-accent" />
                    </div>
                    <div>
                      <div className="flex items-center gap-2">
                        <span className="font-semibold text-ink">{g.name}</span>
                        <span className="text-xs text-ink-soft">{g.reference}</span>
                        <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${cfg.color}`}>
                          {cfg.label}
                        </span>
                      </div>
                      <div className="flex items-center gap-2 text-xs text-ink-soft mt-0.5">
                        <span>{g.origin || '—'}</span>
                        <ArrowRight size={12} />
                        <span>{g.destination || '—'}</span>
                        {g.carrierName && <span className="text-ink-soft">· {g.carrierName}</span>}
                        <span className="text-ink-soft">· {formatDate(g.plannedDeparture)}</span>
                      </div>
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => setDetailId(g.id)}
                      className="px-3 py-1.5 text-xs font-medium text-accent bg-accent-soft rounded-none hover:bg-accent-soft transition-colors"
                    >
                      Détail & membres
                    </button>
                    {nextStatus(g.status) && (
                      <button
                        onClick={() => statusMutation.mutate({ id: g.id, status: nextStatus(g.status)! })}
                        disabled={statusMutation.isPending}
                        className="px-3 py-1.5 text-xs font-medium text-white bg-ink rounded-none hover:bg-ink disabled:opacity-50 transition-colors"
                      >
                        {STATUS_CONFIG[nextStatus(g.status)!].label}
                      </button>
                    )}
                    <button
                      onClick={() => {
                        if (window.confirm(`Supprimer le groupage ${g.reference} ?`)) deleteMutation.mutate(g.id);
                      }}
                      className="p-2 text-ink-soft hover:text-danger rounded-none hover:bg-danger/10 transition-colors"
                      title="Supprimer"
                    >
                      <Trash2 size={16} />
                    </button>
                  </div>
                </div>
                {/* Utilization */}
                <div className="grid sm:grid-cols-2 gap-4 mt-4">
                  <div>
                    <div className="flex justify-between text-xs text-ink-soft mb-1">
                      <span className="flex items-center gap-1"><Scale size={12} /> Poids {g.bookedWeightKg} / {g.capacityWeightKg ?? '∞'} kg</span>
                      <span>{wPct.toFixed(0)}%</span>
                    </div>
                    <div className="h-2 bg-surface-2 rounded-full overflow-hidden">
                      <div className="h-full bg-accent rounded-full" style={{ width: `${wPct}%` }} />
                    </div>
                  </div>
                  <div>
                    <div className="flex justify-between text-xs text-ink-soft mb-1">
                      <span className="flex items-center gap-1"><Ruler size={12} /> Volume {g.bookedVolumeM3} / {g.capacityVolumeM3 ?? '∞'} m³</span>
                      <span>{vPct.toFixed(0)}%</span>
                    </div>
                    <div className="h-2 bg-surface-2 rounded-full overflow-hidden">
                      <div className="h-full bg-accent rounded-full" style={{ width: `${vPct}%` }} />
                    </div>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Create modal */}
      {showCreate && (
        <CreateGroupageModal
          onClose={() => setShowCreate(false)}
          onSubmit={(data) => createMutation.mutate(data)}
          pending={createMutation.isPending}
        />
      )}

      {/* Detail modal */}
      {detailId && detail && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50" onClick={() => setDetailId(null)}>
          <div className="bg-surface rounded-none shadow-xl w-full max-w-3xl max-h-[90vh] overflow-y-auto" onClick={(e) => e.stopPropagation()}>
            <div className="flex items-center justify-between px-6 py-4 border-b border-line">
              <div className="flex items-center gap-3">
                <h2 className="text-lg font-bold text-ink">{detail.name}</h2>
                <span className="text-xs text-ink-soft">{detail.reference}</span>
                <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${STATUS_CONFIG[detail.status].color}`}>
                  {STATUS_CONFIG[detail.status].label}
                </span>
              </div>
              <button onClick={() => setDetailId(null)} className="text-ink-soft hover:text-ink-soft"><XCircle size={22} /></button>
            </div>

            <div className="px-6 py-4 space-y-6">
              {/* Capacity summary */}
              <div className="grid grid-cols-3 gap-3">
                <div className="bg-bg rounded-none p-4">
                  <p className="text-xs text-ink-soft flex items-center gap-1"><Scale size={12} /> Poids</p>
                  <p className="text-xl font-bold text-ink mt-1">{detail.bookedWeightKg} / {detail.capacityWeightKg ?? '∞'} kg</p>
                  <div className="h-1.5 bg-surface-2 rounded-full mt-2 overflow-hidden">
                    <div className="h-full bg-accent rounded-full" style={{ width: `${detail.weightUtilizationPct}%` }} />
                  </div>
                </div>
                <div className="bg-bg rounded-none p-4">
                  <p className="text-xs text-ink-soft flex items-center gap-1"><Ruler size={12} /> Volume</p>
                  <p className="text-xl font-bold text-ink mt-1">{detail.bookedVolumeM3} / {detail.capacityVolumeM3 ?? '∞'} m³</p>
                  <div className="h-1.5 bg-surface-2 rounded-full mt-2 overflow-hidden">
                    <div className="h-full bg-accent rounded-full" style={{ width: `${detail.volumeUtilizationPct}%` }} />
                  </div>
                </div>
                <div className="bg-bg rounded-none p-4">
                  <p className="text-xs text-ink-soft flex items-center gap-1"><Users size={12} /> Expéditions</p>
                  <p className="text-xl font-bold text-ink mt-1">{detail.memberCount}</p>
                  <p className="text-xs text-ink-soft mt-2 flex items-center gap-1">
                    <CalendarDays size={12} /> {formatDate(detail.plannedDeparture)}
                  </p>
                </div>
              </div>

              {/* Members */}
              <div>
                <h3 className="text-sm font-semibold text-ink mb-3">Expéditions consolidées</h3>
                {detail.members.length === 0 ? (
                  <p className="text-sm text-ink-soft py-6 text-center bg-bg rounded-none">Aucune expédition dans ce groupage</p>
                ) : (
                  <div className="overflow-x-auto rounded-none border border-line">
                    <table className="w-full text-sm">
                      <thead>
                        <tr className="bg-bg border-b border-line">
                          <th className="text-left px-4 py-2.5 text-xs font-semibold text-ink-soft uppercase">Référence</th>
                          <th className="text-left px-4 py-2.5 text-xs font-semibold text-ink-soft uppercase">Exportateur</th>
                          <th className="text-right px-4 py-2.5 text-xs font-semibold text-ink-soft uppercase">Poids</th>
                          <th className="text-right px-4 py-2.5 text-xs font-semibold text-ink-soft uppercase">Volume</th>
                          <th className="text-center px-4 py-2.5 text-xs font-semibold text-ink-soft uppercase">Action</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-line">
                        {detail.members.map((m) => (
                          <tr key={m.id}>
                            <td className="px-4 py-3 font-medium text-ink">{m.reference || '—'}</td>
                            <td className="px-4 py-3 text-ink-soft">{m.externalCompany || 'Interne'}</td>
                            <td className="px-4 py-3 text-right text-ink">{m.weightKg} kg</td>
                            <td className="px-4 py-3 text-right text-ink">{m.volumeM3} m³</td>
                            <td className="px-4 py-3 text-center">
                              <button
                                onClick={() => removeMemberMutation.mutate(m.id)}
                                disabled={removeMemberMutation.isPending}
                                className="p-1.5 text-ink-soft hover:text-danger rounded-none hover:bg-danger/10 transition-colors"
                                title="Retirer"
                              >
                                <Trash2 size={15} />
                              </button>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>

              {/* Add member */}
              <form onSubmit={submitMember} className="bg-accent-soft/50 rounded-none p-4 border border-accent/40">
                <h3 className="text-sm font-semibold text-ink mb-3 flex items-center gap-2">
                  <Package size={15} className="text-accent" /> Ajouter une expédition
                </h3>
                <div className="grid grid-cols-1 sm:grid-cols-5 gap-3">
                  <input value={memberForm.reference} onChange={(e) => setMemberForm({ ...memberForm, reference: e.target.value })}
                    placeholder="Référence" className="px-3 py-2 border border-line rounded-none text-sm bg-surface" />
                  <input value={memberForm.externalCompany} onChange={(e) => setMemberForm({ ...memberForm, externalCompany: e.target.value })}
                    placeholder="Exportateur externe" className="px-3 py-2 border border-line rounded-none text-sm bg-surface" />
                  <input value={memberForm.weightKg} onChange={(e) => setMemberForm({ ...memberForm, weightKg: e.target.value })}
                    placeholder="Poids (kg)" type="number" min="0" required className="px-3 py-2 border border-line rounded-none text-sm bg-surface" />
                  <input value={memberForm.volumeM3} onChange={(e) => setMemberForm({ ...memberForm, volumeM3: e.target.value })}
                    placeholder="Volume (m³)" type="number" min="0" step="0.01" required className="px-3 py-2 border border-line rounded-none text-sm bg-surface" />
                  <button type="submit" disabled={addMemberMutation.isPending}
                    className="flex items-center justify-center gap-1.5 px-3 py-2 bg-accent text-white text-sm font-medium rounded-none hover:bg-accent-strong disabled:opacity-50 transition-colors">
                    {addMemberMutation.isPending ? <Loader2 size={14} className="animate-spin" /> : <CheckCircle2 size={14} />}
                    Ajouter
                  </button>
                </div>
              </form>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

const CreateGroupageModal = ({
  onClose,
  onSubmit,
  pending,
}: {
  onClose: () => void;
  onSubmit: (data: Parameters<typeof incokalkAPI.groupages.create>[0]) => void;
  pending: boolean;
}) => {
  const [form, setForm] = useState({
    name: '', transportMode: 'ROAD', carrierName: '', origin: '', destination: '',
    capacityWeightKg: '', capacityVolumeM3: '', plannedDeparture: '', plannedArrival: '',
  });

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.name) return;
    onSubmit({
      name: form.name,
      transportMode: form.transportMode,
      carrierName: form.carrierName || undefined,
      origin: form.origin || undefined,
      destination: form.destination || undefined,
      capacityWeightKg: form.capacityWeightKg ? Number(form.capacityWeightKg) : undefined,
      capacityVolumeM3: form.capacityVolumeM3 ? Number(form.capacityVolumeM3) : undefined,
      plannedDeparture: form.plannedDeparture || undefined,
      plannedArrival: form.plannedArrival || undefined,
    });
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50" onClick={onClose}>
      <form onSubmit={submit} className="bg-surface rounded-none shadow-xl w-full max-w-lg p-6" onClick={(e) => e.stopPropagation()}>
        <h2 className="text-lg font-bold text-ink mb-4">Nouveau groupage</h2>
        <div className="space-y-3">
          <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })}
            placeholder="Nom du groupage *" required className="w-full px-3 py-2.5 border border-line rounded-none text-sm" />
          <div className="grid grid-cols-3 gap-2">
            {(['ROAD', 'SEA', 'AIR'] as const).map((mode) => {
              const Icon = MODE_ICON[mode];
              return (
                <button key={mode} type="button" onClick={() => setForm({ ...form, transportMode: mode })}
                  className={`flex items-center justify-center gap-1.5 py-2.5 rounded-none text-sm font-medium transition-colors ${
                    form.transportMode === mode ? 'bg-accent text-white' : 'bg-surface-2 text-ink-soft hover:bg-surface-2'
                  }`}>
                  <Icon size={15} /> {mode === 'SEA' ? 'Maritime' : mode === 'AIR' ? 'Aérien' : 'Routier'}
                </button>
              );
            })}
          </div>
          <div className="grid grid-cols-2 gap-3">
            <input value={form.origin} onChange={(e) => setForm({ ...form, origin: e.target.value })}
              placeholder="Origine" className="px-3 py-2.5 border border-line rounded-none text-sm" />
            <input value={form.destination} onChange={(e) => setForm({ ...form, destination: e.target.value })}
              placeholder="Destination" className="px-3 py-2.5 border border-line rounded-none text-sm" />
          </div>
          <input value={form.carrierName} onChange={(e) => setForm({ ...form, carrierName: e.target.value })}
            placeholder="Transporteur" className="w-full px-3 py-2.5 border border-line rounded-none text-sm" />
          <div className="grid grid-cols-2 gap-3">
            <input value={form.capacityWeightKg} onChange={(e) => setForm({ ...form, capacityWeightKg: e.target.value })}
              placeholder="Capacité poids (kg)" type="number" min="0" className="px-3 py-2.5 border border-line rounded-none text-sm" />
            <input value={form.capacityVolumeM3} onChange={(e) => setForm({ ...form, capacityVolumeM3: e.target.value })}
              placeholder="Capacité volume (m³)" type="number" min="0" step="0.01" className="px-3 py-2.5 border border-line rounded-none text-sm" />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <input value={form.plannedDeparture} onChange={(e) => setForm({ ...form, plannedDeparture: e.target.value })}
              type="date" className="px-3 py-2.5 border border-line rounded-none text-sm" />
            <input value={form.plannedArrival} onChange={(e) => setForm({ ...form, plannedArrival: e.target.value })}
              type="date" className="px-3 py-2.5 border border-line rounded-none text-sm" />
          </div>
        </div>
        <div className="flex justify-end gap-3 mt-6">
          <button type="button" onClick={onClose} className="px-4 py-2 text-sm font-medium text-ink-soft hover:bg-surface-2 rounded-none transition-colors">
            Annuler
          </button>
          <button type="submit" disabled={pending}
            className="flex items-center gap-2 px-4 py-2 bg-accent text-white text-sm font-medium rounded-none hover:bg-accent-strong disabled:opacity-50 transition-colors">
            {pending ? <Loader2 size={15} className="animate-spin" /> : <Plus size={15} />} Créer
          </button>
        </div>
      </form>
    </div>
  );
};

export default GroupagePage;
