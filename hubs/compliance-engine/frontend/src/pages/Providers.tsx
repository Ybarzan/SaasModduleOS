import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { incokalkAPI } from '../lib/api';
import toast from 'react-hot-toast';
import {
  Wifi, Key, RefreshCw,
  Trash2, X, Server, Activity, Lock, Eye, EyeOff, Loader2,
  Link as LinkIcon
} from 'lucide-react';
import type { ProviderConfig, ProviderHealth } from '../types';

const PROVIDER_DEFS = [
  {
    type: 'SHIPPO',
    name: 'Shippo',
    description: 'Multi-carrier API : UPS, FedEx, USPS, DHL + 100 carriers',
    color: 'blue',
    iconBg: 'bg-accent-soft',
    iconText: 'text-accent',
    borderColor: 'border-accent/20',
    features: ['✦ 100+ transporteurs', '✦ Tarifs temps réel', '✦ Tracking intégré'],
    needsSecret: false,
  },
  {
    type: 'DHL',
    name: 'DHL Express',
    description: 'API officielle DHL — Express & Freight',
    color: 'yellow',
    iconBg: 'bg-warning/10',
    iconText: 'text-warning',
    borderColor: 'border-warning/40',
    features: ['✦ Réseau mondial', '✦ Express 1-3 jours', '✦ Marchandises dangereuses'],
    needsSecret: true,
  },
  {
    type: 'INTERNAL',
    name: 'Rates Internes',
    description: 'Tarifs configurés manuellement dans vos transporteurs',
    color: 'green',
    iconBg: 'bg-success/10',
    iconText: 'text-success',
    borderColor: 'border-success/40',
    features: ['✦ Sans API clé', '✦ Configuration manuelle', '✦ Toujours actif'],
    needsSecret: false,
  },
];

const healthColors: Record<string, string> = {
  HEALTHY: 'bg-success',
  DEGRADED: 'bg-warning',
  DOWN: 'bg-danger',
  UNKNOWN: 'bg-surface-2',
};

const healthLabels: Record<string, string> = {
  HEALTHY: 'Sain',
  DEGRADED: 'Dégradé',
  DOWN: 'Hors ligne',
  UNKNOWN: 'Inconnu',
};

