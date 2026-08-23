import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import toast from 'react-hot-toast';
import { useAuthStore } from '../stores/auth';
import { incokalkAPI } from '../lib/api';
import Seo from '../components/Seo';

const Login = () => {
  const navigate = useNavigate();
  const login = useAuthStore((state) => state.login);

  const [formData, setFormData] = useState({ email: '', password: '' });
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);

    try {
      const response = await incokalkAPI.auth.login(formData.email, formData.password);
      const { token, refreshToken, userId, email, role, plan, fullName } = response.data;

      const nameParts = (fullName || email.split('@')[0]).split(' ');
      const firstName = nameParts[0] || '';
      const lastName = nameParts.slice(1).join(' ') || '';

      login(token, refreshToken, {
        id: userId,
        email: email,
        firstName,
        lastName,
        role: role || 'USER',
        plan: plan || 'FREE',
      });

      toast.success('Connexion réussie !');
      navigate('/dashboard');
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } } };
      const errorMsg = error.response?.data?.message || 'Erreur de connexion';
      toast.error(errorMsg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-bg flex items-center justify-center px-4">
      <Seo
        title="Connexion"
        description="Connectez-vous à votre compte IncoKalk pour accéder à vos simulations Incoterms, devis transport et expéditions."
        path="/login"
      />
      <div className="max-w-md w-full bg-surface rounded-lg shadow-lg p-8">
        <h2 className="text-3xl font-bold text-center mb-8">Connexion</h2>

        <form onSubmit={handleSubmit} className="space-y-6">
          <div>
            <label className="block text-ink-soft font-medium mb-2">Email</label>
            <input
              type="email"
              value={formData.email}
              onChange={(e) => setFormData({ ...formData, email: e.target.value })}
              autoComplete="email"
              className="w-full px-4 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent"
              required
            />
          </div>

          <div>
            <label className="block text-ink-soft font-medium mb-2">Mot de passe</label>
            <input
              type="password"
              autoComplete="current-password"
              value={formData.password}
              onChange={(e) => setFormData({ ...formData, password: e.target.value })}
              className="w-full px-4 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent"
              required
            />
          </div>

          <div className="text-right">
            <Link to="/forgot-password" className="text-sm text-accent hover:underline">
              Mot de passe oublié ?
            </Link>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full bg-accent text-white py-3 rounded-lg font-semibold hover:bg-accent-strong disabled:opacity-50 transition-colors"
          >
            {loading ? 'Connexion...' : 'Se connecter'}
          </button>
        </form>

        <p className="text-center mt-6 text-ink-soft">
          Pas de compte ? <Link to="/register" className="text-accent font-medium hover:underline">S'inscrire</Link>
        </p>
      </div>
    </div>
  );
};

export default Login;
