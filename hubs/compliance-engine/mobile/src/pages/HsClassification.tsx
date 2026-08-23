import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, Loader2 } from 'lucide-react';
import { mobileApi } from '../lib/api';

interface HsSuggestion {
  suggestedCode1: string;
  suggestedDescription1: string;
  confidence1: number;
  suggestedCode2: string;
  suggestedDescription2: string;
  confidence2: number;
  suggestedCode3: string;
  suggestedDescription3: string;
  confidence3: number;
}

const HsClassification = () => {
  const navigate = useNavigate();
  const [description, setDescription] = useState('');

  const suggest = useMutation({
    mutationFn: async () =>
      (await mobileApi.hsSuggestions.suggest({ productDescription: description.trim() })).data as HsSuggestion,
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!description.trim()) return;
    suggest.mutate();
  };

  const suggestions = suggest.data
    ? [
        { code: suggest.data.suggestedCode1, description: suggest.data.suggestedDescription1, confidence: suggest.data.confidence1 },
        { code: suggest.data.suggestedCode2, description: suggest.data.suggestedDescription2, confidence: suggest.data.confidence2 },
        { code: suggest.data.suggestedCode3, description: suggest.data.suggestedDescription3, confidence: suggest.data.confidence3 },
      ].filter((s) => s.code)
    : [];

  return (
    <>
      <div className="header-bar row">
        <button onClick={() => navigate(-1)} style={{ background: 'none', border: 'none', cursor: 'pointer' }}>
          <ArrowLeft size={20} />
        </button>
        <h1 className="title" style={{ margin: 0, fontSize: 18 }}>Classification HS</h1>
      </div>

      <form onSubmit={handleSubmit} className="stack" style={{ marginTop: 12 }}>
        <textarea
          className="input"
          style={{ minHeight: 90, resize: 'vertical', fontFamily: 'inherit' }}
          placeholder="Décrivez le produit (ex : chaussures de sport en cuir)"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />
        <button type="submit" className="btn btn-primary btn-block" disabled={suggest.isPending || !description.trim()}>
          {suggest.isPending ? <Loader2 size={16} className="spin" /> : 'Suggérer un code SH'}
        </button>
        {suggest.isError && <p className="error-text">Impossible de proposer un code SH.</p>}
      </form>

      {suggestions.length > 0 && (
        <div className="stack" style={{ marginTop: 16 }}>
          {suggestions.map((s, i) => (
            <div key={s.code} className="card">
              <div className="row-between" style={{ marginBottom: 6 }}>
                <span className="badge badge-accent" style={{ fontFamily: 'monospace' }}>{s.code}</span>
                <span className="text-sm text-soft">{i === 0 ? 'Meilleure suggestion' : `Alternative ${i + 1}`} · {Math.round(s.confidence)}%</span>
              </div>
              <p className="text-sm" style={{ margin: 0 }}>{s.description}</p>
            </div>
          ))}
        </div>
      )}
    </>
  );
};

export default HsClassification;
