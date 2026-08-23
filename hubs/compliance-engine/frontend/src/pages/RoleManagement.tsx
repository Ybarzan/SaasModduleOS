import type { AxiosError } from 'axios';
import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Shield, ShieldCheck, Users, Plus, Trash2, CheckCircle, Loader2, X } from 'lucide-react';
import toast from 'react-hot-toast';
import { incokalkAPI } from '../lib/api';
import { useAuthStore } from '../stores/auth';

interface Permission {
  id: string;
  key: string;
  label: string;
  module: string;
}

interface Role {
  id: string;
  name: string;
  description: string;
  userCount: number;
  permissions: string[];
  isSystem: boolean;
}

const MODULES = ['Shipments', 'Carriers', 'Finance', 'Admin', 'Quotes', 'Analytics', 'Notifications', 'Team', 'Settings'];

const ALL_PERMISSIONS: Permission[] = MODULES.flatMap((mod) => [
  { id: `${mod.toLowerCase()}-view`, key: `${mod.toLowerCase()}:view`, label: 'Voir', module: mod },
  { id: `${mod.toLowerCase()}-create`, key: `${mod.toLowerCase()}:create`, label: 'Créer', module: mod },
  { id: `${mod.toLowerCase()}-edit`, key: `${mod.toLowerCase()}:edit`, label: 'Modifier', module: mod },
  { id: `${mod.toLowerCase()}-delete`, key: `${mod.toLowerCase()}:delete`, label: 'Supprimer', module: mod },
]);

