import { useState, useEffect } from 'react';
import { WifiOff } from 'lucide-react';

const OfflineIndicator = () => {
  const [isOffline, setIsOffline] = useState(!navigator.onLine);

  useEffect(() => {
    const handleOnline = () => setIsOffline(false);
    const handleOffline = () => setIsOffline(true);
    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);
    return () => {
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
    };
  }, []);

  if (!isOffline) return null;

  return (
    <div className="fixed top-0 left-0 right-0 bg-warning text-white text-center py-2 text-sm font-medium z-[100] flex items-center justify-center space-x-2">
      <WifiOff size={16} />
      <span>Vous êtes hors ligne — certaines fonctionnalités sont indisponibles</span>
    </div>
  );
};

export default OfflineIndicator;
