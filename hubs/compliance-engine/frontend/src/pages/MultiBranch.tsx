import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Building2,
  GitBranch,
  Plus,
  Trash2,
  ArrowRightLeft,
  Package,
  BarChart3,
  Loader2,
} from 'lucide-react';
import { incokalkAPI } from '../lib/api';
import { useAuthStore } from '../stores/auth';

interface Branch {
  id: string;
  branchName: string;
  companyCode: string;
  status: string;
  companyId: string;
  parentCompanyId: string;
  createdAt: string;
}

interface ConsolidatedReport {
  totalRevenue: number;
  totalCost: number;
  totalMargin: number;
  branchCount: number;
  period: string;
}

interface Transfer {
  id: string;
  fromBranch: string;
  toBranch: string;
  goodsDescription: string;
  quantity: number;
  status: string;
  createdAt: string;
}

const formatEUR = (value: number) =>
  new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' }).format(value);

const MultiBranch = () => {
  const queryClient = useQueryClient();
  const canEdit = useAuthStore((s) => s.canEdit);

  const [branchName, setBranchName] = useState('');
  const [fromBranch, setFromBranch] = useState('');
  const [toBranch, setToBranch] = useState('');
  const [goodsDesc, setGoodsDesc] = useState('');
  const [quantity, setQuantity] = useState('');

  const { data: branches = [], isLoading: branchesLoading } = useQuery<Branch[]>({
    queryKey: ['branches'],
    queryFn: async () => {
      const res = await incokalkAPI.branches.list();
      return res.data;
    },
  });

  const { data: parent, isLoading: parentLoading } = useQuery<Branch>({
    queryKey: ['branches-parent'],
    queryFn: async () => {
      const res = await incokalkAPI.branches.parent();
      return res.data;
    },
  });

  const { data: consolidated, isLoading: consLoading, refetch: refetchConsolidated } = useQuery<ConsolidatedReport>({
    queryKey: ['branches-consolidated'],
    queryFn: async () => {
      const res = await incokalkAPI.branches.consolidatedReport();
      return res.data;
    },
    enabled: false,
  });

  const { data: transfers = [], isLoading: transfersLoading } = useQuery<Transfer[]>({
    queryKey: ['branches-transfers'],
    queryFn: async () => {
      const res = await incokalkAPI.branches.transfers();
      return res.data;
    },
  });

  const addBranch = useMutation({
    mutationFn: async (data: { branchCompanyId: string; branchName: string; parentCompanyId: string }) => {
      const res = await incokalkAPI.branches.add(data);
      return res.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['branches'] });
      setBranchName('');
    },
  });

  const removeBranch = useMutation({
    mutationFn: async (id: string) => {
      const res = await incokalkAPI.branches.remove(id);
      return res.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['branches'] });
    },
  });

  const createTransfer = useMutation({
    mutationFn: async (data: { fromBranch: string; toBranch: string; goodsDescription: string; quantity: number }) => {
      const res = await incokalkAPI.branches.createTransfer(data);
      return res.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['branches-transfers'] });
      setFromBranch('');
      setToBranch('');
      setGoodsDesc('');
      setQuantity('');
    },
  });

  const handleAddBranch = (e: React.FormEvent) => {
    e.preventDefault();
    if (!branchName || !parent?.companyId) return;
    addBranch.mutate({
      branchCompanyId: '',
      branchName,
      parentCompanyId: parent.companyId,
    });
  };

  const handleCreateTransfer = (e: React.FormEvent) => {
    e.preventDefault();
    if (!fromBranch || !toBranch || !goodsDesc || !quantity) return;
    createTransfer.mutate({
      fromBranch,
      toBranch,
      goodsDescription: goodsDesc,
      quantity: Number(quantity),
    });
  };

  const handleConsolidatedReport = () => {
    refetchConsolidated();
  };

  return (
    <div className="max-w-6xl mx-auto px-4 py-8">
      {/* Header */}
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-ink">
          <span className="text-accent font-normal" aria-hidden="true">:: </span>
          Multi-Branche
        </h1>
        <p className="text-ink-soft mt-1">Gestion des filiales et consolidation P4.23</p>
      </div>

      {/* Parent company info */}
      {parentLoading ? (
        <div className="flex items-center justify-center py-6 text-ink-soft">
          <Loader2 size={24} className="animate-spin mr-2" />
          Chargement...
        </div>
      ) : parent ? (
        <div className="bg-surface rounded-none border border-line p-5 mb-6">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-none bg-accent-soft flex items-center justify-center">
              <Building2 size={20} className="text-accent" />
            </div>
            <div>
              <p className="text-sm text-ink-soft">Société mère</p>
              <p className="text-lg font-bold text-ink">{parent.branchName || 'N/A'}</p>
              <p className="text-xs text-ink-soft">{parent.companyId}</p>
            </div>
          </div>
        </div>
      ) : null}

      {/* Branches section */}
      <div className="bg-surface rounded-none border border-line overflow-hidden mb-6">
        <div className="px-6 py-4 border-b border-line flex items-center justify-between">
          <h2 className="text-lg font-semibold text-ink">Filiales</h2>
          {canEdit() && (
            <form onSubmit={handleAddBranch} className="flex items-center gap-2">
              <input
                type="text"
                value={branchName}
                onChange={(e) => setBranchName(e.target.value)}
                className="px-3 py-2 border border-line rounded-none text-sm focus:outline-none focus:ring-2 focus:ring-accent"
                placeholder="Nom de la filiale"
                required
              />
              <button
                type="submit"
                disabled={addBranch.isPending}
                className="flex items-center gap-1 px-3 py-2 bg-accent text-white text-sm font-medium rounded-none hover:bg-accent-strong disabled:opacity-50 transition-colors"
              >
                <Plus size={16} />
                Ajouter
              </button>
            </form>
          )}
        </div>
        {branchesLoading ? (
          <div className="flex items-center justify-center py-12 text-ink-soft">
            <Loader2 size={24} className="animate-spin mr-2" />
            Chargement...
          </div>
        ) : branches.length === 0 ? (
          <div className="px-6 py-12 text-center text-ink-soft">
            <GitBranch size={32} className="mx-auto mb-3 text-ink-soft" />
            <p>Aucune filiale</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="bg-surface-2 border-b border-line">
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Nom</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Code</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Statut</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Créée le</th>
                  <th className="text-center text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {branches.map((b) => (
                  <tr key={b.id} className="hover:bg-surface-2 transition-colors">
                    <td className="px-6 py-4 text-sm font-medium text-ink">{b.branchName}</td>
                    <td className="px-6 py-4 text-sm text-ink-soft">{b.companyCode || '-'}</td>
                    <td className="px-6 py-4">
                      <span className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-medium ${
                        b.status === 'ACTIVE' ? 'bg-success/10 text-success' : 'bg-surface-2 text-ink-soft'
                      }`}>
                        {b.status}
                      </span>
                    </td>
                    <td className="px-6 py-4 text-sm text-ink-soft">
                      {new Date(b.createdAt).toLocaleDateString('fr-FR')}
                    </td>
                    <td className="px-6 py-4 text-center">
                      {canEdit() && (
                        <button
                          onClick={() => removeBranch.mutate(b.id)}
                          disabled={removeBranch.isPending}
                          className="p-2 text-danger hover:bg-danger/10 rounded-none transition-colors"
                          title="Supprimer"
                        >
                          <Trash2 size={16} />
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Consolidated report */}
      <div className="bg-surface rounded-none border border-line p-6 mb-6">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-semibold text-ink">Rapport consolidé</h2>
          <button
            onClick={handleConsolidatedReport}
            disabled={consLoading}
            className="flex items-center gap-2 px-4 py-2 bg-accent text-white text-sm font-medium rounded-none hover:bg-accent-strong disabled:opacity-50 transition-colors"
          >
            {consLoading ? <Loader2 size={16} className="animate-spin" /> : <BarChart3 size={16} />}
            Générer
          </button>
        </div>
        {consLoading && (
          <div className="flex items-center justify-center py-6 text-ink-soft">
            <Loader2 size={24} className="animate-spin mr-2" />
            Chargement...
          </div>
        )}
        {consolidated && !consLoading && (
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
            <div className="p-4 bg-surface-2 rounded-none">
              <p className="text-sm text-ink-soft">Revenu total</p>
              <p className="text-lg font-bold text-ink">{formatEUR(consolidated.totalRevenue)}</p>
            </div>
            <div className="p-4 bg-surface-2 rounded-none">
              <p className="text-sm text-ink-soft">Coûts totaux</p>
              <p className="text-lg font-bold text-ink">{formatEUR(consolidated.totalCost)}</p>
            </div>
            <div className="p-4 bg-surface-2 rounded-none">
              <p className="text-sm text-ink-soft">Marge totale</p>
              <p className="text-lg font-bold text-ink">{formatEUR(consolidated.totalMargin)}</p>
            </div>
            <div className="p-4 bg-surface-2 rounded-none">
              <p className="text-sm text-ink-soft">Nb filiales</p>
              <p className="text-lg font-bold text-ink">{consolidated.branchCount}</p>
            </div>
          </div>
        )}
      </div>

      {/* Inter-branch transfers */}
      <div className="bg-surface rounded-none border border-line overflow-hidden">
        <div className="px-6 py-4 border-b border-line">
          <h2 className="text-lg font-semibold text-ink">Transferts inter-branches</h2>
        </div>

        {/* Create transfer form */}
        {canEdit() && (
          <form onSubmit={handleCreateTransfer} className="grid grid-cols-1 sm:grid-cols-5 gap-4 p-4 bg-surface-2 border-b border-line">
            <div>
              <label className="block text-xs font-medium text-ink-soft mb-1">De (branche)</label>
              <input
                type="text"
                value={fromBranch}
                onChange={(e) => setFromBranch(e.target.value)}
                className="w-full px-3 py-2 border border-line rounded-none text-sm focus:outline-none focus:ring-2 focus:ring-accent"
                placeholder="Branche source"
                required
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-ink-soft mb-1">Vers (branche)</label>
              <input
                type="text"
                value={toBranch}
                onChange={(e) => setToBranch(e.target.value)}
                className="w-full px-3 py-2 border border-line rounded-none text-sm focus:outline-none focus:ring-2 focus:ring-accent"
                placeholder="Branche destination"
                required
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-ink-soft mb-1">Marchandise</label>
              <input
                type="text"
                value={goodsDesc}
                onChange={(e) => setGoodsDesc(e.target.value)}
                className="w-full px-3 py-2 border border-line rounded-none text-sm focus:outline-none focus:ring-2 focus:ring-accent"
                placeholder="Description"
                required
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-ink-soft mb-1">Quantité</label>
              <input
                type="number"
                value={quantity}
                onChange={(e) => setQuantity(e.target.value)}
                className="w-full px-3 py-2 border border-line rounded-none text-sm focus:outline-none focus:ring-2 focus:ring-accent"
                placeholder="1"
                min={1}
                required
              />
            </div>
            <div className="flex items-end">
              <button
                type="submit"
                disabled={createTransfer.isPending}
                className="flex items-center gap-1 px-4 py-2 bg-success text-white text-sm font-medium rounded-none hover:bg-success/90 disabled:opacity-50 transition-colors"
              >
                <ArrowRightLeft size={16} />
                Transférer
              </button>
            </div>
          </form>
        )}

        {/* Transfer history */}
        {transfersLoading ? (
          <div className="flex items-center justify-center py-12 text-ink-soft">
            <Loader2 size={24} className="animate-spin mr-2" />
            Chargement...
          </div>
        ) : transfers.length === 0 ? (
          <div className="px-6 py-12 text-center text-ink-soft">
            <Package size={32} className="mx-auto mb-3 text-ink-soft" />
            <p>Aucun transfert</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="bg-surface-2 border-b border-line">
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">De</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Vers</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Marchandise</th>
                  <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Qté</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Statut</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Date</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {transfers.map((t) => (
                  <tr key={t.id} className="hover:bg-surface-2 transition-colors">
                    <td className="px-6 py-4 text-sm text-ink-soft">{t.fromBranch}</td>
                    <td className="px-6 py-4 text-sm text-ink-soft">{t.toBranch}</td>
                    <td className="px-6 py-4 text-sm font-medium text-ink">{t.goodsDescription}</td>
                    <td className="px-6 py-4 text-sm text-ink-soft text-right">{t.quantity}</td>
                    <td className="px-6 py-4">
                      <span className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-medium ${
                        t.status === 'COMPLETED' ? 'bg-success/10 text-success' :
                        t.status === 'IN_TRANSIT' ? 'bg-accent-soft text-accent-strong' : 'bg-warning/10 text-warning'
                      }`}>
                        {t.status}
                      </span>
                    </td>
                    <td className="px-6 py-4 text-sm text-ink-soft">
                      {new Date(t.createdAt).toLocaleDateString('fr-FR')}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
};

export default MultiBranch;
