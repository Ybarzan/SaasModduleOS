const colorFor = (score) => {
  if (score >= 75) return '#7fb98f'
  if (score >= 50) return '#c9a86a'
  return '#c98b8f'
}

export default function ScoreGauge({ score, size = 120 }) {
  const r = size / 2 - 8
  const circ = 2 * Math.PI * r
  const pct = Math.max(0, Math.min(100, score))
  const color = colorFor(pct)

  return (
    <div className="gauge" style={{ width: size, height: size }}>
      <svg width={size} height={size}>
        <circle cx={size / 2} cy={size / 2} r={r} className="gauge-bg" />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={r}
          className="gauge-fg"
          stroke={color}
          strokeDasharray={circ}
          strokeDashoffset={circ * (1 - pct / 100)}
        />
      </svg>
      <div className="gauge-label" style={{ color }}>
        <strong>{Math.round(pct)}</strong>
        <span>/100</span>
      </div>
    </div>
  )
}
