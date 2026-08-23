import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { Navigate } from 'react-router-dom';
import { Loader2, Truck } from 'lucide-react';
import { mobileApi } from '../lib/api';
import { useAuthStore } from '../stores/auth';

const Login = () => {
  const isAuthenticated = useAuthStore((s) => s.token !== null && s.user !== null);
  const login = useAuthStore((s) => s.login);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  const loginMutation = useMutation({
    mutationFn: () => mobileApi.auth.login(email, password),
    onSuccess: (res) => {
      const { token, refreshToken, userId, email: userEmail, fullName, role } = res.data;
      const [firstName, ...rest] = fullName.split(' ');
      login(token, refreshToken, {
        id: userId,
        email: userEmail,
        firstName,
        lastName: rest.join(' '),
        role: role as 'OWNER' | 'ADMIN' | 'MANAGER' | 'USER',
      });
    },
  });

  if (isAuthenticated) return <Navigate to="/" replace />;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!email || !password) return;
    loginMutation.mutate();
  };

  return (
    <div className="center-screen">
      <div className="stack" style={{ width: '100%', maxWidth: 360 }}>
        <div style={{ textAlign: 'center', marginBottom: 12 }}>
          <div
            style={{
              width: 56,
              height: 56,
              borderRadius: 16,
              background: 'rgb(var(--c-accent-soft))',
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              marginBottom: 12,
            }}
          >
            <Truck size={28} color="rgb(var(--c-accent))" />
          </div>
          <h1 className="title">IncoKalk</h1>
          <p className="subtitle">Connectez-vous pour accéder à vos expéditions</p>
        </div>

        <form onSubmit={handleSubmit} className="stack">
          <input
            className="input"
            type="email"
            placeholder="Email"
            autoComplete="username"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
          <input
            className="input"
            type="password"
            placeholder="Mot de passe"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
          {loginMutation.isError && (
            <p className="error-text">Identifiants invalides ou erreur de connexion.</p>
          )}
          <button type="submit" className="btn btn-primary btn-block" disabled={loginMutation.isPending}>
            {loginMutation.isPending ? <Loader2 size={18} className="spin" /> : 'Se connecter'}
          </button>
        </form>
      </div>
    </div>
  );
};

export default Login;
