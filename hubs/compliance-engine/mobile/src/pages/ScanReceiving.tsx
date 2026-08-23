import { useEffect, useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { Camera, CameraResultType, CameraSource } from '@capacitor/camera';
import { BrowserMultiFormatReader } from '@zxing/library';
import { ArrowLeft, ScanLine, Loader2, RefreshCw, WifiOff, CheckCircle2 } from 'lucide-react';
import { mobileApi } from '../lib/api';
import { offlineQueue } from '../lib/offlineQueue';

interface ReceivingOrder {
  id: string;
  orderNumber: string;
  status: 'DRAFT' | 'RECEIVING' | 'COMPLETED' | 'CANCELLED';
}

// Capture photo (@capacitor/camera, déjà utilisé par ScanDocument.tsx) puis décodage
// sur l'image fixe (zxing decodeFromImageUrl) plutôt qu'un flux vidéo live comme sur
// web -- getUserMedia en direct est peu fiable dans une webview Capacitor native,
// la capture photo est le pattern déjà éprouvé dans cette app.
const ScanReceiving = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [orderId, setOrderId] = useState('');
  const [barcode, setBarcode] = useState('');
  const [qty, setQty] = useState('1');
  const [lot, setLot] = useState('');
  const [pending, setPending] = useState(0);
  const [isOnline, setIsOnline] = useState(navigator.onLine);
  const [lastScan, setLastScan] = useState<string | null>(null);
  const [decodeError, setDecodeError] = useState<string | null>(null);

  useEffect(() => {
    const handleOnline = () => setIsOnline(true);
    const handleOffline = () => setIsOnline(false);
    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);
    return () => {
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
    };
  }, []);

  useEffect(() => {
    let cancelled = false;
    offlineQueue.count().then((n) => {
      if (!cancelled) setPending(n);
    });
    return () => {
      cancelled = true;
    };
  }, []);

  const { data: orders = [] } = useQuery({
    queryKey: ['mobile-receivings-open'],
    queryFn: async () => {
      const res = await mobileApi.receivings.list();
      return ((res.data || []) as ReceivingOrder[]).filter((o) => o.status === 'DRAFT' || o.status === 'RECEIVING');
    },
  });

  const scanMutation = useMutation({
    mutationFn: (code: string) =>
      mobileApi.receivings.scan(orderId, { barcode: code || undefined, quantity: Number(qty) || 1, lotNumber: lot || undefined }),
    onSuccess: (_data, code) => {
      // `code` = l'argument passé à mutate(), pas l'état `barcode` -- capturer l'état
      // ici lirait une valeur obsolète (le scan caméra appelle setBarcode(text) puis
      // handleScan(text) dans la même passe, avant que le re-render n'ait eu lieu).
      setLastScan(code || '—');
      setBarcode('');
      setLot('');
      queryClient.invalidateQueries({ queryKey: ['mobile-receivings-open'] });
    },
  });

  const handleScan = async (code: string) => {
    if (!orderId) return;
    const payload = { barcode: code || undefined, quantity: Number(qty) || 1, lotNumber: lot || undefined };
    if (!navigator.onLine) {
      await offlineQueue.enqueue(orderId, payload);
      setPending(await offlineQueue.count());
      setBarcode('');
      return;
    }
    scanMutation.mutate(code);
  };

  const capture = async () => {
    setDecodeError(null);
    try {
      const photo = await Camera.getPhoto({
        resultType: CameraResultType.Uri,
        source: CameraSource.Camera,
        quality: 90,
        promptLabelPicture: 'Prendre une photo du code-barres',
      });
      if (!photo.webPath) return;
      const reader = new BrowserMultiFormatReader();
      try {
        const result = await reader.decodeFromImageUrl(photo.webPath);
        const text = result.getText();
        setBarcode(text);
        handleScan(text);
      } catch {
        setDecodeError('Aucun code-barres détecté sur la photo — réessayez ou saisissez-le manuellement.');
      }
    } catch {
      // Annulation utilisateur.
    }
  };

  const syncQueue = async () => {
    const queued = await offlineQueue.list();
    if (queued.length === 0) return;
    for (const item of queued) {
      try {
        await mobileApi.receivings.scan(item.orderId, item.payload);
        await offlineQueue.remove(item.id!);
      } catch {
        break;
      }
    }
    setPending(await offlineQueue.count());
    queryClient.invalidateQueries();
  };

  return (
    <>
      <div className="header-bar row-between">
        <div className="row" style={{ gap: 8 }}>
          <button onClick={() => navigate(-1)} style={{ background: 'none', border: 'none', cursor: 'pointer' }}>
            <ArrowLeft size={20} />
          </button>
          <h1 className="title" style={{ margin: 0, fontSize: 18 }}>Scanner réception</h1>
        </div>
        {!isOnline && <WifiOff size={18} color="rgb(var(--c-warning))" />}
      </div>

      <div className="stack" style={{ marginTop: 12 }}>
        <div className="card">
          <p className="section-label">Bon de réception</p>
          <select className="input" value={orderId} onChange={(e) => setOrderId(e.target.value)}>
            <option value="">— Sélectionner —</option>
            {orders.map((o) => (
              <option key={o.id} value={o.id}>{o.orderNumber} ({o.status})</option>
            ))}
          </select>
        </div>

        <button className="btn btn-primary btn-block" onClick={capture} disabled={!orderId}>
          <ScanLine size={18} />
          Photographier le code-barres
        </button>
        {decodeError && <p className="error-text">{decodeError}</p>}

        <div className="card stack">
          <p className="section-label">Ou saisir manuellement</p>
          <input
            className="input"
            style={{ fontFamily: 'monospace' }}
            placeholder="Code-barres"
            value={barcode}
            onChange={(e) => setBarcode(e.target.value)}
          />
          <div className="row" style={{ gap: 8 }}>
            <input className="input" type="number" min="1" placeholder="Qté" value={qty} onChange={(e) => setQty(e.target.value)} style={{ width: 80 }} />
            <input className="input" placeholder="N° de lot (optionnel)" value={lot} onChange={(e) => setLot(e.target.value)} style={{ flex: 1 }} />
          </div>
          <button
            className="btn btn-primary btn-block"
            onClick={() => handleScan(barcode)}
            disabled={!orderId || !barcode.trim() || scanMutation.isPending}
          >
            {scanMutation.isPending ? <Loader2 size={16} className="spin" /> : 'Enregistrer le scan'}
          </button>
        </div>

        {lastScan && (
          <div className="card row" style={{ gap: 8, color: 'rgb(var(--c-accent))' }}>
            <CheckCircle2 size={16} />
            <span className="text-sm">Dernier scan : <strong style={{ fontFamily: 'monospace' }}>{lastScan}</strong></span>
          </div>
        )}

        {pending > 0 && (
          <button className="card row-between" onClick={syncQueue} style={{ border: 'none', width: '100%', textAlign: 'left', cursor: 'pointer' }}>
            <span className="text-sm text-soft">{pending} scan(s) en attente de synchronisation</span>
            <RefreshCw size={16} color="rgb(var(--c-accent))" />
          </button>
        )}
      </div>
    </>
  );
};

export default ScanReceiving;