const RoleManagement = () => {
  const queryClient = useQueryClient();
  const isAdmin = useAuthStore((s) => s.isAdmin());
  const [expandedRole, setExpandedRole] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [editingRole, setEditingRole] = useState<Role | null>(null);
  const [deleteConfirm, setDeleteConfirm] = useState<string | null>(null);
  const [form, setForm] = useState({ name: '', description: '', permissions: [] as string[] });

  const { data: rolesData, isLoading } = useQuery({
    queryKey: ['roles'],
    queryFn: async () => {
      const res = await incokalkAPI.roles?.list();
      return (res?.data ?? []) as Role[];
    },
  });

  const roles = Array.isArray(rolesData) ? rolesData : [];

  const saveMutation = useMutation({
    mutationFn: (data: { id?: string; name: string; description: string; permissions: string[] }) => {
      if (data.id) return incokalkAPI.roles.update(data.id, data);
      return incokalkAPI.roles.create(data);
    },
    onSuccess: () => {
      toast.success(editingRole ? 'Rôle mis à jour' : 'Rôle créé');
      setShowForm(false);
      setEditingRole(null);
      setForm({ name: '', description: '', permissions: [] });
      queryClient.invalidateQueries({ queryKey: ['roles'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || 'Erreur lors de la sauvegarde');
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => incokalkAPI.roles.delete(id),
    onSuccess: () => {
      toast.success('Rôle supprimé');
      setDeleteConfirm(null);
      queryClient.invalidateQueries({ queryKey: ['roles'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || 'Erreur lors de la suppression');
    },
  });

  const openAddForm = () => {
    setEditingRole(null);
    setForm({ name: '', description: '', permissions: [] });
    setShowForm(true);
  };

  const openEditForm = (role: Role) => {
    setEditingRole(role);
    setForm({ name: role.name, description: role.description, permissions: [...role.permissions] });
    setShowForm(true);
  };

  const togglePermission = (permKey: string) => {
    setForm((prev) => ({
      ...prev,
      permissions: prev.permissions.includes(permKey)
        ? prev.permissions.filter((p) => p !== permKey)
        : [...prev.permissions, permKey],
    }));
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    saveMutation.mutate({ id: editingRole?.id, ...form });
  };

  const getModulePermissions = (module: string) => ALL_PERMISSIONS.filter((p) => p.module === module);

  return (
    <div className="max-w-6xl mx-auto px-4 py-8">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between mb-8 gap-4">
        <div>
          <h1 className="text-2xl font-bold text-ink">Gestion des rôles</h1>
          <p className="text-ink-soft mt-1">Définissez les permissions par rôle</p>
        </div>
        {isAdmin && (
          <button
            onClick={openAddForm}
            className="flex items-center gap-2 bg-accent text-white px-4 py-2 rounded-lg font-medium hover:bg-accent-strong transition-colors"
          >
            <Plus size={18} />
            Nouveau rôle
          </button>
        )}
      </div>

      {isLoading ? (
        <div className="px-6 py-12 text-center text-ink-soft">
          <Loader2 size={24} className="animate-spin mx-auto mb-2 text-ink-soft" />
          Chargement...
        </div>
      ) : roles.length === 0 ? (
        <div className="bg-surface rounded-xl border border-line px-6 py-12 text-center text-ink-soft">
          <Shield size={32} className="mx-auto mb-3 text-ink-soft" />
          <p>Aucun rôle défini</p>
        </div>
      ) : (
        <div className="space-y-4">
          {roles.map((role) => {
            const isExpanded = expandedRole === role.id;
            return (
              <div key={role.id} className="bg-surface rounded-xl border border-line overflow-hidden">
                <div className="px-6 py-4 flex items-center justify-between">
                  <div className="flex items-center gap-4">
                    <div className="w-10 h-10 rounded-lg bg-accent-soft flex items-center justify-center">
                      {role.isSystem ? <ShieldCheck size={20} className="text-accent" /> : <Shield size={20} className="text-accent" />}
                    </div>
                    <div>
                      <h3 className="text-sm font-semibold text-ink">{role.name}</h3>
                      <p className="text-xs text-ink-soft">{role.description}</p>
                    </div>
                  </div>
                  <div className="flex items-center gap-4">
                    <div className="flex items-center gap-1.5 text-sm text-ink-soft">
                      <Users size={14} />
                      <span>{role.userCount}</span>
                    </div>
                    {isAdmin && !role.isSystem && (
                      <div className="flex items-center gap-1">
                        {deleteConfirm === role.id ? (
                          <div className="flex items-center gap-1">
                            <button
                              onClick={() => deleteMutation.mutate(role.id)}
                              disabled={deleteMutation.isPending}
                              className="px-2 py-1 text-xs bg-danger text-white rounded hover:bg-danger/90 transition-colors"
                            >
                              {deleteMutation.isPending ? <Loader2 size={12} className="animate-spin" /> : 'Confirmer'}
                            </button>
                            <button
                              onClick={() => setDeleteConfirm(null)}
                              className="px-2 py-1 text-xs bg-surface-2 text-ink-soft rounded hover:bg-line transition-colors"
                            >
                              Annuler
                            </button>
                          </div>
                        ) : (
                          <>
                            <button
                              onClick={() => openEditForm(role)}
                              className="p-1.5 rounded-lg text-ink-soft hover:text-accent hover:bg-accent-soft transition-colors"
                              title="Modifier"
                            >
                              <ShieldCheck size={16} />
                            </button>
                            <button
                              onClick={() => setDeleteConfirm(role.id)}
                              className="p-1.5 rounded-lg text-ink-soft hover:text-danger hover:bg-danger/10 transition-colors"
                              title="Supprimer"
                            >
                              <Trash2 size={16} />
                            </button>
                          </>
                        )}
                      </div>
                    )}
                    <button
                      onClick={() => setExpandedRole(isExpanded ? null : role.id)}
                      className="text-xs text-accent hover:text-accent-strong font-medium"
                    >
                      {isExpanded ? 'Réduire' : 'Permissions'}
                    </button>
                  </div>
                </div>
                {isExpanded && (
                  <div className="border-t border-line px-6 py-4 bg-bg">
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                      {MODULES.map((module) => {
                        const perms = getModulePermissions(module);
                        const selected = perms.filter((p) => role.permissions.includes(p.key)).length;
                        return (
                          <div key={module} className="bg-surface rounded-lg border border-line p-3">
                            <h4 className="text-xs font-semibold text-ink uppercase mb-2">{module}</h4>
                            <div className="space-y-1.5">
                              {perms.map((perm) => (
                                <div key={perm.id} className="flex items-center gap-2">
                                  {role.permissions.includes(perm.key) ? (
                                    <CheckCircle size={14} className="text-success" />
                                  ) : (
                                    <X size={14} className="text-ink-soft" />
                                  )}
                                  <span className="text-sm text-ink-soft">{perm.label}</span>
                                </div>
                              ))}
                            </div>
                            <p className="text-xs text-ink-soft mt-2">{selected}/{perms.length} permissions</p>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}

      {/* Add / Edit modal */}
      {showForm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div className="absolute inset-0 bg-black/40" onClick={() => setShowForm(false)} />
          <div className="relative bg-surface rounded-xl shadow-2xl w-full max-w-2xl mx-4 p-6 max-h-[90vh] overflow-y-auto">
            <h3 className="text-lg font-semibold text-ink mb-4">
              {editingRole ? 'Modifier le rôle' : 'Nouveau rôle'}
            </h3>
            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">Nom</label>
                  <input
                    type="text"
                    value={form.name}
                    onChange={(e) => setForm({ ...form, name: e.target.value })}
                    className="w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                    placeholder="Ex: Manager"
                    required
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">Description</label>
                  <input
                    type="text"
                    value={form.description}
                    onChange={(e) => setForm({ ...form, description: e.target.value })}
                    className="w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
                    placeholder="Description du rôle"
                    required
                  />
                </div>
              </div>

              <div>
                <label className="block text-sm font-medium text-ink mb-3">Permissions</label>
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                  {MODULES.map((module) => {
                    const perms = getModulePermissions(module);
                    const allSelected = perms.every((p) => form.permissions.includes(p.key));
                    return (
                      <div key={module} className="border border-line rounded-lg p-3">
                        <div className="flex items-center justify-between mb-2">
                          <h4 className="text-xs font-semibold text-ink uppercase">{module}</h4>
                          <button
                            type="button"
                            onClick={() => {
                              if (allSelected) {
                                setForm((prev) => ({
                                  ...prev,
                                  permissions: prev.permissions.filter((p) => !perms.some((pp) => pp.key === p)),
                                }));
                              } else {
                                const keys = perms.map((p) => p.key);
                                setForm((prev) => ({
                                  ...prev,
                                  permissions: [...new Set([...prev.permissions, ...keys])],
                                }));
                              }
                            }}
                            className="text-xs text-accent hover:text-accent-strong"
                          >
                            {allSelected ? 'Tout désélectionner' : 'Tout sélectionner'}
                          </button>
                        </div>
                        <div className="space-y-2">
                          {perms.map((perm) => (
                            <label key={perm.id} className="flex items-center gap-2 cursor-pointer">
                              <input
                                type="checkbox"
                                checked={form.permissions.includes(perm.key)}
                                onChange={() => togglePermission(perm.key)}
                                className="rounded border-line text-accent focus:ring-accent"
                              />
                              <span className="text-sm text-ink-soft">{perm.label}</span>
                            </label>
                          ))}
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>

              <div className="flex gap-3 pt-2">
                <button
                  type="button"
                  onClick={() => setShowForm(false)}
                  className="flex-1 px-4 py-2 border border-line rounded-lg text-sm font-medium text-ink hover:bg-bg transition-colors"
                >
                  Annuler
                </button>
                <button
                  type="submit"
                  disabled={saveMutation.isPending}
                  className="flex-1 px-4 py-2 bg-accent text-white rounded-lg text-sm font-medium hover:bg-accent-strong disabled:opacity-50 transition-colors flex items-center justify-center gap-2"
                >
                  {saveMutation.isPending && <Loader2 size={14} className="animate-spin" />}
                  {editingRole ? 'Mettre à jour' : 'Créer le rôle'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default RoleManagement;
