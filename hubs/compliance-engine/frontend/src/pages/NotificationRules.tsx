import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { incokalkAPI } from '../lib/api';
import toast from 'react-hot-toast';
import {
  Bell, Plus, Pencil, Trash2, Mail, Webhook, Wifi, Package, Truck,
  Search, Filter, ToggleLeft, ToggleRight, Loader2, Send, X, Zap,
} from 'lucide-react';
import type { NotificationRule, NotificationRuleFormData, Carrier } from '../types';
import Pagination from '../components/Pagination';
import Modal from '../components/Modal';
import EmptyState from '../components/EmptyState';
import DeleteConfirmModal from '../components/DeleteConfirmModal';

const PAGE_SIZE = 20;

const EVENT_TYPES = [
  { value: 'SHIPMENT_STATUS_CHANGE', label: "Changement de statut d'expédition", icon: Truck, color: 'bg-accent-soft text-accent-strong' },
  { value: 'SHIPMENT_CREATED', label: 'Nouvelle expédition', icon: Package, color: 'bg-success/10 text-success' },
  { value: 'QUOTE_RECEIVED', label: 'Devis reçu', icon: Search, color: 'bg-accent-soft text-accent-strong' },
  { value: 'PROVIDER_DOWN', label: 'Fournisseur indisponible', icon: Wifi, color: 'bg-danger/10 text-danger' },
  { value: 'PROVIDER_RECOVERED', label: 'Fournisseur rétabli', icon: Wifi, color: 'bg-success/10 text-success' },
];

const STATUS_OPTIONS = [
  { value: 'DRAFT', label: 'Brouillon' },
  { value: 'QUOTED', label: 'Devisé' },
  { value: 'BOOKED', label: 'Réservé' },
  { value: 'IN_TRANSIT', label: 'En transit' },
  { value: 'DELIVERED', label: 'Livré' },
  { value: 'CANCELLED', label: 'Annulé' },
];

const EMPTY_FORM: NotificationRuleFormData = {
  name: '',
  eventType: 'SHIPMENT_STATUS_CHANGE',
  isActive: true,
  sendEmail: false,
  sendWebhook: false,
  sendInApp: true,
  emailRecipients: '',
  webhookUrl: '',
  webhookSecret: '',
  filterStatus: '',
  filterCarrierId: '',
  filterDataSource: '',
  actionType: '',
  maxBudgetAmount: undefined,
  allowedCarrierIds: '',
};

// Un seul type d'action existe côté moteur de règles pour l'instant (voir
// docs/04-composants-techniques.md) — la liste s'étendra avec l'exécuteur.
const ACTION_TYPES = [
  { value: 'SUGGEST_ERP_ORDER_ADJUSTMENT', label: 'Ajustement commande ERP' },
];

// Modèles courants pour éviter de faire remplir les 12 champs bruts du formulaire
// à quelqu'un qui veut juste "être alerté quand une expédition est livrée" — un clic
// pré-remplit nom/événement/canaux/filtre, le formulaire reste modifiable ensuite.
const RULE_TEMPLATES: { id: string; label: string; description: string; icon: typeof Truck; data: Partial<NotificationRuleFormData> }[] = [
  {
    id: 'delivered',
    label: 'Livraison confirmée',
    description: 'Alerte in-app + email quand une expédition est livrée',
    icon: Package,
    data: { name: 'Alerte livraison confirmée', eventType: 'SHIPMENT_STATUS_CHANGE', filterStatus: 'DELIVERED', sendInApp: true, sendEmail: true },
  },
  {
    id: 'in-transit',
    label: 'Suivi des transits',
    description: "Notification à chaque passage en statut 'En transit'",
    icon: Truck,
    data: { name: 'Suivi expéditions en transit', eventType: 'SHIPMENT_STATUS_CHANGE', filterStatus: 'IN_TRANSIT', sendInApp: true, sendEmail: false },
  },
  {
    id: 'new-quote',
    label: 'Nouveau devis',
    description: 'Alerte in-app + email dès qu’un devis est reçu',
    icon: Search,
    data: { name: 'Alerte nouveau devis', eventType: 'QUOTE_RECEIVED', sendInApp: true, sendEmail: true },
  },
  {
    id: 'provider-down',
    label: 'Fournisseur indisponible',
    description: 'Alerte immédiate si un fournisseur/API tombe en panne',
    icon: Wifi,
    data: { name: 'Alerte fournisseur indisponible', eventType: 'PROVIDER_DOWN', sendInApp: true, sendEmail: true },
  },
];

