import { useState } from 'react';
import { Link } from 'react-router-dom';
import toast from 'react-hot-toast';
import { incokalkAPI } from '../lib/api';
import { Mail, ArrowLeft, CheckCircle } from 'lucide-react';

const ForgotPassword = () => {
  const [email, setEmail] = useState('');
  const [loading, setLoading] = useState(false);
  const [sent, setSent] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);

    try {
      await incokalkAPI.auth.forgotPassword(email);
      setSent(true);
      toast.success('Email de réinitialisation envoyé');
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } } };
      const errorMsg = error.response?.data?.message || 'Erreur lors de l\'envoi';
      toast.error(errorMsg);
    } finally {
      setLoading(false);
    }
  };

  if (sent) {
    return (
      <div className="min-h-screen bg-bg flex items-center justify-center px-4">
        <div className="max-w-md w-full bg-surface rounded-lg shadow-lg p-8 text-center">
          <div className="w-16 h-16 bg-success/10 rounded-full flex items-center justify-center mx-auto mb-4">
            <CheckCircle className="w-8 h-8 text-success" />
          </div>
          <h2 className="text-2xl font-bold text-ink mb-2">Email envoyé</h2>
          <p className="text-ink-soft mb-6">
            Si un compte existe avec l'adresse <strong>{email}</strong>, vous recevrez un lien de réinitialisation.
          </p>
          <Link
            to="/login"
            className="inline-flex items-center gap-2 text-accent font-medium hover:underline"
          >
            <ArrowLeft size={16} />
            Retour à la connexion
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-bg flex items-center justify-center px-4">
      <div className="max-w-md w-full bg-surface rounded-lg shadow-lg p-8">
        <h2 className="text-3xl font-bold text-center mb-2">Mot de passe oublié</h2>
        <p className="text-ink-soft text-center mb-8">
          Entrez votre email pour recevoir un lien de réinitialisation
        </p>

        <form onSubmit={handleSubmit} className="space-y-6">
          <div>
            <label className="block text-ink font-medium mb-2">Email</label>
            <div className="relative">
              <Mail className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-ink-soft" />
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="vous@entreprise.com"
                autoComplete="email"
                className="w-full pl-10 pr-4 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent"
                required
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full bg-accent text-white py-3 rounded-lg font-semibold hover:bg-accent-strong disabled:opacity-50 transition-colors"
          >
            {loading ? 'Envoi...' : 'Envoyer le lien'}
          </button>
        </form>

        <p className="text-center mt-6 text-ink-soft">
          <Link to="/login" className="text-accent font-medium hover:underline inline-flex items-center gap-1">
            <ArrowLeft size={14} />
            Retour à la connexion
          </Link>
        </p>
      </div>
    </div>
  );
};

export default ForgotPassword;
