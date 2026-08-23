import { useState } from 'react';
import type { Incoterm } from '../types';
import { Shield, DollarSign, AlertTriangle, Ship, Plane, Truck } from 'lucide-react';

interface IncotermCardProps {
  incoterm: Incoterm;
  onClick?: (mode: 'SEA' | 'AIR' | 'ROAD') => void;
}

const TRANSPORT_MODES = [
  { value: 'SEA' as const, label: 'Maritime', icon: Ship },
  { value: 'AIR' as const, label: 'Aérien', icon: Plane },
  { value: 'ROAD' as const, label: 'Routier', icon: Truck },
];

const IncotermCard = ({ incoterm, onClick }: IncotermCardProps) => {
  const isSeaOnly = incoterm.mode === 'sea_only';
  const [selectedMode, setSelectedMode] = useState<'SEA' | 'AIR' | 'ROAD'>(isSeaOnly ? 'SEA' : 'SEA');

  const getRiskColor = (score: number) => {
    if (score <= 2) return 'text-success bg-success/10';
    if (score <= 3) return 'text-warning bg-warning/10';
    return 'text-danger bg-danger/10';
  };

  return (
    <div className="bg-surface rounded-2xl p-6 border border-line hover:-translate-y-1 hover:shadow-[0_20px_40px_-12px_rgba(0,0,0,0.12)] hover:border-accent/30 transition-all duration-300">
      {/* Header */}
      <div className="flex items-center justify-between mb-4">
        <div>
          <h3 className="text-2xl font-bold text-accent">{incoterm.code}</h3>
          <h4 className="text-lg font-semibold text-ink">{incoterm.fullName}</h4>
        </div>
      </div>

      {/* Risk Score */}
      <div className={`inline-flex items-center space-x-2 px-3 py-1 rounded-full text-sm font-medium mb-4 ${getRiskColor(incoterm.buyerRiskScore)}`}>
        <AlertTriangle size={16} />
        <span>Risque acheteur: {incoterm.buyerRiskScore}/5</span>
      </div>

      {/* Transport Mode Selector */}
      <div className="mb-4">
        <span className="text-xs font-semibold text-ink-soft uppercase tracking-wider mb-2 block">Mode de transport</span>
        <div className="flex gap-2">
          {TRANSPORT_MODES.map(({ value, label, icon: Icon }) => {
            const disabled = isSeaOnly && value !== 'SEA';
            return (
              <button
                key={value}
                type="button"
                disabled={disabled}
                onClick={(e) => {
                  e.stopPropagation();
                  if (!disabled) setSelectedMode(value);
                }}
                className={`flex-1 flex flex-col items-center gap-1 py-2 px-1 rounded-lg text-xs font-medium transition-all
                  ${disabled
                    ? 'bg-bg text-ink-soft cursor-not-allowed'
                    : selectedMode === value
                      ? 'bg-accent text-white shadow-md'
                      : 'bg-surface-2 text-ink-soft hover:bg-accent-soft hover:text-accent'
                  }`}
              >
                <Icon size={16} />
                <span>{label}</span>
              </button>
            );
          })}
        </div>
        {isSeaOnly && (
          <p className="text-[10px] text-ink-soft mt-1 italic">Cet Incoterm est réservé au maritime</p>
        )}
      </div>

      {/* Description */}
      <p className="text-ink-soft text-sm leading-relaxed mb-4 line-clamp-3">
        {incoterm.description}
      </p>

      {/* Risks */}
      {incoterm.risks && incoterm.risks.length > 0 && (
        <div className="mb-3">
          <div className="flex items-center space-x-2 mb-2">
            <Shield size={16} className="text-danger" />
            <span className="text-sm font-medium text-ink">Risques principaux:</span>
          </div>
          <ul className="text-xs text-ink-soft space-y-1 ml-6">
            {incoterm.risks.slice(0, 2).map((risk, index) => (
              <li key={index} className="flex items-center space-x-1">
                <span className="w-1 h-1 bg-danger rounded-full"></span>
                <span>{risk}</span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* Costs */}
      {incoterm.costs && incoterm.costs.length > 0 && (
        <div className="mb-4">
          <div className="flex items-center space-x-2 mb-2">
            <DollarSign size={16} className="text-success" />
            <span className="text-sm font-medium text-ink">Coûts couverts:</span>
          </div>
          <ul className="text-xs text-ink-soft space-y-1 ml-6">
            {incoterm.costs.slice(0, 2).map((cost, index) => (
              <li key={index} className="flex items-center space-x-1">
                <span className="w-1 h-1 bg-success rounded-full"></span>
                <span>{cost}</span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* Action Button */}
      <div className="mt-4 pt-4 border-t border-line">
        <button
          className="w-full bg-accent text-white py-2 px-4 rounded-lg hover:bg-accent-strong transition-colors font-medium text-sm"
          onClick={(e) => {
            e.stopPropagation();
            onClick?.(selectedMode);
          }}
        >
          Calculer les coûts
        </button>
      </div>
    </div>
  );
};

export default IncotermCard;
