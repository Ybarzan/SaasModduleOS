import { useQuery } from '@tanstack/react-query';
import { CheckCircle2, XCircle, HelpCircle, RefreshCw } from 'lucide-react';
import { incokalkAPI } from '../lib/api';
import Seo from '../components/Seo';
import Breadcrumbs from '../components/Breadcrumbs';

type HealthState = 'up' | 'down' | 'unknown';

const REFRESH_INTERVAL_MS = 30_000;

// Le seul signal réellement disponible côté backend est /actuator/health, un
// UP/DOWN global sans détail par composant (show-details n'est pas activé --
// voir lib/api.ts). Pas de page de statut historique/incidents ici, aucune
// donnée ne l'alimenterait honnêtement : juste le statut actuel, vérifié en
// direct, pas une capture figée qui pourrait mentir en cas d'incident réel.
export default function StatusPage() {
  const { data, isError, isFetching, dataUpdatedAt, errorUpdatedAt, refetch } = useQuery({
    queryKey: ['system-health'],
    queryFn: async () => {
      const res = await incokalkAPI.system.health();
      return res.data as { status?: string };
    },
    refetchInterval: REFRESH_INTERVAL_MS,
    retry: false,
  });

  // Dérivé de l'horodatage déjà suivi par react-query -- pas besoin d'un
  // useState/useEffect séparé rien que pour capturer "quand la dernière
  // requête a résolu".
  const lastCheckedAt = Math.max(dataUpdatedAt, errorUpdatedAt);
  const lastChecked = lastCheckedAt > 0 ? new Date(lastCheckedAt) : null;

  const state: HealthState = isError ? 'down' : data?.status === 'UP' ? 'up' : data ? 'down' : 'unknown';

  const display = {
    up: {
      icon: CheckCircle2,
      color: 'text-success',
      bg: 'bg-success/10 border-success/30',
      title: 'Tous les systèmes sont opérationnels',
    },
    down: {
      icon: XCircle,
      color: 'text-danger',
      bg: 'bg-danger/10 border-danger/30',
      title: 'Interruption de service en cours',
    },
    unknown: {
      icon: HelpCircle,
      color: 'text-ink-soft',
      bg: 'bg-surface-2 border-line',
      title: 'Vérification du statut...',
    },
  }[state];

  const Icon = display.icon;

  return (
    <div className="min-h-screen bg-gradient-to-br from-bg to-white">
      <Seo
        title="Statut du service"
        description="Statut en direct de la plateforme IncoKalk."
        path="/status"
        noindex
      />
      <div className="container mx-auto px-4 py-10 max-w-2xl">
        <Breadcrumbs items={[{ label: 'Statut' }]} />

        <div className="text-center my-10">
          <h1 className="text-3xl font-extrabold text-ink mb-3">
            <span className="text-accent font-normal" aria-hidden="true">:: </span>
            Statut du service
          </h1>
          <p className="text-ink-soft">Vérification en direct de la disponibilité d'IncoKalk.</p>
        </div>

        <div className={`relative rounded-none border p-8 text-center ${display.bg}`}>
          <span className="hud-corner hud-corner-tl" aria-hidden="true" />
          <span className="hud-corner hud-corner-tr" aria-hidden="true" />
          <span className="hud-corner hud-corner-bl" aria-hidden="true" />
          <span className="hud-corner hud-corner-br" aria-hidden="true" />
          <Icon size={48} className={`mx-auto mb-4 ${display.color}`} />
          <h2 className={`text-xl font-bold mb-2 ${display.color}`}>{display.title}</h2>
          {lastChecked && (
            <p className="text-sm text-ink-soft">
              Dernière vérification : {lastChecked.toLocaleTimeString('fr-FR')}
            </p>
          )}
          <button
            onClick={() => refetch()}
            disabled={isFetching}
            className="mt-4 inline-flex items-center gap-2 px-4 py-2 rounded-none text-sm font-medium text-ink-soft border border-line hover:border-accent hover:text-accent transition-colors disabled:opacity-50"
          >
            <RefreshCw size={14} className={isFetching ? 'animate-spin' : ''} />
            Vérifier maintenant
          </button>
        </div>

        <p className="text-xs text-ink-soft text-center mt-6">
          Ce statut reflète la disponibilité globale de l'API IncoKalk, vérifiée automatiquement
          toutes les 30 secondes. Pour un incident en cours, contactez{' '}
          <a href="mailto:contact@incokalk.com" className="text-accent hover:underline">
            contact@incokalk.com
          </a>
          .
        </p>
      </div>
    </div>
  );
}
