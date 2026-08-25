import { useEffect, useRef, useState } from 'react';
import { AnimatePresence, motion, useReducedMotion } from 'framer-motion';
import type { LucideIcon } from 'lucide-react';
import { Ship, Building2, ShoppingCart, X, Check, Gift, ShieldCheck, Sparkles } from 'lucide-react';

interface PersonaScene {
  kind: 'persona';
  id: string;
  icon: LucideIcon;
  name: string;
  role: string;
  pain: string;
  solution: string;
  module: string;
}

interface DemoScene {
  kind: 'demo';
  id: string;
}

type Scene = PersonaScene | DemoScene;

// Les 3 profils type de /cas-usage, condensés pour un format "avant / après" --
// mêmes personas, même discipline (pas de client réel nommé). Le montant de la
// scène démo est un exemple illustratif de calcul, pas une simulation réelle.
const SCENES: Scene[] = [
  {
    kind: 'persona', id: 'sophie', icon: Ship, name: 'Sophie', role: 'PME exportatrice',
    pain: 'Jongle entre 4 portails transporteurs',
    solution: 'Compare tous ses transporteurs au même endroit',
    module: 'Transport Multimodal',
  },
  {
    kind: 'persona', id: 'marc', icon: Building2, name: 'Marc', role: 'Supply chain multi-sites',
    pain: 'Aucune vue consolidée entre filiales',
    solution: 'Une vue unique, toutes filiales confondues',
    module: 'Multi-branche',
  },
  {
    kind: 'persona', id: 'karim', icon: ShoppingCart, name: 'Karim', role: 'E-commerce cross-border',
    pain: '« Où est ma commande ? » en boucle',
    solution: 'Lien de suivi partagé automatiquement',
    module: 'Portail client',
  },
  { kind: 'demo', id: 'demo' },
];

const COST_ROWS = [
  { label: 'Transport', amount: 1850, width: 68 },
  { label: 'Droits de douane', amount: 620, width: 34 },
  { label: 'Assurance', amount: 145, width: 12 },
];
const TOTAL = COST_ROWS.reduce((sum, r) => sum + r.amount, 0);

const AUTO_ADVANCE_MS = 4500;

