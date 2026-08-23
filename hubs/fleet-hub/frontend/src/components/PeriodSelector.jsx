const periods = [
  { key: 'DAY', label: 'Jour' },
  { key: 'WEEK', label: 'Semaine' },
  { key: 'MONTH', label: 'Mois' }
]

export default function PeriodSelector({ value, onChange }) {
  return (
    <div className="period-selector">
      {periods.map((p) => (
        <button
          key={p.key}
          className={value === p.key ? 'active' : ''}
          onClick={() => onChange(p.key)}
        >
          {p.label}
        </button>
      ))}
    </div>
  )
}
