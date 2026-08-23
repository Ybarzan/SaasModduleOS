import { Area, AreaChart, ResponsiveContainer } from 'recharts'

const CONFIG = {
  costPerKm: { label: 'Coût au kilomètre', unit: '€/km', icon: '⛽', goodDown: true, good: 0.95, mid: 1.15 },
  utilization: { label: "Taux d'utilisation", unit: '%', icon: '⏱', goodDown: false, good: 65, mid: 50 },
  maintenance: { label: 'Conformité maintenance', unit: '%', icon: '⚙', goodDown: false, good: 90, mid: 75 },
  downtime: { label: 'Indisponibilité imprévue', unit: '%', icon: '⛔', goodDown: true, good: 5, mid: 10 }
}

const STATUS = {
  good: { color: '#7fb98f', label: 'Bon' },
  mid: { color: '#c9a86a', label: 'à surveiller' },
  bad: { color: '#c98b8f', label: 'Critique' }
}

function statusOf(key, value) {
  const cfg = CONFIG[key]
  const hit = (v) => (cfg.goodDown ? v <= cfg.good : v >= cfg.good)
  if (hit(value)) return 'good'
  return cfg.goodDown ? (value <= cfg.mid ? 'mid' : 'bad') : (value >= cfg.mid ? 'mid' : 'bad')
}

export default function KpiWidget({ widget, onClick }) {
  const cfg = CONFIG[widget.key] || { label: widget.key, unit: '', icon: '📊', goodDown: false }
  const status = statusOf(widget.key, widget.value)
  const st = STATUS[status]
  const delta = widget.deltaPct || 0
  const flat = Math.abs(delta) < 1
  const goodMove = cfg.goodDown ? delta < 0 : delta > 0
  const arrow = flat ? '→' : delta > 0 ? '▲' : '▼'
  const trendClass = flat ? 'flat' : goodMove ? 'up' : 'down'
  const value = widget.key === 'costPerKm' ? widget.value.toFixed(2) : widget.value.toFixed(1)
  const data = widget.sparkline.map((v, i) => ({ i, v }))

  const inner = (
    <>
      <div className="kpi-widget-top">
        <span className="kpi-widget-icon">{cfg.icon}</span>
        <span className="kpi-widget-label">{cfg.label}</span>
        <span className="kpi-widget-dot" />
      </div>
      <div className="kpi-widget-value">
        {value}
        {cfg.unit && <span className="kpi-widget-unit">{cfg.unit}</span>}
      </div>
      <div className="kpi-widget-meta">
        <span className={`trend ${trendClass}`}>
          {arrow} {Math.abs(delta).toFixed(1)}%
        </span>
        <span className="kpi-widget-status">{st.label}</span>
      </div>
      <div className="kpi-widget-spark">
        <ResponsiveContainer width="100%" height={44}>
          <AreaChart data={data} margin={{ top: 2, right: 0, bottom: 0, left: 0 }}>
            <defs>
              <linearGradient id={`spark-${widget.key}`} x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor={st.color} stopOpacity={0.35} />
                <stop offset="100%" stopColor={st.color} stopOpacity={0} />
              </linearGradient>
            </defs>
            <Area
              type="monotone"
              dataKey="v"
              stroke={st.color}
              strokeWidth={2}
              fill={`url(#spark-${widget.key})`}
              dot={false}
              isAnimationActive={false}
            />
          </AreaChart>
        </ResponsiveContainer>
      </div>
      {onClick && <span className="kpi-widget-drill">Analyser les couples →</span>}
    </>
  )

  if (!onClick) {
    return <div className={`kpi-widget ${status}`}>{inner}</div>
  }

  return (
    <button type="button" className={`kpi-widget kpi-widget-clickable ${status}`} onClick={onClick}>
      {inner}
    </button>
  )
}
