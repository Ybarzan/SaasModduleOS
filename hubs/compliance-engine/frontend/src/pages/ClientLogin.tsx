import type { AxiosError } from 'axios';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { useClientAuthStore } from '../stores/clientAuth';
import { incokalkAPI } from '../lib/api';
import { Package, Eye, EyeOff } from 'lucide-react';

const ClientLogin = () => {
  const navigate = useNavigate();
  const login = useClientAuthStore((state) => state.login);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    try {
      const res = await incokalkAPI.clientAuth.login(email, password);
      const { token, clientId, email: em, fullName, companyId } = res.data;
      login(token, { id: clientId, email: em, fullName, companyId });
      toast.success('Bienvenue !');
      navigate('/client/dashboard');
    } catch (err) {
      const e = err as AxiosError<{ message?: string; details?: unknown }>;
      toast.error(e.response?.data?.message || 'Erreur de connexion');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-bg flex items-center justify-center px-4">
      <div className="max-w-md w-full">
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-16 h-16 bg-accent-soft rounded-2xl mb-4">
            <Package className="w-8 h-8 text-accent" />
          </div>
          <h1 className="text-2xl font-bold text-ink">Espace Client</h1>
          <p className="text-ink-soft mt-1">Connectez-vous pour suivre vos expéditions</p>
        </div>

        <div className="bg-surface rounded-2xl shadow-sm border border-line p-8">
          <form onSubmit={handleSubmit} className="space-y-5">
            <div>
              <label className="block text-sm font-medium text-ink mb-1.5">Email</label>
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="w-full px-4 py-2.5 border border-line rounded-xl focus:ring-2 focus:ring-accent focus:border-accent outline-none transition"
                placeholder="client@entreprise.com"
                required
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-ink mb-1.5">Mot de passe</label>
              <div className="relative">
                <input
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="w-full px-4 py-2.5 border border-line rounded-xl focus:ring-2 focus:ring-accent focus:border-accent outline-none transition pr-10"
                  required
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-ink-soft hover:text-ink"
                >
                  {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
            </div>
            <button
              type="submit"
              disabled={loading}
              className="w-full btn-primary py-3"
            >
              {loading ? 'Connexion...' : 'Se connecter'}
            </button>
          </form>
        </div>

        <p className="text-center mt-6 text-sm text-ink-soft">
          <a href="/" className="text-accent hover:underline">← Retour au site</a>
        </p>
      </div>
    </div>
  );
};

export default ClientLogin;