const NotificationRules = () => {
  const queryClient = useQueryClient();
  const [showModal, setShowModal] = useState(false);
  const [editing, setEditing] = useState<NotificationRule | null>(null);
  const [form, setForm] = useState<NotificationRuleFormData>({ ...EMPTY_FORM });
  const [deleteId, setDeleteId] = useState<string | null>(null);
  const [page, setPage] = useState(0);

  const { data: rulesData, isLoading } = useQuery({
    queryKey: ['notification-rules', page],
    queryFn: async () => {
      const res = await incokalkAPI.notificationRules.getPage(page, PAGE_SIZE);
      return res.data;
    },
  });

  const { data: carriersData } = useQuery({
    queryKey: ['carriers'],
    queryFn: async () => {
      const res = await incokalkAPI.carriers.getAll();
      return (res.data as Carrier[]) || [];
    },
  });

  const rules: NotificationRule[] = Array.isArray(rulesData) ? rulesData : (rulesData?.content ?? []);
  const totalPages: number = Array.isArray(rulesData) ? 1 : (rulesData?.totalPages ?? 1);
  const carriers: Carrier[] = carriersData ?? [];

  const createMutation = useMutation({
    mutationFn: (d: NotificationRuleFormData) => incokalkAPI.notificationRules.create(d),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notification-rules'] });
      toast.success('Règle créée avec succès');
      closeModal();
    },
    onError: () => toast.error('Erreur lors de la création'),
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: NotificationRuleFormData }) =>
      incokalkAPI.notificationRules.update(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notification-rules'] });
      toast.success('Règle mise à jour');
      closeModal();
    },
    onError: () => toast.error('Erreur lors de la mise à jour'),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => incokalkAPI.notificationRules.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notification-rules'] });
      toast.success('Règle supprimée');
      setDeleteId(null);
    },
    onError: () => toast.error('Erreur lors de la suppression'),
  });

  const testMutation = useMutation({
    mutationFn: (data: { ruleId: string; eventType: string }) => incokalkAPI.notificationRules.test(data),
    onSuccess: () => toast.success('Notification de test envoyée'),
    onError: () => toast.error("Erreur lors de l'envoi du test"),
  });

  const toggleActiveMutation = useMutation({
    mutationFn: (rule: NotificationRule) =>
      incokalkAPI.notificationRules.update(rule.id, { ...rule, isActive: !rule.active }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notification-rules'] });
      toast.success('Statut mis à jour');
    },
    onError: () => toast.error('Erreur lors de la mise à jour'),
  });

  const openCreate = () => {
    setEditing(null);
    setForm({ ...EMPTY_FORM });
    setShowModal(true);
  };

  const openEdit = (rule: NotificationRule) => {
    setEditing(rule);
    setForm({
      name: rule.name,
      eventType: rule.eventType,
      isActive: rule.active,
      sendEmail: rule.sendEmail,
      sendWebhook: rule.sendWebhook,
      sendInApp: rule.sendInApp,
      emailRecipients: rule.emailRecipients || '',
      webhookUrl: rule.webhookUrl || '',
      webhookSecret: rule.webhookSecret || '',
      filterStatus: rule.filterStatus || '',
      filterCarrierId: rule.filterCarrierId || '',
      filterDataSource: rule.filterDataSource || '',
      actionType: rule.actionType || '',
      maxBudgetAmount: rule.maxBudgetAmount,
      allowedCarrierIds: rule.allowedCarrierIds || '',
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
    if (!form.name.trim()) {
      toast.error('Le nom est obligatoire');
      return;
    }
    if (!form.sendEmail && !form.sendWebhook && !form.sendInApp) {
      toast.error('Sélectionnez au moins un canal de diffusion');
      return;
    }
    if (form.maxBudgetAmount != null && form.maxBudgetAmount < 0) {
      toast.error('Le budget maximum ne peut pas être négatif');
      return;
    }
    if (editing) {
      updateMutation.mutate({ id: editing.id, data: form });
    } else {
      createMutation.mutate(form);
    }
  };

  const handleTest = (rule: NotificationRule) => {
    testMutation.mutate({ ruleId: rule.id, eventType: rule.eventType });
  };

  const selectedCarrierIds = form.allowedCarrierIds ? form.allowedCarrierIds.split(',').filter(Boolean) : [];

  const toggleAllowedCarrier = (carrierId: string) => {
    const next = selectedCarrierIds.includes(carrierId)
      ? selectedCarrierIds.filter((id) => id !== carrierId)
      : [...selectedCarrierIds, carrierId];
    setForm({ ...form, allowedCarrierIds: next.join(',') });
  };

  const getActionTypeLabel = (actionType?: string) =>
    ACTION_TYPES.find((a) => a.value === actionType)?.label || actionType;

  const getEventLabel = (eventType: string) => {
    return EVENT_TYPES.find((e) => e.value === eventType)?.label || eventType;
  };

  const getEventColor = (eventType: string) => {
    return EVENT_TYPES.find((e) => e.value === eventType)?.color || 'bg-surface-2 text-ink';
  };

  if (isLoading) {
    return (
      <div className="min-h-screen bg-bg py-12">
        <div className="container mx-auto px-4">
          <div className="space-y-4">
            {[1, 2, 3].map((i) => (
              <div key={i} className="bg-surface rounded-lg shadow-lg p-6 animate-pulse">
                <div className="flex items-start justify-between">
                  <div className="space-y-3 flex-1">
                    <div className="h-5 bg-surface-2 rounded w-1/3" />
                    <div className="h-4 bg-surface-2 rounded w-1/4" />
                    <div className="flex gap-2">
                      <div className="h-6 bg-surface-2 rounded-full w-16" />
                      <div className="h-6 bg-surface-2 rounded-full w-16" />
                    </div>
                  </div>
                  <div className="h-8 bg-surface-2 rounded w-20" />
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-bg py-12">
      <div className="container mx-auto px-4">
        <div className="flex items-center justify-between mb-8">
          <div>
            <h1 className="text-4xl font-bold text-ink mb-2">Règles de notification</h1>
            <p className="text-ink-soft">Configurez vos alertes et canaux de diffusion</p>
          </div>
          <button
            onClick={openCreate}
            className="bg-accent text-white px-4 py-2 rounded-lg hover:bg-accent-strong transition-colors flex items-center space-x-2"
          >
            <Plus size={20} />
            <span>Nouvelle règle</span>
          </button>
        </div>

        {rules.length === 0 ? (
          <EmptyState
            icon={Bell}
            title="Aucune règle de notification"
            description="Créez votre première règle pour recevoir des alertes"
            action={{ label: 'Créer une règle', onClick: openCreate, icon: Plus }}
          />
        ) : (
          <div className="space-y-4">
            {rules.map((rule) => (
              <div
                key={rule.id}
                className="bg-surface rounded-lg shadow-lg p-6 hover:shadow-xl transition-shadow"
              >
                <div className="flex items-start justify-between">
                  <div className="flex-1">
                    <div className="flex items-center gap-3 mb-3">
                      <h3 className="text-lg font-bold text-ink">{rule.name}</h3>
                      <span className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium ${getEventColor(rule.eventType)}`}>
                        {getEventLabel(rule.eventType)}
                      </span>
                    </div>
                    <div className="flex flex-wrap gap-2 mb-3">
                      {rule.sendInApp && (
                        <span className="inline-flex items-center gap-1 px-2 py-1 rounded-full text-xs font-medium bg-success/10 text-success">
                          <Bell size={12} />
                          In-App
                        </span>
                      )}
                      {rule.sendEmail && (
                        <span className="inline-flex items-center gap-1 px-2 py-1 rounded-full text-xs font-medium bg-accent-soft text-accent-strong">
                          <Mail size={12} />
                          Email
                        </span>
                      )}
                      {rule.sendWebhook && (
                        <span className="inline-flex items-center gap-1 px-2 py-1 rounded-full text-xs font-medium bg-accent-soft text-accent-strong">
                          <Webhook size={12} />
                          Webhook
                        </span>
                      )}
                    </div>
                    {(rule.filterStatus || rule.filterCarrierId || rule.filterDataSource) && (
                      <div className="flex items-center gap-2 text-sm text-ink-soft mb-2">
                        <Filter size={14} />
                        {rule.filterStatus && (
                          <span>Statut: {rule.filterStatus}</span>
                        )}
                        {rule.filterCarrierId && (
                          <span>
                            Transporteur: {carriers.find((c) => c.id === rule.filterCarrierId)?.name || rule.filterCarrierId}
                          </span>
                        )}
                        {rule.filterDataSource && (
                          <span>
                            Source: {rule.filterDataSource === 'LIVE' ? 'Live uniquement' : 'Manuel uniquement'}
                          </span>
                        )}
                      </div>
                    )}
                    {rule.actionType && rule.actionType !== 'NONE' && (
                      <div className="flex items-start gap-2 text-sm">
                        <Zap size={14} className="text-warning shrink-0 mt-0.5" />
                        <div className="text-ink-soft">
                          <span className="font-medium text-ink">{getActionTypeLabel(rule.actionType)}</span>
                          {' — génère une suggestion à valider'}
                          {rule.maxBudgetAmount != null && ` · budget max ${rule.maxBudgetAmount} €`}
                          {rule.allowedCarrierIds && (
                            <>
                              {' · transporteurs autorisés : '}
                              {rule.allowedCarrierIds
                                .split(',')
                                .map((id) => carriers.find((c) => c.id === id)?.name || id)
                                .join(', ')}
                            </>
                          )}
                        </div>
                      </div>
                    )}
                  </div>
                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => handleTest(rule)}
                      disabled={testMutation.isPending}
                      className="p-2 text-ink-soft hover:text-accent hover:bg-accent-soft rounded-lg transition-colors disabled:opacity-50"
                      title="Tester"
                    >
                      {testMutation.isPending ? (
                        <Loader2 className="h-4 w-4 animate-spin" />
                      ) : (
                        <Send size={16} />
                      )}
                    </button>
                    <button
                      onClick={() => toggleActiveMutation.mutate(rule)}
                      disabled={toggleActiveMutation.isPending}
                      className="flex-shrink-0"
                      title={rule.active ? 'Désactiver' : 'Activer'}
                    >
                      {rule.active ? (
                        <ToggleRight className="h-8 w-8 text-success" />
                      ) : (
                        <ToggleLeft className="h-8 w-8 text-ink-soft" />
                      )}
                    </button>
                    <button
                      onClick={() => openEdit(rule)}
                      className="p-2 text-ink-soft hover:text-accent hover:bg-accent-soft rounded-lg transition-colors"
                      title="Modifier"
                    >
                      <Pencil size={16} />
                    </button>
                    <button
                      onClick={() => setDeleteId(rule.id)}
                      className="p-2 text-ink-soft hover:text-danger hover:bg-danger/10 rounded-lg transition-colors"
                      title="Supprimer"
                    >
                      <Trash2 size={16} />
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}

        <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />

        {/* Create/Edit Modal */}
        <Modal
          open={showModal}
          onClose={closeModal}
          ariaLabel={editing ? 'Modifier la règle' : 'Nouvelle règle'}
        >
          <div className="p-6">
            <div className="flex items-center justify-between mb-6">
              <h2 className="text-xl font-bold text-ink">
                {editing ? 'Modifier la règle' : 'Nouvelle règle'}
              </h2>
              <button
                onClick={closeModal}
                className="p-1.5 text-ink-soft hover:text-ink-soft hover:bg-surface-2 rounded-lg transition-colors"
              >
                <X size={18} />
              </button>
            </div>
            <form onSubmit={handleSubmit} className="space-y-5">
                  {!editing && (
                    <div>
                      <label className="block text-sm font-medium text-ink mb-2">
                        Modèles rapides (optionnel)
                      </label>
                      <div className="grid grid-cols-2 gap-2">
                        {RULE_TEMPLATES.map((tpl) => {
                          const Icon = tpl.icon;
                          return (
                            <button
                              key={tpl.id}
                              type="button"
                              onClick={() => setForm({ ...EMPTY_FORM, ...tpl.data })}
                              className="flex items-start gap-2 text-left p-3 border border-line rounded-lg hover:border-accent hover:bg-accent-soft transition-colors"
                            >
                              <Icon size={16} className="text-accent mt-0.5 flex-shrink-0" />
                              <span>
                                <span className="block text-sm font-medium text-ink">{tpl.label}</span>
                                <span className="block text-xs text-ink-soft">{tpl.description}</span>
                              </span>
                            </button>
                          );
                        })}
                      </div>
                    </div>
                  )}
                  <div>
                    <label className="block text-sm font-medium text-ink mb-1">Nom *</label>
                    <input
                      type="text"
                      value={form.name}
                      onChange={(e) => setForm({ ...form, name: e.target.value })}
                      className="w-full border border-line rounded-lg px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent"
                      placeholder="Ex: Alerte changement de statut"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-ink mb-1">Type d'événement *</label>
                    <select
                      value={form.eventType}
                      onChange={(e) => setForm({ ...form, eventType: e.target.value })}
                      className="w-full border border-line rounded-lg px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent"
                    >
                      {EVENT_TYPES.map((et) => (
                        <option key={et.value} value={et.value}>{et.label}</option>
                      ))}
                    </select>
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-ink mb-2">Canaux de diffusion *</label>
                    <div className="space-y-3">
                      <label className="flex items-center gap-3 p-3 border border-line rounded-lg hover:bg-bg cursor-pointer">
                        <input
                          type="checkbox"
                          checked={form.sendInApp}
                          onChange={(e) => setForm({ ...form, sendInApp: e.target.checked })}
                          className="w-4 h-4 text-accent rounded focus:ring-accent"
                        />
                        <Bell size={16} className="text-success" />
                        <span className="text-sm font-medium text-ink">In-App</span>
                        <span className="text-xs text-ink-soft ml-auto">Notifications dans l'application</span>
                      </label>
                      <label className="flex items-center gap-3 p-3 border border-line rounded-lg hover:bg-bg cursor-pointer">
                        <input
                          type="checkbox"
                          checked={form.sendEmail}
                          onChange={(e) => setForm({ ...form, sendEmail: e.target.checked })}
                          className="w-4 h-4 text-accent rounded focus:ring-accent"
                        />
                        <Mail size={16} className="text-accent" />
                        <span className="text-sm font-medium text-ink">Email</span>
                        <span className="text-xs text-ink-soft ml-auto">Notifications par email</span>
                      </label>
                      {form.sendEmail && (
                        <div className="ml-10">
                          <label className="block text-sm font-medium text-ink mb-1">
                            Destinataires (séparés par des virgules)
                          </label>
                          <textarea
                            value={form.emailRecipients || ''}
                            onChange={(e) => setForm({ ...form, emailRecipients: e.target.value })}
                            className="w-full border border-line rounded-lg px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent text-sm"
                            rows={2}
                            placeholder="email1@exemple.com, email2@exemple.com"
                          />
                        </div>
                      )}
                      <label className="flex items-center gap-3 p-3 border border-line rounded-lg hover:bg-bg cursor-pointer">
                        <input
                          type="checkbox"
                          checked={form.sendWebhook}
                          onChange={(e) => setForm({ ...form, sendWebhook: e.target.checked })}
                          className="w-4 h-4 text-accent rounded focus:ring-accent"
                        />
                        <Webhook size={16} className="text-accent" />
                        <span className="text-sm font-medium text-ink">Webhook</span>
                        <span className="text-xs text-ink-soft ml-auto">Notifications HTTP</span>
                      </label>
                      {form.sendWebhook && (
                        <div className="ml-10 space-y-3">
                          <div>
                            <label className="block text-sm font-medium text-ink mb-1">URL du webhook *</label>
                            <input
                              type="url"
                              value={form.webhookUrl || ''}
                              onChange={(e) => setForm({ ...form, webhookUrl: e.target.value })}
                              className="w-full border border-line rounded-lg px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent text-sm"
                              placeholder="https://..."
                            />
                          </div>
                          <div>
                            <label className="block text-sm font-medium text-ink mb-1">Secret (optionnel)</label>
                            <input
                              type="password"
                              value={form.webhookSecret || ''}
                              onChange={(e) => setForm({ ...form, webhookSecret: e.target.value })}
                              className="w-full border border-line rounded-lg px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent text-sm"
                              placeholder="Clé secrète pour la signature"
                            />
                          </div>
                        </div>
                      )}
                    </div>
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-ink mb-2">
                      Filtres (optionnel)
                    </label>
                    <div className="grid grid-cols-2 gap-3">
                      <div>
                        <label className="block text-xs text-ink-soft mb-1">Filtrer par statut</label>
                        <select
                          value={form.filterStatus || ''}
                          onChange={(e) => setForm({ ...form, filterStatus: e.target.value || undefined })}
                          className="w-full border border-line rounded-lg px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent text-sm"
                        >
                          <option value="">Tous les statuts</option>
                          {STATUS_OPTIONS.map((s) => (
                            <option key={s.value} value={s.value}>{s.label}</option>
                          ))}
                        </select>
                      </div>
                      <div>
                        <label className="block text-xs text-ink-soft mb-1">Filtrer par transporteur</label>
                        <select
                          value={form.filterCarrierId || ''}
                          onChange={(e) => setForm({ ...form, filterCarrierId: e.target.value || undefined })}
                          className="w-full border border-line rounded-lg px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent text-sm"
                        >
                          <option value="">Tous les transporteurs</option>
                          {carriers.map((c) => (
                            <option key={c.id} value={c.id}>{c.name}</option>
                          ))}
                        </select>
                      </div>
                      <div>
                        <label className="block text-xs text-ink-soft mb-1">Filtrer par provenance</label>
                        <select
                          value={form.filterDataSource || ''}
                          onChange={(e) => setForm({ ...form, filterDataSource: (e.target.value || undefined) as NotificationRuleFormData['filterDataSource'] })}
                          className="w-full border border-line rounded-lg px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent text-sm"
                        >
                          <option value="">Toutes provenances</option>
                          <option value="LIVE">Live uniquement (transporteur/webhook)</option>
                          <option value="MANUAL">Manuel uniquement (saisie humaine)</option>
                        </select>
                        <p className="text-xs text-ink-soft/70 mt-1">
                          Ne s'applique qu'aux changements de statut d'expédition
                        </p>
                      </div>
                    </div>
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-ink mb-2">
                      Action automatisée (optionnel)
                    </label>
                    <select
                      value={form.actionType || ''}
                      onChange={(e) => setForm({ ...form, actionType: e.target.value || undefined })}
                      className="w-full border border-line rounded-lg px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent text-sm"
                    >
                      <option value="">Aucune (notification seule)</option>
                      {ACTION_TYPES.map((a) => (
                        <option key={a.value} value={a.value}>{a.label}</option>
                      ))}
                    </select>
                    {form.actionType && (
                      <div className="mt-3 space-y-3 p-3 border border-line rounded-lg bg-bg">
                        <p className="text-xs text-ink-soft flex items-start gap-1.5">
                          <Zap size={14} className="text-warning shrink-0 mt-0.5" />
                          Cette règle créera une suggestion à valider manuellement — jamais d'exécution
                          automatique silencieuse. Approuver la suggestion déclenche l'action réelle.
                        </p>
                        <div>
                          <label className="block text-xs text-ink-soft mb-1">Budget maximum (€, optionnel)</label>
                          <input
                            type="number"
                            min={0}
                            step="0.01"
                            value={form.maxBudgetAmount ?? ''}
                            onChange={(e) =>
                              setForm({ ...form, maxBudgetAmount: e.target.value === '' ? undefined : Number(e.target.value) })
                            }
                            className="w-full border border-line rounded-lg px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent text-sm"
                            placeholder="Aucune limite"
                          />
                        </div>
                        <div>
                          <label className="block text-xs text-ink-soft mb-1">
                            Transporteurs autorisés (optionnel — laisser vide = tous autorisés)
                          </label>
                          <div className="max-h-32 overflow-y-auto border border-line rounded-lg divide-y divide-line">
                            {carriers.length === 0 ? (
                              <p className="text-xs text-ink-soft p-2">Aucun transporteur configuré</p>
                            ) : (
                              carriers.map((c) => (
                                <label key={c.id} className="flex items-center gap-2 px-3 py-1.5 text-sm cursor-pointer hover:bg-surface-2">
                                  <input
                                    type="checkbox"
                                    checked={selectedCarrierIds.includes(c.id)}
                                    onChange={() => toggleAllowedCarrier(c.id)}
                                    className="w-4 h-4 text-accent rounded focus:ring-accent"
                                  />
                                  {c.name}
                                </label>
                              ))
                            )}
                          </div>
                        </div>
                      </div>
                    )}
                  </div>

                  <div className="flex justify-end space-x-3 pt-4">
                    <button
                      type="button"
                      onClick={closeModal}
                      className="px-4 py-2 text-ink bg-surface-2 rounded-lg hover:bg-surface-2 transition-colors"
                    >
                      Annuler
                    </button>
                    <button
                      type="submit"
                      disabled={createMutation.isPending || updateMutation.isPending}
                      className="px-4 py-2 bg-accent text-white rounded-lg hover:bg-accent-strong transition-colors disabled:opacity-50 flex items-center space-x-2"
                    >
                      {(createMutation.isPending || updateMutation.isPending) && (
                        <Loader2 className="h-4 w-4 animate-spin" />
                      )}
                      <span>{editing ? 'Mettre à jour' : 'Créer'}</span>
                    </button>
                  </div>
                </form>
          </div>
        </Modal>

        {/* Delete Confirmation */}
        {deleteId && (
          <DeleteConfirmModal
            open
            onClose={() => setDeleteId(null)}
            onConfirm={() => deleteMutation.mutate(deleteId)}
            isPending={deleteMutation.isPending}
            message="Êtes-vous sûr de vouloir supprimer cette règle de notification ? Cette action est irréversible."
          />
        )}
      </div>
    </div>
  );
};

export default NotificationRules;
