import { useEffect, useState } from 'react';
import { Network } from '@capacitor/network';
import { WifiOff } from 'lucide-react';

const OfflineBanner = () => {
  const [offline, setOffline] = useState(false);

  useEffect(() => {
    let mounted = true;
    Network.getStatus().then((s) => mounted && setOffline(!s.connected));
    const listener = Network.addListener('networkStatusChange', (s) => setOffline(!s.connected));
    return () => {
      mounted = false;
      listener.then((l) => l.remove());
    };
  }, []);

  if (!offline) return null;

  return (
    <div
      className="row"
      style={{
        justifyContent: 'center',
        gap: 6,
        background: 'rgb(var(--c-danger) / 0.12)',
        color: 'rgb(var(--c-danger))',
        fontSize: 12,
        fontWeight: 600,
        padding: '6px 12px',
      }}
    >
      <WifiOff size={13} /> Pas de connexion — certaines données peuvent être obsolètes
    </div>
  );
};

export default OfflineBanner;
