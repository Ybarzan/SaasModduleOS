import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { Camera, CameraResultType, CameraSource } from '@capacitor/camera';
import { ArrowLeft, Camera as CameraIcon, CheckCircle2, Loader2, RotateCcw } from 'lucide-react';
import { mobileApi } from '../lib/api';
import { DOCUMENT_TYPE_LABELS } from '../types';
import type { ParsedDocument, ParsedDocumentType } from '../types';

const DOC_TYPES = Object.keys(DOCUMENT_TYPE_LABELS) as ParsedDocumentType[];

async function photoToBlob(webPath: string): Promise<Blob> {
  const res = await fetch(webPath);
  return res.blob();
}

const ScanDocument = () => {
  const navigate = useNavigate();
  const [documentType, setDocumentType] = useState<ParsedDocumentType>('COMMERCIAL_INVOICE');
  const [preview, setPreview] = useState<string | null>(null);
  const [captureError, setCaptureError] = useState<string | null>(null);

  const parse = useMutation({
    mutationFn: async (webPath: string) => {
      const blob = await photoToBlob(webPath);
      return (await mobileApi.documentParser.parseImage(blob, 'scan.jpg', documentType)).data as ParsedDocument;
    },
  });

  const takePhoto = async () => {
    setCaptureError(null);
    parse.reset();
    try {
      const photo = await Camera.getPhoto({
        resultType: CameraResultType.Uri,
        source: CameraSource.Prompt,
        quality: 85,
        promptLabelHeader: 'Scanner un document',
        promptLabelPhoto: 'Choisir depuis la galerie',
        promptLabelPicture: 'Prendre une photo',
      });
      if (!photo.webPath) return;
      setPreview(photo.webPath);
      parse.mutate(photo.webPath);
    } catch {
      // Annulation utilisateur (fermeture du sélecteur) -- pas une vraie erreur.
    }
  };

  const reset = () => {
    setPreview(null);
    setCaptureError(null);
    parse.reset();
  };

  const parsedFields = parse.data?.parsedData
    ? Object.entries(parse.data.parsedData).filter(([, v]) => v !== null && v !== undefined && v !== '')
    : [];

  return (
    <>
      <div className="header-bar row">
        <button onClick={() => navigate(-1)} style={{ background: 'none', border: 'none', cursor: 'pointer' }}>
          <ArrowLeft size={20} />
        </button>
        <h1 className="title" style={{ margin: 0, fontSize: 18 }}>Scanner un document</h1>
      </div>

      <div className="stack" style={{ marginTop: 12 }}>
        <div className="card">
          <p className="section-label">Type de document</p>
          <select
            className="input"
            value={documentType}
            onChange={(e) => setDocumentType(e.target.value as ParsedDocumentType)}
            disabled={parse.isPending}
          >
            {DOC_TYPES.map((t) => (
              <option key={t} value={t}>{DOCUMENT_TYPE_LABELS[t]}</option>
            ))}
          </select>
        </div>

        {preview && (
          <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
            <img src={preview} alt="Document scanné" style={{ width: '100%', display: 'block', maxHeight: 240, objectFit: 'cover' }} />
          </div>
        )}

        {captureError && <p className="error-text">{captureError}</p>}

        {!preview && (
          <button className="btn btn-primary btn-block" onClick={takePhoto}>
            <CameraIcon size={18} />
            Prendre une photo ou choisir un fichier
          </button>
        )}

        {preview && (
          <button className="btn btn-primary btn-block" style={{ background: 'rgb(var(--c-ink-soft))' }} onClick={reset}>
            <RotateCcw size={16} />
            Rescanner
          </button>
        )}

        {parse.isPending && (
          <div className="card row" style={{ justifyContent: 'center', gap: 10 }}>
            <Loader2 className="spin" size={18} color="rgb(var(--c-ink-soft))" />
            <span className="text-sm text-soft">Extraction du texte en cours…</span>
          </div>
        )}

        {parse.isError && (
          <p className="error-text">
            {(parse.error as { response?: { data?: { message?: string } } })?.response?.data?.message
              || "Impossible d'analyser ce document."}
          </p>
        )}

        {parse.isSuccess && parse.data && (
          <div className="card">
            <div className="row" style={{ gap: 8, marginBottom: 10 }}>
              <CheckCircle2 size={18} color="rgb(var(--c-accent))" />
              <p style={{ fontWeight: 700, margin: 0 }}>Document analysé</p>
              {parse.data.confidence !== undefined && (
                <span className="badge badge-accent" style={{ marginLeft: 'auto' }}>
                  {Math.round(parse.data.confidence * 100)}% confiance
                </span>
              )}
            </div>
            {parsedFields.length === 0 && (
              <p className="text-sm text-soft">Aucun champ structuré détecté — vérifiez le cadrage et réessayez.</p>
            )}
            {parsedFields.length > 0 && (
              <div className="stack">
                {parsedFields.map(([key, value]) => (
                  <div key={key} className="kv-row">
                    <span className="text-soft">{key}</span>
                    <span style={{ textAlign: 'right', maxWidth: '60%' }}>{String(value)}</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>
    </>
  );
};

export default ScanDocument;
