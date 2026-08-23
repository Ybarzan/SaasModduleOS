import type { AxiosError } from 'axios';
import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  FileText,
  Upload,
  Loader2,
  CheckCircle,
  AlertTriangle,
  ChevronDown,
  ChevronUp,
  Eye,
  Globe,
  Mail,
} from 'lucide-react';
import toast from 'react-hot-toast';
import { incokalkAPI } from '../lib/api';
import { useAuthStore } from '../stores/auth';
import RelatedTools, { type RelatedTool } from '../components/RelatedTools';
import StatCard from '../components/StatCard';

interface ParsedDocument {
  id: string;
  documentType: string;
  originalFilename: string | null;
  rawText: string | null;
  parsedData: Record<string, unknown> | null;
  confidence: number | null;
  status: string;
  createdAt: string;
}

interface ParserStats {
  total: number;
  parsed: number;
  verified: number;
  rejected: number;
}

const DOC_TYPES = [
  { value: 'COMMERCIAL_INVOICE', label: 'Facture commerciale', icon: FileText, color: 'blue' },
  { value: 'BILL_OF_LADING', label: 'Bill of Lading', icon: Globe, color: 'purple' },
  { value: 'CERTIFICATE_OF_ORIGIN', label: "Certificat d'origine", icon: CheckCircle, color: 'green' },
  { value: 'PACKING_LIST', label: 'Liste de colisage', icon: Upload, color: 'orange' },
] as const;

const statusConfig: Record<string, { label: string; color: string; icon: React.ElementType }> = {
  PARSED: { label: 'Parsé', color: 'bg-accent-soft text-accent-strong', icon: Eye },
  VERIFIED: { label: 'Vérifié', color: 'bg-success/10 text-success', icon: CheckCircle },
  REJECTED: { label: 'Rejeté', color: 'bg-danger/10 text-danger', icon: AlertTriangle },
};

