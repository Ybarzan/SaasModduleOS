import type { AxiosError } from 'axios';
import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { incokalkAPI } from '../lib/api';
import StatCard from '../components/StatCard';
import type { SharedLinkItem } from '../types';
import {
  Link2, Plus, Trash2, Copy, ExternalLink, Eye,
  X
} from 'lucide-react';

const SharedLinks = () => {
  const queryClient = useQueryClient();
  const [showCreate, setShowCreate] = useState(false);
  const [form, setForm] = useState({ shipmentId: '', label: '', expiresHours: '' });

  const { data: links = [], isLoading } = useQuery<SharedLinkItem[]>({
    queryKey: ['shared-links'],
    queryFn: async () => { const r = await incokalkAPI.sharedLinks.list(); return r.data; },
  });

  const { data: stats } = useQuery<{ totalLinks: number; activeLinks: number; totalAccesses: number }>({
    queryKey: ['shared-link-stats'],
    queryFn: async () => { const r = await incokalkAPI.sharedLinks.stats(); return r.data; },
  });

  const createMut = useMutation({
    mutationFn: (data: { shipmentId: string; label?: string; expiresHours?: number }) =>
      incokalkAPI.sharedLinks.create(data),
    onSuccess: (res) => {
      queryClient.invalidateQueries({ queryKey: ['shared-links'] });
      queryClient.invalidateQueries({ queryKey: ['shared-link-stats'] });
      toast.success('Lien créé');
      const url = `${window.location.origin}${res.data.url}`;
      navigator.clipboard.writeText(url).then(() => toast.success('Lien copié !'));
      setShowCreate(false);
      setForm({ shipmentId: '', label: '', expiresHours: '' });
    },
    onError: (e: AxiosError<{ message?: string }>) => toast.error(e.response?.data?.message || 'Erreur'),
  });

  const revokeMut = useMutation({
    mutationFn: (id: string) => incokalkAPI.sharedLinks.revoke(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['shared-links'] });
      toast.success('Lien révoqué');
    },
  });

  const copyLink = (url: string) => {
    navigator.clipboard.writeText(`${window.location.origin}${url}`);
    toast.success('Lien copié !');
  };

  return (
    <div className="max-w-5xl mx-auto px-4 py-8">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-ink flex items-center gap-2">
            <Link2 className="w-6 h-6 text-accent" />
            <span className="text-accent font-normal" aria-hidden="true">:: </span>
            Liens de suivi
          </h1>
          <p className="text-sm text-ink-soft mt-1">
            Créez des liens partagés pour que vos clients suivent leurs expéditions
          </p>
        </div>
        <button onClick={() => setShowCreate(true)} className="btn-primary flex items-center gap-2">
          <Plus className="w-4 h-4" /> Nouveau lien
        </button>
      </div>

      {/* Stats */}
      {stats && (
        <div className="grid grid-cols-3 gap-4 mb-6">
          <StatCard label="Total liens" value={stats.totalLinks} />
          <StatCard label="Actifs" value={<span className="text-success">{stats.activeLinks}</span>} />
          <StatCard label="Consultations" value={<span className="text-accent">{stats.totalAccesses}</span>} />
        </div>
      )}

      {/* Create Modal */}
      {showCreate && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
          <div className="bg-surface rounded-none max-w-md w-full p-6">
            <div className="flex items-center justify-between mb-4">
              <h3 className="font-bold text-ink">Nouveau lien de suivi</h3>
              <button onClick={() => setShowCreate(false)}><X className="w-5 h-5 text-ink-soft" /></button>
            </div>
            <div className="space-y-3">
              <input placeholder="ID de l'expédition (UUID)" value={form.shipmentId}
                onChange={(e) => setForm({ ...form, shipmentId: e.target.value })}
                className="w-full px-3 py-2 border border-line rounded-none text-sm font-mono" />
              <input placeholder="Label (optionnel)" value={form.label}
                onChange={(e) => setForm({ ...form, label: e.target.value })}
                className="w-full px-3 py-2 border border-line rounded-none text-sm" />
              <input placeholder="Expiration en heures (optionnel, vide = pas d'expiration)" type="number" value={form.expiresHours}
                onChange={(e) => setForm({ ...form, expiresHours: e.target.value })}
                className="w-full px-3 py-2 border border-line rounded-none text-sm" />
              <button onClick={() => {
                if (!form.shipmentId.trim()) { toast.error("ID d'expédition requis"); return; }
                createMut.mutate({
                  shipmentId: form.shipmentId.trim(),
                  label: form.label.trim() || undefined,
                  expiresHours: form.expiresHours ? parseInt(form.expiresHours) : undefined,
                });
              }} disabled={createMut.isPending || !form.shipmentId.trim()}
                className="w-full btn-primary py-2.5 text-sm">
                {createMut.isPending ? 'Création...' : 'Créer le lien'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Links Table */}
      <div className="bg-surface rounded-none border border-line overflow-hidden">
        {isLoading ? (
          <p className="text-center py-12 text-ink-soft">Chargement...</p>
        ) : links.length === 0 ? (
          <div className="text-center py-12">
            <Link2 className="w-12 h-12 text-ink-soft mx-auto mb-3" />
            <p className="text-ink-soft">Aucun lien partagé</p>
          </div>
        ) : (
          <table className="w-full">
            <thead className="bg-surface-2 border-b border-line">
              <tr>
                <th className="text-left px-4 py-3 text-xs font-semibold text-ink-soft uppercase">Label</th>
                <th className="text-left px-4 py-3 text-xs font-semibold text-ink-soft uppercase">Expédition</th>
                <th className="text-left px-4 py-3 text-xs font-semibold text-ink-soft uppercase">Accès</th>
                <th className="text-left px-4 py-3 text-xs font-semibold text-ink-soft uppercase">Expiration</th>
                <th className="text-left px-4 py-3 text-xs font-semibold text-ink-soft uppercase">Statut</th>
                <th className="text-left px-4 py-3 text-xs font-semibold text-ink-soft uppercase">Créé le</th>
                <th className="text-right px-4 py-3 text-xs font-semibold text-ink-soft uppercase">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {links.map((link) => (
                <tr key={link.id} className="hover:bg-surface-2 transition">
                  <td className="px-4 py-3">
                    <p className="text-sm font-medium text-ink">{link.label || '—'}</p>
                  </td>
                  <td className="px-4 py-3">
                    <p className="text-sm font-mono text-ink-soft">{link.orderNumber}</p>
                  </td>
                  <td className="px-4 py-3 text-sm text-ink-soft">
                    <span className="flex items-center gap-1"><Eye className="w-3 h-3" /> {link.accessCount}</span>
                  </td>
                  <td className="px-4 py-3 text-sm text-ink-soft">
                    {link.expiresAt ? new Date(link.expiresAt).toLocaleDateString('fr-FR') : 'Jamais'}
                  </td>
                  <td className="px-4 py-3">
                    <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${link.active ? 'bg-success/15 text-success' : 'bg-danger/15 text-danger'}`}>
                      {link.active ? 'Actif' : 'Révoqué'}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-sm text-ink-soft">
                    {new Date(link.createdAt).toLocaleDateString('fr-FR')}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex items-center justify-end gap-1">
                      {link.active && (
                        <>
                          <button onClick={() => copyLink(link.url)}
                            className="p-1.5 rounded-none hover:bg-surface-2 text-ink-soft" title="Copier le lien">
                            <Copy className="w-4 h-4" />
                          </button>
                          <a href={link.url} target="_blank" rel="noopener noreferrer"
                            className="p-1.5 rounded-none hover:bg-surface-2 text-ink-soft" title="Ouvrir">
                            <ExternalLink className="w-4 h-4" />
                          </a>
                          <button onClick={() => revokeMut.mutate(link.id)}
                            className="p-1.5 rounded-none hover:bg-danger/10 text-danger" title="Révoquer">
                            <Trash2 className="w-4 h-4" />
                          </button>
                        </>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
};

export default SharedLinks;
