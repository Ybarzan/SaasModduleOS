import { useNavigate, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { incokalkAPI } from '../lib/api';
import { useAuthStore } from '../stores/auth';
import IncotermCard from '../components/IncotermCard';
import { Loader2, AlertCircle, Info, ArrowRight } from 'lucide-react';
import type { Incoterm } from '../types';

const Simulation = () => {
  const navigate = useNavigate();
  const user = useAuthStore((state) => state.user);

  // Fetch incoterms with React Query
  const { data: incoterms = [], isLoading, error } = useQuery({
    queryKey: ['incoterms'],
    queryFn: async () => {
      const response = await incokalkAPI.incoterms.getAll();
      return response.data as Incoterm[];
    },
  });

  const handleIncotermClick = (incoterm: Incoterm, mode: 'SEA' | 'AIR' | 'ROAD') => {
    navigate('/calculator', { state: { incotermId: incoterm.id, transportMode: mode } });
  };

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-bg">
        <div className="text-center">
          <Loader2 className="h-12 w-12 animate-spin mx-auto text-accent mb-4" />
          <div className="text-xl text-ink-soft">Chargement des Incoterms...</div>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-bg">
        <div className="text-center max-w-md">
          <AlertCircle className="h-12 w-12 mx-auto text-danger mb-4" />
          <div className="bg-danger/10 border border-danger/40 text-danger px-6 py-4 rounded-none">
            Erreur lors du chargement des Incoterms
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-bg py-12">
      <div className="container-narrow mx-auto px-4">
        {/* Header */}
        <div className="text-center mb-12">
          <p className="eyebrow mb-4">Calculateur Incoterms</p>
          <h1 className="text-4xl md:text-5xl font-extrabold text-ink mb-4">
            Simulateur Incoterms <span className="gradient-text">2020</span>
          </h1>
          <p className="text-xl text-ink-soft max-w-3xl mx-auto">
            Découvrez les règles Incoterms et calculez les coûts associés à vos expéditions internationales
          </p>

          {!user && (
            <div className="mt-6 bg-accent-soft border border-accent/15 rounded-none p-4 inline-block">
              <div className="flex items-center space-x-2 text-accent-strong">
                <Info size={20} />
                <span className="font-medium">Connectez-vous pour sauvegarder vos simulations</span>
              </div>
            </div>
          )}
        </div>

        {/* Incoterms Grid */}
        <div className="grid md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
          {incoterms.map((incoterm) => (
            <IncotermCard
              key={incoterm.id}
              incoterm={incoterm}
              onClick={(mode) => handleIncotermClick(incoterm, mode)}
            />
          ))}
        </div>

        {/* Info Section */}
        <div className="mt-16 bg-surface rounded-none border border-line shadow-xl shadow-accent/5 p-8">
          <h2 className="text-2xl font-bold text-ink mb-6 text-center">
            Qu'est-ce qu'un Incoterm ?
          </h2>

          <div className="grid md:grid-cols-2 gap-8">
            <div>
              <h3 className="text-lg font-semibold text-ink mb-3">Définition</h3>
              <p className="text-ink-soft leading-relaxed">
                Les Incoterms (International Commercial Terms) sont un ensemble de règles définies par la Chambre de Commerce Internationale (CCI) qui déterminent les responsabilités respectives du vendeur et de l'acheteur dans une transaction internationale.
              </p>
            </div>

            <div>
              <h3 className="text-lg font-semibold text-ink mb-3">Pourquoi les utiliser ?</h3>
              <ul className="text-ink-soft space-y-2">
                <li>• Clarifier les responsabilités de chaque partie</li>
                <li>• Réduire les risques de litiges</li>
                <li>• Faciliter les négociations commerciales</li>
                <li>• Optimiser les coûts de transport</li>
              </ul>
            </div>
          </div>

          <div className="mt-8 text-center">
            <Link to="/calculator" className="btn-primary inline-flex items-center gap-2">
              Accéder au calculateur
              <ArrowRight size={18} />
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Simulation;