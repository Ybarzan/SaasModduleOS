import { useState, useEffect } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import toast from 'react-hot-toast';
import { incokalkAPI } from '../lib/api';
import { CheckCircle, XCircle, Loader2 } from 'lucide-react';

const VerifyEmail = () => {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');

  const [status, setStatus] = useState<'loading' | 'success' | 'error'>(() => !token ? 'error' : 'loading');
  const [message, setMessage] = useState(() => !token ? 'Lien de vérification invalide' : '');

  useEffect(() => {
    if (!token) return;

    const verify = async () => {
      try {
        const res = await incokalkAPI.auth.verifyEmail(token);
        setStatus('success');
        setMessage(res.data?.message || 'Email vérifié avec succès');
        toast.success('Email vérifié !');
      } catch (err: unknown) {
        setStatus('error');
        const error = err as { response?: { data?: { message?: string } } };
        setMessage(error.response?.data?.message || 'Erreur lors de la vérification');
        toast.error('Erreur de vérification');
      }
    };

    verify();
  }, [token]);

  return (
    <div className="min-h-screen bg-bg flex items-center justify-center px-4">
      <div className="max-w-md w-full bg-surface rounded-none shadow-lg p-8 text-center">
        {status === 'loading' && (
          <>
            <Loader2 className="w-12 h-12 text-accent mx-auto mb-4 animate-spin" />
            <h2 className="text-2xl font-bold text-ink mb-2">
              <span className="text-accent font-normal" aria-hidden="true">:: </span>
              Vérification en cours...
            </h2>
            <p className="text-ink-soft">Nous vérifions votre adresse email</p>
          </>
        )}

        {status === 'success' && (
          <>
            <div className="w-16 h-16 bg-success/10 rounded-full flex items-center justify-center mx-auto mb-4">
              <CheckCircle className="w-8 h-8 text-success" />
            </div>
            <h2 className="text-2xl font-bold text-ink mb-2">
              <span className="text-accent font-normal" aria-hidden="true">:: </span>
              Email vérifié !
            </h2>
            <p className="text-ink-soft mb-6">{message}</p>
            <Link
              to="/login"
              className="inline-flex items-center gap-2 bg-accent text-white px-6 py-3 rounded-none font-semibold hover:bg-accent-strong transition-colors"
            >
              Se connecter
            </Link>
          </>
        )}

        {status === 'error' && (
          <>
            <div className="w-16 h-16 bg-danger/10 rounded-full flex items-center justify-center mx-auto mb-4">
              <XCircle className="w-8 h-8 text-danger" />
            </div>
            <h2 className="text-2xl font-bold text-ink mb-2">
              <span className="text-accent font-normal" aria-hidden="true">:: </span>
              Erreur de vérification
            </h2>
            <p className="text-ink-soft mb-6">{message}</p>
            <Link
              to="/login"
              className="inline-flex items-center gap-2 bg-accent text-white px-6 py-3 rounded-none font-semibold hover:bg-accent-strong transition-colors"
            >
              Retour à la connexion
            </Link>
          </>
        )}
      </div>
    </div>
  );
};

export default VerifyEmail;
