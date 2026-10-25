import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Link2, Loader2, Share2 } from 'lucide-react';
import toast from 'react-hot-toast';
import { logistixAPI } from '../lib/api';

type ApiError = { response?: { data?: { message?: string } } };
type PraxioIntegrationSettings = { praxioIntegrationEnabled: boolean; praxioIntegrationApiKey: string | null };

const Settings = () => {
  const queryClient = useQueryClient();
  const [apiKey, setApiKey] = useState('');

  const { data: status, isLoading } = useQuery({
    queryKey: ['fleethub-key-status'],
    queryFn: async () => (await logistixAPI.company.fleetHubKeyStatus()).data as { configured: boolean },
  });

  const saveMutation = useMutation({
    mutationFn: () => logistixAPI.company.setFleetHubKey(apiKey),
    onSuccess: () => {
      toast.success('Clé fleet-hub enregistrée');
      setApiKey('');
      queryClient.invalidateQueries({ queryKey: ['fleethub-key-status'] });
    },
    onError: (err: ApiError) => {
      toast.error(err.response?.data?.message || "Échec de l'enregistrement de la clé");
    },
  });

  const clearMutation = useMutation({
    mutationFn: () => logistixAPI.company.clearFleetHubKey(),
    onSuccess: () => {
      toast.success('Clé fleet-hub effacée');
      queryClient.invalidateQueries({ queryKey: ['fleethub-key-status'] });
    },
    onError: (err: ApiError) => {
      toast.error(err.response?.data?.message || "Échec de l'effacement de la clé");
    },
  });

  return (
    <div className="max-w-4xl mx-auto px-4 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-ink">
          <span className="text-accent font-normal" aria-hidden="true">
            ::{' '}
          </span>
          Paramètres
        </h1>
        <p className="text-ink-soft mt-1">Intégrations de Logistix avec les autres modules Praxio</p>
      </div>

      <div className="bg-surface rounded-none border border-line overflow-hidden">
        <div className="px-6 py-4 border-b border-line flex items-center gap-2">
          <Link2 size={18} className="text-accent" />
          <h2 className="text-lg font-semibold text-ink">Intégration Fleet Hub</h2>
        </div>
        <div className="px-6 py-4 space-y-4">
          <p className="text-sm text-ink-soft">
            Permet de dériver automatiquement les lignes attendues d'un bon de réception depuis les
            articles d'une expédition Fleet Hub. Générez la clé dans Fleet Hub (Administration →
            « Logistix &amp; Compliance Engine »), puis collez-la ici.
          </p>
          {isLoading ? (
            <p className="text-sm text-ink-soft flex items-center gap-2">
              <Loader2 size={14} className="animate-spin" /> Chargement…
            </p>
          ) : status?.configured ? (
            <div className="flex items-center justify-between gap-4">
              <span className="inline-flex items-center gap-2 text-sm font-medium text-ink">
                <span className="w-2 h-2 rounded-full bg-success" aria-hidden="true" />
                Clé configurée
              </span>
              <button
                type="button"
                onClick={() => clearMutation.mutate()}
                disabled={clearMutation.isPending}
                className="px-4 py-2 border border-line rounded-none text-sm font-medium text-ink hover:bg-bg transition-colors disabled:opacity-50"
              >
                {clearMutation.isPending ? 'Effacement…' : 'Effacer la clé'}
              </button>
            </div>
          ) : (
            <div className="flex flex-col sm:flex-row gap-3">
              <input
                type="text"
                value={apiKey}
                onChange={(e) => setApiKey(e.target.value)}
                placeholder="Clé d'intégration fleet-hub (X-Internal-Integration-Key)"
                className="flex-1 px-3 py-2 border border-line rounded-none text-sm font-mono focus:ring-2 focus:ring-accent focus:border-transparent"
              />
              <button
                type="button"
                onClick={() => saveMutation.mutate()}
                disabled={saveMutation.isPending || !apiKey.trim()}
                className="px-4 py-2 bg-accent text-white rounded-none text-sm font-medium hover:bg-accent-strong disabled:opacity-50 transition-colors"
              >
                {saveMutation.isPending ? 'Enregistrement…' : 'Enregistrer'}
              </button>
            </div>
          )}
        </div>
      </div>

      <div className="bg-surface rounded-none border border-line overflow-hidden mt-6">
        <div className="px-6 py-4 border-b border-line flex items-center gap-2">
          <Share2 size={18} className="text-accent" />
          <h2 className="text-lg font-semibold text-ink">Tableau de bord Praxio</h2>
        </div>
        <PraxioIntegrationCard />
      </div>
    </div>
  );
};

