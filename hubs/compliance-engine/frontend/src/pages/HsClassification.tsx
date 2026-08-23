import type { AxiosError } from 'axios';
import { useState, useRef } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Search, CheckCircle2, Clock, FileText, Loader2, Upload, Image } from 'lucide-react';
import toast from 'react-hot-toast';
import { incokalkAPI } from '../lib/api';
import InfoTooltip from '../components/InfoTooltip';

interface HsSuggestion {
  id: string;
  productDescription: string;
  suggestedCode1: string;
  suggestedDescription1: string;
  confidence1: number;
  suggestedCode2: string;
  suggestedDescription2: string;
  confidence2: number;
  suggestedCode3: string;
  suggestedDescription3: string;
  confidence3: number;
  userSelection: string | null;
  createdAt: string;
}

const confidenceColor = (pct: number) => {
  if (pct >= 80) return 'bg-success';
  if (pct >= 50) return 'bg-warning';
  return 'bg-danger';
};

const confidenceBg = (pct: number) => {
  if (pct >= 80) return 'bg-success/10 border-success/40';
  if (pct >= 50) return 'bg-warning/10 border-warning/40';
  return 'bg-danger/10 border-danger/40';
};

const HsClassification = () => {
  const queryClient = useQueryClient();
  const [description, setDescription] = useState('');
  const [filter, setFilter] = useState('');
  const [inputMode, setInputMode] = useState<'text' | 'image'>('text');
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const { data: historyData, isLoading: historyLoading } = useQuery({
    queryKey: ['hs-history'],
    queryFn: async () => {
      const res = await incokalkAPI.hsSuggestions.history();
      return res.data as HsSuggestion[];
    },
  });

  const suggestMutation = useMutation({
    mutationFn: (productDescription: string) =>
      incokalkAPI.hsSuggestions.suggest({ productDescription }),
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || 'Erreur lors de la classification');
    },
  });

  const imageMutation = useMutation({
    mutationFn: (file: File) => incokalkAPI.hsSuggestions.suggestFromImage(file),
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || 'Erreur lors de la classification par image');
    },
  });

  const confirmMutation = useMutation({
    mutationFn: ({ id, selectedCode }: { id: string; selectedCode: string }) =>
      incokalkAPI.hsSuggestions.confirm(id, { selectedCode }),
    onSuccess: () => {
      toast.success('Code HS confirmé');
      queryClient.invalidateQueries({ queryKey: ['hs-history'] });
    },
    onError: (err: AxiosError<{ message?: string; details?: unknown }>) => {
      toast.error(err.response?.data?.message || 'Erreur lors de la confirmation');
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (inputMode === 'text') {
      if (!description.trim()) return;
      suggestMutation.mutate(description.trim());
    } else {
      if (!selectedFile) return;
      imageMutation.mutate(selectedFile);
    }
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0] || null;
    setSelectedFile(file);
  };

  const activeMutation = inputMode === 'text' ? suggestMutation : imageMutation;
  const result = activeMutation.data?.data as HsSuggestion | undefined;

  const suggestions = result
    ? [
        { code: result.suggestedCode1, description: result.suggestedDescription1, confidence: result.confidence1, rank: 1 },
        { code: result.suggestedCode2, description: result.suggestedDescription2, confidence: result.confidence2, rank: 2 },
        { code: result.suggestedCode3, description: result.suggestedDescription3, confidence: result.confidence3, rank: 3 },
      ]
    : [];

  const history = historyData ?? [];
  const filteredHistory = Array.isArray(history)
    ? history.filter((h: HsSuggestion) =>
        !filter || h.productDescription.toLowerCase().includes(filter.toLowerCase())
      )
    : [];

  return (
    <div className="max-w-6xl mx-auto px-4 py-8">
      {/* Header */}
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-ink">Classification HS</h1>
        <p className="text-ink-soft mt-1">Classification tarifaire automatisée par intelligence artificielle</p>
      </div>

      {/* Suggestion form */}
      <div className="bg-surface rounded-xl border border-line p-6 mb-8">
        {/* Mode toggle */}
        <div className="flex items-center gap-1 mb-6 bg-surface-2 p-1 rounded-lg w-fit">
          <button
            onClick={() => setInputMode('text')}
            className={`flex items-center gap-2 px-4 py-2 rounded-md text-sm font-medium transition-colors ${
              inputMode === 'text' ? 'bg-surface text-accent-strong shadow-sm' : 'text-ink-soft hover:text-ink'
            }`}
          >
            <Search size={16} />
            Description texte
          </button>
          <button
            onClick={() => setInputMode('image')}
            className={`flex items-center gap-2 px-4 py-2 rounded-md text-sm font-medium transition-colors ${
              inputMode === 'image' ? 'bg-surface text-accent-strong shadow-sm' : 'text-ink-soft hover:text-ink'
            }`}
          >
            <Image size={16} />
            Image / Document
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          {inputMode === 'text' ? (
            <div>
              <label className="block text-sm font-medium text-ink mb-1">Description du produit</label>
              <textarea
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                rows={4}
                className="w-full px-4 py-3 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm resize-none"
                placeholder="Décrivez votre produit en détail pour une classification précise..."
                required
              />
            </div>
          ) : (
            <div>
              <label className="block text-sm font-medium text-ink mb-1">Image du produit ou document</label>
              <div
                onClick={() => fileInputRef.current?.click()}
                className="border-2 border-dashed border-line rounded-lg p-8 text-center cursor-pointer hover:border-accent/60 hover:bg-accent-soft/30 transition-colors"
              >
                <input
                  ref={fileInputRef}
                  type="file"
                  accept="image/*,.pdf"
                  onChange={handleFileChange}
                  className="hidden"
                />
                {selectedFile ? (
                  <div className="space-y-2">
                    <Upload size={32} className="mx-auto text-accent" />
                    <p className="text-sm font-medium text-ink">{selectedFile.name}</p>
                    <p className="text-xs text-ink-soft">{(selectedFile.size / 1024).toFixed(0)} Ko</p>
                  </div>
                ) : (
                  <div className="space-y-2">
                    <Image size={32} className="mx-auto text-ink-soft" />
                    <p className="text-sm text-ink-soft">Cliquez pour sélectionner une image ou un PDF</p>
                    <p className="text-xs text-ink-soft">PNG, JPG, PDF acceptés</p>
                  </div>
                )}
              </div>
            </div>
          )}
          <div className="flex items-center gap-3">
            <button
              type="submit"
              disabled={activeMutation.isPending || (inputMode === 'text' ? !description.trim() : !selectedFile)}
              className="flex items-center gap-2 bg-accent text-white px-5 py-2.5 rounded-lg font-medium hover:bg-accent-strong disabled:opacity-50 transition-colors"
            >
              {activeMutation.isPending ? (
                <Loader2 size={18} className="animate-spin" />
              ) : (
                <Search size={18} />
              )}
              Classifier
            </button>
            {activeMutation.isPending && (
              <span className="text-sm text-ink-soft">Analyse en cours...</span>
            )}
          </div>
        </form>
      </div>

      {/* Results */}
      {result && suggestions.length > 0 && (
        <div className="mb-8">
          <h2 className="text-lg font-semibold text-ink mb-4">Résultats de la classification</h2>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            {suggestions.map((s) => {
              const isTop = s.rank === 1;
              const isSelected = result.userSelection === s.code;

              return (
                <div
                  key={s.rank}
                  className={`rounded-xl border-2 p-5 transition-all ${
                    isTop
                      ? 'border-accent bg-accent-soft/30 shadow-md'
                      : 'border-line bg-surface'
                  }`}
                >
                  {isTop && (
                    <span className="inline-block bg-accent text-white text-xs font-medium px-2 py-0.5 rounded-full mb-3">
                      Meilleure suggestion
                    </span>
                  )}
                  {!isTop && (
                    <span className="inline-block bg-surface-2 text-ink-soft text-xs font-medium px-2 py-0.5 rounded-full mb-3">
                      Suggestion {s.rank}
                    </span>
                  )}

                  <div className="mb-3">
                    <p className="text-xs text-ink-soft uppercase tracking-wider mb-1 flex items-center gap-1.5">
                      Code SH
                      <InfoTooltip text="Le code du Système Harmonisé identifie la nature de la marchandise pour la douane : il détermine le taux de droits applicable et les formalités requises." />
                    </p>
                    <p className={`font-mono font-bold ${isTop ? 'text-2xl text-accent-strong' : 'text-xl text-ink'}`}>
                      {s.code}
                    </p>
                  </div>

                  <div className="mb-4">
                    <p className="text-xs text-ink-soft uppercase tracking-wider mb-1">Description</p>
                    <p className="text-sm text-ink leading-relaxed">{s.description}</p>
                  </div>

                  <div className="mb-4">
                    <div className="flex items-center justify-between mb-1">
                      <p className="text-xs text-ink-soft uppercase tracking-wider flex items-center gap-1.5">
                        Barre de confiance
                        <InfoTooltip text="Estime la fiabilité de la suggestion de l'IA à partir de votre description. Une confiance basse ne veut pas dire un code faux — vérifiez-le avant de le confirmer." />
                      </p>
                      <span className={`text-xs font-semibold px-2 py-0.5 rounded-full ${confidenceBg(s.confidence)} ${
                        s.confidence >= 80 ? 'text-success' : s.confidence >= 50 ? 'text-warning' : 'text-danger'
                      }`}>
                        {s.confidence}%
                      </span>
                    </div>
                    <div className="w-full bg-surface-2 rounded-full h-2">
                      <div
                        className={`h-2 rounded-full transition-all ${confidenceColor(s.confidence)}`}
                        style={{ width: `${s.confidence}%` }}
                      />
                    </div>
                  </div>

                  {isSelected ? (
                    <div className="flex items-center gap-2 bg-success/10 text-success px-4 py-2 rounded-lg font-medium text-sm">
                      <CheckCircle2 size={16} />
                      Sélectionné
                    </div>
                  ) : (
                    <button
                      onClick={() => confirmMutation.mutate({ id: result.id, selectedCode: s.code })}
                      disabled={confirmMutation.isPending}
                      className="w-full bg-accent text-white px-4 py-2 rounded-lg font-medium text-sm hover:bg-accent-strong disabled:opacity-50 transition-colors"
                    >
                      {confirmMutation.isPending ? (
                        <Loader2 size={14} className="animate-spin mx-auto" />
                      ) : (
                        'Sélectionner'
                      )}
                    </button>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* History */}
      <div className="bg-surface rounded-xl border border-line overflow-hidden">
        <div className="px-6 py-4 border-b border-line flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
          <h2 className="text-lg font-semibold text-ink flex items-center gap-2">
            <Clock size={20} className="text-ink-soft" />
            Historique des classifications
          </h2>
          <div className="relative w-full sm:w-72">
            <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-soft" />
            <input
              type="text"
              value={filter}
              onChange={(e) => setFilter(e.target.value)}
              placeholder="Filtrer par description..."
              className="w-full pl-9 pr-3 py-2 border border-line rounded-lg text-sm focus:ring-2 focus:ring-accent focus:border-transparent"
            />
          </div>
        </div>

        {historyLoading ? (
          <div className="px-6 py-12 text-center text-ink-soft">
            <Loader2 size={24} className="animate-spin mx-auto mb-2 text-ink-soft" />
            Chargement...
          </div>
        ) : filteredHistory.length === 0 ? (
          <div className="px-6 py-12 text-center text-ink-soft">
            <FileText size={32} className="mx-auto mb-3 text-ink-soft" />
            <p>Aucune classification trouvée</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="bg-bg border-b border-line">
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Date</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Description</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Code sélectionné</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Code suggéré 1</th>
                  <th className="text-left text-xs font-medium text-ink-soft uppercase tracking-wider px-6 py-3">Confiance</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {filteredHistory.map((item: HsSuggestion) => (
                  <tr key={item.id} className="hover:bg-bg transition-colors">
                    <td className="px-6 py-4 text-sm text-ink-soft whitespace-nowrap">
                      {new Date(item.createdAt).toLocaleDateString('fr-FR')}
                    </td>
                    <td className="px-6 py-4 text-sm text-ink max-w-xs truncate">
                      {item.productDescription}
                    </td>
                    <td className="px-6 py-4">
                      {item.userSelection ? (
                        <span className="inline-flex items-center gap-1.5 bg-success/10 text-success text-xs font-medium px-2.5 py-1 rounded-full">
                          <CheckCircle2 size={12} />
                          {item.userSelection}
                        </span>
                      ) : (
                        <span className="text-xs text-ink-soft italic">—</span>
                      )}
                    </td>
                    <td className="px-6 py-4 font-mono text-sm font-medium text-ink">
                      {item.suggestedCode1}
                    </td>
                    <td className="px-6 py-4">
                      <div className="flex items-center gap-2">
                        <div className="w-16 bg-surface-2 rounded-full h-1.5">
                          <div
                            className={`h-1.5 rounded-full ${confidenceColor(item.confidence1)}`}
                            style={{ width: `${item.confidence1}%` }}
                          />
                        </div>
                        <span className="text-xs text-ink-soft">{item.confidence1}%</span>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
};

export default HsClassification;
