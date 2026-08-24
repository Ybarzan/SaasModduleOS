import { Fragment, useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { incokalkAPI } from '../lib/api';
import toast from 'react-hot-toast';
import {
  DollarSign, Plus, Pencil, Trash2, ToggleLeft, ToggleRight,
  Ship, Plane, Truck, Loader2, X, Search
} from 'lucide-react';
import type { Carrier, ShippingRate, ShippingRateFormData } from '../types';
import Pagination from '../components/Pagination';
import { COUNTRIES } from '@/lib/constants';

const PAGE_SIZE = 20;

const TRANSPORT_MODES = [
  { value: 'SEA', label: 'Maritime', icon: Ship, color: 'bg-accent-soft text-accent-strong' },
  { value: 'AIR', label: 'Aérien', icon: Plane, color: 'bg-accent-soft text-accent-strong' },
  { value: 'ROAD', label: 'Routier', icon: Truck, color: 'bg-warning/10 text-warning' },
];

const CURRENCIES = ['EUR', 'USD', 'GBP', 'MAD', 'CHF', 'CAD', 'JPY'];

const EMPTY_FORM: ShippingRateFormData = {
  carrierId: '',
  name: '',
  originCountry: '',
  destinationCountry: '',
  transportMode: 'ROAD',
  minWeightKg: undefined,
  maxWeightKg: undefined,
  baseRate: 0,
  currency: 'EUR',
  ratePerKg: 0,
  ratePerCbm: 0,
  transitDaysMin: undefined,
  transitDaysMax: undefined,
  co2EstimateKg: undefined,
};

const ShippingRates = () => {
  const queryClient = useQueryClient();
  const [showModal, setShowModal] = useState(false);
  const [editing, setEditing] = useState<ShippingRate | null>(null);
  const [form, setForm] = useState<ShippingRateFormData>({ ...EMPTY_FORM });
  const [deleteId, setDeleteId] = useState<string | null>(null);
  const [search, setSearch] = useState('');
  const [filterMode, setFilterMode] = useState('');
  const [filterActive, setFilterActive] = useState<'all' | 'active' | 'inactive'>('all');
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [page, setPage] = useState(0);

  const { data: carriers = [] } = useQuery({
    queryKey: ['carriers'],
    queryFn: async () => {
      const res = await incokalkAPI.carriers.getAll();
      return res.data || [];
    },
  });

  const { data, isLoading } = useQuery({
    queryKey: ['shipping-rates', page],
    queryFn: async () => {
      const res = await incokalkAPI.shippingRates.getPage(page, PAGE_SIZE);
      return res.data;
    },
  });

  const rates: ShippingRate[] = Array.isArray(data) ? data : (data?.content ?? []);
  const totalPages: number = Array.isArray(data) ? 1 : (data?.totalPages ?? 1);

  const createMutation = useMutation({
    mutationFn: (d: ShippingRateFormData) => incokalkAPI.shippingRates.create(d),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['shipping-rates'] });
      toast.success('Tarif créé avec succès');
      closeModal();
    },
    onError: () => toast.error('Erreur lors de la création'),
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: ShippingRateFormData }) =>
      incokalkAPI.shippingRates.update(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['shipping-rates'] });
      toast.success('Tarif mis à jour');
      closeModal();
    },
    onError: () => toast.error('Erreur lors de la mise à jour'),
  });

  const toggleMutation = useMutation({
    mutationFn: (id: string) => incokalkAPI.shippingRates.toggle(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['shipping-rates'] });
      toast.success('Statut mis à jour');
    },
    onError: () => toast.error('Erreur lors du changement de statut'),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => incokalkAPI.shippingRates.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['shipping-rates'] });
      toast.success('Tarif supprimé');
      setDeleteId(null);
    },
    onError: () => toast.error('Erreur lors de la suppression'),
  });

  const openCreate = () => {
    setEditing(null);
    setForm({ ...EMPTY_FORM });
    setShowModal(true);
  };

  const openEdit = (r: ShippingRate) => {
    setEditing(r);
    setForm({
      carrierId: r.carrierId || '',
      name: r.name,
      originCountry: r.originCountry,
      destinationCountry: r.destinationCountry,
      transportMode: r.transportMode,
      minWeightKg: r.minWeightKg,
      maxWeightKg: r.maxWeightKg,
      baseRate: r.baseRate,
      currency: r.currency,
      ratePerKg: r.ratePerKg,
      ratePerCbm: r.ratePerCbm,
      transitDaysMin: r.transitDaysMin,
      transitDaysMax: r.transitDaysMax,
      co2EstimateKg: r.co2EstimateKg,
    });
    setShowModal(true);
  };

  const closeModal = () => {
    setShowModal(false);
    setEditing(null);
    setForm({ ...EMPTY_FORM });
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.carrierId || !form.name || !form.originCountry || !form.destinationCountry) {
      toast.error('Veuillez remplir tous les champs obligatoires');
      return;
    }
    if (form.baseRate <= 0) {
      toast.error('Le tarif de base doit être supérieur à 0');
      return;
    }
    if (editing) {
      updateMutation.mutate({ id: editing.id, data: form });
    } else {
      createMutation.mutate(form);
    }
  };

  const filteredRates = rates.filter((r) => {
    const matchSearch = !search ||
      r.name.toLowerCase().includes(search.toLowerCase()) ||
      r.originCountry.toLowerCase().includes(search.toLowerCase()) ||
      r.destinationCountry.toLowerCase().includes(search.toLowerCase()) ||
      (r.carrierName || '').toLowerCase().includes(search.toLowerCase()) ||
      (r.carrierCode || '').toLowerCase().includes(search.toLowerCase());
    const matchMode = !filterMode || r.transportMode === filterMode;
    const matchActive = filterActive === 'all' ||
      (filterActive === 'active' && r.active) ||
      (filterActive === 'inactive' && !r.active);
    return matchSearch && matchMode && matchActive;
  });

  const getCarrierDisplay = (r: ShippingRate) => {
    if (r.carrierName) return r.carrierName;
    const carrier = carriers.find((c: Carrier) => c.id === r.carrierId);
    return carrier ? carrier.name : '—';
  };

  const getModeBadge = (mode: string) => {
    const m = TRANSPORT_MODES.find(t => t.value === mode);
    return m ? (
      <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${m.color}`}>
        <m.icon size={12} className="mr-1" />
        {m.label}
      </span>
    ) : mode;
  };

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-bg">
        <Loader2 className="h-12 w-12 animate-spin text-accent" />
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-bg py-8">
      <div className="container mx-auto px-4 max-w-7xl">
        {/* Header */}
        <div className="flex items-center justify-between mb-8">
          <div>
            <h1 className="text-3xl font-bold text-ink flex items-center gap-3">
              <DollarSign className="h-8 w-8 text-success" />
              <span className="text-accent font-normal" aria-hidden="true">:: </span>
              Gestion des tarifs
            </h1>
            <p className="text-ink-soft mt-1">
              Gérez les tarifs négociés avec vos transporteurs par route et mode de transport
            </p>
          </div>
          <button
            onClick={openCreate}
            className="bg-success text-white px-4 py-2.5 rounded-none hover:bg-success/90 transition-colors flex items-center space-x-2 font-medium shadow-sm"
          >
            <Plus size={20} />
            <span>Ajouter un tarif</span>
          </button>
        </div>

        {/* Filters */}
        <div className="bg-surface rounded-none shadow-sm p-4 mb-6 flex flex-wrap items-center gap-4">
          <div className="relative flex-1 min-w-[200px]">
            <Search size={18} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-soft" />
            <input
              type="text"
              placeholder="Rechercher par nom, transporteur, origine, destination..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="w-full pl-10 pr-4 py-2 border border-line rounded-none focus:ring-2 focus:ring-success border-success/40 text-sm"
            />
          </div>
          <select
            value={filterMode}
            onChange={(e) => setFilterMode(e.target.value)}
            className="border border-line rounded-none px-3 py-2 text-sm focus:ring-2 focus:ring-success"
          >
            <option value="">Tous les modes</option>
            {TRANSPORT_MODES.map(m => (
              <option key={m.value} value={m.value}>{m.label}</option>
            ))}
          </select>
          <select
            value={filterActive}
            onChange={(e) => setFilterActive(e.target.value as 'all' | 'active' | 'inactive')}
            className="border border-line rounded-none px-3 py-2 text-sm focus:ring-2 focus:ring-success"
          >
            <option value="all">Tous les statuts</option>
            <option value="active">Actifs</option>
            <option value="inactive">Inactifs</option>
          </select>
          <div className="text-sm text-ink-soft">
            {filteredRates.length} tarif{filteredRates.length !== 1 ? 's' : ''}
          </div>
        </div>

        {/* Table */}
        <div className="bg-surface rounded-none shadow-sm overflow-hidden">
          {filteredRates.length === 0 ? (
            <div className="p-12 text-center">
              <DollarSign className="h-16 w-16 text-ink-soft mx-auto mb-4" />
              <h3 className="text-lg font-semibold text-ink mb-2">
                {rates.length === 0 ? 'Aucun tarif configuré' : 'Aucun résultat'}
              </h3>
              <p className="text-ink-soft mb-4">
                {rates.length === 0
                  ? 'Ajoutez des tarifs pour vos transporteurs afin de comparer les coûts d\'expédition.'
                  : 'Modifiez vos filtres pour voir plus de résultats.'}
              </p>
              {rates.length === 0 && (
                <button onClick={openCreate} className="text-success hover:text-success font-medium">
                  + Ajouter votre premier tarif
                </button>
              )}
            </div>
          ) : (
            <table className="w-full">
              <thead className="bg-bg border-b border-line">
                <tr>
                  <th className="text-left px-4 py-3 text-xs font-semibold text-ink-soft uppercase">Nom</th>
                  <th className="text-left px-4 py-3 text-xs font-semibold text-ink-soft uppercase">Transporteur</th>
                  <th className="text-left px-4 py-3 text-xs font-semibold text-ink-soft uppercase">Route</th>
                  <th className="text-left px-4 py-3 text-xs font-semibold text-ink-soft uppercase">Mode</th>
                  <th className="text-right px-4 py-3 text-xs font-semibold text-ink-soft uppercase">Tarif base</th>
                  <th className="text-right px-4 py-3 text-xs font-semibold text-ink-soft uppercase">€/kg</th>
                  <th className="text-center px-4 py-3 text-xs font-semibold text-ink-soft uppercase">Transit</th>
                  <th className="text-center px-4 py-3 text-xs font-semibold text-ink-soft uppercase">Statut</th>
                  <th className="text-right px-4 py-3 text-xs font-semibold text-ink-soft uppercase">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {filteredRates.map((rate) => (
                  <Fragment key={rate.id}>
                    <tr
                      className="hover:bg-bg transition-colors cursor-pointer"
                      onClick={() => setExpandedId(expandedId === rate.id ? null : rate.id)}
                    >
                      <td className="px-4 py-3">
                        <div className="font-medium text-ink text-sm">{rate.name}</div>
                        <div className="text-xs text-ink-soft">{rate.currency}</div>
                      </td>
                      <td className="px-4 py-3 text-sm text-ink">
                        <div>{getCarrierDisplay(rate)}</div>
                        {rate.carrierCode && <div className="text-xs text-ink-soft font-mono">{rate.carrierCode}</div>}
                      </td>
                      <td className="px-4 py-3 text-sm">
                        <span className="text-ink">{rate.originCountry}</span>
                        <span className="text-ink-soft mx-1">→</span>
                        <span className="text-ink">{rate.destinationCountry}</span>
                      </td>
                      <td className="px-4 py-3">{getModeBadge(rate.transportMode)}</td>
                      <td className="px-4 py-3 text-right font-semibold text-ink text-sm">
                        {rate.baseRate.toFixed(2)} €
                      </td>
                      <td className="px-4 py-3 text-right text-sm text-ink-soft">
                        {rate.ratePerKg > 0 ? `${rate.ratePerKg.toFixed(2)} €` : '—'}
                      </td>
                      <td className="px-4 py-3 text-center text-sm text-ink-soft">
                        {rate.transitDaysMin && rate.transitDaysMax
                          ? `${rate.transitDaysMin}-${rate.transitDaysMax}j`
                          : '—'}
                      </td>
                      <td className="px-4 py-3 text-center">
                        <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${
                          rate.active ? 'bg-success/10 text-success' : 'bg-surface-2 text-ink-soft'
                        }`}>
                          {rate.active ? 'Actif' : 'Inactif'}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-right" onClick={(e) => e.stopPropagation()}>
                        <div className="flex items-center justify-end space-x-1">
                          <button
                            onClick={() => toggleMutation.mutate(rate.id)}
                            className="p-1.5 rounded-none hover:bg-surface-2 transition-colors"
                            title={rate.active ? 'Désactiver' : 'Activer'}
                          >
                            {rate.active
                              ? <ToggleRight size={18} className="text-success" />
                              : <ToggleLeft size={18} className="text-ink-soft" />
                            }
                          </button>
                          <button
                            onClick={() => openEdit(rate)}
                            className="p-1.5 rounded-none hover:bg-accent-soft transition-colors"
                            title="Modifier"
                          >
                            <Pencil size={16} className="text-accent" />
                          </button>
                          <button
                            onClick={() => setDeleteId(rate.id)}
                            className="p-1.5 rounded-none hover:bg-danger/10 transition-colors"
                            title="Supprimer"
                          >
                            <Trash2 size={16} className="text-danger" />
                          </button>
                        </div>
                      </td>
                    </tr>
                    {expandedId === rate.id && (
                      <tr>
                        <td colSpan={9} className="px-4 py-3 bg-bg border-b">
                          <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
                            <div>
                              <span className="text-ink-soft">Poids :</span>
                              <span className="ml-2 font-medium">
                                {rate.minWeightKg || '—'} — {rate.maxWeightKg || '—'} kg
                              </span>
                            </div>
                            <div>
                              <span className="text-ink-soft">Prix au m³ :</span>
                              <span className="ml-2 font-medium">
                                {rate.ratePerCbm > 0 ? `${rate.ratePerCbm.toFixed(2)} €` : '—'}
                              </span>
                            </div>
                            <div>
                              <span className="text-ink-soft">CO₂ estimé :</span>
                              <span className="ml-2 font-medium">
                                {rate.co2EstimateKg ? `${rate.co2EstimateKg} kg` : '—'}
                              </span>
                            </div>
                            <div>
                              <span className="text-ink-soft">Validité :</span>
                              <span className="ml-2 font-medium">
                                {rate.validFrom ? new Date(rate.validFrom).toLocaleDateString('fr-FR') : '—'}
                                {' → '}
                                {rate.validUntil ? new Date(rate.validUntil).toLocaleDateString('fr-FR') : '—'}
                              </span>
                            </div>
                          </div>
                        </td>
                      </tr>
                    )}
                  </Fragment>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>

      {!Array.isArray(data) && totalPages > 1 && (
        <div className="bg-surface rounded-none shadow-lg mt-4 py-3">
          <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
        </div>
      )}

      {/* Create/Edit Modal */}
      {showModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
          <div className="bg-surface rounded-none shadow-2xl w-full max-w-2xl mx-4 max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between px-6 py-4 border-b border-line">
              <h2 className="text-xl font-bold text-ink">
                {editing ? 'Modifier le tarif' : 'Nouveau tarif'}
              </h2>
              <button onClick={closeModal} className="p-1 rounded-none hover:bg-surface-2">
                <X size={20} className="text-ink-soft" />
              </button>
            </div>

            <form onSubmit={handleSubmit} className="p-6 space-y-5">
              {/* Carrier + Name */}
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">Transporteur *</label>
                  <select
                    value={form.carrierId}
                    onChange={(e) => setForm({ ...form, carrierId: e.target.value })}
                    className="w-full border border-line rounded-none px-3 py-2 focus:ring-2 focus:ring-success border-success/40"
                    required
                  >
                    <option value="">Sélectionner...</option>
                    {carriers.map((c: Carrier) => (
                      <option key={c.id} value={c.id}>{c.name} ({c.code})</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">Nom du tarif *</label>
                  <input
                    type="text"
                    value={form.name}
                    onChange={(e) => setForm({ ...form, name: e.target.value })}
                    className="w-full border border-line rounded-none px-3 py-2 focus:ring-2 focus:ring-success border-success/40"
                    placeholder="Ex: TARIF STANDARD FR→DE"
                    required
                  />
                </div>
              </div>

              {/* Route */}
              <div className="grid grid-cols-3 gap-4">
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">Origine *</label>
                  <select
                    value={form.originCountry}
                    onChange={(e) => setForm({ ...form, originCountry: e.target.value })}
                    className="w-full border border-line rounded-none px-3 py-2 focus:ring-2 focus:ring-success border-success/40"
                    required
                  >
                    <option value="">Pays...</option>
                    {COUNTRIES.map(c => <option key={c} value={c}>{c}</option>)}
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">Destination *</label>
                  <select
                    value={form.destinationCountry}
                    onChange={(e) => setForm({ ...form, destinationCountry: e.target.value })}
                    className="w-full border border-line rounded-none px-3 py-2 focus:ring-2 focus:ring-success border-success/40"
                    required
                  >
                    <option value="">Pays...</option>
                    {COUNTRIES.map(c => <option key={c} value={c}>{c}</option>)}
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">Mode *</label>
                  <select
                    value={form.transportMode}
                    onChange={(e) => setForm({ ...form, transportMode: e.target.value })}
                    className="w-full border border-line rounded-none px-3 py-2 focus:ring-2 focus:ring-success border-success/40"
                    required
                  >
                    {TRANSPORT_MODES.map(m => (
                      <option key={m.value} value={m.value}>{m.label}</option>
                    ))}
                  </select>
                </div>
              </div>

              {/* Pricing */}
              <div className="grid grid-cols-4 gap-4">
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">Tarif base (€) *</label>
                  <input
                    type="number"
                    step="0.01"
                    min="0.01"
                    value={form.baseRate}
                    onChange={(e) => setForm({ ...form, baseRate: parseFloat(e.target.value) || 0 })}
                    className="w-full border border-line rounded-none px-3 py-2 focus:ring-2 focus:ring-success border-success/40"
                    required
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">Devise</label>
                  <select
                    value={form.currency}
                    onChange={(e) => setForm({ ...form, currency: e.target.value })}
                    className="w-full border border-line rounded-none px-3 py-2 focus:ring-2 focus:ring-success border-success/40"
                  >
                    {CURRENCIES.map(c => <option key={c} value={c}>{c}</option>)}
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">€/kg</label>
                  <input
                    type="number"
                    step="0.01"
                    min="0"
                    value={form.ratePerKg ?? ''}
                    onChange={(e) => setForm({ ...form, ratePerKg: e.target.value ? parseFloat(e.target.value) : undefined })}
                    className="w-full border border-line rounded-none px-3 py-2 focus:ring-2 focus:ring-success border-success/40"
                    placeholder="0.00"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">€/m³</label>
                  <input
                    type="number"
                    step="0.01"
                    min="0"
                    value={form.ratePerCbm ?? ''}
                    onChange={(e) => setForm({ ...form, ratePerCbm: e.target.value ? parseFloat(e.target.value) : undefined })}
                    className="w-full border border-line rounded-none px-3 py-2 focus:ring-2 focus:ring-success border-success/40"
                    placeholder="0.00"
                  />
                </div>
              </div>

              {/* Weight + Transit */}
              <div className="grid grid-cols-4 gap-4">
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">Poids min (kg)</label>
                  <input
                    type="number"
                    step="0.1"
                    min="0"
                    value={form.minWeightKg ?? ''}
                    onChange={(e) => setForm({ ...form, minWeightKg: e.target.value ? parseFloat(e.target.value) : undefined })}
                    className="w-full border border-line rounded-none px-3 py-2 focus:ring-2 focus:ring-success border-success/40"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">Poids max (kg)</label>
                  <input
                    type="number"
                    step="0.1"
                    min="0"
                    value={form.maxWeightKg ?? ''}
                    onChange={(e) => setForm({ ...form, maxWeightKg: e.target.value ? parseFloat(e.target.value) : undefined })}
                    className="w-full border border-line rounded-none px-3 py-2 focus:ring-2 focus:ring-success border-success/40"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">Transit min (jours)</label>
                  <input
                    type="number"
                    min="1"
                    value={form.transitDaysMin ?? ''}
                    onChange={(e) => setForm({ ...form, transitDaysMin: e.target.value ? parseInt(e.target.value) : undefined })}
                    className="w-full border border-line rounded-none px-3 py-2 focus:ring-2 focus:ring-success border-success/40"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">Transit max (jours)</label>
                  <input
                    type="number"
                    min="1"
                    value={form.transitDaysMax ?? ''}
                    onChange={(e) => setForm({ ...form, transitDaysMax: e.target.value ? parseInt(e.target.value) : undefined })}
                    className="w-full border border-line rounded-none px-3 py-2 focus:ring-2 focus:ring-success border-success/40"
                  />
                </div>
              </div>

              {/* CO2 */}
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-ink mb-1">CO₂ estimé (kg)</label>
                  <input
                    type="number"
                    step="0.1"
                    min="0"
                    value={form.co2EstimateKg ?? ''}
                    onChange={(e) => setForm({ ...form, co2EstimateKg: e.target.value ? parseFloat(e.target.value) : undefined })}
                    className="w-full border border-line rounded-none px-3 py-2 focus:ring-2 focus:ring-success border-success/40"
                  />
                </div>
              </div>

              {/* Actions */}
              <div className="flex justify-end space-x-3 pt-4 border-t border-line">
                <button
                  type="button"
                  onClick={closeModal}
                  className="px-4 py-2 border border-line rounded-none text-ink hover:bg-bg transition-colors"
                >
                  Annuler
                </button>
                <button
                  type="submit"
                  disabled={createMutation.isPending || updateMutation.isPending}
                  className="px-6 py-2 bg-success text-white rounded-none hover:bg-success/90 transition-colors flex items-center space-x-2 disabled:opacity-50"
                >
                  {(createMutation.isPending || updateMutation.isPending) && (
                    <Loader2 size={16} className="animate-spin" />
                  )}
                  <span>{editing ? 'Enregistrer' : 'Créer'}</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Delete Confirmation Modal */}
      {deleteId && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
          <div className="bg-surface rounded-none shadow-2xl p-6 w-full max-w-md mx-4">
            <h3 className="text-lg font-bold text-ink mb-2">Confirmer la suppression</h3>
            <p className="text-ink-soft mb-6">
              Voulez-vous vraiment supprimer ce tarif ? Cette action est irréversible.
            </p>
            <div className="flex justify-end space-x-3">
              <button
                onClick={() => setDeleteId(null)}
                className="px-4 py-2 border border-line rounded-none text-ink hover:bg-bg"
              >
                Annuler
              </button>
              <button
                onClick={() => deleteMutation.mutate(deleteId)}
                disabled={deleteMutation.isPending}
                className="px-4 py-2 bg-danger text-white rounded-none hover:bg-danger/90 flex items-center space-x-2 disabled:opacity-50"
              >
                {deleteMutation.isPending && <Loader2 size={16} className="animate-spin" />}
                <span>Supprimer</span>
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default ShippingRates;
