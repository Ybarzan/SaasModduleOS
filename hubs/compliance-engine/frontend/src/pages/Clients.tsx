import type { AxiosError } from 'axios';
import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { incokalkAPI } from '../lib/api';
import type { ClientUser } from '../types';
import {
  Users, Plus, Trash2, Power, PowerOff,
  X, Key
} from 'lucide-react';

const Clients = () => {
  const queryClient = useQueryClient();
  const [showCreate, setShowCreate] = useState(false);
  const [showResetPw, setShowResetPw] = useState<string | null>(null);
  const [newPw, setNewPw] = useState('');
  const [showPassword] = useState(false);
  const [form, setForm] = useState({ email: '', password: '', fullName: '', phone: '' });

  const { data: clients = [], isLoading } = useQuery<ClientUser[]>({
    queryKey: ['clients'],
    queryFn: async () => { const r = await incokalkAPI.clients.list(); return r.data; },
  });

  const { data: stats } = useQuery<{ totalClients: number; activeClients: number }>({
    queryKey: ['client-stats'],
    queryFn: async () => { const r = await incokalkAPI.clients.stats(); return r.data; },
  });

  const createMut = useMutation({
    mutationFn: (data: typeof form) => incokalkAPI.clients.create(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['clients'] });
      queryClient.invalidateQueries({ queryKey: ['client-stats'] });
      toast.success('Client créé');
      setShowCreate(false);
      setForm({ email: '', password: '', fullName: '', phone: '' });
    },
    onError: (e: AxiosError<{ message?: string }>) => toast.error(e.response?.data?.message || 'Erreur'),
  });

  const toggleMut = useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) =>
      incokalkAPI.clients.update(id, { active }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['clients'] });
      toast.success('Client mis à jour');
    },
  });

  const deleteMut = useMutation({
    mutationFn: (id: string) => incokalkAPI.clients.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['clients'] });
      queryClient.invalidateQueries({ queryKey: ['client-stats'] });
      toast.success('Client supprimé');
    },
  });

  const resetPwMut = useMutation({
    mutationFn: ({ id, pw }: { id: string; pw: string }) => incokalkAPI.clients.resetPassword(id, pw),
    onSuccess: () => {
      toast.success('Mot de passe réinitialisé');
      setShowResetPw(null);
      setNewPw('');
    },
    onError: (e: AxiosError<{ message?: string }>) => toast.error(e.response?.data?.message || 'Erreur'),
  });

  return (
    <div className="max-w-5xl mx-auto px-4 py-8">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-ink flex items-center gap-2">
            <Users className="w-6 h-6 text-accent" />
            <span className="text-accent font-normal" aria-hidden="true">:: </span>
            Clients
          </h1>
          <p className="text-sm text-ink-soft mt-1">
            Gérez les comptes clients qui accèdent au portail de suivi
          </p>
        </div>
        <button onClick={() => setShowCreate(true)} className="btn-primary flex items-center gap-2">
          <Plus className="w-4 h-4" /> Nouveau client
        </button>
      </div>

      {/* Stats */}
      {stats && (
        <div className="grid grid-cols-2 gap-4 mb-6">
          <div className="bg-surface rounded-none border border-line p-4 text-center">
            <p className="text-2xl font-bold text-ink">{stats.totalClients}</p>
            <p className="text-xs text-ink-soft">Total clients</p>
          </div>
          <div className="bg-surface rounded-none border border-line p-4 text-center">
            <p className="text-2xl font-bold text-success">{stats.activeClients}</p>
            <p className="text-xs text-ink-soft">Actifs</p>
          </div>
        </div>
      )}

      {/* Create Modal */}
      {showCreate && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
          <div className="bg-surface rounded-none max-w-md w-full p-6">
            <div className="flex items-center justify-between mb-4">
              <h3 className="font-bold text-ink">Nouveau client</h3>
              <button onClick={() => setShowCreate(false)}><X className="w-5 h-5 text-ink-soft" /></button>
            </div>
            <div className="space-y-3">
              <input placeholder="Nom complet" value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })}
                className="w-full px-3 py-2 border border-line rounded-none text-sm bg-surface text-ink" />
              <input placeholder="Email" type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })}
                className="w-full px-3 py-2 border border-line rounded-none text-sm bg-surface text-ink" />
              <input placeholder="Mot de passe (min. 8 car.)" type={showPassword ? 'text' : 'password'} value={form.password}
                onChange={(e) => setForm({ ...form, password: e.target.value })}
                className="w-full px-3 py-2 border border-line rounded-none text-sm bg-surface text-ink" />
              <input placeholder="Téléphone (optionnel)" value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })}
                className="w-full px-3 py-2 border border-line rounded-none text-sm bg-surface text-ink" />
              <button onClick={() => createMut.mutate(form)} disabled={createMut.isPending}
                className="w-full btn-primary py-2.5 text-sm">
                {createMut.isPending ? 'Création...' : 'Créer le client'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Reset Password Modal */}
      {showResetPw && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
          <div className="bg-surface rounded-none max-w-sm w-full p-6">
            <h3 className="font-bold text-ink mb-3 flex items-center gap-2">
              <Key className="w-5 h-5 text-accent" /> Nouveau mot de passe
            </h3>
            <input type="password" placeholder="Nouveau mot de passe (min. 8 car.)" value={newPw}
              onChange={(e) => setNewPw(e.target.value)}
              className="w-full px-3 py-2 border border-line rounded-none text-sm mb-3 bg-surface text-ink" />
            <div className="flex gap-2">
              <button onClick={() => { setShowResetPw(null); setNewPw(''); }}
                className="flex-1 px-4 py-2 border border-line rounded-none text-sm hover:bg-surface-2">Annuler</button>
              <button onClick={() => resetPwMut.mutate({ id: showResetPw, pw: newPw })} disabled={newPw.length < 8}
                className="flex-1 btn-primary py-2 text-sm">
                Réinitialiser
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Clients Table */}
      <div className="bg-surface rounded-none border border-line overflow-hidden">
        {isLoading ? (
          <p className="text-center py-12 text-ink-soft">Chargement...</p>
        ) : clients.length === 0 ? (
          <div className="text-center py-12">
            <Users className="w-12 h-12 text-ink-soft mx-auto mb-3" />
            <p className="text-ink-soft">Aucun client</p>
          </div>
        ) : (
          <table className="w-full">
            <thead className="bg-surface-2 border-b border-line">
              <tr>
                <th className="text-left px-4 py-3 text-xs font-semibold text-ink-soft uppercase">Client</th>
                <th className="text-left px-4 py-3 text-xs font-semibold text-ink-soft uppercase">Email</th>
                <th className="text-left px-4 py-3 text-xs font-semibold text-ink-soft uppercase">Téléphone</th>
                <th className="text-left px-4 py-3 text-xs font-semibold text-ink-soft uppercase">Statut</th>
                <th className="text-left px-4 py-3 text-xs font-semibold text-ink-soft uppercase">Créé le</th>
                <th className="text-right px-4 py-3 text-xs font-semibold text-ink-soft uppercase">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {clients.map((client) => (
                <tr key={client.id} className="hover:bg-surface-2 transition">
                  <td className="px-4 py-3">
                    <p className="font-medium text-sm text-ink">{client.fullName || '—'}</p>
                  </td>
                  <td className="px-4 py-3 text-sm text-ink-soft">{client.email}</td>
                  <td className="px-4 py-3 text-sm text-ink-soft">{client.phone || '—'}</td>
                  <td className="px-4 py-3">
                    <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${client.active ? 'bg-success/10 text-success' : 'bg-danger/10 text-danger'}`}>
                      {client.active ? 'Actif' : 'Inactif'}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-sm text-ink-soft">
                    {new Date(client.createdAt).toLocaleDateString('fr-FR')}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex items-center justify-end gap-1">
                      <button onClick={() => toggleMut.mutate({ id: client.id, active: !client.active })}
                        className="p-1.5 rounded-none hover:bg-surface-2 text-ink-soft" title={client.active ? 'Désactiver' : 'Activer'}>
                        {client.active ? <PowerOff className="w-4 h-4" /> : <Power className="w-4 h-4 text-success" />}
                      </button>
                      <button onClick={() => setShowResetPw(client.id)}
                        className="p-1.5 rounded-none hover:bg-surface-2 text-ink-soft" title="Réinitialiser mot de passe">
                        <Key className="w-4 h-4" />
                      </button>
                      <button onClick={() => { if (confirm('Supprimer ce client ?')) deleteMut.mutate(client.id); }}
                        className="p-1.5 rounded-none hover:bg-danger/10 text-danger" title="Supprimer">
                        <Trash2 className="w-4 h-4" />
                      </button>
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

export default Clients;
