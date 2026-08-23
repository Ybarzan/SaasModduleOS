import { useState, useEffect } from 'react';
import { Download, X } from 'lucide-react';

interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

const PWAInstallPrompt = () => {
  const [deferredPrompt, setDeferredPrompt] = useState<BeforeInstallPromptEvent | null>(null);
  const [showPrompt, setShowPrompt] = useState(false);

  useEffect(() => {
    const handler = (e: Event) => {
      const installEvent = e as BeforeInstallPromptEvent;
      installEvent.preventDefault();
      setDeferredPrompt(installEvent);
      if (!window.sessionStorage.getItem('pwa-dismissed')) {
        setTimeout(() => setShowPrompt(true), 30000);
      }
    };
    window.addEventListener('beforeinstallprompt', handler);
    return () => window.removeEventListener('beforeinstallprompt', handler);
  }, []);

  const handleInstall = async () => {
    if (!deferredPrompt) return;
    deferredPrompt.prompt();
    const { outcome } = await deferredPrompt.userChoice;
    if (outcome === 'accepted') {
      setShowPrompt(false);
      setDeferredPrompt(null);
    }
  };

  const handleDismiss = () => {
    setShowPrompt(false);
    setDeferredPrompt(null);
    sessionStorage.setItem('pwa-dismissed', 'true');
  };

  if (!showPrompt || !deferredPrompt) return null;

  return (
    <div className="fixed bottom-4 left-4 right-4 md:left-auto md:right-4 md:w-80 z-50">
      <div className="bg-surface rounded-xl shadow-2xl border border-line p-4">
        <div className="flex items-start justify-between mb-3">
          <div className="flex items-center space-x-2">
            <div className="w-10 h-10 bg-accent rounded-lg flex items-center justify-center">
              <Download size={20} className="text-white" />
            </div>
            <div>
              <h4 className="font-semibold text-ink text-sm">Installer IncoKalk</h4>
              <p className="text-xs text-ink-soft">Accès rapide depuis votre écran d'accueil</p>
            </div>
          </div>
          <button onClick={handleDismiss} className="text-ink-soft hover:text-ink p-1">
            <X size={16} />
          </button>
        </div>
        <div className="flex space-x-2">
          <button
            onClick={handleInstall}
            className="flex-1 bg-accent text-white text-sm py-2 rounded-lg hover:bg-accent-strong transition-colors font-medium"
          >
            Installer
          </button>
          <button
            onClick={handleDismiss}
            className="px-4 py-2 text-sm text-ink-soft hover:text-ink transition-colors"
          >
            Plus tard
          </button>
        </div>
      </div>
    </div>
  );
};

export default PWAInstallPrompt;
