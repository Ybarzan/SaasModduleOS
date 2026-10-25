import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Share2, Loader2 } from 'lucide-react';
import toast from 'react-hot-toast';
import { financeAPI } from '../lib/api';

type ApiError = { response?: { data?: { message?: string } } };
type InternalIntegrationSettings = { internalIntegrationEnabled: boolean; internalIntegrationApiKey: string | null };

const Settings = () => {
  return (
    <div className="max-w-4xl mx-auto px-4 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-ink">
          <span className="text-accent font-normal" aria-hidden="true">
            ::{' '}
          </span>
          Paramètres
        </h1>
        <p className="text-ink-soft mt-1">Intégrations de Finance avec les autres modules Praxio</p>
      </div>

      <div className="bg-surface rounded-lg border border-line overflow-hidden">
        <div className="px-6 py-4 border-b border-line flex items-center gap-2">
          <Share2 size={18} className="text-accent" />
          <h2 className="text-lg font-semibold text-ink">Intégration interne (Praxio &amp; compliance-engine)</h2>
        </div>
        <InternalIntegrationCard />
      </div>
    </div>
  );
};

const InternalIntegrationCard = () => {
  const queryClient = useQueryClient();
  const [newKey, setNewKey] = useState<string | null>(null);

  const { data: settings, isLoading } = useQuery({
    queryKey: ['internal-integration-settings'],
    queryFn: async () => (await financeAPI.internalIntegration.settings()).data as InternalIntegrationSettings,
  });

  const enableMutation = useMutation({
    mutationFn: () => financeAPI.internalIntegration.enable(),
    onSuccess: (res) => {
      const data = res.data as InternalIntegrationSettings;
      setNewKey(data.internalIntegrationApiKey);
      queryClient.invalidateQueries({ queryKey: ['internal-integration-settings'] });
    },
    onError: (err: ApiError) => {
      toast.error(err.response?.data?.message || "Échec de l'activation");
    },
  });

  const disableMutation = useMutation({
    mutationFn: () => financeAPI.internalIntegration.disable(),
    onSuccess: () => {
      setNewKey(null);
      toast.success('Partage désactivé');
      queryClient.invalidateQueries({ queryKey: ['internal-integration-settings'] });
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
        Autorise le tableau de bord cross-hub de Praxio et compliance-engine (ponctualité de
        facturation, consolidation multi-filiales, exposition de change) à lire des agrégats Finance
        via une clé propre à votre société, jamais un accès direct à vos données.
      </p>
      {isLoading ? (
        <p className="text-sm text-ink-soft flex items-center gap-2">
          <Loader2 size={14} className="animate-spin" /> Chargement…
        </p>
      ) : settings?.internalIntegrationEnabled ? (
        <div className="flex items-center justify-between gap-4">
          <span className="inline-flex items-center gap-2 text-sm font-medium text-ink">
            <span className="w-2 h-2 rounded-full bg-success" aria-hidden="true" />
            Partage activé
          </span>
          <button
            type="button"
            onClick={() => disableMutation.mutate()}
            disabled={disableMutation.isPending}
            className="px-4 py-2 border border-line rounded-lg text-sm font-medium text-ink hover:bg-bg transition-colors disabled:opacity-50"
          >
            {disableMutation.isPending ? 'Désactivation…' : 'Désactiver le partage'}
          </button>
        </div>
      ) : (
        <button
          type="button"
          onClick={() => enableMutation.mutate()}
          disabled={enableMutation.isPending}
          className="px-4 py-2 bg-accent text-white rounded-lg text-sm font-medium hover:bg-accent-strong disabled:opacity-50 transition-colors"
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
              className="px-3 py-2 border border-line rounded-lg text-sm font-medium text-ink hover:bg-bg transition-colors"
            >
              Copier
            </button>
          </div>
          <p className="text-sm text-ink-soft">
            Collez cette clé dans les paramètres du tableau de bord de Praxio, et dans les paramètres
            d'intégration Finance de compliance-engine.
          </p>
        </div>
      )}
    </div>
  );
};

export default Settings;
