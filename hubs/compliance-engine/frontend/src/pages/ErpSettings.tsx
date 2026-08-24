import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { incokalkAPI } from '../lib/api';
import toast from 'react-hot-toast';
import {
  Database, RefreshCw, Download, Upload, X, AlertTriangle,
  Settings, Eye, EyeOff, Loader2, Package, ShoppingCart, Users,
  FileText, Link as LinkIcon, Unlink, Server, type LucideIcon
} from 'lucide-react';
import type { ErpConfig, ErpSyncLog, ErpHealth } from '../types';

// ──────────────────────────────────────────────
// Constants
// ──────────────────────────────────────────────

const ERP_DEFS = [
  {
    type: 'ODOO',
    name: 'Odoo',
    description: 'ERP open-source — gestion commerciale, stock, comptabilité',
    iconBg: 'bg-accent-soft',
    iconText: 'text-accent',
    borderColor: 'border-accent/40',
    headerBg: 'bg-accent/10',
    btnBg: 'bg-accent hover:bg-accent-strong',
    btnLight: 'bg-accent/10 text-accent-strong hover:bg-accent-soft',
    features: [
      '✦ Produits & catalogues',
      '✦ Commandes & factures',
      '✦ Contacts & partenaires',
      '✦ Synchronisation bi-directionnelle',
    ],
  },
  {
    type: 'SAP',
    name: 'SAP Business One',
    description: 'ERP entreprise — gestion intégrée',
    iconBg: 'bg-accent-soft',
    iconText: 'text-accent',
    borderColor: 'border-accent/20',
    headerBg: 'bg-accent-soft',
    btnBg: 'bg-accent hover:bg-accent-strong',
    btnLight: 'bg-accent-soft text-accent-strong hover:bg-accent-soft',
    features: [
      '✦ Items & nomenclatures',
      '✦ Sales orders',
      '✦ Business partners',
      '✦ Delivery notes',
    ],
  },
  {
    type: 'QUICKBOOKS',
    name: 'QuickBooks Online',
    description: 'Comptabilité & facturation Intuit',
    iconBg: 'bg-success/10',
    iconText: 'text-success',
    borderColor: 'border-success/40',
    headerBg: 'bg-success/10',
    btnBg: 'bg-success hover:bg-success/90',
    btnLight: 'bg-success/10 text-success hover:bg-success/10',
    features: [
      '✦ Produits & services',
      '✦ Sales orders',
      '✦ Customers',
      '✦ Invoices',
    ],
  },
];

const SYNC_TYPES = [
  { key: 'PRODUCTS', label: 'Import Produits', icon: Package, direction: 'INBOUND' },
  { key: 'ORDERS', label: 'Import Commandes', icon: ShoppingCart, direction: 'INBOUND' },
  { key: 'CONTACTS', label: 'Import Contacts', icon: Users, direction: 'INBOUND' },
  { key: 'SHIPMENTS', label: 'Export Expéditions', icon: Upload, direction: 'OUTBOUND' },
];

const syncStatusColors: Record<string, string> = {
  IDLE: 'bg-surface-2 text-ink',
  SYNCING: 'bg-accent-soft text-accent-strong',
  SUCCESS: 'bg-success/10 text-success',
  ERROR: 'bg-danger/10 text-danger',
};

const logStatusColors: Record<string, string> = {
  STARTED: 'bg-accent-soft text-accent-strong',
  RUNNING: 'bg-accent-soft text-accent-strong',
  SUCCESS: 'bg-success/10 text-success',
  PARTIAL: 'bg-warning/10 text-warning',
  FAILED: 'bg-danger/10 text-danger',
};

const logStatusLabels: Record<string, string> = {
  STARTED: 'Démarré',
  RUNNING: 'En cours',
  SUCCESS: 'Succès',
  PARTIAL: 'Partiel',
  FAILED: 'Échoué',
};

type ModalTab = 'sync' | 'history' | 'data';

// ──────────────────────────────────────────────
// Component
// ──────────────────────────────────────────────