const PraxioIntegrationCard = () => {
  const queryClient = useQueryClient();
  const [newKey, setNewKey] = useState<string | null>(null);

  const { data: settings, isLoading } = useQuery({
    queryKey: ['praxio-integration-settings'],
    queryFn: async () => (await logistixAPI.praxioIntegration.settings()).data as PraxioIntegrationSettings,
  });

  const enableMutation = useMutation({
    mutationFn: () => logistixAPI.praxioIntegration.enable(),
    onSuccess: (res) => {
      const data = res.data as PraxioIntegrationSettings;
      setNewKey(data.praxioIntegrationApiKey);
      queryClient.invalidateQueries({ queryKey: ['praxio-integration-settings'] });
    },
    onError: (err: ApiError) => {
      toast.error(err.response?.data?.message || "Échec de l'activation");
    },
  });

  const disableMutation = useMutation({
    mutationFn: () => logistixAPI.praxioIntegration.disable(),
    onSuccess: () => {
      setNewKey(null);
      toast.success('Partage désactivé');
      queryClient.invalidateQueries({ queryKey: ['praxio-integration-settings'] });
    },
    onError: (err: ApiError) => {
      toast.error(err.response?.data?.message || 'Échec de la désactivation');
    },
  });

  const copyKey = async () => {
    if (!newKey) return;
    try {
      await navigator.clipboard.writeText(newKey);
      toast.success('Copié dans le presse-papiers');
    } catch {
      toast.error('Impossible de copier — copiez le texte manuellement');
    }
  };

  return (
    <div className="px-6 py-4 space-y-4">
      <p className="text-sm text-ink-soft">
        Autorise le tableau de bord cross-hub de Praxio à lire un résumé d'activité (entrepôts
        actifs, réceptions ouvertes, écarts ouverts) via une clé propre à votre société, jamais un
        accès direct à vos données.
      </p>
      {isLoading ? (
        <p className="text-sm text-ink-soft flex items-center gap-2">
          <Loader2 size={14} className="animate-spin" /> Chargement…
        </p>
      ) : settings?.praxioIntegrationEnabled ? (
        <div className="flex items-center justify-between gap-4">
          <span className="inline-flex items-center gap-2 text-sm font-medium text-ink">
            <span className="w-2 h-2 rounded-full bg-success" aria-hidden="true" />
            Partage activé
          </span>
          <button
            type="button"
            onClick={() => disableMutation.mutate()}
            disabled={disableMutation.isPending}
            className="px-4 py-2 border border-line rounded-none text-sm font-medium text-ink hover:bg-bg transition-colors disabled:opacity-50"
          >
            {disableMutation.isPending ? 'Désactivation…' : 'Désactiver le partage'}
          </button>
        </div>
      ) : (
        <button
          type="button"
          onClick={() => enableMutation.mutate()}
          disabled={enableMutation.isPending}
          className="px-4 py-2 bg-accent text-white rounded-none text-sm font-medium hover:bg-accent-strong disabled:opacity-50 transition-colors"
        >
          {enableMutation.isPending ? 'Activation…' : 'Activer le partage'}
        </button>
      )}
      {newKey && (
        <div className="space-y-2">
          <p className="text-sm text-ink-soft">Montrée une seule fois — notez-la maintenant :</p>
          <div className="flex items-center gap-2">
            <code className="flex-1 px-3 py-2 bg-bg border border-line text-sm font-mono overflow-x-auto">
              {newKey}
            </code>
            <button
              type="button"
              onClick={copyKey}
              className="px-3 py-2 border border-line rounded-none text-sm font-medium text-ink hover:bg-bg transition-colors"
            >
              Copier
            </button>
          </div>
          <p className="text-sm text-ink-soft">
            Collez cette clé dans les paramètres du tableau de bord de Praxio.
          </p>
        </div>
      )}
    </div>
  );
};

export default Settings;
