import { useState } from 'react'
import api from '../services/api'

const TRANSPORT_MODES = [
  { value: 'SEA', label: 'Maritime' },
  { value: 'AIR', label: 'Aérien' },
  { value: 'ROAD', label: 'Routier' }
]

const EMPTY_FORM = {
  originCountry: '',
  destinationCountry: '',
  transportMode: '',
  weightKg: '',
  volumeM3: ''
}

export default function RateComparison() {
  const [form, setForm] = useState(EMPTY_FORM)
  const [showAdvanced, setShowAdvanced] = useState(false)
  const [expandedId, setExpandedId] = useState(null)
  const [results, setResults] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [exporting, setExporting] = useState(false)

  const set = (field) => (e) => setForm({ ...form, [field]: e.target.value })

  const runComparison = async () => {
    setLoading(true)
    setError('')
    try {
      const res = await api.get('/shipping-rates/compare', {
        params: {
          originCountry: form.originCountry,
          destinationCountry: form.destinationCountry,
          transportMode: form.transportMode,
          weightKg: form.weightKg ? parseFloat(form.weightKg) : undefined
        }
      })
      setResults(res.data || [])
    } catch {
      setError('Erreur lors de la comparaison des tarifs')
      setResults(null)
    } finally {
      setLoading(false)
    }
  }

  const handleSubmit = (e) => {
    e.preventDefault()
    if (!form.originCountry || !form.destinationCountry || !form.transportMode) {
      setError('Veuillez remplir les champs obligatoires')
      return
    }
    runComparison()
  }

  const clearForm = () => {
    setForm(EMPTY_FORM)
    setShowAdvanced(false)
    setResults(null)
    setError('')
  }

  const exportPdf = async () => {
    setExporting(true)
    try {
      const res = await api.post(
        '/documents/quotes/pdf',
        {
          originCountry: form.originCountry,
          destinationCountry: form.destinationCountry,
          transportMode: form.transportMode,
          weightKg: form.weightKg ? parseFloat(form.weightKg) : 10,
          volumeM3: form.volumeM3 ? parseFloat(form.volumeM3) : 1,
          goodsValue: 0
        },
        { responseType: 'blob' }
      )
      const url = window.URL.createObjectURL(new Blob([res.data], { type: 'application/pdf' }))
      const a = document.createElement('a')
      a.href = url
      a.download = `devis-${form.originCountry}-${form.destinationCountry}.pdf`
      a.click()
      window.URL.revokeObjectURL(url)
    } catch {
      setError('Erreur lors de la génération du PDF')
    } finally {
      setExporting(false)
    }
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Comparateur de tarifs multi-transporteurs</h2>
          <p>Comparez les tarifs de vos transporteurs pour un trajet donné et trouvez la meilleure option</p>
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      <div className="card">
        <form onSubmit={handleSubmit}>
          <div className="data-grid">
            <div className="form-field">
              <label>Pays d'origine *</label>
              <input type="text" value={form.originCountry} required onChange={set('originCountry')} placeholder="France" />
            </div>
            <div className="form-field">
              <label>Pays de destination *</label>
              <input type="text" value={form.destinationCountry} required onChange={set('destinationCountry')} placeholder="Allemagne" />
            </div>
            <div className="form-field">
              <label>Mode de transport *</label>
              <select value={form.transportMode} required onChange={set('transportMode')}>
                <option value="">Sélectionner…</option>
                {TRANSPORT_MODES.map((m) => (
                  <option key={m.value} value={m.value}>
                    {m.label}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <button
            type="button"
            className="link"
            style={{ marginTop: 14, background: 'none', border: 'none', cursor: 'pointer' }}
            onClick={() => setShowAdvanced(!showAdvanced)}
          >
            {showAdvanced ? 'Masquer' : 'Afficher'} les options avancées
          </button>

          {showAdvanced && (
            <div className="data-grid" style={{ marginTop: 12 }}>
              <div className="form-field">
                <label>Poids (kg)</label>
                <input type="number" step="0.1" min="0" value={form.weightKg} onChange={set('weightKg')} placeholder="100" />
              </div>
              <div className="form-field">
                <label>Volume (m³)</label>
                <input type="number" step="0.01" min="0" value={form.volumeM3} onChange={set('volumeM3')} placeholder="2.5" />
              </div>
            </div>
          )}

          <div className="form-actions">
            <button type="submit" className="btn btn-primary" disabled={loading}>
              {loading ? 'Comparaison…' : 'Comparer les tarifs'}
            </button>
            <button type="button" className="btn btn-outline" onClick={clearForm}>
              Effacer
            </button>
          </div>
        </form>
      </div>

      {results && results.length > 0 && (
        <>
          <div className="page-header" style={{ marginTop: 10 }}>
            <div>
              <h3>
                {results.length} tarif{results.length > 1 ? 's' : ''} trouvé{results.length > 1 ? 's' : ''}
              </h3>
              <p className="muted">
                {form.originCountry} → {form.destinationCountry} ·{' '}
                {TRANSPORT_MODES.find((m) => m.value === form.transportMode)?.label}
              </p>
            </div>
            <div className="header-actions">
              <button className="btn btn-outline" disabled={exporting} onClick={exportPdf}>
                {exporting ? 'Génération…' : '⬇ Exporter PDF'}
              </button>
            </div>
          </div>

          <div className="couple-cards">
            {results.map((result, index) => {
              const isBest = index === 0
              const isExpanded = expandedId === result.rate.id
              return (
                <div key={result.rate.id} className={`couple-card ${isBest ? 'best-rate' : ''}`}>
                  <div className="couple-card-top">
                    <div>
                      <strong>
                        {result.carrierName}
                        {isBest && <span className="badge badge-orange" style={{ marginLeft: 8 }}>Meilleur prix</span>}
                      </strong>
                      <span className="muted block">{result.carrierCode}</span>
                    </div>
                    <span className="score-pill good">{result.estimatedCost.toFixed(2)} €</span>
                  </div>
                  <div className="truck-card-grid">
                    <div className="couple-stat">
                      <span>Délai moyen</span>
                      <b>{result.transitDaysAvg} j</b>
                    </div>
                    <div className="couple-stat">
                      <span>CO₂ estimé</span>
                      <b>{result.co2EstimateKg > 0 ? `${result.co2EstimateKg} kg` : '—'}</b>
                    </div>
                  </div>
                  <button
                    className="link"
                    style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 0 }}
                    onClick={() => setExpandedId(isExpanded ? null : result.rate.id)}
                  >
                    {isExpanded ? 'Masquer les détails ↑' : 'Voir les détails ↓'}
                  </button>
                  {isExpanded && (
                    <div className="truck-card-grid" style={{ marginTop: 10 }}>
                      <div className="couple-stat">
                        <span>Tarif de base</span>
                        <b>{result.rate.baseRate.toFixed(2)} €</b>
                      </div>
                      <div className="couple-stat">
                        <span>Devise</span>
                        <b>{result.rate.currency}</b>
                      </div>
                      <div className="couple-stat">
                        <span>Prix au kg</span>
                        <b>{result.rate.ratePerKg?.toFixed(2) || '—'} €/kg</b>
                      </div>
                      <div className="couple-stat">
                        <span>Prix au m³</span>
                        <b>{result.rate.ratePerCbm?.toFixed(2) || '—'} €/m³</b>
                      </div>
                      <div className="couple-stat">
                        <span>Délai min/max</span>
                        <b>
                          {result.rate.transitDaysMin || '—'} - {result.rate.transitDaysMax || '—'} jours
                        </b>
                      </div>
                      <div className="couple-stat">
                        <span>Poids min/max</span>
                        <b>
                          {result.rate.minWeightKg || '—'} - {result.rate.maxWeightKg || '—'} kg
                        </b>
                      </div>
                    </div>
                  )}
                </div>
              )
            })}
          </div>
        </>
      )}

      {results && results.length === 0 && form.originCountry && (
        <div className="card" style={{ textAlign: 'center', padding: 40 }}>
          <h3>Aucun tarif trouvé</h3>
          <p className="muted">Aucun tarif ne correspond à vos critères pour ce trajet.</p>
          <p className="muted">Vérifiez que vos transporteurs ont configuré des tarifs pour cette route et ce mode de transport.</p>
        </div>
      )}
    </div>
  )
}
