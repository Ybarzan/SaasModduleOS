import { useState, useEffect } from 'react';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
import toast from 'react-hot-toast';
import { incokalkAPI } from '../lib/api';
import { Lock, ArrowLeft, CheckCircle } from 'lucide-react';

const ResetPassword = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');

  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState(false);

  useEffect(() => {
    if (!token) {
      toast.error('Lien de réinitialisation invalide');
      navigate('/login');
    }
  }, [token, navigate]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (newPassword !== confirmPassword) {
      toast.error('Les mots de passe ne correspondent pas');
      return;
    }

    if (newPassword.length < 8) {
      toast.error('Le mot de passe doit contenir au moins 8 caractères');
      return;
    }

    setLoading(true);
    try {
      await incokalkAPI.auth.resetPassword(token!, newPassword);
      setSuccess(true);
      toast.success('Mot de passe réinitialisé avec succès');
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } } };
      const errorMsg = error.response?.data?.message || 'Erreur lors de la réinitialisation';
      toast.error(errorMsg);
    } finally {
      setLoading(false);
    }
  };

  if (success) {
    return (
      <div className="min-h-screen bg-bg flex items-center justify-center px-4">
        <div className="max-w-md w-full bg-surface rounded-none shadow-lg p-8 text-center">
          <div className="w-16 h-16 bg-success/10 rounded-full flex items-center justify-center mx-auto mb-4">
            <CheckCircle className="w-8 h-8 text-success" />
          </div>
          <h2 className="text-2xl font-bold text-ink mb-2">
            <span className="text-accent font-normal" aria-hidden="true">:: </span>
            Mot de passe réinitialisé
          </h2>
          <p className="text-ink-soft mb-6">
            Votre mot de passe a été mis à jour. Vous pouvez maintenant vous connecter.
          </p>
          <Link
            to="/login"
            className="inline-flex items-center gap-2 bg-accent text-white px-6 py-3 rounded-none font-semibold hover:bg-accent-strong transition-colors"
          >
            Se connecter
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-bg flex items-center justify-center px-4">
      <div className="max-w-md w-full bg-surface rounded-none shadow-lg p-8">
        <h2 className="text-3xl font-bold text-center mb-2">
          <span className="text-accent font-normal" aria-hidden="true">:: </span>
          Nouveau mot de passe
        </h2>
        <p className="text-ink-soft text-center mb-8">
          Choisissez un nouveau mot de passe pour votre compte
        </p>

        <form onSubmit={handleSubmit} className="space-y-6">
          <div>
            <label className="block text-ink-soft font-medium mb-2">Nouveau mot de passe</label>
            <div className="relative">
              <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-ink-soft" />
              <input
                type="password"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                placeholder="••••••••"
                autoComplete="new-password"
                className="w-full pl-10 pr-4 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent"
                required
                minLength={8}
              />
            </div>
          </div>

          <div>
            <label className="block text-ink-soft font-medium mb-2">Confirmer le mot de passe</label>
            <div className="relative">
              <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-ink-soft" />
              <input
                type="password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                placeholder="••••••••"
                autoComplete="new-password"
                className="w-full pl-10 pr-4 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent"
                required
                minLength={8}
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full bg-accent text-white py-3 rounded-none font-semibold hover:bg-accent-strong disabled:opacity-50 transition-colors"
          >
            {loading ? 'Réinitialisation...' : 'Réinitialiser le mot de passe'}
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

export default ResetPassword;