const ErpSettings = () => {
  const queryClient = useQueryClient();

  // ── State ──
  const [connectModal, setConnectModal] = useState<string | null>(null);
  const [connectedPanel, setConnectedPanel] = useState<string | null>(null);
  const [connectedTab, setConnectedTab] = useState<ModalTab>('sync');
  const [deleteId, setDeleteId] = useState<string | null>(null);

  // Form state
  const [formName, setFormName] = useState('');
  const [formEndpoint, setFormEndpoint] = useState('');
  const [formDb, setFormDb] = useState('');
  const [formUser, setFormUser] = useState('');
  const [formPassword, setFormPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);

  // ── Queries ──
  const { data: erpData, isLoading } = useQuery({
    queryKey: ['erp'],
    queryFn: async () => {
      const res = await incokalkAPI.erp.getAll();
      return (res.data as ErpConfig[]) || [];
    },
  });

  const { data: healthData } = useQuery({
    queryKey: ['erp-health'],
    queryFn: async () => {
      const res = await incokalkAPI.erp.health();
      return (res.data as ErpHealth[]) || [];
    },
    refetchInterval: 30000,
  });

  const { data: syncLogsData } = useQuery({
    queryKey: ['erp-sync-logs'],
    queryFn: async () => {
      const res = await incokalkAPI.erp.syncLogs();
      return (res.data as ErpSyncLog[]) || [];
    },
    refetchInterval: 15000,
  });

  const erpConfigs: ErpConfig[] = erpData ?? [];
  const healthList: ErpHealth[] = healthData ?? [];
  const syncLogs: ErpSyncLog[] = syncLogsData ?? [];

  // ── Mutations ──
  const createMutation = useMutation({
    mutationFn: (data: unknown) => incokalkAPI.erp.create(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['erp'] });
      queryClient.invalidateQueries({ queryKey: ['erp-health'] });
      toast.success('Intégration ERP configurée avec succès');
      closeConnectModal();
    },
    onError: () => toast.error('Erreur lors de la configuration de l\'ERP'),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => incokalkAPI.erp.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['erp'] });
      queryClient.invalidateQueries({ queryKey: ['erp-health'] });
      toast.success('Intégration ERP supprimée');
      setDeleteId(null);
      setConnectedPanel(null);
    },
    onError: () => toast.error('Erreur lors de la suppression'),
  });

  const testMutation = useMutation({
    mutationFn: (id: string) => incokalkAPI.erp.test(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['erp'] });
      queryClient.invalidateQueries({ queryKey: ['erp-health'] });
      toast.success('Connexion testée avec succès');
    },
    onError: () => toast.error('Échec du test de connexion'),
  });

  const syncMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: unknown }) => incokalkAPI.erp.sync(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['erp'] });
      queryClient.invalidateQueries({ queryKey: ['erp-health'] });
      queryClient.invalidateQueries({ queryKey: ['erp-sync-logs'] });
      toast.success('Synchronisation lancée');
    },
    onError: () => toast.error('Erreur lors de la synchronisation'),
  });

  // ── ERP data queries (for Data tab) ──
  const { data: productsData } = useQuery({
    queryKey: ['erp-products', connectedPanel],
    queryFn: async () => {
      if (!connectedPanel) return [];
      const res = await incokalkAPI.erp.products(connectedPanel);
      return res.data || [];
    },
    enabled: !!connectedPanel && connectedTab === 'data',
  });

  const { data: ordersData } = useQuery({
    queryKey: ['erp-orders', connectedPanel],
    queryFn: async () => {
      if (!connectedPanel) return [];
      const res = await incokalkAPI.erp.orders(connectedPanel);
      return res.data || [];
    },
    enabled: !!connectedPanel && connectedTab === 'data',
  });

  const { data: contactsData } = useQuery({
    queryKey: ['erp-contacts', connectedPanel],
    queryFn: async () => {
      if (!connectedPanel) return [];
      const res = await incokalkAPI.erp.contacts(connectedPanel);
      return res.data || [];
    },
    enabled: !!connectedPanel && connectedTab === 'data',
  });

  // ── Helpers ──
  const getConfig = (type: string) => erpConfigs.find((c) => c.erpType === type && c.isActive);
  const getHealth = (type: string) => healthList.find((h) => h.erpType === type);
  const getLogsForConfig = (configId: string) =>
    syncLogs.filter((l) => l.erpConfigId === configId).slice(0, 20);

  const closeConnectModal = () => {
    setConnectModal(null);
    setFormName('');
    setFormEndpoint('');
    setFormDb('');
    setFormUser('');
    setFormPassword('');
    setShowPassword(false);
  };

  const openConnectModal = (type: string) => {
    setFormName('');
    setFormEndpoint('');
    setFormDb('');
    setFormUser('');
    setFormPassword('');
    setShowPassword(false);
    setConnectModal(type);
  };

  const handleConnect = () => {
    if (!formName.trim()) {
      toast.error('Le nom de la connexion est requis');
      return;
    }
    createMutation.mutate({
      erpType: connectModal,
      name: formName.trim(),
      apiEndpoint: formEndpoint.trim() || undefined,
      apiKey: formPassword.trim() || undefined,
      databaseName: formDb.trim() || undefined,
      username: formUser.trim() || undefined,
      isActive: true,
    });
  };

  const formatDate = (date?: string) => {
    if (!date) return '—';
    return new Date(date).toLocaleString('fr-FR');
  };

  // ── Loading ──
  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-bg">
        <div className="text-center">
          <Loader2 className="h-12 w-12 animate-spin mx-auto text-accent mb-4" />
          <div className="text-xl text-ink-soft">Chargement des intégrations ERP...</div>
        </div>
      </div>
    );
  }

  // ── Render ──
  return (
    <div className="min-h-screen bg-bg py-12">
      <div className="container mx-auto px-4">
        {/* Header */}
        <div className="mb-8">
          <h1 className="text-4xl font-bold text-ink mb-2">
            <span className="text-accent font-normal" aria-hidden="true">:: </span>
            Intégrations ERP
          </h1>
          <p className="text-ink-soft">Connectez votre système ERP pour synchroniser les données</p>
        </div>

        {/* ERP Provider Cards */}
        <div className="grid md:grid-cols-3 gap-6 mb-10">
          {ERP_DEFS.map((def) => {
            const config = getConfig(def.type);
            const health = getHealth(def.type);
            const isConnected = !!config;

            return (
              <div
                key={def.type}
                className={`bg-surface rounded-none shadow-lg border-2 ${
                  isConnected ? def.borderColor : 'border-transparent'
                } hover:shadow-xl transition-shadow overflow-hidden`}
              >
                {/* Card Header */}
                <div className={`${def.headerBg} px-6 py-5`}>
                  <div className="flex items-center space-x-3">
                    <div className={`w-14 h-14 ${def.iconBg} rounded-none flex items-center justify-center`}>
                      <span className={`text-2xl font-bold ${def.iconText}`}>
                        {def.name.charAt(0)}
                      </span>
                    </div>
                    <div>
                      <h3 className="text-lg font-bold text-ink">{def.name}</h3>
                      <p className="text-sm text-ink-soft">{def.description}</p>
                    </div>
                  </div>
                </div>

                {/* Card Body */}
                <div className="px-6 py-4">
                  {/* Status */}
                  <div className="flex items-center space-x-2 mb-4">
                    <div className={`w-2.5 h-2.5 rounded-full ${
                      isConnected
                        ? health?.syncStatus === 'ERROR'
                          ? 'bg-danger'
                          : health?.syncStatus === 'SYNCING'
                          ? 'bg-accent animate-pulse'
                          : 'bg-success'
                        : 'bg-surface-2'
                    }`} />
                    <span className="text-sm font-medium text-ink">
                      {isConnected ? (health?.syncStatus === 'ERROR' ? 'Erreur de sync' : health?.syncStatus === 'SYNCING' ? 'Synchronisation...' : 'Connecté') : 'Non connecté'}
                    </span>
                    {isConnected && config && (
                      <span className={`ml-auto text-xs font-medium px-2 py-0.5 rounded-full ${
                        syncStatusColors[config.syncStatus] || syncStatusColors.IDLE
                      }`}>
                        {config.syncStatus}
                      </span>
                    )}
                  </div>

                  {isConnected && config && (
                    <div className="text-xs text-ink-soft mb-3">
                      {config.lastSyncAt ? (
                        <span>Dernière synchro : {formatDate(config.lastSyncAt)}</span>
                      ) : (
                        <span>Aucune synchronisation effectuée</span>
                      )}
                      {config.lastError && (
                        <div className="mt-1 text-danger flex items-center space-x-1">
                          <AlertTriangle size={12} />
                          <span>{config.lastError}</span>
                        </div>
                      )}
                    </div>
                  )}

                  {/* Features */}
                  <div className="space-y-1.5 mb-5">
                    {def.features.map((f) => (
                      <div key={f} className="text-sm text-ink-soft">
                        {f}
                      </div>
                    ))}
                  </div>
                </div>

                {/* Card Actions */}
                <div className="px-6 py-4 border-t border-line">
                  {isConnected && config ? (
                    <div className="flex items-center space-x-2">
                      <button
                        onClick={() => setConnectedPanel(config.id)}
                        className={`flex-1 px-3 py-2 ${def.btnLight} rounded-none transition-colors text-sm font-medium flex items-center justify-center space-x-1`}
                      >
                        <Settings size={14} />
                        <span>Gérer</span>
                      </button>
                      <button
                        onClick={() => testMutation.mutate(config.id)}
                        disabled={testMutation.isPending}
                        className="px-3 py-2 bg-surface-2 text-ink rounded-none hover:bg-surface-2 transition-colors text-sm font-medium disabled:opacity-50"
                        title="Tester la connexion"
                      >
                        {testMutation.isPending ? (
                          <Loader2 className="h-4 w-4 animate-spin" />
                        ) : (
                          <RefreshCw size={14} />
                        )}
                      </button>
                      <button
                        onClick={() => setDeleteId(config.id)}
                        className="px-3 py-2 bg-danger/10 text-danger rounded-none hover:bg-danger/10 transition-colors text-sm font-medium"
                        title="Déconnecter"
                      >
                        <Unlink size={14} />
                      </button>
                    </div>
                  ) : (
                    <button
                      onClick={() => openConnectModal(def.type)}
                      className={`w-full px-4 py-2.5 ${def.btnBg} text-white rounded-none transition-colors font-medium flex items-center justify-center space-x-2`}
                    >
                      <LinkIcon size={16} />
                      <span>Connecter</span>
                    </button>
                  )}
                </div>
              </div>
            );
          })}
        </div>

        {/* Empty State */}
        {erpConfigs.length === 0 && (
          <div className="bg-surface rounded-none shadow-lg p-12 mb-10 text-center">
            <Database className="h-16 w-16 text-ink-soft mx-auto mb-4" />
            <h3 className="text-xl font-semibold text-ink mb-2">Aucune intégration ERP configurée</h3>
            <p className="text-ink-soft mb-6">
              Connectez Odoo, SAP ou QuickBooks pour synchroniser vos données commerce et logistique.
            </p>
            <div className="flex justify-center space-x-4">
              {ERP_DEFS.map((def) => (
                <button
                  key={def.type}
                  onClick={() => openConnectModal(def.type)}
                  className={`px-4 py-2 ${def.btnBg} text-white rounded-none transition-colors text-sm font-medium flex items-center space-x-2`}
                >
                  <span className={`text-lg font-bold`}>{def.name.charAt(0)}</span>
                  <span>Connecter {def.name.split(' ')[0]}</span>
                </button>
              ))}
            </div>
          </div>
        )}

        {/* Connected ERP Panel */}
        {connectedPanel && (() => {
          const config = erpConfigs.find((c) => c.id === connectedPanel);
          if (!config) return null;
          const def = ERP_DEFS.find((d) => d.type === config.erpType);
          const logs = getLogsForConfig(config.id);

          return (
            <div className="relative bg-surface rounded-none shadow-lg overflow-hidden mb-10">
              <span className="hud-corner hud-corner-tl" aria-hidden="true" />
              <span className="hud-corner hud-corner-tr" aria-hidden="true" />
              <span className="hud-corner hud-corner-bl" aria-hidden="true" />
              <span className="hud-corner hud-corner-br" aria-hidden="true" />
              {/* Panel Header */}
              <div className={`${def?.headerBg || 'bg-bg'} px-6 py-4 border-b border-line flex items-center justify-between`}>
                <div className="flex items-center space-x-3">
                  <div className={`w-10 h-10 ${def?.iconBg || 'bg-surface-2'} rounded-none flex items-center justify-center`}>
                    <span className={`text-lg font-bold ${def?.iconText || 'text-ink-soft'}`}>
                      {config.name.charAt(0)}
                    </span>
                  </div>
                  <div>
                    <h2 className="text-lg font-bold text-ink">{config.name}</h2>
                    <p className="text-sm text-ink-soft">{config.erpType}</p>
                  </div>
                </div>
                <button
                  onClick={() => setConnectedPanel(null)}
                  className="p-1.5 text-ink-soft hover:text-ink-soft hover:bg-surface-2 rounded-none transition-colors"
                >
                  <X size={18} />
                </button>
              </div>

              {/* Tabs */}
              <div className="border-b border-line px-6">
                <nav className="flex space-x-6">
                  {([
                    { key: 'sync' as ModalTab, label: 'Synchroniser', icon: RefreshCw },
                    { key: 'history' as ModalTab, label: 'Historique', icon: FileText },
                    { key: 'data' as ModalTab, label: 'Données', icon: Database },
                  ]).map((tab) => (
                    <button
                      key={tab.key}
                      onClick={() => setConnectedTab(tab.key)}
                      className={`py-3 px-1 border-b-2 text-sm font-medium flex items-center space-x-1.5 transition-colors ${
                        connectedTab === tab.key
                          ? 'border-accent text-accent'
                          : 'border-transparent text-ink-soft hover:text-ink'
                      }`}
                    >
                      <tab.icon size={14} />
                      <span>{tab.label}</span>
                    </button>
                  ))}
                </nav>
              </div>

              {/* Tab Content */}
              <div className="p-6">
                {/* Sync Tab */}
                {connectedTab === 'sync' && (
                  <div className="grid md:grid-cols-2 gap-4">
                    {SYNC_TYPES.map((st) => (
                      <button
                        key={st.key}
                        onClick={() => syncMutation.mutate({
                          id: config.id,
                          data: { syncType: st.key, direction: st.direction },
                        })}
                        disabled={syncMutation.isPending || config.syncStatus === 'SYNCING'}
                        className={`p-4 rounded-none border-2 border-dashed ${
                          st.direction === 'OUTBOUND'
                            ? 'border-warning/40 bg-warning/10 hover:bg-warning/10 text-warning'
                            : 'border-line bg-bg hover:bg-surface-2 text-ink'
                        } transition-colors flex items-center space-x-4 disabled:opacity-50`}
                      >
                        <div className={`w-10 h-10 rounded-none flex items-center justify-center ${
                          st.direction === 'OUTBOUND' ? 'bg-warning/10' : 'bg-accent-soft'
                        }`}>
                          {syncMutation.isPending ? (
                            <Loader2 className="h-5 w-5 animate-spin text-accent" />
                          ) : (
                            <st.icon className={`h-5 w-5 ${
                              st.direction === 'OUTBOUND' ? 'text-warning' : 'text-accent'
                            }`} />
                          )}
                        </div>
                        <div className="text-left">
                          <div className="font-medium text-sm">{st.label}</div>
                          <div className="text-xs text-ink-soft mt-0.5">
                            {st.direction === 'OUTBOUND' ? 'IncoKalk → ERP' : 'ERP → IncoKalk'}
                          </div>
                        </div>
                        {st.direction === 'OUTBOUND' ? (
                          <Upload className="ml-auto h-4 w-4 text-warning" />
                        ) : (
                          <Download className="ml-auto h-4 w-4 text-accent" />
                        )}
                      </button>
                    ))}
                  </div>
                )}

                {/* History Tab */}
                {connectedTab === 'history' && (
                  <div className="overflow-x-auto">
                    {logs.length > 0 ? (
                      <table className="w-full">
                        <thead>
                          <tr className="bg-bg text-left">
                            <th className="px-4 py-3 text-xs font-medium text-ink-soft uppercase">Type</th>
                            <th className="px-4 py-3 text-xs font-medium text-ink-soft uppercase">Direction</th>
                            <th className="px-4 py-3 text-xs font-medium text-ink-soft uppercase">Statut</th>
                            <th className="px-4 py-3 text-xs font-medium text-ink-soft uppercase">Enregistré/Total</th>
                            <th className="px-4 py-3 text-xs font-medium text-ink-soft uppercase">Date</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-line">
                          {logs.map((log) => (
                            <SyncLogRow key={log.id} log={log} />
                          ))}
                        </tbody>
                      </table>
                    ) : (
                      <div className="text-center py-8 text-ink-soft">
                        <FileText className="h-10 w-10 text-ink-soft mx-auto mb-3" />
                        <p>Aucun historique de synchronisation</p>
                      </div>
                    )}
                  </div>
                )}

                {/* Data Tab */}
                {connectedTab === 'data' && (
                  <div className="space-y-8">
                    <DataSection
                      title="Produits"
                      icon={Package}
                      data={productsData}
                      columns={['name', 'code', 'price']}
                      emptyText="Aucun produit synchronisé"
                    />
                    <DataSection
                      title="Commandes"
                      icon={ShoppingCart}
                      data={ordersData}
                      columns={['orderNumber', 'status', 'total']}
                      emptyText="Aucune commande synchronisée"
                    />
                    <DataSection
                      title="Contacts"
                      icon={Users}
                      data={contactsData}
                      columns={['name', 'email', 'phone']}
                      emptyText="Aucun contact synchronisé"
                    />
                  </div>
                )}
              </div>
            </div>
          );
        })()}

        {/* Health Dashboard */}
        {healthList.length > 0 && (
          <div className="bg-surface rounded-none shadow-lg overflow-hidden mb-10">
            <div className="px-6 py-4 border-b border-line flex items-center justify-between">
              <div className="flex items-center space-x-2">
                <Server size={20} className="text-ink" />
                <h2 className="text-lg font-bold text-ink">Tableau de bord santé</h2>
              </div>
              <div className="flex items-center space-x-3">
                <span className="text-sm text-ink-soft">Mise à jour auto. 30s</span>
                <button
                  onClick={() => queryClient.invalidateQueries({ queryKey: ['erp-health'] })}
                  className="p-1.5 text-ink-soft hover:text-accent hover:bg-accent-soft rounded-none transition-colors"
                  title="Rafraîchir"
                >
                  <RefreshCw size={16} />
                </button>
              </div>
            </div>
            <div className="grid md:grid-cols-3 divide-y md:divide-y-0 md:divide-x divide-line">
              {healthList.map((h) => {
                const def = ERP_DEFS.find((d) => d.type === h.erpType);
                return (
                  <div key={h.erpType} className="px-6 py-5">
                    <div className="flex items-center space-x-3 mb-3">
                      <div className={`w-8 h-8 ${def?.iconBg || 'bg-surface-2'} rounded-none flex items-center justify-center`}>
                        <span className={`text-sm font-bold ${def?.iconText || 'text-ink-soft'}`}>
                          {h.name.charAt(0)}
                        </span>
                      </div>
                      <div>
                        <div className="font-medium text-ink">{h.name}</div>
                        <div className="text-xs text-ink-soft">{h.erpType}</div>
                      </div>
                    </div>
                    <div className="space-y-2">
                      <div className="flex items-center justify-between">
                        <span className="text-xs text-ink-soft">Statut</span>
                        <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${
                          syncStatusColors[h.syncStatus] || syncStatusColors.IDLE
                        }`}>
                          {h.syncStatus}
                        </span>
                      </div>
                      <div className="flex items-center justify-between">
                        <span className="text-xs text-ink-soft">Dernière synchro</span>
                        <span className="text-xs text-ink-soft">{formatDate(h.lastSyncAt)}</span>
                      </div>
                      {h.lastError && (
                        <div className="flex items-start space-x-1 mt-2 text-xs text-danger">
                          <AlertTriangle size={12} className="mt-0.5 flex-shrink-0" />
                          <span>{h.lastError}</span>
                        </div>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {/* Connect Modal */}
        {connectModal && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
            <div className="bg-surface rounded-none shadow-xl w-full max-w-md mx-4">
              <div className="p-6">
                <div className="flex items-center justify-between mb-6">
                  <h2 className="text-xl font-bold text-ink">
                    {getConfig(connectModal) ? 'Configurer' : 'Connecter'} {ERP_DEFS.find((d) => d.type === connectModal)?.name}
                  </h2>
                  <button
                    onClick={closeConnectModal}
                    className="p-1.5 text-ink-soft hover:text-ink-soft hover:bg-surface-2 rounded-none transition-colors"
                  >
                    <X size={18} />
                  </button>
                </div>

                <div className="space-y-4">
                  {/* Name */}
                  <div>
                    <label className="block text-sm font-medium text-ink mb-1">
                      Nom de la connexion *
                    </label>
                    <input
                      type="text"
                      value={formName}
                      onChange={(e) => setFormName(e.target.value)}
                      className="w-full border border-line rounded-none px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent"
                      placeholder="Ma connexion Odoo"
                    />
                  </div>

                  {/* Endpoint URL */}
                  <div>
                    <label className="block text-sm font-medium text-ink mb-1">
                      {connectModal === 'QUICKBOOKS' ? 'Company ID' : 'URL de l\'instance'}
                    </label>
                    <input
                      type="url"
                      value={formEndpoint}
                      onChange={(e) => setFormEndpoint(e.target.value)}
                      className="w-full border border-line rounded-none px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent"
                      placeholder={
                        connectModal === 'ODOO'
                          ? 'https://votre-odoo.com'
                          : connectModal === 'SAP'
                          ? 'https://votre-sap.com:50000/b1s/v2'
                          : 'Entrez votre Company ID'
                      }
                    />
                  </div>

                  {/* Database name (Odoo / SAP) */}
                  {connectModal !== 'QUICKBOOKS' && (
                    <div>
                      <label className="block text-sm font-medium text-ink mb-1">
                        {connectModal === 'SAP' ? 'Nom de la société' : 'Nom de la base de données'}
                      </label>
                      <input
                        type="text"
                        value={formDb}
                        onChange={(e) => setFormDb(e.target.value)}
                        className="w-full border border-line rounded-none px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent"
                        placeholder={connectModal === 'SAP' ? 'Ma Société SAP' : 'odoo_db'}
                      />
                    </div>
                  )}

                  {/* Username */}
                  {connectModal !== 'QUICKBOOKS' && (
                    <div>
                      <label className="block text-sm font-medium text-ink mb-1">
                        Nom d'utilisateur
                      </label>
                      <input
                        type="text"
                        value={formUser}
                        onChange={(e) => setFormUser(e.target.value)}
                        className="w-full border border-line rounded-none px-3 py-2 focus:ring-2 focus:ring-accent focus:border-accent"
                        placeholder="admin"
                      />
                    </div>
                  )}

                  {/* Password / API Key */}
                  <div>
                    <label className="block text-sm font-medium text-ink mb-1">
                      {connectModal === 'QUICKBOOKS' ? 'Access Token' : 'Mot de passe / Clé API'}
                    </label>
                    <div className="relative">
                      <input
                        type={showPassword ? 'text' : 'password'}
                        value={formPassword}
                        onChange={(e) => setFormPassword(e.target.value)}
                        className="w-full border border-line rounded-none px-3 pr-10 py-2 focus:ring-2 focus:ring-accent focus:border-accent"
                        placeholder={connectModal === 'QUICKBOOKS' ? 'Entrez votre Access Token' : 'Entrez le mot de passe ou la clé API'}
                      />
                      <button
                        type="button"
                        onClick={() => setShowPassword(!showPassword)}
                        className="absolute right-3 top-1/2 -translate-y-1/2 text-ink-soft hover:text-ink-soft"
                      >
                        {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                      </button>
                    </div>
                  </div>

                  {/* Refresh Token (QuickBooks only) */}
                  {connectModal === 'QUICKBOOKS' && (
                    <div>
                      <label className="block text-sm font-medium text-ink mb-1">
                        Refresh Token
                      </label>
                      <input
                        type="password"
                        value=""
                        readOnly
                        className="w-full border border-line rounded-none px-3 py-2 bg-bg text-ink-soft"
                        placeholder="Généré automatiquement après connexion"
                      />
                    </div>
                  )}
                </div>

                <div className="flex justify-end space-x-3 pt-6">
                  <button
                    onClick={closeConnectModal}
                    className="px-4 py-2 text-ink bg-surface-2 rounded-none hover:bg-surface-2 transition-colors"
                  >
                    Annuler
                  </button>
                  <button
                    onClick={handleConnect}
                    disabled={createMutation.isPending}
                    className="px-4 py-2 bg-accent text-white rounded-none hover:bg-accent-strong transition-colors disabled:opacity-50 flex items-center space-x-2"
                  >
                    {createMutation.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
                    <span>Sauvegarder</span>
                  </button>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Delete Confirmation */}
        {deleteId && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
            <div className="bg-surface rounded-none shadow-xl w-full max-w-md mx-4 p-6">
              <h3 className="text-lg font-bold text-ink mb-4">Confirmer la déconnexion</h3>
              <p className="text-ink-soft mb-6">
                Êtes-vous sûr de vouloir déconnecter cette intégration ERP ? La synchronisation des données sera interrompue.
              </p>
              <div className="flex justify-end space-x-3">
                <button
                  onClick={() => setDeleteId(null)}
                  className="px-4 py-2 text-ink bg-surface-2 rounded-none hover:bg-surface-2 transition-colors"
                >
                  Annuler
                </button>
                <button
                  onClick={() => deleteMutation.mutate(deleteId)}
                  disabled={deleteMutation.isPending}
                  className="px-4 py-2 bg-danger text-white rounded-none hover:bg-danger/90 transition-colors disabled:opacity-50 flex items-center space-x-2"
                >
                  {deleteMutation.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
                  <span>Déconnecter</span>
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

// ──────────────────────────────────────────────
// Sub-components
// ──────────────────────────────────────────────

function SyncLogRow({ log }: { log: ErpSyncLog }) {
  const [expanded, setExpanded] = useState(false);

  return (
    <>
      <tr
        className="hover:bg-bg transition-colors cursor-pointer"
        onClick={() => setExpanded(!expanded)}
      >
        <td className="px-4 py-3 text-sm text-ink">{log.syncType}</td>
        <td className="px-4 py-3 text-sm text-ink-soft">
          {log.direction === 'INBOUND' ? (
            <span className="flex items-center space-x-1">
              <Download size={12} className="text-accent" />
              <span>Import</span>
            </span>
          ) : (
            <span className="flex items-center space-x-1">
              <Upload size={12} className="text-warning" />
              <span>Export</span>
            </span>
          )}
        </td>
        <td className="px-4 py-3">
          <span className={`text-xs font-medium px-2.5 py-1 rounded-full ${
            logStatusColors[log.status] || logStatusColors.STARTED
          }`}>
            {logStatusLabels[log.status] || log.status}
          </span>
        </td>
        <td className="px-4 py-3 text-sm text-ink-soft">
          {log.recordsSynced}/{log.recordsTotal}
          {log.recordsFailed > 0 && (
            <span className="ml-2 text-danger text-xs">({log.recordsFailed} échoué(s))</span>
          )}
        </td>
        <td className="px-4 py-3 text-sm text-ink-soft">
          {formatDate(log.startedAt)}
        </td>
      </tr>
      {expanded && log.errorMessage && (
        <tr>
          <td colSpan={5} className="px-4 py-3 bg-danger/10">
            <div className="flex items-start space-x-2 text-sm text-danger">
              <AlertTriangle size={14} className="mt-0.5 flex-shrink-0" />
              <span>{log.errorMessage}</span>
            </div>
          </td>
        </tr>
      )}
    </>
  );
}

function DataSection({
  title,
  icon: Icon,
  data,
  columns,
  emptyText,
}: {
  title: string;
  icon: LucideIcon;
  data: Record<string, unknown>[] | null | undefined;
  columns: string[];
  emptyText: string;
}) {
  const [expanded, setExpanded] = useState(false);
  const items = Array.isArray(data) ? data : [];
  const displayItems = expanded ? items : items.slice(0, 10);

  const formatKey = (key: string) => {
    return key
      .replace(/([A-Z])/g, ' $1')
      .replace(/^./, (s) => s.toUpperCase());
  };

  return (
    <div>
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center space-x-2">
          <Icon size={18} className="text-ink-soft" />
          <h3 className="text-sm font-bold text-ink">{title}</h3>
          <span className="text-xs text-ink-soft">({items.length})</span>
        </div>
        {items.length > 10 && (
          <button
            onClick={() => setExpanded(!expanded)}
            className="text-xs text-accent hover:text-accent-strong font-medium flex items-center space-x-1"
          >
            <Eye size={12} />
            <span>{expanded ? 'Voir moins' : 'Voir tout'}</span>
          </button>
        )}
      </div>
      {displayItems.length > 0 ? (
        <div className="overflow-x-auto border border-line rounded-none">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-bg text-left">
                {columns.map((col) => (
                  <th key={col} className="px-4 py-2 text-xs font-medium text-ink-soft uppercase">
                    {formatKey(col)}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
{displayItems.map((item, idx) => (
  <tr key={String(item.id ?? idx)} className="hover:bg-bg">
                  {columns.map((col) => (
                    <td key={col} className="px-4 py-2 text-ink">
                      {item[col] != null ? String(item[col]) : '—'}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="text-center py-6 text-ink-soft text-sm border border-dashed border-line rounded-none">
          {emptyText}
        </div>
      )}
    </div>
  );
}

function formatDate(date?: string) {
  if (!date) return '—';
  return new Date(date).toLocaleString('fr-FR');
}

export default ErpSettings;