function useCountUp(target: number, skipAnimation: boolean, durationMs = 900) {
  const [value, setValue] = useState(skipAnimation ? target : 0);
  useEffect(() => {
    if (skipAnimation) return;
    let raf: number;
    const start = performance.now();
    const tick = (now: number) => {
      const progress = Math.min(1, (now - start) / durationMs);
      setValue(Math.round(target * (1 - Math.pow(1 - progress, 3))));
      if (progress < 1) raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [skipAnimation, target, durationMs]);
  return value;
}

function PersonaSceneCard({ scene }: { scene: PersonaScene }) {
  const Icon = scene.icon;
  return (
    <div className="flex flex-col h-full">
      <div className="flex items-center gap-3 mb-5">
        <span className="inline-flex items-center justify-center w-11 h-11 rounded-none bg-accent-soft text-accent-strong shrink-0">
          <Icon size={20} />
        </span>
        <div>
          <p className="font-bold text-ink text-sm">{scene.name}</p>
          <p className="text-xs text-ink-soft">{scene.role}</p>
        </div>
      </div>

      <div className="flex items-start gap-2.5 mb-3">
        <span className="inline-flex items-center justify-center w-5 h-5 rounded-full bg-danger/10 text-danger shrink-0 mt-0.5">
          <X size={12} />
        </span>
        <p className="text-sm text-ink-soft line-through decoration-danger/40">{scene.pain}</p>
      </div>
      <div className="flex items-start gap-2.5 mb-5">
        <span className="inline-flex items-center justify-center w-5 h-5 rounded-full bg-success/10 text-success shrink-0 mt-0.5">
          <Check size={12} />
        </span>
        <p className="text-sm text-ink font-medium">{scene.solution}</p>
      </div>

      <span className="mt-auto self-start inline-flex items-center gap-1.5 text-xs bg-surface-2 text-ink rounded-full px-3 py-1">
        {scene.module}
      </span>
    </div>
  );
}

function DemoSceneCard({ reduceMotion }: { reduceMotion: boolean }) {
  const total = useCountUp(TOTAL, reduceMotion);
  return (
    <div className="flex flex-col h-full">
      <div className="flex items-center gap-2 mb-5">
        <span className="relative flex h-2 w-2">
          <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-accent opacity-75" />
          <span className="relative inline-flex rounded-full h-2 w-2 bg-accent" />
        </span>
        <p className="font-bold text-ink text-sm">Calculateur Incoterms — en direct</p>
      </div>

      <div className="space-y-2.5 mb-4">
        {COST_ROWS.map((row) => (
          <div key={row.label} className="flex items-center gap-3">
            <span className="text-xs text-ink-soft w-28 shrink-0 font-medium">{row.label}</span>
            <div className="flex-1 bg-surface-2 rounded-full h-2 overflow-hidden">
              <motion.div
                className="h-full bg-accent rounded-full"
                initial={{ width: reduceMotion ? `${row.width}%` : 0 }}
                animate={{ width: `${row.width}%` }}
                transition={{ duration: reduceMotion ? 0 : 0.8, ease: 'easeOut' }}
              />
            </div>
          </div>
        ))}
      </div>

      <div className="flex items-baseline gap-1.5 mb-5">
        <span className="text-xs text-ink-soft font-medium">Total DAP</span>
        <span className="text-2xl font-extrabold text-ink">{total.toLocaleString('fr-FR')} €</span>
      </div>

      <div className="mt-auto flex flex-wrap gap-2">
        <span className="inline-flex items-center gap-1.5 text-xs bg-surface-2 text-ink rounded-full px-3 py-1">
          <ShieldCheck size={12} className="text-success" />
          TLS chiffré
        </span>
        <span className="inline-flex items-center gap-1.5 text-xs bg-surface-2 text-ink rounded-full px-3 py-1">
          <Sparkles size={12} className="text-accent" />
          7 Hubs métier
        </span>
      </div>
    </div>
  );
}

/** Vignette animée du hero -- alterne les douleurs des 3 profils type (voir
 * /cas-usage) et une démo du calculateur, en boucle. Respecte prefers-reduced-motion
 * (voir PageReveal.tsx pour le même bail-out) : pas d'autoplay ni de transition,
 * juste la 1ère scène affichée statiquement avec la navigation manuelle active. */
export default function HeroShowcase() {
  const [index, setIndex] = useState(0);
  const [hovered, setHovered] = useState(false);
  const reduceMotion = useReducedMotion();
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    if (reduceMotion || hovered) return;
    timerRef.current = setInterval(() => {
      setIndex((i) => (i + 1) % SCENES.length);
    }, AUTO_ADVANCE_MS);
    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, [reduceMotion, hovered]);

  const scene = SCENES[index];

  return (
    <div className="relative max-w-md mx-auto lg:mx-0">
      {/* Overlays flottants -- revendications réelles (essai 14j : BillingService,
          parrainage : /billing, voir project_incokalk_marketing_backlog memory) */}
      <div className="hidden sm:flex absolute -top-4 -right-4 z-20 items-center gap-1.5 bg-surface border border-line rounded-none px-3 py-2 shadow-lg animate-float">
        <ShieldCheck size={16} className="text-success shrink-0" />
        <span className="text-xs font-semibold text-ink whitespace-nowrap">Essai 14 jours, sans engagement</span>
      </div>
      <div className="hidden sm:flex absolute -bottom-4 -left-4 z-20 items-center gap-1.5 bg-surface border border-line rounded-none px-3 py-2 shadow-lg animate-float" style={{ animationDelay: '2s' }}>
        <Gift size={16} className="text-accent shrink-0" />
        <span className="text-xs font-semibold text-ink whitespace-nowrap">1 mois offert en parrainant</span>
      </div>

      <div
        className="bg-bg rounded-none border border-line p-6 shadow-xl shadow-accent/5"
        onMouseEnter={() => setHovered(true)}
        onMouseLeave={() => setHovered(false)}
      >
        <div className="bg-surface rounded-none p-6 border border-line min-h-[240px] flex flex-col overflow-hidden">
          <div className="flex items-center gap-2 mb-4">
            <div className="w-2.5 h-2.5 rounded-full bg-accent" />
            <div className="w-2.5 h-2.5 rounded-full bg-accent/60" />
            <div className="w-2.5 h-2.5 rounded-full bg-accent/30" />
          </div>

          <div className="flex-1 relative">
            {reduceMotion ? (
              scene.kind === 'persona' ? <PersonaSceneCard scene={scene} /> : <DemoSceneCard reduceMotion={true} />
            ) : (
              <AnimatePresence mode="wait">
                <motion.div
                  key={scene.id}
                  initial={{ opacity: 0, x: 16 }}
                  animate={{ opacity: 1, x: 0 }}
                  exit={{ opacity: 0, x: -16 }}
                  transition={{ duration: 0.35, ease: 'easeOut' }}
                  className="h-full"
                >
                  {scene.kind === 'persona' ? <PersonaSceneCard scene={scene} /> : <DemoSceneCard reduceMotion={false} />}
                </motion.div>
              </AnimatePresence>
            )}
          </div>
        </div>

        <div className="flex items-center justify-center gap-2 mt-4">
          {SCENES.map((s, i) => (
            <button
              key={s.id}
              onClick={() => setIndex(i)}
              aria-label={`Scène ${i + 1}`}
              className={`h-1.5 rounded-full transition-all ${
                i === index ? 'w-6 bg-accent' : 'w-1.5 bg-surface-2 hover:bg-line'
              }`}
            />
          ))}
        </div>
      </div>
    </div>
  );
}