const Providers = () => {
  const queryClient = useQueryClient();
  const [connectModal, setConnectModal] = useState<string | null>(null);
  const [apiKey, setApiKey] = useState('');
  const [apiSecret, setApiSecret] = useState('');
  const [priority, setPriority] = useState(5);
  const [showKey, setShowKey] = useState(false);
  const [showSecret, setShowSecret] = useState(false);
  const [deleteId, setDeleteId] = useState<string | null>(null);

  const { data: providersData, isLoading } = useQuery({
    queryKey: ['providers'],
    queryFn: async () => {
      const res = await incokalkAPI.providers.getAll();
      return (res.data as ProviderConfig[]) || [];
    },
  });

  const { data: healthData } = useQuery({
    queryKey: ['providers-health'],
    queryFn: async () => {
      const res = await incokalkAPI.providers.health();
      return (res.data as ProviderHealth[]) || [];
    },
    refetchInterval: 30000,
  });

  const providers: ProviderConfig[] = providersData ?? [];
  const healthList: ProviderHealth[] = healthData ?? [];

  const createMutation = useMutation({
    mutationFn: (data: { providerType: string | null; apiKey: string; apiSecret?: string; priority: number; isActive: boolean }) =>
      incokalkAPI.providers.create(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['providers'] });
      queryClient.invalidateQueries({ queryKey: ['providers-health'] });
      toast.success('Fournisseur connecté avec succès');
      closeConnectModal();
    },
    onError: () => toast.error('Erreur lors de la connexion du fournisseur'),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => incokalkAPI.providers.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['providers'] });
      queryClient.invalidateQueries({ queryKey: ['providers-health'] });
      toast.success('Fournisseur déconnecté');
      setDeleteId(null);
    },
    onError: () => toast.error('Erreur lors de la déconnexion'),
  });

  const testMutation = useMutation({
    mutationFn: (id: string) => incokalkAPI.providers.test(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['providers'] });
      queryClient.invalidateQueries({ queryKey: ['providers-health'] });
      toast.success('Connexion testée avec succès');
    },
    onError: () => toast.error('Échec du test de connexion'),
  });

  const healthCheckMutation = useMutation({
    mutationFn: () => incokalkAPI.providers.health(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['providers'] });
      queryClient.invalidateQueries({ queryKey: ['providers-health'] });
      toast.success('Vérification de santé effectuée');
    },
    onError: () => toast.error('Erreur lors de la vérification'),
  });

  const closeConnectModal = () => {
    setConnectModal(null);
    setApiKey('');
    setApiSecret('');
    setPriority(5);
    setShowKey(false);
    setShowSecret(false);
  };

  const handleConnect = () => {
    if (!apiKey.trim()) {
      toast.error('La clé API est requise');
      return;
    }
    createMutation.mutate({
      providerType: connectModal,
      apiKey: apiKey.trim(),
      apiSecret: apiSecret.trim() || undefined,
      priority,
      isActive: true,
    });
  };

  const getProviderConfig = (type: string) => {
    return providers.find((p) => p.providerType === type);
  };

  const getHealth = (type: string) => {
    return healthList.find((h) => h.providerType === type);
  };

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-bg">
        <div className="text-center">
          <Loader2 className="h-12 w-12 animate-spin mx-auto text-accent mb-4" />
          <div className="text-xl text-ink-soft">Chargement des fournisseurs...</div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-bg py-12">
      <div className="container mx-auto px-4">
        {/* Header */}
        <div className="mb-8">
          <h1 className="text-4xl font-bold text-ink mb-2">Fournisseurs de tarifs</h1>
          <p className="text-ink-soft">Connectez vos API de transport pour des devis en temps réel</p>
        </div>

        {/* Provider Cards */}
        <div className="grid md:grid-cols-3 gap-6 mb-10">
          {PROVIDER_DEFS.map((def) => {
            const config = getProviderConfig(def.type);
            const health = getHealth(def.type);
            const isInternal = def.type === 'INTERNAL';
            const isConnected = !!config && config.active;

            const statusColor = health
              ? healthColors[health.healthStatus]
              : isInternal
              ? healthColors.HEALTHY
              : healthColors.UNKNOWN;

            const statusLabel = health
              ? healthLabels[health.healthStatus]
              : isInternal
              ? 'Sain'
              : 'Inconnu';

            return (
              <div
                key={def.type}
                className={`bg-surface rounded-lg shadow-lg p-6 border-2 ${isConnected || isInternal ? def.borderColor : 'border-transparent'} hover:shadow-xl transition-shadow`}
              >
                {/* Header */}
                <div className="flex items-start justify-between mb-4">
                  <div className="flex items-center space-x-3">
                    <div className={`w-14 h-14 ${def.iconBg} rounded-xl flex items-center justify-center`}>
                      {isInternal ? (
                        <Server className={`h-7 w-7 ${def.iconText}`} />
                      ) : (
                        <span className={`text-2xl font-bold ${def.iconText}`}>
                          {def.name.charAt(0)}
                        </span>
                      )}
                    </div>
                    <div>
                      <h3 className="text-lg font-bold text-ink">{def.name}</h3>
                      <p className="text-sm text-ink-soft">{def.description}</p>
                    </div>
                  </div>
                </div>

                {/* Status Badge */}
                <div className="flex items-center space-x-2 mb-4">
                  <div className={`w-2.5 h-2.5 rounded-full ${statusColor}`} />
                  <span className="text-sm font-medium text-ink">{statusLabel}</span>
                  {isInternal && (
                    <span className="ml-2 bg-success/10 text-success px-2 py-0.5 rounded-full text-xs font-medium">
                      Toujours actif
                    </span>
                  )}
                  {isConnected && !isInternal && config && (
                    <>
                      {config.consecutiveFailures > 0 && (
                        <span className="ml-2 bg-danger/10 text-danger px-2 py-0.5 rounded-full text-xs font-medium">
                          {config.consecutiveFailures} échec(s)
                        </span>
                      )}
                      {config.lastHealthCheck && (
                        <span className="text-xs text-ink-soft ml-auto">
                          {new Date(config.lastHealthCheck).toLocaleString('fr-FR')}
                        </span>
                      )}
                    </>
                  )}
                </div>

                {/* Features */}
                <div className="space-y-1.5 mb-5">
                  {def.features.map((f) => (
                    <div key={f} className="text-sm text-ink-soft flex items-center space-x-1">
                      <span>{f}</span>
                    </div>
                  ))}
                </div>

                {/* Actions */}
                {!isInternal && (
                  <div className="pt-4 border-t border-line">
                    {isConnected ? (
                      <div className="flex items-center space-x-2">
                        <button
                          onClick={() => {
                            if (config) {
                              setApiKey(config.apiKey || '');
                              setPriority(config.priority || 5);
                              setConnectModal(def.type);
                            }
                          }}
                          className="flex-1 px-3 py-2 bg-accent-soft text-accent-strong rounded-lg hover:bg-accent-soft transition-colors text-sm font-medium flex items-center justify-center space-x-1"
                        >
                          <Wifi size={14} />
                          <span>Reconnecter</span>
                        </button>
                        {config && (
                          <>
                            <button
                              onClick={() => testMutation.mutate(config.id)}
                              disabled={testMutation.isPending}
                              className="px-3 py-2 bg-surface-2 text-ink rounded-lg hover:bg-surface-2 transition-colors text-sm font-medium disabled:opacity-50"
                              title="Tester la connexion"
                            >
                              {testMutation.isPending ? (
                                <Loader2 className="h-4 w-4 animate-spin" />
                              ) : (
                                <Activity size={14} />
                              )}
                            </button>
                            <button
                              onClick={() => setDeleteId(config.id)}
                              className="px-3 py-2 bg-danger/10 text-danger rounded-lg hover:bg-danger/10 transition-colors text-sm font-medium"
                              title="Déconnecter"
                            >
                              <Trash2 size={14} />
                            </button>
                          </>
                        )}
                      </div>
                    ) : (
                      <button
                        onClick={() => {
                          setApiKey('');
                          setApiSecret('');
                          setPriority(5);
                          setConnectModal(def.type);
                        }}
                        className="w-full px-4 py-2.5 bg-accent text-white rounded-lg hover:bg-accent-strong transition-colors font-medium flex items-center justify-center space-x-2"
                      >
                        <LinkIcon size={16} />
                        <span>Connecter</span>
                      </button>
                    )}
                  </div>
                )}
              </div>
            );
          })}
        </div>

        {/* Health Status Panel */}
        <div className="bg-surface rounded-lg shadow-lg overflow-hidden">
          <div className="px-6 py-4 border-b border-line flex items-center justify-between">
            <div className="flex items-center space-x-2">
              <Activity size={20} className="text-ink" />
              <h2 className="text-lg font-bold text-ink">État de santé</h2>
            </div>
            <button
              onClick={() => healthCheckMutation.mutate()}
              disabled={healthCheckMutation.isPending}
              className="px-3 py-1.5 bg-surface-2 text-ink rounded-lg hover:bg-surface-2 transition-colors text-sm font-medium flex items-center space-x-1.5 disabled:opacity-50"
            >
              {healthCheckMutation.isPending ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <RefreshCw size={14} />
              )}
              <span>Vérifier la santé</span>
            </button>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="bg-bg text-left">
                  <th className="px-6 py-3 text-xs font-medium text-ink-soft uppercase">Fournisseur</th>
                  <th className="px-6 py-3 text-xs font-medium text-ink-soft uppercase">Statut</th>
                  <th className="px-6 py-3 text-xs font-medium text-ink-soft uppercase">Dernière vérification</th>
                  <th className="px-6 py-3 text-xs font-medium text-ink-soft uppercase">Échecs consécutifs</th>
                  <th className="px-6 py-3 text-xs font-medium text-ink-soft uppercase">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {healthList.length > 0 ? (
                  healthList.map((h) => {
                    const def = PROVIDER_DEFS.find((d) => d.type === h.providerType);
                    const config = getProviderConfig(h.providerType);
                    return (
                      <tr key={h.providerType} className="hover:bg-bg transition-colors">
                        <td className="px-6 py-4">
                          <div className="flex items-center space-x-3">
                            <div className={`w-8 h-8 ${def?.iconBg || 'bg-surface-2'} rounded-lg flex items-center justify-center`}>
                              {h.providerType === 'INTERNAL' ? (
                                <Server size={16} className={def?.iconText || 'text-ink-soft'} />
                              ) : (
                                <span className={`text-sm font-bold ${def?.iconText || 'text-ink-soft'}`}>
                                  {h.providerName?.charAt(0) || '?'}
                                </span>
                              )}
                            </div>
                            <div>
                              <div className="font-medium text-ink">{h.providerName}</div>
                              <div className="text-xs text-ink-soft">{h.providerType}</div>
                            </div>
                          </div>
                        </td>
                        <td className="px-6 py-4">
                          <div className="flex items-center space-x-2">
                            <div className={`w-2.5 h-2.5 rounded-full ${healthColors[h.healthStatus]}`} />
                            <span className="text-sm font-medium text-ink">
                              {healthLabels[h.healthStatus]}
                            </span>
                          </div>
                        </td>
                        <td className="px-6 py-4 text-sm text-ink-soft">
                          {h.lastHealthCheck
                            ? new Date(h.lastHealthCheck).toLocaleString('fr-FR')
                            : '—'}
                        </td>
                        <td className="px-6 py-4">
                          <span
                            className={`text-sm font-medium ${
                              h.consecutiveFailures > 0 ? 'text-danger' : 'text-ink-soft'
                            }`}
                          >
                            {h.consecutiveFailures}
                          </span>
                        </td>
                        <td className="px-6 py-4">
                          <div className="flex items-center space-x-2">
                            {config && (
                              <>
                                <button
                                  onClick={() => testMutation.mutate(config.id)}
                                  disabled={testMutation.isPending}
                                  className="p-1.5 text-ink-soft hover:text-accent hover:bg-accent-soft rounded-lg transition-colors disabled:opacity-50"
                                  title="Tester"
                                >
                                  <Wifi size={14} />
                                </button>
                                {!h.active && (
                                  <button
                                    onClick={() => setDeleteId(config.id)}
                                    className="p-1.5 text-ink-soft hover:text-danger hover:bg-danger/10 rounded-lg transition-colors"
                                    title="Supprimer"
                                  >
                                    <Trash2 size={14} />
                                  </button>
                                )}
                              </>
                            )}
                          </div>
                        </td>
                      </tr>
                    );
                  })
                ) : (
                  <tr>
                    <td colSpan={5} className="px-6 py-8 text-center text-ink-soft">
                      Aucun fournisseur configuré
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>

        {/* Connect Modal */}
        {connectModal && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
            <div className="bg-surface rounded-lg shadow-xl w-full max-w-md mx-4">
              <div className="p-6">
                <div className="flex items-center justify-between mb-6">
                  <h2 className="text-xl font-bold text-ink">
                    Connecter {PROVIDER_DEFS.find((d) => d.type === connectModal)?.name}
                  </h2>
                  <button
                    onClick={closeConnectModal}
                    className="p-1.5 text-ink-soft hover:text-ink-soft hover:bg-surface-2 rounded-lg transition-colors"
                  >
                    <X size={18} />
                  </button>
                </div>

                <div className="space-y-4">
                  <div>
                    <label className="block text-sm font-medium text-ink mb-1">
                      Clé API *
                    </label>
                    <div className="relative">
                      <Lock size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-soft" />
                      <input
                        type={showKey ? 'text' : 'password'}
                        value={apiKey}
                        onChange={(e) => setApiKey(e.target.value)}
                        className="w-full border border-line rounded-lg pl-10 pr-10 py-2 focus:ring-2 focus:ring-accent focus:border-accent"
                        placeholder="Entrez votre clé API"
                      />
                      <button
                        type="button"
                        onClick={() => setShowKey(!showKey)}
                        className="absolute right-3 top-1/2 -translate-y-1/2 text-ink-soft hover:text-ink-soft"
                      >
                        {showKey ? <EyeOff size={16} /> : <Eye size={16} />}
                      </button>
                    </div>
                  </div>

                  {PROVIDER_DEFS.find((d) => d.type === connectModal)?.needsSecret && (
                    <div>
                      <label className="block text-sm font-medium text-ink mb-1">
                        Clé secrète
                      </label>
                      <div className="relative">
                        <Key size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-soft" />
                        <input
                          type={showSecret ? 'text' : 'password'}
                          value={apiSecret}
                          onChange={(e) => setApiSecret(e.target.value)}
                          className="w-full border border-line rounded-lg pl-10 pr-10 py-2 focus:ring-2 focus:ring-accent focus:border-accent"
                          placeholder="Clé secrète DHL"
                        />
                        <button
                          type="button"
                          onClick={() => setShowSecret(!showSecret)}
                          className="absolute right-3 top-1/2 -translate-y-1/2 text-ink-soft hover:text-ink-soft"
                        >
                          {showSecret ? <EyeOff size={16} /> : <Eye size={16} />}
                        </button>
                      </div>
                    </div>
                  )}

                  <div>
                    <label className="block text-sm font-medium text-ink mb-1">
                      Priorité : {priority}
                    </label>
                    <input
                      type="range"
                      min="1"
                      max="10"
                      value={priority}
                      onChange={(e) => setPriority(parseInt(e.target.value))}
                      className="w-full accent-accent"
                    />
                    <div className="flex justify-between text-xs text-ink-soft mt-1">
                      <span>Basse (1)</span>
                      <span>Haute (10)</span>
                    </div>
                  </div>
                </div>

                <div className="flex justify-end space-x-3 pt-6">
                  <button
                    onClick={closeConnectModal}
                    className="px-4 py-2 text-ink bg-surface-2 rounded-lg hover:bg-surface-2 transition-colors"
                  >
                    Annuler
                  </button>
                  <button
                    onClick={handleConnect}
                    disabled={createMutation.isPending}
                    className="px-4 py-2 bg-accent text-white rounded-lg hover:bg-accent-strong transition-colors disabled:opacity-50 flex items-center space-x-2"
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
            <div className="bg-surface rounded-lg shadow-xl w-full max-w-md mx-4 p-6">
              <h3 className="text-lg font-bold text-ink mb-4">Confirmer la déconnexion</h3>
              <p className="text-ink-soft mb-6">
                Êtes-vous sûr de vouloir déconnecter ce fournisseur ? Les devis de cette source ne seront plus disponibles.
              </p>
              <div className="flex justify-end space-x-3">
                <button
                  onClick={() => setDeleteId(null)}
                  className="px-4 py-2 text-ink bg-surface-2 rounded-lg hover:bg-surface-2 transition-colors"
                >
                  Annuler
                </button>
                <button
                  onClick={() => deleteMutation.mutate(deleteId)}
                  disabled={deleteMutation.isPending}
                  className="px-4 py-2 bg-danger text-white rounded-lg hover:bg-danger/90 transition-colors disabled:opacity-50 flex items-center space-x-2"
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

export default Providers;
