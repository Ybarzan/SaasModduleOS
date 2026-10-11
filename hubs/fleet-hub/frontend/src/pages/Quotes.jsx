import { useState } from 'react'
import api from '../services/api'

const TRANSPORT_MODES = [
  { value: '', label: 'Tous' },
  { value: 'SEA', label: 'Maritime' },
  { value: 'AIR', label: 'Aérien' },
  { value: 'ROAD', label: 'Routier' }
]

const DISPLAY_CURRENCIES = ['EUR', 'USD', 'GBP', 'MAD', 'CHF', 'CAD', 'CNY', 'JPY', 'TND', 'TRY']

const modeLabels = { SEA: 'Maritime', AIR: 'Aérien', ROAD: 'Routier' }

const providerLabel = (type) => (type === 'SHIPPO' ? 'API Shippo' : type === 'DHL' ? 'API DHL' : 'Interne')

const EMPTY_FORM = {
  originCountry: '',
  destinationCountry: '',
  transportMode: '',
  weightKg: '',
  volumeM3: '',
  goodsValue: '',
  currency: 'EUR',
  hsCode: ''
}

export default function Quotes() {
  const [form, setForm] = useState(EMPTY_FORM)
  const [results, setResults] = useState([])
  const [loading, setLoading] = useState(false)
  const [exporting, setExporting] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const set = (field) => (e) => setForm({ ...form, [field]: e.target.value })

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setNotice('')
    if (!form.originCountry || !form.destinationCountry) {
      setError("Veuillez sélectionner les pays d'origine et de destination")
      return
    }
    if (!form.weightKg || !form.volumeM3 || parseFloat(form.weightKg) <= 0 || parseFloat(form.volumeM3) <= 0) {
      setError('Le poids et le volume doivent être supérieurs à 0')
      return
    }
    setLoading(true)
    try {
      const payload = {
        ...form,
        weightKg: parseFloat(form.weightKg) || 0,
        volumeM3: parseFloat(form.volumeM3) || 0,
        goodsValue: parseFloat(form.goodsValue) || 0
      }
      const res = await api.post('/quotes', payload)
      const quotes = res.data || []
      setResults(quotes)
      setNotice(quotes.length === 0 ? 'Aucun tarif trouvé pour cette recherche' : `${quotes.length} tarif(s) trouvé(s)`)
    } catch {
      setError('Erreur lors de la demande de devis')
    } finally {
      setLoading(false)
    }
  }

  const cheapest = results.length > 0 ? results.reduce((min, r) => (r.totalCost < min.totalCost ? r : min), results[0]) : null
  const fastest =
    results.length > 0
      ? results.reduce((min, r) => ((r.transitDaysMin ?? 999) < (min.transitDaysMin ?? 999) ? r : min), results[0])
      : null
  const greenest =
    results.length > 0
      ? results.reduce((min, r) => ((r.co2EstimateKg ?? 999999) < (min.co2EstimateKg ?? 999999) ? r : min), results[0])
      : null

  const exportPdf = async () => {
    setExporting(true)
    try {
      const payload = {
        ...form,
        weightKg: parseFloat(form.weightKg) || 0,
        volumeM3: parseFloat(form.volumeM3) || 0,
        goodsValue: parseFloat(form.goodsValue) || 0
      }
      const res = await api.post('/documents/quotes/pdf', payload, { responseType: 'blob' })
      const url = window.URL.createObjectURL(new Blob([res.data], { type: 'application/pdf' }))
      const a = document.createElement('a')
      a.href = url
      a.download = `devis-fleet-hub-${new Date().toISOString().slice(0, 10)}.pdf`
      a.click()
      window.URL.revokeObjectURL(url)
    } catch {
      setError("Erreur lors de l'export PDF")
    } finally {
      setExporting(false)
    }
  }

  const internalCount = results.filter((q) => !q.providerType || q.providerType === 'INTERNAL').length
  const shippoCount = results.filter((q) => q.providerType === 'SHIPPO').length
  const dhlCount = results.filter((q) => q.providerType === 'DHL').length
  const sourceParts = []
  if (internalCount > 0) sourceParts.push(`Interne (${internalCount})`)
  if (shippoCount > 0) sourceParts.push(`Shippo (${shippoCount})`)
  if (dhlCount > 0) sourceParts.push(`DHL (${dhlCount})`)

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Comparaison de devis</h2>
          <p>Comparez les tarifs de transport entre transporteurs</p>
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}
      {notice && <div className="alert">{notice}</div>}

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
              <label>Mode de transport</label>
              <select value={form.transportMode} onChange={set('transportMode')}>
                {TRANSPORT_MODES.map((m) => (
                  <option key={m.value} value={m.value}>
                    {m.label}
                  </option>
                ))}
              </select>
            </div>
            <div className="form-field">
              <label>Poids (kg) *</label>
              <input type="number" step="0.1" min="0" value={form.weightKg} required onChange={set('weightKg')} placeholder="0" />
            </div>
            <div className="form-field">
              <label>Volume (m³) *</label>
              <input type="number" step="0.01" min="0" value={form.volumeM3} required onChange={set('volumeM3')} placeholder="0" />
            </div>
            <div className="form-field">
              <label>Valeur des marchandises (€)</label>
              <input type="number" step="0.01" min="0" value={form.goodsValue} onChange={set('goodsValue')} placeholder="0.00" />
            </div>
            <div className="form-field">
              <label>Code SH (optionnel)</label>
              <input type="text" value={form.hsCode} onChange={set('hsCode')} placeholder="Ex : 8471.30" />
            </div>
            <div className="form-field">
              <label>Devise d'affichage</label>
              <select value={form.currency} onChange={set('currency')}>
                {DISPLAY_CURRENCIES.map((c) => (
                  <option key={c} value={c}>
                    {c}
                  </option>
                ))}
              </select>
            </div>
          </div>
          <div className="form-actions">
            <button type="submit" className="btn btn-primary" disabled={loading}>
              {loading ? 'Recherche…' : 'Demander des devis'}
            </button>
          </div>
        </form>
      </div>

      {loading && (
        <div className="loading-spinner" aria-label="Recherche de devis…">
          Chargement…
        </div>
      )}

      {!loading && results.length > 0 && (
        <>
          <div className="page-header" style={{ marginTop: 10 }}>
            <p className="muted">
              {results.length} tarif(s) trouvé(s) — sources : {sourceParts.join(', ')}
            </p>
            <div className="header-actions">
              <button className="btn btn-outline" disabled={exporting} onClick={exportPdf}>
                {exporting ? 'Génération…' : '⬇ Exporter PDF'}
              </button>
            </div>
          </div>

          <div className="north-star-grid">
            {cheapest && (
              <div className="stat-card green">
                <div className="stat-icon">🏆</div>
                <div className="stat-body">
                  <div className="stat-label">Meilleur tarif</div>
                  <div className="stat-value">{cheapest.totalCost.toFixed(2)} €</div>
                  <div className="stat-sub">{cheapest.carrierName}</div>
                </div>
              </div>
            )}
            {fastest && (
              <div className="stat-card blue">
                <div className="stat-icon">⚡</div>
                <div className="stat-body">
                  <div className="stat-label">Le plus rapide</div>
                  <div className="stat-value">
                    {fastest.transitDaysMin}-{fastest.transitDaysMax}j
                  </div>
                  <div className="stat-sub">{fastest.carrierName}</div>
                </div>
              </div>
            )}
            {greenest && (
              <div className="stat-card green">
                <div className="stat-icon">🌿</div>
                <div className="stat-body">
                  <div className="stat-label">Le plus éco</div>
                  <div className="stat-value">{greenest.co2EstimateKg?.toFixed(1)} kg CO₂</div>
                  <div className="stat-sub">{greenest.carrierName}</div>
                </div>
              </div>
            )}
          </div>

          <div className="couple-cards">
            {[...results]
              .sort((a, b) => a.totalCost - b.totalCost)
              .map((quote, idx) => {
                const isCheapest = quote === cheapest
                const isFastest = quote === fastest
                const isGreenest = quote === greenest
                return (
                  <div key={idx} className={`couple-card ${isCheapest ? 'best-rate' : ''}`}>
                    <div className="couple-card-top">
                      <div>
                        <strong>{quote.carrierName}</strong>
                        <span className="muted block">{quote.rateName}</span>
                      </div>
                      <span className="badge badge-blue">{modeLabels[quote.transportMode] || quote.transportMode}</span>
                    </div>
                    <div className="chip-row" style={{ marginBottom: 10 }}>
                      <span className="badge badge-gray">{providerLabel(quote.providerType)}</span>
                      {isCheapest && <span className="badge badge-green">Meilleur tarif</span>}
                      {isFastest && <span className="badge badge-blue">Le plus rapide</span>}
                      {isGreenest && <span className="badge badge-green">Le plus éco</span>}
                    </div>
                    <div className="couple-stats">
                      <div className="couple-stat">
                        <span>Coût total</span>
                        <b>
                          {quote.totalCost.toFixed(2)} {quote.currency}
                        </b>
                      </div>
                      {quote.totalCostConverted != null && quote.displayCurrency && (
                        <div className="couple-stat">
                          <span>≈ Converti</span>
                          <b>
                            {quote.totalCostConverted.toFixed(2)} {quote.displayCurrency}
                          </b>
                        </div>
                      )}
                      {quote.transitDaysMin != null && quote.transitDaysMax != null && (
                        <div className="couple-stat">
                          <span>Délai</span>
                          <b>
                            {quote.transitDaysMin}-{quote.transitDaysMax}j
                          </b>
                        </div>
                      )}
                      {quote.co2EstimateKg != null && (
                        <div className="couple-stat">
                          <span>CO₂</span>
                          <b>{quote.co2EstimateKg.toFixed(1)} kg</b>
                        </div>
                      )}
                      <div className="couple-stat">
                        <span>Tarif de base</span>
                        <b>
                          {quote.baseRate.toFixed(2)} {quote.currency}
                        </b>
                      </div>
                    </div>
                  </div>
                )
              })}
          </div>
        </>
      )}

      {!loading && results.length === 0 && !notice && (
        <div className="card" style={{ textAlign: 'center', padding: 40 }}>
          <h3>Recherchez des devis de transport</h3>
          <p className="muted">Remplissez le formulaire ci-dessus pour comparer les tarifs entre transporteurs</p>
        </div>
      )}
    </div>
  )
}
