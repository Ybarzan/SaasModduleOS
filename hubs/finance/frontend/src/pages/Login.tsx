import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Loader2, Landmark } from 'lucide-react';
import toast from 'react-hot-toast';
import { financeAPI } from '../lib/api';
import { useAuthStore } from '../stores/auth';

type ApiError = { response?: { data?: { message?: string } } };

const Login = () => {
  const navigate = useNavigate();
  const login = useAuthStore((s) => s.login);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSubmitting(true);
    try {
      const { data } = await financeAPI.auth.login(email, password);
      // /v1/auth/login répond à plat (voir com.finance.dto.AuthResponse),
      // jamais un objet `user` imbriqué -- même reconstruction que SsoEntry.tsx.
      const [firstName, ...rest] = (data.displayName || data.email.split('@')[0]).split(' ');
      login(data.token, {
        id: data.email,
        email: data.email,
        firstName,
        lastName: rest.join(' '),
        role: data.role,
        company: data.companyName,
        companyId: data.companyId,
      });
      navigate('/client-invoices', { replace: true });
    } catch (err) {
      const apiErr = err as ApiError;
      toast.error(apiErr.response?.data?.message || 'Identifiants invalides');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-bg px-4">
      <div className="w-full max-w-sm">
        <div className="flex flex-col items-center mb-8">
          <div className="w-12 h-12 rounded-lg bg-accent-soft flex items-center justify-center mb-3">
            <Landmark size={22} className="text-accent" />
          </div>
          <h1 className="text-xl font-semibold text-ink">Finance</h1>
          <p className="text-ink-soft text-sm mt-1">Facturation, affacturage et trésorerie</p>
        </div>

        <form onSubmit={handleSubmit} className="bg-surface border border-line rounded-lg p-6 space-y-4">
          <div>
            <label className="block text-sm font-medium text-ink mb-1">Email</label>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
              autoComplete="email"
              required
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-ink mb-1">Mot de passe</label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="w-full px-3 py-2 border border-line rounded-lg focus:ring-2 focus:ring-accent focus:border-transparent text-sm"
              autoComplete="current-password"
              required
            />
          </div>
          <button
            type="submit"
            disabled={isSubmitting}
            className="w-full flex items-center justify-center gap-2 bg-accent text-white px-4 py-2 rounded-lg font-medium hover:bg-accent-strong disabled:opacity-50 transition-colors"
          >
            {isSubmitting && <Loader2 size={14} className="animate-spin" />}
            Se connecter
          </button>
        </form>
      </div>
    </div>
  );
};

export default Login;
