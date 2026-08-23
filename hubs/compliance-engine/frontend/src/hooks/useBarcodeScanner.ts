import { useEffect, useRef, useState } from 'react';
import { BrowserMultiFormatReader, NotFoundException } from '@zxing/library';

export interface ScanResult {
  text: string;
  format?: string;
}

export interface BarcodeScannerOptions {
  active: boolean;
  onDetected: (result: ScanResult) => void;
  onError?: (error: unknown) => void;
  videoId?: string;
}

export function useBarcodeScanner({ active, onDetected, onError, videoId = 'barcode-scanner-video' }: BarcodeScannerOptions) {
  const [isScanning, setIsScanning] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const readerRef = useRef<BrowserMultiFormatReader | null>(null);
  const onDetectedRef = useRef(onDetected);

  useEffect(() => {
    onDetectedRef.current = onDetected;
  });

  useEffect(() => {
    if (!active) return;
    let cancelled = false;

    const start = async () => {
      try {
        const reader = new BrowserMultiFormatReader(undefined, 300);
        readerRef.current = reader;
        setIsScanning(true);
        setError(null);
        await reader.decodeFromVideoDevice(null, videoId, (result, err) => {
          if (cancelled) return;
          if (result) {
            const text = result.getText();
            if (text) {
              onDetectedRef.current({
                text,
                format: result.getBarcodeFormat()?.toString() ?? undefined,
              });
            }
            return;
          }
          if (err instanceof NotFoundException) {
            // no barcode in frame yet — keep scanning
            return;
          }
        });
      } catch (e: unknown) {
        if (cancelled) return;
        const errObj = e as { name?: string; message?: string };
        const msg = errObj?.name === 'NotAllowedError'
          ? 'Accès caméra refusé. Autorisez la caméra dans votre navigateur.'
          : errObj?.message || 'Erreur caméra';
        setError(msg);
        if (onError) onError(e);
      }
    };

    start();

    return () => {
      cancelled = true;
      setIsScanning(false);
      const reader = readerRef.current;
      if (reader) {
        try {
          reader.reset();
        } catch {
          // ignore
        }
      }
      readerRef.current = null;
    };
  }, [active, videoId, onError]);

  return { isScanning, error };
}
