const statusMeta = {
  ROULAGE: { label: 'En roulage', color: 'green' },
  ARRET: { label: 'Arrêt', color: 'orange' },
  REPOS: { label: 'Repos', color: 'blue' },
  ALERTE: { label: 'Alerte', color: 'red' },
  IMMOBILISE: { label: 'Immobilisé', color: 'darkred' }
}

export default function StatusBadge({ status }) {
  const meta = statusMeta[status] || { label: status, color: 'gray' }
  return <span className={`badge badge-${meta.color}`}>{meta.label}</span>
}
