import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { incokalkAPI } from '../lib/api';
import {
  Search, Ship, Plane, Truck, MapPin, Loader2, Package,
  Activity, Globe,
} from 'lucide-react';
import type { TrackingUpdate } from '../types';

const MODES = [
  { value: 'MARITIME', label: 'Maritime', icon: Ship, placeholder: 'MMSI (ex: 226000000)' },
  { value: 'AIR', label: 'Aérien', icon: Plane, placeholder: 'N° vol IATA (ex: AF1234)' },
  { value: 'ROAD', label: 'Routier', icon: Truck, placeholder: 'N° de suivi transporteur' },
];

const TrackingLookup = () => {
  const [trackingNumber, setTrackingNumber] = useState('');
  const [mode, setMode] = useState('ROAD');
  const [submitted, setSubmitted] = useState(false);
  const [queryNumber, setQueryNumber] = useState('');
  const [queryMode, setQueryMode] = useState('ROAD');

  const { data: results = [], isLoading, error } = useQuery({
    queryKey: ['tracking-lookup', queryNumber, queryMode],
    queryFn: async () => {
      const res = await incokalkAPI.tracking.lookup(queryNumber, queryMode);
      return (res.data as TrackingUpdate[]) || [];
    },
    enabled: submitted && queryNumber.length > 0,
    retry: false,
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!trackingNumber.trim()) return;
    setQueryNumber(trackingNumber.trim());
    setQueryMode(mode);
    setSubmitted(true);
  };

  const selectedMode = MODES.find((m) => m.value === mode) || MODES[2];

  return (
    <div className="min-h-screen bg-bg">
      <div className="max-w-4xl mx-auto px-4 py-12">
        <div className="text-center mb-10">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-accent-soft mb-4">
            <Globe size={32} className="text-accent" />
          </div>
          <h1 className="text-3xl sm:text-4xl font-bold text-ink mb-3">
            <span className="text-accent font-normal" aria-hidden="true">:: </span>
            Suivi d'Expédition
          </h1>
          <p className="text-ink-soft max-w-md mx-auto">
            Entrez votre numéro de tracking pour suivre votre envoi en temps réel
          </p>
        </div>

        <form onSubmit={handleSubmit} className="bg-surface rounded-none shadow-lg border border-line p-6 sm:p-8 mb-8">
          <div className="flex flex-wrap gap-2 mb-5">
            {MODES.map((m) => {
              const Icon = m.icon;
              return (
                <button
                  key={m.value}
                  type="button"
                  onClick={() => setMode(m.value)}
                  className={`flex items-center space-x-2 px-4 py-2 rounded-none text-sm font-medium transition-all ${
                    mode === m.value
                      ? 'bg-accent text-white shadow-md'
                      : 'bg-surface-2 text-ink-soft hover:bg-line'
                  }`}
                >
                  <Icon size={16} />
                  <span>{m.label}</span>
                </button>
              );
            })}
          </div>

          <div className="flex flex-col sm:flex-row gap-3">
            <div className="flex-1 relative">
              <Search size={18} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-soft" />
              <input
                type="text"
                value={trackingNumber}
                onChange={(e) => setTrackingNumber(e.target.value)}
                placeholder={selectedMode.placeholder}
                className="w-full pl-10 pr-4 py-3 border border-line rounded-none focus:ring-2 focus:ring-accent focus:border-accent text-sm bg-surface-2 text-ink"
              />
            </div>
            <button
              type="submit"
              disabled={!trackingNumber.trim()}
              className="px-6 py-3 bg-accent text-white rounded-none hover:bg-accent-strong transition-colors disabled:opacity-40 flex items-center justify-center space-x-2 font-medium text-sm"
            >
              <Search size={16} />
              <span>Rechercher</span>
            </button>
          </div>
        </form>

        {submitted && (
          <div className="bg-surface rounded-none shadow-lg border border-line overflow-hidden">
            <div className="bg-surface-2 px-6 py-4 border-b border-line flex items-center space-x-2">
              <Activity size={18} className="text-ink-soft" />
              <h2 className="font-semibold text-ink">
                Résultats — {selectedMode.label}
              </h2>
            </div>

            <div className="p-6">
              {isLoading ? (
                <div className="flex items-center justify-center py-10">
                  <Loader2 className="h-6 w-6 animate-spin text-accent" />
                  <span className="ml-3 text-ink-soft">Recherche en cours...</span>
                </div>
              ) : error ? (
                <div className="text-center py-10">
                  <Package size={40} className="mx-auto text-danger/40 mb-3" />
                  <p className="text-danger font-medium">Erreur lors de la recherche</p>
                  <p className="text-sm text-ink-soft mt-1">Veuillez réessayer ultérieurement</p>
                </div>
              ) : results.length === 0 ? (
                <div className="text-center py-10">
                  <Package size={40} className="mx-auto text-ink-soft mb-3" />
                  <p className="text-ink-soft font-medium">Aucun résultat trouvé</p>
                  <p className="text-sm text-ink-soft mt-1">
                    Vérifiez le numéro de tracking et le mode de transport sélectionné
                  </p>
                </div>
              ) : (
                <div className="relative">
                  <div className="absolute left-4 top-0 bottom-0 w-0.5 bg-line" />
                  <div className="space-y-6">
                    {results.map((update, idx) => {
                      const isFirst = idx === 0;
                      return (
                        <div key={idx} className="relative pl-10">
                          <div
                            className={`absolute left-2.5 w-3 h-3 rounded-full border-2 border-surface ${
                              isFirst ? 'bg-accent' : 'bg-line'
                            }`}
                          />
                          <div>
                            <div className="flex items-center space-x-2 mb-1">
                              <span
                                className={`text-xs font-medium px-2.5 py-0.5 rounded-full ${
                                  isFirst
                                    ? 'bg-accent-soft text-accent-strong'
                                    : 'bg-surface-2 text-ink-soft'
                                }`}
                              >
                                {update.status}
                              </span>
                              <span className="text-xs text-ink-soft">
                                {update.eventTime
                                  ? new Date(update.eventTime).toLocaleString('fr-FR')
                                  : '—'}
                              </span>
                              {update.source && (
                                <span className="text-[10px] px-1.5 py-0.5 rounded bg-surface-2 text-ink-soft">
                                  {update.source}
                                </span>
                              )}
                            </div>
                            {update.location && (
                              <div className="text-sm text-ink-soft flex items-center space-x-1 mt-1">
                                <MapPin size={12} className="text-ink-soft" />
                                <span>{update.location}</span>
                              </div>
                            )}
                            {update.description && (
                              <p className="text-sm text-ink-soft mt-1">{update.description}</p>
                            )}
                            {update.latitude != null && update.longitude != null && (
                              <p className="text-xs text-ink-soft mt-1 font-mono">
                                {update.latitude.toFixed(4)}°, {update.longitude.toFixed(4)}°
                              </p>
                            )}
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>
              )}
            </div>
          </div>
        )}

        <div className="text-center mt-10 text-xs text-ink-soft">
          <p>IncoKalk — Suivi d'expéditions en temps réel</p>
        </div>
      </div>
    </div>
  );
};

export default TrackingLookup;
