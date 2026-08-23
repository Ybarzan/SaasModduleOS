import { useEffect, useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Camera, Keyboard, Loader2, RefreshCw, WifiOff, Wifi, ScanLine, CheckCircle2 } from 'lucide-react';
import toast from 'react-hot-toast';
import { incokalkAPI } from '../lib/api';
import { useBarcodeScanner } from '../hooks/useBarcodeScanner';
import { offlineQueue } from '../lib/offlineQueue';

interface ReceivingOrder {
  id: string;
  orderNumber: string;
  status: 'DRAFT' | 'RECEIVING' | 'COMPLETED' | 'CANCELLED';
  warehouseId: string;
}

type ApiError = { response?: { data?: { message?: string } } };

const ScanReceiving = () => {
  const queryClient = useQueryClient();
  const [orderId, setOrderId] = useState('');
  const [camMode, setCamMode] = useState(true);
  const [barcode, setBarcode] = useState('');
  const [qty, setQty] = useState('1');
  const [lot, setLot] = useState('');
  const [expiry, setExpiry] = useState('');
  const [serial, setSerial] = useState('');
  const [pending, setPending] = useState(0);
  const [isOnline, setIsOnline] = useState(navigator.onLine);
  const [lastScan, setLastScan] = useState<string | null>(null);

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

  const { data } = useQuery({
    queryKey: ['receivings-open'],
    queryFn: async () => {
      const res = await incokalkAPI.receivings.list();
      return ((res?.data ?? []) as ReceivingOrder[]).filter(
        (o) => o.status === 'DRAFT' || o.status === 'RECEIVING'
      );
    },
  });
  const orders = Array.isArray(data) ? data : [];

  const { isScanning, error: camError } = useBarcodeScanner({
    active: camMode && !!orderId,
    onDetected: (result) => {
      setBarcode(result.text);
      handleScan(result.text);
    },
  });

  const scanMutation = useMutation({
    mutationFn: (code: string) =>
      incokalkAPI.receivings.scan(orderId, {
        barcode: code || undefined,
        quantity: Number(qty) || 1,
        lotNumber: lot || undefined,
        expiryDate: expiry || undefined,
        serialNumber: serial || undefined,
      }),
    onSuccess: () => {
      setLastScan(barcode || '—');
      setBarcode('');
      setLot('');
      setSerial('');
      setExpiry('');
      queryClient.invalidateQueries({ queryKey: ['receivings-open'] });
      queryClient.invalidateQueries({ queryKey: ['receiving-detail'] });
      queryClient.invalidateQueries({ queryKey: ['inventory-balances'] });
    },
    onError: (err: ApiError) => toast.error(err.response?.data?.message || 'Erreur lors du scan'),
  });

  const handleScan = async (code: string) => {
    if (!orderId) {
      toast.error('Sélectionnez d’abord un bon de réception');
      return;
    }
    const payload = {
      barcode: code || undefined,
      quantity: Number(qty) || 1,
      lotNumber: lot || undefined,
      expiryDate: expiry || undefined,
      serialNumber: serial || undefined,
    };
    if (!navigator.onLine) {
      await offlineQueue.enqueue(orderId, payload);
      setPending(await offlineQueue.count());
      toast.success('Hors ligne — scan mis en file d’attente');
      setBarcode('');
      return;
    }
    scanMutation.mutate(code);
  };

  const syncQueue = async () => {
    const queued = await offlineQueue.list();
    if (queued.length === 0) {
      toast('Aucun scan en attente');
      return;
    }
    let ok = 0;
    for (const item of queued) {
      try {
        await incokalkAPI.receivings.scan(item.orderId, item.payload);
        await offlineQueue.remove(item.id!);
        ok++;
      } catch {
        toast.error('Échec de synchronisation — certains scans restent en attente');
        break;
      }
    }
    setPending(await offlineQueue.count());
    queryClient.invalidateQueries();
    toast.success(`${ok} scan(s) synchronisé(s)`);
  };

  return (
    <div className="max-w-3xl mx-auto px-4 py-8">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between mb-8 gap-4">
        <div>
          <h1 className="text-2xl font-bold text-ink">Scanner une réception</h1>
          <p className="text-ink-soft mt-1">Scan code-barres / QR avec mode hors ligne</p>
        </div>
        <span className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-medium ${
          isOnline ? 'bg-success/15 text-success' : 'bg-warning/15 text-warning'
        }`}>
          {isOnline ? <Wifi size={14} /> : <WifiOff size={14} />}
          {isOnline ? 'En ligne' : 'Hors ligne'}
        </span>
      </div>

      <div className="bg-surface rounded-xl border border-line p-6 mb-6">
        <label className="block text-sm font-medium text-ink-soft mb-1">Bon de réception</label>
        <select
          value={orderId}
          onChange={(e) => setOrderId(e.target.value)}
          className="w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
        >
          <option value="">— Sélectionner un bon ouvert —</option>
          {orders.map((o) => (
            <option key={o.id} value={o.id}>{o.orderNumber} ({o.status})</option>
          ))}
        </select>
      </div>

      <div className="flex items-center gap-2 mb-4">
        <button
          onClick={() => setCamMode(true)}
          className={`flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
            camMode ? 'bg-accent text-white' : 'bg-surface-2 text-ink-soft hover:bg-line'
          }`}
        >
          <Camera size={16} />
          Caméra
        </button>
        <button
          onClick={() => setCamMode(false)}
          className={`flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
            !camMode ? 'bg-accent text-white' : 'bg-surface-2 text-ink-soft hover:bg-line'
          }`}
        >
          <Keyboard size={16} />
          Manuel
        </button>
      </div>

      <div className="bg-surface rounded-xl border border-line overflow-hidden mb-6">
        {camMode ? (
          <div className="relative bg-black">
            <video id="barcode-scanner-video" className="w-full h-72 object-cover" muted playsInline />
            {!isScanning && !camError && (
              <div className="absolute inset-0 flex flex-col items-center justify-center text-ink-soft">
                <Camera size={28} className="mb-2" />
                <p className="text-sm">Activation de la caméra...</p>
              </div>
            )}
            {camError && (
              <div className="absolute inset-0 flex flex-col items-center justify-center bg-ink/95 p-4 text-center">
                <p className="text-sm text-warning mb-2">{camError}</p>
                <button
                  onClick={() => setCamMode(false)}
                  className="text-sm text-white underline"
                >
                  Passer en saisie manuelle
                </button>
              </div>
            )}
            {!orderId && (
              <div className="absolute inset-0 flex items-center justify-center bg-ink/80">
                <p className="text-sm text-ink-soft">Sélectionnez un bon de réception pour activer le scanner</p>
              </div>
            )}
          </div>
        ) : (
          <div className="p-6">
            <label className="block text-sm font-medium text-ink-soft mb-1">Code-barres</label>
            <div className="flex gap-2">
              <input
                type="text"
                value={barcode}
                onChange={(e) => setBarcode(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') handleScan(barcode);
                }}
                className="flex-1 px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm font-mono"
                placeholder="Scanner ou saisir puis Entrée"
                autoFocus
              />
              <button
                onClick={() => handleScan(barcode)}
                disabled={scanMutation.isPending}
                className="flex items-center gap-2 bg-accent text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-accent-strong disabled:opacity-50 transition-colors"
              >
                {scanMutation.isPending ? <Loader2 size={16} className="animate-spin" /> : <ScanLine size={16} />}
                Scanner
              </button>
            </div>
          </div>
        )}
      </div>

      {lastScan && (
        <div className="mb-6 flex items-center gap-2 bg-success/10 border border-success/20 text-success rounded-lg px-4 py-3 text-sm">
          <CheckCircle2 size={16} />
          Dernier scan enregistré : <span className="font-mono font-semibold">{lastScan}</span>
        </div>
      )}

      <div className="bg-surface rounded-xl border border-line p-6 mb-6">
        <h3 className="text-sm font-semibold text-ink mb-3">Détails du scan</h3>
        <div className="grid grid-cols-1 sm:grid-cols-4 gap-3">
          <div>
            <label className="block text-xs font-medium text-ink-soft mb-1">Quantité</label>
            <input
              type="number"
              value={qty}
              onChange={(e) => setQty(e.target.value)}
              className="w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
              min={1}
              step={0.01}
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-ink-soft mb-1">N° de lot</label>
            <input
              type="text"
              value={lot}
              onChange={(e) => setLot(e.target.value)}
              className="w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-ink-soft mb-1">DLUO</label>
            <input
              type="date"
              value={expiry}
              onChange={(e) => setExpiry(e.target.value)}
              className="w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-ink-soft mb-1">N° de série</label>
            <input
              type="text"
              value={serial}
              onChange={(e) => setSerial(e.target.value)}
              className="w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
            />
          </div>
        </div>
      </div>

      <div className="bg-surface rounded-xl border border-line p-6">
        <div className="flex items-center justify-between mb-3">
          <h3 className="text-sm font-semibold text-ink flex items-center gap-2">
            <WifiOff size={14} className="text-warning" />
            File d’attente hors ligne
          </h3>
          <button
            onClick={syncQueue}
            disabled={pending === 0 || !isOnline}
            className="flex items-center gap-2 text-sm font-medium text-accent hover:text-accent-strong disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            <RefreshCw size={14} />
            Synchroniser ({pending})
          </button>
        </div>
        {pending === 0 ? (
          <p className="text-sm text-ink-soft">Aucun scan en attente.</p>
        ) : (
          <p className="text-sm text-warning">
            {pending} scan(s) en attente — ils seront envoyés au serveur au retour de la connexion.
          </p>
        )}
      </div>
    </div>
  );
};

export default ScanReceiving;
