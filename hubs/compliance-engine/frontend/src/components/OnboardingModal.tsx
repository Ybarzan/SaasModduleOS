import { useState } from 'react';
import { Sparkles, LayoutGrid, Search, Compass } from 'lucide-react';
import Modal from './Modal';
import { useOnboardingStore } from '../stores/onboarding';

const SLIDES = [
  {
    icon: Sparkles,
    title: 'Bienvenue sur IncoKalk',
    body: "IncoKalk regroupe tous les outils dont vous avez besoin pour piloter vos opérations d'import-export, du calcul de devis jusqu'à la facturation.",
  },
  {
    icon: LayoutGrid,
    title: '7 Hubs métier',
    body: "La navigation est organisée par Hub (Import-Export, Transport, Documents, Entrepôt, Finance, Portail Client, Plateforme) plutôt que par département technique interne — retrouvez les outils par ce que vous voulez faire.",
  },
  {
    icon: Search,
    title: 'Recherche rapide',
    body: "Appuyez sur Ctrl+K (Cmd+K sur Mac) à tout moment pour retrouver n'importe quelle page sans naviguer dans les menus.",
  },
  {
    icon: Compass,
    title: 'Par où commencer',
    body: "Le Calculateur Incoterms, épinglé en haut du menu, est le point d'entrée le plus rapide. Vous pouvez revoir ce guide à tout moment depuis le bouton Aide.",
  },
];

/**
 * Avec 88 pages réparties en 7 Hubs, la richesse fonctionnelle devient un
 * obstacle sans guide pour un nouvel utilisateur -- c'est le risque identifié
 * en amont de la stratégie de productisation Hub, à résoudre côté UX plutôt
 * que côté pricing seul. Volontairement une modale statique à quelques écrans
 * (pas un tour interactif pointant sur la vraie UI) : aucune dépendance
 * externe, contenu simple à maintenir, testable sans manipulation du DOM réel.
 *
 * Le composant enfant n'est monté que quand isOpen est vrai (même pattern que
 * CommandPalette) : `step` revient à 0 via montage/démontage plutôt qu'un
 * useEffect qui le réinitialiserait.
 */
const OnboardingModal = () => {
  const isOpen = useOnboardingStore((s) => s.isOpen);
  const close = useOnboardingStore((s) => s.close);

  if (!isOpen) return null;
  return <OnboardingDialog onClose={close} />;
};

const OnboardingDialog = ({ onClose }: { onClose: () => void }) => {
  const [step, setStep] = useState(0);
  const slide = SLIDES[step];
  const Icon = slide.icon;
  const isLast = step === SLIDES.length - 1;

  return (
    <Modal open onClose={onClose} ariaLabel={slide.title} maxWidth="max-w-md">
      <div className="p-6 text-center">
        <div className="inline-flex items-center justify-center w-14 h-14 rounded-full bg-accent-soft mb-4">
          <Icon size={28} className="text-accent" />
        </div>
        <h2 className="text-lg font-bold text-ink mb-2">{slide.title}</h2>
        <p className="text-sm text-ink-soft mb-6">{slide.body}</p>

        <div className="flex items-center justify-center gap-1.5 mb-6">
          {SLIDES.map((s, i) => (
            <span
              key={s.title}
              className={`h-1.5 rounded-full transition-all ${i === step ? 'w-6 bg-accent' : 'w-1.5 bg-surface-2'}`}
            />
          ))}
        </div>

        <div className="flex items-center justify-between gap-3">
          <button
            onClick={onClose}
            className="text-sm text-ink-soft hover:text-ink transition-colors"
          >
            Passer
          </button>
          <button
            onClick={() => (isLast ? onClose() : setStep((s) => s + 1))}
            className="px-5 py-2 bg-accent text-white rounded-none text-sm font-medium hover:bg-accent-strong transition-colors"
          >
            {isLast ? 'Terminer' : 'Suivant'}
          </button>
        </div>
      </div>
    </Modal>
  );
};

export default OnboardingModal;
