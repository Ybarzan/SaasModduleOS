import type { AxiosError } from 'axios';
import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Shield, Users, UserPlus, Trash2, ChevronDown, Loader2, AlertCircle, Eye } from 'lucide-react';
import toast from 'react-hot-toast';
import { incokalkAPI } from '../lib/api';
import { useAuthStore } from '../stores/auth';
import RoleBadge from '../components/RoleBadge';

interface TeamMember {
  id: string;
  email: string;
  fullName: string;
  role: 'OWNER' | 'ADMIN' | 'MANAGER' | 'USER';
  createdAt: string;
  customRoleId?: string | null;
  customRoleName?: string | null;
}

interface CustomRoleOption {
  id: string;
  name: string;
  isSystem: boolean;
}

interface TeamStats {
  total: number;
  admins: number;
  members: number;
  viewers: number;
}

const Team = () => {
  const queryClient = useQueryClient();
  const user = useAuthStore((s) => s.user);
  const isAdmin = useAuthStore((s) => s.isAdmin());
  const [inviteOpen, setInviteOpen] = useState(false);
  const [inviteForm, setInviteForm] = useState({ email: '', fullName: '', role: 'USER' as string });
  const [openRoleMenu, setOpenRoleMenu] = useState<string | null>(null);
  const [deleteConfirm, setDeleteConfirm] = useState<string | null>(null);

  const { data: membersData, isLoading: membersLoading } = useQuery({
    queryKey: ['team-members'],
    queryFn: async () => {
      const res = await incokalkAPI.team.list();
      return res.data as TeamMember[] | { members: TeamMember[] };
    },
  });

  const members = Array.isArray(membersData) ? membersData : membersData?.members ?? [];

  const { data: rolesData } = useQuery({
    queryKey: ['roles'],
    queryFn: async () => {
      const res = await incokalkAPI.roles.list();
      return (res.data ?? []) as CustomRoleOption[];
    },
  });
  const customRoles = (rolesData ?? []).filter((r) => !r.isSystem);

  const { data: stats } = useQuery({
    queryKey: ['team-stats'],
    queryFn: async () => {
      const res = await incokalkAPI.team.stats();
      return res.data as TeamStats;
    },
  });

  const inviteMutation = useMutation({
    mutationFn: (data: typeof inviteForm) => incokalkAPI.team.invite(data),
    onSuccess: () => {
      toast.success('Invitation envoyée avec succès');
      setInviteOpen(false);
      setInviteForm({ email: '', fullName: '', role: 'USER' });
      queryClient.invalidateQueries({ queryKey: ['team-members'] });
      queryClient.invalidateQueries({ queryKey: ['team-stats'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || "Erreur lors de l'invitation");
    },
  });

  const updateRoleMutation = useMutation({
    mutationFn: ({ id, role }: { id: string; role: string }) => incokalkAPI.team.update(id, { role }),
    onSuccess: () => {
      toast.success('Rôle mis à jour');
      setOpenRoleMenu(null);
      queryClient.invalidateQueries({ queryKey: ['team-members'] });
      queryClient.invalidateQueries({ queryKey: ['team-stats'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || 'Erreur lors de la mise à jour');
    },
  });

  const updateCustomRoleMutation = useMutation({
    mutationFn: ({ id, customRoleId }: { id: string; customRoleId: string }) =>
      incokalkAPI.team.update(id, { customRoleId }),
    onSuccess: () => {
      toast.success('Rôle personnalisé mis à jour');
      setOpenRoleMenu(null);
      queryClient.invalidateQueries({ queryKey: ['team-members'] });
      queryClient.invalidateQueries({ queryKey: ['roles'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || 'Erreur lors de la mise à jour');
    },
  });

  const removeMutation = useMutation({
    mutationFn: (id: string) => incokalkAPI.team.remove(id),
    onSuccess: () => {
      toast.success('Membre retiré de l\'équipe');
      setDeleteConfirm(null);
      queryClient.invalidateQueries({ queryKey: ['team-members'] });
      queryClient.invalidateQueries({ queryKey: ['team-stats'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || 'Erreur lors de la suppression');
    },
  });

  const handleInvite = (e: React.FormEvent) => {
    e.preventDefault();
    inviteMutation.mutate(inviteForm);
  };

  const lastAdmin = (stats?.admins || 0) <= 1;

  return (
    <div className="max-w-6xl mx-auto px-4 py-8">
      {/* Header */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between mb-8 gap-4">
        <div>
          <h1 className="text-2xl font-bold text-ink">
            <span className="text-accent font-normal" aria-hidden="true">:: </span>
            Gestion de l'équipe
          </h1>
          <p className="text-ink-soft mt-1">Gérez les membres de votre entreprise et leurs droits d'accès</p>
        </div>
        {isAdmin && (
          <button
            onClick={() => setInviteOpen(true)}
            className="flex items-center gap-2 bg-accent text-white px-4 py-2 rounded-none font-medium hover:bg-accent-strong transition-colors"
          >
            <UserPlus size={18} />
            Inviter un membre
          </button>
        )}
      </div>

      {/* Non-admin notice */}
      {!isAdmin && (
        <div className="mb-6 flex items-center gap-3 bg-warning/10 border border-warning/40 text-warning rounded-none px-4 py-3">
          <AlertCircle size={20} />
          <span className="text-sm font-medium">Seuls les administrateurs peuvent gérer l'équipe</span>
        </div>
      )}

      {/* Stats cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-8">
        <div className="bg-surface rounded-none border border-line p-5">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-none bg-accent-soft flex items-center justify-center">
              <Shield size={20} className="text-accent" />
            </div>
            <div>
              <p className="text-sm text-ink-soft">Administrateurs</p>
              <p className="text-2xl font-bold text-ink">{stats?.admins ?? '—'}</p>
            </div>
          </div>
        </div>
        <div className="bg-surface rounded-none border border-line p-5">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-none bg-success/10 flex items-center justify-center">
              <Users size={20} className="text-success" />
            </div>
            <div>
              <p className="text-sm text-ink-soft">Membres</p>
              <p className="text-2xl font-bold text-ink">{stats?.members ?? '—'}</p>
            </div>
          </div>
        </div>
        <div className="bg-surface rounded-none border border-line p-5">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-none bg-bg flex items-center justify-center">
              <Eye size={20} className="text-ink-soft" />
            </div>
            <div>
              <p className="text-sm text-ink-soft">Visiteurs</p>
              <p className="text-2xl font-bold text-ink">{stats?.viewers ?? '—'}</p>
            </div>
          </div>
        </div>
      </div>

      {/* Team table */}
      <div className="relative bg-surface rounded-none border border-line overflow-hidden">
        <span className="hud-corner hud-corner-tl" aria-hidden="true" />
        <span className="hud-corner hud-corner-tr" aria-hidden="true" />
        <span className="hud-corner hud-corner-bl" aria-hidden="true" />
        <span className="hud-corner hud-corner-br" aria-hidden="true" />
        <div className="px-6 py-4 border-b border-line">
          <h2 className="text-lg font-semibold text-ink">Membres de l'équipe</h2>
        </div>

        {membersLoading ? (
          <div className="px-6 py-12 text-center text-ink-soft">
            <Loader2 size={24} className="animate-spin mx-auto mb-2 text-ink-soft" />
            Chargement...
          </div>
        ) : members.length === 0 ? (
          <div className="px-6 py-12 text-center text-ink-soft">
            <Users size={32} className="mx-auto mb-3 text-ink-soft" />
            <p>Aucun membre dans l'équipe</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="bg-bg border-b border-line">
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Nom</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Email</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Rôle</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Date d'arrivée</th>
                  {isAdmin && <th className="text-right text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Actions</th>}
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {members.map((member: TeamMember) => {
                  const isSelf = member.id === user?.id;
                  const isMemberAdmin = member.role === 'ADMIN';

                  return (
                    <tr key={member.id} className="hover:bg-bg transition-colors">
                      <td className="px-6 py-4">
                        <div className="flex items-center gap-3">
                          <div className="w-8 h-8 rounded-full bg-surface-2 flex items-center justify-center text-sm font-medium text-ink-soft">
                            {(member.fullName?.[0] || member.email[0]).toUpperCase()}
                          </div>
                          <span className="text-sm font-medium text-ink">
                            {member.fullName || member.email}
                          </span>
                        </div>
                      </td>
                      <td className="px-6 py-4 text-sm text-ink-soft">{member.email}</td>
                      <td className="px-6 py-4">
                        <div className="flex items-center gap-2">
                          <RoleBadge role={member.role} />
                          {member.customRoleName && (
                            <span className="text-xs px-2 py-0.5 rounded-full bg-accent-soft text-accent-strong">
                              {member.customRoleName}
                            </span>
                          )}
                        </div>
                      </td>
                      <td className="px-6 py-4 text-sm text-ink-soft">
                        {member.createdAt ? new Date(member.createdAt).toLocaleDateString('fr-FR') : '—'}
                      </td>
                      {isAdmin && (
                        <td className="px-6 py-4 text-right">
                          <div className="flex items-center justify-end gap-2 relative">
                            {/* Role change dropdown */}
                            <div className="relative">
                              <button
                                onClick={() => setOpenRoleMenu(openRoleMenu === member.id ? null : member.id)}
                                className="p-1.5 rounded-none text-ink-soft hover:text-ink-soft hover:bg-surface-2 transition-colors"
                                title="Changer le rôle"
                              >
                                <ChevronDown size={16} />
                              </button>
                              {openRoleMenu === member.id && (
                                <div className="absolute right-0 top-full mt-1 bg-surface border border-line rounded-none shadow-lg py-1 z-10 min-w-[200px]">
                                  {(['ADMIN', 'MANAGER', 'USER'] as const).map((r) => (
                                    <button
                                      key={r}
                                      onClick={() => updateRoleMutation.mutate({ id: member.id, role: r })}
                                      disabled={member.role === r}
                                      className={`w-full text-left px-3 py-2 text-sm flex items-center gap-2 ${
                                        member.role === r
                                          ? 'bg-bg text-ink-soft cursor-not-allowed'
                                          : 'text-ink hover:bg-bg'
                                      }`}
                                    >
                                      {r === 'ADMIN' && <Shield size={14} className="text-accent" />}
                                      {r === 'MANAGER' && <Shield size={14} className="text-accent" />}
                                      {r === 'USER' && <Users size={14} className="text-accent" />}
                                      {r === 'ADMIN' ? 'Administrateur' : r === 'MANAGER' ? 'Manager' : 'Utilisateur'}
                                    </button>
                                  ))}
                                  {customRoles.length > 0 && (
                                    <>
                                      <div className="border-t border-line my-1" />
                                      <div className="px-3 py-1 text-[10px] font-medium text-ink-soft uppercase tracking-wider">
                                        Rôles personnalisés
                                      </div>
                                      {customRoles.map((cr) => (
                                        <button
                                          key={cr.id}
                                          onClick={() => updateCustomRoleMutation.mutate({ id: member.id, customRoleId: cr.id })}
                                          disabled={member.customRoleId === cr.id}
                                          className={`w-full text-left px-3 py-2 text-sm flex items-center gap-2 ${
                                            member.customRoleId === cr.id
                                              ? 'bg-bg text-ink-soft cursor-not-allowed'
                                              : 'text-ink hover:bg-bg'
                                          }`}
                                        >
                                          <Shield size={14} className="text-accent" />
                                          {cr.name}
                                        </button>
                                      ))}
                                      {member.customRoleId && (
                                        <button
                                          onClick={() => updateCustomRoleMutation.mutate({ id: member.id, customRoleId: '' })}
                                          className="w-full text-left px-3 py-2 text-sm text-ink-soft hover:bg-bg"
                                        >
                                          Retirer le rôle personnalisé
                                        </button>
                                      )}
                                    </>
                                  )}
                                </div>
                              )}
                            </div>

                            {/* Remove button */}
                            {!isSelf && (
                              deleteConfirm === member.id ? (
                                <div className="flex items-center gap-1">
                                  <span className="text-xs text-ink-soft mr-1">Confirmer ?</span>
                                  <button
                                    onClick={() => removeMutation.mutate(member.id)}
                                    disabled={removeMutation.isPending}
                                    className="px-2 py-1 text-xs bg-danger text-white rounded hover:bg-danger/90 transition-colors"
                                  >
                                    {removeMutation.isPending ? <Loader2 size={12} className="animate-spin" /> : 'Oui'}
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
                                  onClick={() => setDeleteConfirm(member.id)}
                                  disabled={isMemberAdmin && lastAdmin}
                                  className="p-1.5 rounded-none text-ink-soft hover:text-danger hover:bg-danger/10 transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
                                  title={isMemberAdmin && lastAdmin ? "Impossible de retirer le dernier administrateur" : "Retirer de l'équipe"}
                                >
                                  <Trash2 size={16} />
                                </button>
                              )
                            )}
                          </div>
                        </td>
                      )}
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Invite Modal */}
      {inviteOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div className="absolute inset-0 bg-black/40" onClick={() => setInviteOpen(false)} />
          <div className="relative bg-surface rounded-none shadow-2xl w-full max-w-md mx-4 p-6">
            <h3 className="text-lg font-semibold text-ink mb-4">Inviter un membre</h3>
            <form onSubmit={handleInvite} className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-ink mb-1">Email</label>
                <input
                  type="email"
                  value={inviteForm.email}
                  onChange={(e) => setInviteForm({ ...inviteForm, email: e.target.value })}
                  className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                  placeholder="membre@entreprise.com"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-ink mb-1">Nom complet</label>
                <input
                  type="text"
                  value={inviteForm.fullName}
                  onChange={(e) => setInviteForm({ ...inviteForm, fullName: e.target.value })}
                  className="w-full px-3 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                  placeholder="Jean Dupont"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-ink mb-2">Rôle</label>
                <div className="space-y-2">
                  {[
                    { value: 'ADMIN', label: 'Administrateur', desc: 'Accès complet — gère l\'équipe, les transporteurs, les expéditions et les paramètres', icon: Shield, color: 'text-accent' },
                    { value: 'MANAGER', label: 'Manager', desc: 'Gère les transporteurs, expéditions et notifications — peut voir l\'équipe', icon: Shield, color: 'text-accent' },
                    { value: 'USER', label: 'Utilisateur', desc: 'Crée et gère les transporteurs et expéditions', icon: Users, color: 'text-accent' },
                  ].map(({ value, label, desc, icon: Icon, color }) => (
                    <label
                      key={value}
                      className={`flex items-start gap-3 p-3 rounded-none border cursor-pointer transition-colors ${
                        inviteForm.role === value
                          ? 'border-accent/40 bg-accent-soft'
                          : 'border-line hover:border-line'
                      }`}
                    >
                      <input
                        type="radio"
                        name="role"
                        value={value}
                        checked={inviteForm.role === value}
                        onChange={(e) => setInviteForm({ ...inviteForm, role: e.target.value })}
                        className="mt-0.5"
                      />
                      <div className="flex-1">
                        <div className="flex items-center gap-2">
                          <Icon size={14} className={color} />
                          <span className="text-sm font-medium text-ink">{label}</span>
                        </div>
                        <p className="text-xs text-ink-soft mt-0.5">{desc}</p>
                      </div>
                    </label>
                  ))}
                </div>
              </div>
              <div className="flex gap-3 pt-2">
                <button
                  type="button"
                  onClick={() => setInviteOpen(false)}
                  className="flex-1 px-4 py-2 border border-line rounded-none text-sm font-medium text-ink hover:bg-bg transition-colors"
                >
                  Annuler
                </button>
                <button
                  type="submit"
                  disabled={inviteMutation.isPending}
                  className="flex-1 px-4 py-2 bg-accent text-white rounded-none text-sm font-medium hover:bg-accent-strong disabled:opacity-50 transition-colors flex items-center justify-center gap-2"
                >
                  {inviteMutation.isPending && <Loader2 size={14} className="animate-spin" />}
                  Envoyer l'invitation
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default Team;
