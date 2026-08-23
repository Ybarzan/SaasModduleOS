import { useState, useRef } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Upload, X, FileText, Loader2 } from 'lucide-react';
import toast from 'react-hot-toast';
import { incokalkAPI } from '../lib/api';

interface CsvImportModalProps {
  isOpen: boolean;
  onClose: () => void;
  title: string;
  endpoint: 'carriers';
  queryKey: string[];
}

export default function CsvImportModal({ isOpen, onClose, title, queryKey }: CsvImportModalProps) {
  const queryClient = useQueryClient();
  const fileRef = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<{ headers: string[]; preview: string[][] } | null>(null);

  const previewMutation = useMutation({
    mutationFn: async (f: File) => {
      const formData = new FormData();
      formData.append('file', f);
      const res = await incokalkAPI.import.preview(formData);
      return res.data;
    },
    onSuccess: (data) => {
      setPreview(data);
    },
    onError: () => {
      toast.error('Erreur lors de la prévisualisation');
    },
  });

  const importMutation = useMutation({
    mutationFn: async (f: File) => {
      const formData = new FormData();
      formData.append('file', f);
      const res = await incokalkAPI.import.carriers(formData);
      return res.data;
    },
    onSuccess: (data: { imported: number; skipped: number; errors: string[] }) => {
      queryClient.invalidateQueries({ queryKey });
      toast.success(`${data.imported} ligne(s) importée(s), ${data.skipped} ignorée(s)`);
      if (data.errors.length > 0) {
        data.errors.slice(0, 3).forEach((e: string) => toast(e, { icon: '⚠️' }));
      }
      handleClose();
    },
    onError: () => {
      toast.error('Erreur lors de l\'import');
    },
  });

  const handleClose = () => {
    setFile(null);
    setPreview(null);
    onClose();
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0];
    if (f) {
      setFile(f);
      previewMutation.mutate(f);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm">
      <div className="bg-surface rounded-2xl shadow-2xl w-full max-w-2xl mx-4 max-h-[90vh] overflow-hidden flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-line">
          <h2 className="text-lg font-bold text-ink flex items-center gap-2">
            <Upload size={20} className="text-accent" />
            {title}
          </h2>
          <button onClick={handleClose} className="p-1.5 rounded-lg hover:bg-surface-2 text-ink-soft">
            <X size={18} />
          </button>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto p-6">
          {!file ? (
            <div
              onClick={() => fileRef.current?.click()}
              className="border-2 border-dashed border-line rounded-xl p-12 text-center cursor-pointer hover:border-accent hover:bg-accent-soft/30 transition-colors"
            >
              <Upload size={40} className="mx-auto mb-3 text-ink-soft" />
              <p className="text-sm font-medium text-ink-soft">Cliquez pour sélectionner un fichier CSV</p>
              <p className="text-xs text-ink-soft mt-1">ou glissez-déposez ici</p>
              <input
                ref={fileRef}
                type="file"
                accept=".csv"
                onChange={handleFileChange}
                className="hidden"
              />
            </div>
          ) : previewMutation.isPending ? (
            <div className="text-center py-12">
              <Loader2 size={32} className="mx-auto mb-3 text-accent animate-spin" />
              <p className="text-sm text-ink-soft">Analyse du fichier...</p>
            </div>
          ) : preview ? (
            <div>
              <div className="flex items-center gap-2 mb-4">
                <FileText size={16} className="text-ink-soft" />
                <span className="text-sm font-medium text-ink-soft">{file.name}</span>
                <span className="text-xs text-ink-soft">({(file.size / 1024).toFixed(1)} Ko)</span>
              </div>

              <div className="overflow-x-auto border border-line rounded-lg">
                <table className="w-full text-xs">
                  <thead>
                    <tr className="bg-surface-2">
                      {preview.headers.map((h: string, i: number) => (
                        <th key={i} className="px-3 py-2 text-left font-semibold text-ink-soft whitespace-nowrap">
                          {h}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {preview.preview.map((row: string[], i: number) => (
                      <tr key={i} className="border-t border-line">
                        {row.map((cell: string, j: number) => (
                          <td key={j} className="px-3 py-2 text-ink-soft whitespace-nowrap max-w-[150px] truncate">
                            {cell}
                          </td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <p className="text-xs text-ink-soft mt-2">Aperçu des 5 premières lignes</p>
            </div>
          ) : null}
        </div>

        {/* Footer */}
        <div className="flex items-center justify-end gap-3 px-6 py-4 border-t border-line">
          <button
            onClick={handleClose}
            className="px-4 py-2 rounded-xl text-sm font-medium text-ink-soft hover:bg-surface-2 transition-colors"
          >
            Annuler
          </button>
          <button
            onClick={() => file && importMutation.mutate(file)}
            disabled={!file || importMutation.isPending}
            className="px-5 py-2 rounded-xl text-sm font-semibold bg-accent text-white hover:bg-accent-strong disabled:opacity-50 transition-colors inline-flex items-center gap-2"
          >
            {importMutation.isPending ? (
              <><Loader2 size={14} className="animate-spin" /> Importation...</>
            ) : (
              <><Upload size={14} /> Importer</>
            )}
          </button>
        </div>
      </div>
    </div>
  );
}