const DocumentParser = () => {
  const queryClient = useQueryClient();
  const [docType, setDocType] = useState<string>('COMMERCIAL_INVOICE');
  const [textInput, setTextInput] = useState('');
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const { data: stats } = useQuery({
    queryKey: ['doc-parser-stats'],
    queryFn: async () => {
      const res = await incokalkAPI.documentParser.stats();
      return res.data as ParserStats;
    },
  });

  const { data: historyData, isLoading: historyLoading } = useQuery({
    queryKey: ['doc-parser-history'],
    queryFn: async () => {
      const res = await incokalkAPI.documentParser.history();
      return res.data as ParsedDocument[];
    },
  });

  const parseTextMutation = useMutation({
    mutationFn: (data: { text: string; documentType: string }) =>
      incokalkAPI.documentParser.parseText(data),
    onSuccess: () => {
      toast.success('Document parsé avec succès');
      setTextInput('');
      queryClient.invalidateQueries({ queryKey: ['doc-parser-history'] });
      queryClient.invalidateQueries({ queryKey: ['doc-parser-stats'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || 'Erreur lors du parsing');
    },
  });

  const parsePdfMutation = useMutation({
    mutationFn: (data: { file: File; documentType: string }) =>
      incokalkAPI.documentParser.parsePdf(data.file, data.documentType),
    onSuccess: () => {
      toast.success('PDF parsé avec succès');
      setSelectedFile(null);
      queryClient.invalidateQueries({ queryKey: ['doc-parser-history'] });
      queryClient.invalidateQueries({ queryKey: ['doc-parser-stats'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || 'Erreur lors du parsing PDF');
    },
  });

  const handleParseText = () => {
    if (!textInput.trim()) return;
    parseTextMutation.mutate({ text: textInput.trim(), documentType: docType });
  };

  const handleParsePdf = () => {
    if (!selectedFile) return;
    parsePdfMutation.mutate({ file: selectedFile, documentType: docType });
  };

  const history = Array.isArray(historyData) ? historyData : [];
  const hasMinimumRole = useAuthStore((s) => s.hasMinimumRole);
  const relatedTools: RelatedTool[] = [
    { to: '/documents', label: 'Génération de documents', icon: FileText },
    ...(hasMinimumRole('ADMIN') ? [{ to: '/email-intake', label: 'Import Email', icon: Mail }] : []),
  ];

  return (
    <div className="max-w-6xl mx-auto px-4 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-ink">Parser de documents</h1>
        <p className="text-ink-soft mt-1">Extraction automatique de données depuis des documents trade</p>
      </div>

      <RelatedTools tools={relatedTools} />

      {/* Stats */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-8">
        <StatCard label="Total" value={stats?.total ?? '—'} icon={FileText} />
        <StatCard label="Parsés" value={stats?.parsed ?? '—'} icon={Eye} />
        <StatCard label="Vérifiés" value={stats?.verified ?? '—'} icon={CheckCircle} iconColor="text-success" iconBg="bg-success/10" />
        <StatCard label="Rejetés" value={stats?.rejected ?? '—'} icon={AlertTriangle} iconColor="text-danger" iconBg="bg-danger/10" />
      </div>

      {/* Parser form */}
      <div className="bg-surface rounded-xl border border-line p-6 mb-8">
        <h2 className="text-lg font-semibold text-ink mb-4">Parser un document</h2>

        {/* Document type selector */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-6">
          {DOC_TYPES.map((dt) => (
            <button
              key={dt.value}
              onClick={() => setDocType(dt.value)}
              className={`flex items-center gap-2 p-3 rounded-lg border-2 transition-all text-sm font-medium ${
                docType === dt.value
                  ? `border-${dt.color}-500 bg-${dt.color}-50 text-${dt.color}-700`
                  : 'border-line text-ink-soft hover:border-line'
              }`}
            >
              <dt.icon size={18} />
              {dt.label}
            </button>
          ))}
        </div>

        {/* Text input */}
        <div className="mb-4">
          <label className="block text-sm font-medium text-ink mb-2">Texte brut du document</label>
          <textarea
            value={textInput}
            onChange={(e) => setTextInput(e.target.value)}
            rows={8}
            className="w-full px-4 py-3 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm font-mono"
            placeholder={`Collez le texte extrait de votre document ici...\n\nExemple facture:\nSeller: ACME Corp\nBuyer: Import SARL\nInvoice No: INV-2026-001\nTotal Amount: 15,000.00 EUR\nHS Code: 6109.10`}
          />
        </div>

        {/* File upload */}
        <div className="mb-4">
          <label className="block text-sm font-medium text-ink mb-2">Ou uploader un PDF</label>
          <div className="flex items-center gap-3">
            <label className="flex-1 flex items-center justify-center px-4 py-3 border-2 border-dashed border-line rounded-lg cursor-pointer hover:border-accent/60 transition-colors">
              <Upload size={18} className="text-ink-soft mr-2" />
              <span className="text-sm text-ink-soft">
                {selectedFile ? selectedFile.name : 'Choisir un PDF'}
              </span>
              <input
                type="file"
                accept=".pdf"
                className="hidden"
                onChange={(e) => setSelectedFile(e.target.files?.[0] || null)}
              />
            </label>
          </div>
        </div>

        {/* Action buttons */}
        <div className="flex gap-3">
          <button
            onClick={handleParseText}
            disabled={!textInput.trim() || parseTextMutation.isPending}
            className="flex items-center gap-2 px-5 py-2.5 bg-accent text-white rounded-lg text-sm font-medium hover:bg-accent-strong disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            {parseTextMutation.isPending ? <Loader2 size={16} className="animate-spin" /> : <FileText size={16} />}
            Parser le texte
          </button>
          <button
            onClick={handleParsePdf}
            disabled={!selectedFile || parsePdfMutation.isPending}
            className="flex items-center gap-2 px-5 py-2.5 bg-accent text-white rounded-lg text-sm font-medium hover:bg-accent-strong disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            {parsePdfMutation.isPending ? <Loader2 size={16} className="animate-spin" /> : <Upload size={16} />}
            Parser le PDF
          </button>
        </div>
      </div>

      {/* History */}
      <div className="bg-surface rounded-xl border border-line overflow-hidden">
        <div className="px-6 py-4 border-b border-line">
          <h2 className="text-lg font-semibold text-ink">Historique des parsings</h2>
        </div>
        {historyLoading ? (
          <div className="px-6 py-12 text-center text-ink-soft">
            <Loader2 size={24} className="animate-spin mx-auto mb-2 text-ink-soft" />
            Chargement...
          </div>
        ) : history.length === 0 ? (
          <div className="px-6 py-12 text-center text-ink-soft">
            <FileText size={32} className="mx-auto mb-3 text-ink-soft" />
            <p>Aucun document parsé</p>
          </div>
        ) : (
          <div className="divide-y divide-line">
            {history.map((doc) => {
              const st = statusConfig[doc.status] || statusConfig.PARSED;
              const Icon = st.icon;
              const isExpanded = expandedId === doc.id;
              const parsedFields = doc.parsedData ? Object.entries(doc.parsedData).filter(([, v]) => v != null) : [];
              const fieldCount = parsedFields.length;

              return (
                <div key={doc.id} className="hover:bg-bg transition-colors">
                  <div
                    className="flex items-center justify-between px-6 py-4 cursor-pointer"
                    onClick={() => setExpandedId(isExpanded ? null : doc.id)}
                  >
                    <div className="flex items-center gap-4 flex-1 min-w-0">
                      <div>
                        <p className="text-sm font-medium text-ink truncate">
                          {doc.originalFilename || DOC_TYPES.find(d => d.value === doc.documentType)?.label || doc.documentType}
                        </p>
                        <p className="text-xs text-ink-soft mt-0.5">
                          {DOC_TYPES.find(d => d.value === doc.documentType)?.label || doc.documentType}
                          {' · '}
                          {new Date(doc.createdAt).toLocaleDateString('fr-FR', {
                            day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
                          })}
                        </p>
                      </div>
                    </div>
                    <div className="flex items-center gap-3">
                      {doc.confidence != null && (
                        <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${
                          doc.confidence >= 60 ? 'bg-success/10 text-success' : doc.confidence >= 30 ? 'bg-warning/10 text-warning' : 'bg-danger/10 text-danger'
                        }`}>
                          {Number(doc.confidence).toFixed(0)}%
                        </span>
                      )}
                      <span className={`inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-medium ${st.color}`}>
                        <Icon size={12} />
                        {st.label}
                      </span>
                      <span className="text-xs text-ink-soft">{fieldCount} champs</span>
                      {isExpanded ? <ChevronUp size={16} className="text-ink-soft" /> : <ChevronDown size={16} className="text-ink-soft" />}
                    </div>
                  </div>
                  {isExpanded && (
                    <div className="px-6 pb-4 border-t border-line">
                      <div className="mt-3 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
                        {parsedFields.map(([key, value]) => (
                          <div key={key} className="flex gap-2 text-sm">
                            <span className="text-ink-soft font-medium min-w-[120px]">{key}:</span>
                            <span className="text-ink truncate">{String(value)}</span>
                          </div>
                        ))}
                      </div>
                      {doc.rawText && (
                        <details className="mt-3">
                          <summary className="text-xs text-ink-soft cursor-pointer hover:text-ink">Texte brut</summary>
                          <pre className="mt-2 p-3 bg-bg rounded text-xs text-ink-soft overflow-x-auto max-h-40 whitespace-pre-wrap">
                            {doc.rawText}
                          </pre>
                        </details>
                      )}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
};

export default DocumentParser;
