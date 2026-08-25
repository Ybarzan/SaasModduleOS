import { useState, useEffect } from 'react';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
import { CheckCircle2, ArrowRight, Gift } from 'lucide-react';
import toast from 'react-hot-toast';
import { useAuthStore } from '../stores/auth';
import { incokalkAPI } from '../lib/api';
import Seo from '../components/Seo';

const Register = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const login = useAuthStore((state) => state.login);
  const [formData, setFormData] = useState({
    email: '',
    password: '',
    fullName: '',
    company: '',
    referralCode: (searchParams.get('ref') || '').toUpperCase(),
  });
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');
  const [registeredName, setRegisteredName] = useState('');

  // Redirection automatique vers le dashboard apres l'ecran de bienvenue,
  // au cas ou l'utilisateur ne clique pas sur le bouton.
  useEffect(() => {
    if (!registeredName) return;
    const t = setTimeout(() => navigate('/dashboard'), 6000);
    return () => clearTimeout(t);
  }, [registeredName, navigate]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setErrorMsg('');

    try {
      const response = await incokalkAPI.auth.register({
        email: formData.email,
        password: formData.password,
        fullName: formData.fullName,
        company: formData.company,
        referralCode: formData.referralCode,
      });

      const { token, refreshToken, userId, email, role, plan, fullName } = response.data;

      const nameParts = (fullName || formData.fullName || email.split('@')[0]).split(' ');
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

      toast.success('Inscription réussie ! Bienvenue sur IncoKalk.');
      setRegisteredName(firstName || email.split('@')[0]);
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string; details?: Record<string, string> } } };
      let msg = error.response?.data?.message || "Erreur lors de l'inscription";
      const details = error.response?.data?.details;
      if (details) {
        msg += ': ' + Object.values(details).join(', ');
      }
      setErrorMsg(msg);
      toast.error(msg);
    } finally {
      setLoading(false);
    }
  };

  if (registeredName) {
    return (
      <div className="min-h-screen bg-bg flex items-center justify-center px-4">
        <Seo title="Bienvenue" description="Votre compte IncoKalk a été créé avec succès." path="/register" noindex />
        <div className="max-w-md w-full bg-surface rounded-none shadow-lg p-8 text-center">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-success/10 mb-6">
            <CheckCircle2 size={32} className="text-success" />
          </div>
          <h2 className="text-2xl font-bold mb-3">Bienvenue, {registeredName} !</h2>
          <p className="text-ink-soft mb-8">
            Votre compte a été créé avec succès. Vous êtes prêt à calculer vos premiers coûts logistiques.
          </p>
          <button
            onClick={() => navigate('/dashboard')}
            className="w-full bg-accent text-white py-3 rounded-none font-semibold hover:bg-accent-strong transition-colors inline-flex items-center justify-center gap-2"
          >
            Continuer vers le tableau de bord
            <ArrowRight size={18} />
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-bg flex items-center justify-center px-4">
      <Seo
        title="Créer un compte gratuit"
        description="Créez votre compte IncoKalk gratuitement, sans carte bancaire. Simulez vos coûts logistiques et comparez les transporteurs dès aujourd'hui."
        path="/register"
      />
      <div className="relative max-w-md w-full bg-surface rounded-none shadow-lg p-8">
        <span className="hud-corner hud-corner-tl" aria-hidden="true" />
        <span className="hud-corner hud-corner-tr" aria-hidden="true" />
        <span className="hud-corner hud-corner-bl" aria-hidden="true" />
        <span className="hud-corner hud-corner-br" aria-hidden="true" />
        <h2 className="text-3xl font-bold text-center mb-8">
          <span className="text-accent font-normal" aria-hidden="true">:: </span>
          Inscription
        </h2>

        {errorMsg && (
          <div className="mb-4 p-3 bg-danger/10 border border-danger/20 rounded-none text-danger text-sm">
            {errorMsg}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-6">
          <div>
            <label className="block text-ink-soft font-medium mb-2">Nom Complet</label>
            <input
              type="text"
              value={formData.fullName}
              onChange={(e) => setFormData({ ...formData, fullName: e.target.value })}
              placeholder="Jean Dupont"
              autoComplete="name"
              className="w-full px-4 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent"
              required
            />
          </div>

          <div>
            <label className="block text-ink-soft font-medium mb-2">Email</label>
            <input
              type="email"
              value={formData.email}
              onChange={(e) => setFormData({ ...formData, email: e.target.value })}
              autoComplete="email"
              className="w-full px-4 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent"
              required
            />
          </div>

          <div>
            <label className="block text-ink-soft font-medium mb-2">Mot de passe</label>
            <input
              type="password"
              value={formData.password}
              onChange={(e) => setFormData({ ...formData, password: e.target.value })}
              autoComplete="new-password"
              className="w-full px-4 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent"
              required
              minLength={8}
            />
          </div>

          <div>
            <label className="block text-ink-soft font-medium mb-2">Entreprise (optionnel)</label>
            <input
              type="text"
              value={formData.company}
              onChange={(e) => setFormData({ ...formData, company: e.target.value })}
              autoComplete="organization"
              className="w-full px-4 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent"
            />
          </div>

          <div>
            <label className="block text-ink-soft font-medium mb-2">Code de parrainage (optionnel)</label>
            <input
              type="text"
              value={formData.referralCode}
              onChange={(e) => setFormData({ ...formData, referralCode: e.target.value.toUpperCase() })}
              placeholder="Ex : ABCD1234"
              className="w-full px-4 py-2 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-transparent uppercase"
            />
            {formData.referralCode && (
              <p className="mt-2 flex items-center gap-1.5 text-sm text-success">
                <Gift size={14} />
                1 mois offert dès votre premier abonnement payant
              </p>
            )}
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full bg-accent text-white py-3 rounded-none font-semibold hover:bg-accent-strong disabled:opacity-50 transition-colors"
          >
            {loading ? "Inscription..." : "S'inscrire"}
          </button>
        </form>

        <p className="text-center mt-6 text-ink-soft">
          Déjà un compte ? <Link to="/login" className="text-accent font-medium hover:underline">Se connecter</Link>
        </p>
      </div>
    </div>
  );
};

export default Register;
