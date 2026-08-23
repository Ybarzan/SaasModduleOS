export default function StatCard({ label, value, unit, icon, tone = 'default', sub }) {
  return (
    <div className={`stat-card ${tone}`}>
      <div className="stat-icon">{icon}</div>
      <div className="stat-body">
        <div className="stat-label">{label}</div>
        <div className="stat-value">
          {value}
          {unit && <span className="stat-unit">{unit}</span>}
        </div>
        {sub && <div className="stat-sub">{sub}</div>}
      </div>
    </div>
  )
}
