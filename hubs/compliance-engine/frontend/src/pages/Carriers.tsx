import type { AxiosError } from 'axios';
import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { incokalkAPI } from '../lib/api';
import toast from 'react-hot-toast';
import {
  Truck, Plus, Pencil, Trash2, Globe, Mail, Phone,
  ToggleLeft, ToggleRight, Ship, Plane, Package, Loader2, Download, Upload
} from 'lucide-react';
import type { Carrier, CarrierFormData } from '../types';
import CsvImportModal from '../components/CsvImportModal';
import Pagination from '../components/Pagination';
import { COUNTRIES } from '@/lib/constants';

const PAGE_SIZE = 20;

const TRANSPORT_MODES = [
  { value: 'SEA', label: 'Maritime', icon: Ship, color: 'bg-accent-soft text-accent-strong' },
  { value: 'AIR', label: 'Aérien', icon: Plane, color: 'bg-accent-soft text-accent-strong' },
  { value: 'ROAD', label: 'Routier', icon: Truck, color: 'bg-warning/10 text-warning' },
];

const EMPTY_FORM: CarrierFormData = {
  name: '',
  code: '',
  transportModes: '',
  country: '',
  contactName: '',
  contactEmail: '',
  contactPhone: '',
  logoUrl: '',
};

const Carriers = () => {
  const queryClient = useQueryClient();
  const [showModal, setShowModal] = useState(false);
  const [showImport, setShowImport] = useState(false);
  const [editing, setEditing] = useState<Carrier | null>(null);
  const [form, setForm] = useState<CarrierFormData>({ ...EMPTY_FORM });
  const [selectedModes, setSelectedModes] = useState<string[]>([]);
  const [deleteId, setDeleteId] = useState<string | null>(null);
  const [page, setPage] = useState(0);

  const { data, isLoading, error } = useQuery({
    queryKey: ['carriers', page],
    queryFn: async () => {
      const res = await incokalkAPI.carriers.getPage(page, PAGE_SIZE);
      return res.data;
    },
  });

  const carriers: Carrier[] = Array.isArray(data) ? data : (data?.content ?? []);
  const totalPages: number = Array.isArray(data) ? 1 : (data?.totalPages ?? 1);

  const createMutation = useMutation({
    mutationFn: (d: CarrierFormData) => incokalkAPI.carriers.create(d),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['carriers'] });
      toast.success('Transporteur créé avec succès');
      closeModal();
    },
    onError: () => toast.error('Erreur lors de la création'),
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: CarrierFormData }) =>
      incokalkAPI.carriers.update(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['carriers'] });
      toast.success('Transporteur mis à jour');
      closeModal();
    },
    onError: () => toast.error('Erreur lors de la mise à jour'),
  });

  const toggleMutation = useMutation({
    mutationFn: (id: string) => incokalkAPI.carriers.toggle(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['carriers'] });
      toast.success('Statut mis à jour');
    },
    onError: () => toast.error('Erreur lors du changement de statut'),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => incokalkAPI.carriers.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['carriers'] });
      toast.success('Transporteur supprimé');
      setDeleteId(null);
    },
    onError: (err: AxiosError<{ message?: string }>) => toast.error(err.response?.data?.message || 'Erreur lors de la suppression'),
  });

  const openCreate = () => {
    setEditing(null);
    setForm({ ...EMPTY_FORM });
    setSelectedModes([]);
    setShowModal(true);
  };

  const openEdit = (c: Carrier) => {
    setEditing(c);
    const modes = c.transportModes.split(',').map((m) => m.trim());
    setSelectedModes(modes);
    setForm({
      name: c.name,
      code: c.code,
      transportModes: c.transportModes,
      country: c.country || '',
      contactName: c.contactName || '',
      contactEmail: c.contactEmail || '',
      contactPhone: c.contactPhone || '',
      logoUrl: c.logoUrl || '',
    });
    setShowModal(true);
  };

  const closeModal = () => {
    setShowModal(false);
    setEditing(null);
    setForm({ ...EMPTY_FORM });
    setSelectedModes([]);
  };

  const toggleMode = (mode: string) => {
    const next = selectedModes.includes(mode)
      ? selectedModes.filter((m) => m !== mode)
      : [...selectedModes, mode];
    setSelectedModes(next);
    setForm({ ...form, transportModes: next.join(',') });
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.name.trim() || !form.code.trim()) {
      toast.error('Le nom et le code sont obligatoires');
      return;
    }
    if (selectedModes.length === 0) {
      toast.error('Sélectionnez au moins un mode de transport');
      return;
    }
    if (editing) {
      updateMutation.mutate({ id: editing.id, data: form });
    } else {
      createMutation.mutate(form);
    }
  };

  const modeColors: Record<string, string> = {
    SEA: 'bg-accent-soft text-accent-strong',
    AIR: 'bg-accent-soft text-accent-strong',
    ROAD: 'bg-warning/10 text-warning',
  };

  const modeLabels: Record<string, string> = {
    SEA: 'Maritime',
    AIR: 'Aérien',
    ROAD: 'Routier',
  };

  const modeIcons: Record<string, typeof Ship> = {
    SEA: Ship,
    AIR: Plane,
    ROAD: Truck,
  };

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-bg">
        <div className="text-center">
          <Loader2 className="h-12 w-12 animate-spin mx-auto text-accent mb-4" />
          <div className="text-xl text-ink-soft">Chargement des transporteurs...</div>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-bg">
        <div className="bg-danger/10 border border-danger text-danger px-6 py-4 rounded">
          Erreur lors du chargement des transporteurs
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-bg py-12">
      <div className="container mx-auto px-4">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between mb-8 gap-4">
          <div>
            <h1 className="text-4xl font-bold text-ink mb-2">
              <span className="text-accent font-normal" aria-hidden="true">:: </span>
              Transporteurs
            </h1>
            <p className="text-ink-soft">Gérez vos compagnies de transport</p>
          </div>
          <div className="flex items-center gap-3">
            <button
              onClick={async () => {
                try {
                  const res = await incokalkAPI.export.csv.carriers();
                  const blob = new Blob([res.data], { type: 'text/csv' });
                  const url = window.URL.createObjectURL(blob);
                  const a = document.createElement('a');
                  a.href = url;
                  a.download = 'carriers_export.csv';
                  a.click();
                  window.URL.revokeObjectURL(url);
                  toast.success('Export téléchargé');
                } catch { toast.error('Erreur export'); }
              }}
              className="border border-line text-ink-soft px-4 py-2 rounded-none hover:bg-surface-2 transition-colors flex items-center space-x-2"
            >
              <Download size={18} />
              <span>Exporter</span>
            </button>
            <button
              onClick={() => setShowImport(true)}
              className="border border-line text-ink-soft px-4 py-2 rounded-none hover:bg-surface-2 transition-colors flex items-center space-x-2"
            >
              <Upload size={18} />
              <span>Importer CSV</span>
            </button>
            <button
              onClick={openCreate}
              className="bg-accent text-white px-4 py-2 rounded-none hover:bg-accent-strong transition-colors flex items-center space-x-2 tap-target"
            >
              <Plus size={20} />
              <span>Ajouter un transporteur</span>
            </button>
          </div>
        </div>

        {carriers.length === 0 ? (
          <div className="bg-surface rounded-none shadow-lg p-12 text-center">
            <Truck className="h-16 w-16 text-ink-soft mx-auto mb-4" />
            <h3 className="text-xl font-semibold text-ink-soft mb-2">Aucun transporteur</h3>
            <p className="text-ink-soft mb-6">
              Ajoutez votre premier transporteur pour commencer
            </p>
            <button
              onClick={openCreate}
              className="bg-accent text-white px-6 py-3 rounded-none hover:bg-accent-strong transition-colors inline-flex items-center space-x-2"
            >
              <Plus size={20} />
              <span>Ajouter un transporteur</span>
            </button>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {carriers.map((carrier) => {
              const modes = carrier.transportModes.split(',').map((m) => m.trim());
              return (
                <div
                  key={carrier.id}
                  className="bg-surface rounded-none shadow-lg p-6 hover:shadow-xl transition-shadow"
                >
                  <div className="flex items-start justify-between mb-4">
                    <div className="flex items-center space-x-3">
                      <div className="w-12 h-12 bg-accent-soft rounded-full flex items-center justify-center">
                        <Truck className="h-6 w-6 text-accent" />
                      </div>
                      <div>
                        <h3 className="font-semibold text-ink">{carrier.name}</h3>
                        <span className="text-sm text-ink-soft font-mono">{carrier.code}</span>
                      </div>
                    </div>
                    <button
                      onClick={() => toggleMutation.mutate(carrier.id)}
                      className="flex-shrink-0"
                      title={carrier.active ? 'Désactiver' : 'Activer'}
                    >
                      {carrier.active ? (
                        <ToggleRight className="h-8 w-8 text-success" />
                      ) : (
                        <ToggleLeft className="h-8 w-8 text-ink-soft" />
                      )}
                    </button>
                  </div>

                  <div className="flex flex-wrap gap-2 mb-4">
                    {modes.map((mode) => {
                      const Icon = modeIcons[mode] || Package;
                      return (
                        <span
                          key={mode}
                          className={`inline-flex items-center space-x-1 px-2 py-1 rounded-full text-xs font-medium ${modeColors[mode] || 'bg-surface-2 text-ink'}`}
                        >
                          <Icon size={12} />
                          <span>{modeLabels[mode] || mode}</span>
                        </span>
                      );
                    })}
                  </div>

                  {carrier.country && (
                    <div className="flex items-center space-x-2 text-sm text-ink-soft mb-2">
                      <Globe size={14} />
                      <span>{carrier.country}</span>
                    </div>
                  )}
                  {carrier.contactEmail && (
                    <div className="flex items-center space-x-2 text-sm text-ink-soft mb-2">
                      <Mail size={14} />
                      <span>{carrier.contactEmail}</span>
                    </div>
                  )}
                  {carrier.contactPhone && (
                    <div className="flex items-center space-x-2 text-sm text-ink-soft mb-4">
                      <Phone size={14} />
                      <span>{carrier.contactPhone}</span>
                    </div>
                  )}

                  <div className="flex items-center justify-between pt-4 border-t border-line">
                    <span
                      className={`text-xs font-medium px-2 py-1 rounded-full ${
                        carrier.active
                          ? 'bg-success/15 text-success'
                          : 'bg-surface-2 text-ink-soft'
                      }`}
                    >
                      {carrier.active ? 'Actif' : 'Inactif'}
                    </span>
                    <div className="flex items-center space-x-2">
                      <button
                        onClick={() => openEdit(carrier)}
                        className="p-2 text-ink-soft hover:text-accent hover:bg-accent-soft rounded-none transition-colors"
                        title="Modifier"
                      >
                        <Pencil size={16} />
                      </button>
                      <button
                        onClick={() => setDeleteId(carrier.id)}
                        className="p-2 text-ink-soft hover:text-danger hover:bg-danger/10 rounded-none transition-colors"
                        title="Supprimer"
                      >
                        <Trash2 size={16} />
                      </button>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}

        {!Array.isArray(data) && totalPages > 1 && (
          <div className="bg-surface rounded-none shadow-lg mt-4 py-3">
            <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
          </div>
        )}

        {/* Create/Edit Modal */}
        {showModal && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
            <div className="bg-surface rounded-none shadow-xl w-full max-w-lg mx-4 max-h-[90vh] overflow-y-auto">
              <div className="p-6">
                <h2 className="text-xl font-bold text-ink mb-6">
                  {editing ? 'Modifier le transporteur' : 'Ajouter un transporteur'}
                </h2>
                <form onSubmit={handleSubmit} className="space-y-4">
                  <div>
                    <label className="block text-sm font-medium text-ink-soft mb-1">Nom *</label>
                    <input
                      type="text"
                      value={form.name}
                      onChange={(e) => setForm({ ...form, name: e.target.value })}
                      className="w-full border border-line rounded-none px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent"
                      placeholder="Nom du transporteur"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-ink-soft mb-1">Code *</label>
                    <input
                      type="text"
                      value={form.code}
                      onChange={(e) => setForm({ ...form, code: e.target.value })}
                      className="w-full border border-line rounded-none px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent"
                      placeholder="Ex: DHL, FedEx..."
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-ink-soft mb-2">
                      Modes de transport *
                    </label>
                    <div className="flex flex-wrap gap-3">
                      {TRANSPORT_MODES.map((m) => {
                        const Icon = m.icon;
                        const selected = selectedModes.includes(m.value);
                        return (
                          <button
                            key={m.value}
                            type="button"
                            onClick={() => toggleMode(m.value)}
                            className={`inline-flex items-center space-x-2 px-3 py-2 rounded-none border-2 transition-colors ${
                              selected
                                ? 'border-accent bg-accent-soft text-accent-strong'
                                : 'border-line text-ink-soft hover:border-line'
                            }`}
                          >
                            <Icon size={16} />
                            <span className="text-sm font-medium">{m.label}</span>
                          </button>
                        );
                      })}
                    </div>
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-ink-soft mb-1">Pays</label>
                    <select
                      value={form.country || ''}
                      onChange={(e) => setForm({ ...form, country: e.target.value })}
                      className="w-full border border-line rounded-none px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent"
                    >
                      <option value="">Sélectionner un pays</option>
                      {COUNTRIES.map((c) => (
                        <option key={c} value={c}>{c}</option>
                      ))}
                    </select>
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-ink-soft mb-1">Contact</label>
                    <input
                      type="text"
                      value={form.contactName || ''}
                      onChange={(e) => setForm({ ...form, contactName: e.target.value })}
                      className="w-full border border-line rounded-none px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent"
                      placeholder="Nom du contact"
                    />
                  </div>
                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <label className="block text-sm font-medium text-ink-soft mb-1">Email</label>
                      <input
                        type="email"
                        value={form.contactEmail || ''}
                        onChange={(e) => setForm({ ...form, contactEmail: e.target.value })}
                        className="w-full border border-line rounded-none px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent"
                        placeholder="email@exemple.com"
                      />
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-ink-soft mb-1">Téléphone</label>
                      <input
                        type="tel"
                        value={form.contactPhone || ''}
                        onChange={(e) => setForm({ ...form, contactPhone: e.target.value })}
                        className="w-full border border-line rounded-none px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent"
                        placeholder="+33 1 23 45 67 89"
                      />
                    </div>
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-ink-soft mb-1">Logo URL</label>
                    <input
                      type="url"
                      value={form.logoUrl || ''}
                      onChange={(e) => setForm({ ...form, logoUrl: e.target.value })}
                      className="w-full border border-line rounded-none px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent"
                      placeholder="https://..."
                    />
                  </div>
                  <div className="flex justify-end space-x-3 pt-4">
                    <button
                      type="button"
                      onClick={closeModal}
                      className="px-4 py-2 text-ink-soft bg-surface-2 rounded-none hover:bg-line transition-colors"
                    >
                      Annuler
                    </button>
                    <button
                      type="submit"
                      disabled={createMutation.isPending || updateMutation.isPending}
                      className="px-4 py-2 bg-accent text-white rounded-none hover:bg-accent-strong transition-colors disabled:opacity-50 flex items-center space-x-2"
                    >
                      {(createMutation.isPending || updateMutation.isPending) && (
                        <Loader2 className="h-4 w-4 animate-spin" />
                      )}
                      <span>{editing ? 'Mettre à jour' : 'Créer'}</span>
                    </button>
                  </div>
                </form>
              </div>
            </div>
          </div>
        )}

        {/* Delete Confirmation */}
        {deleteId && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
            <div className="bg-surface rounded-none shadow-xl w-full max-w-md mx-4 p-6">
              <h3 className="text-lg font-bold text-ink mb-4">Confirmer la suppression</h3>
              <p className="text-ink-soft mb-6">
                Êtes-vous sûr de vouloir supprimer ce transporteur ? Cette action est irréversible.
              </p>
              <div className="flex justify-end space-x-3">
                <button
                  onClick={() => setDeleteId(null)}
                  className="px-4 py-2 text-ink-soft bg-surface-2 rounded-none hover:bg-line transition-colors"
                >
                  Annuler
                </button>
                <button
                  onClick={() => deleteMutation.mutate(deleteId)}
                  disabled={deleteMutation.isPending}
                  className="px-4 py-2 bg-danger text-white rounded-none hover:bg-danger/90 transition-colors disabled:opacity-50 flex items-center space-x-2"
                >
                  {deleteMutation.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
                  <span>Supprimer</span>
                </button>
              </div>
            </div>
          </div>
        )}

        <CsvImportModal
          isOpen={showImport}
          onClose={() => setShowImport(false)}
          title="Importer des transporteurs (CSV)"
          endpoint="carriers"
          queryKey={['carriers']}
        />
      </div>
    </div>
  );
};

export default Carriers;
