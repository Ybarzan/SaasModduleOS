import { useState } from 'react'
import api from '../services/api'

const ORIGIN_COUNTRIES = [
  { value: 'CN', label: 'Chine' },
  { value: 'US', label: 'États-Unis' },
  { value: 'DE', label: 'Allemagne' },
  { value: 'JP', label: 'Japon' },
  { value: 'MA', label: 'Maroc' },
  { value: 'TR', label: 'Turquie' },
  { value: 'IN', label: 'Inde' },
  { value: 'VN', label: 'Vietnam' },
  { value: 'FR', label: 'France' }
]

const DEST_COUNTRIES = [
  { value: 'FR', label: 'France' },
  { value: 'US', label: 'États-Unis' },
  { value: 'DE', label: 'Allemagne' },
  { value: 'GB', label: 'Royaume-Uni' },
  { value: 'ES', label: 'Espagne' },
  { value: 'IT', label: 'Italie' },
  { value: 'NL', label: 'Pays-Bas' },
  { value: 'BE', label: 'Belgique' }
]

const EMPTY_ITEM = { sku: '', lengthCm: 30, widthCm: 20, heightCm: 15, weightKg: 2, quantity: 1 }

const fmtEur = (n) => `${n.toLocaleString('fr-FR')} €`

export default function Logistics() {
  const [origin, setOrigin] = useState('CN')
  const [destination, setDestination] = useState('FR')
  const [items, setItems] = useState([])
  const [newItem, setNewItem] = useState(EMPTY_ITEM)
  const [packagingResult, setPackagingResult] = useState(null)
  const [truckingResult, setTruckingResult] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const addItem = () => {
    if (!newItem.sku) {
      setError('SKU requis')
      return
    }
    setError('')
    setItems([...items, newItem])
    setNewItem(EMPTY_ITEM)
  }

  const removeItem = (idx) => setItems(items.filter((_, i) => i !== idx))

  const totalWeight = items.reduce((s, i) => s + i.weightKg * i.quantity, 0)
  const totalVolume = items.reduce(
    (s, i) => s + ((i.lengthCm * i.widthCm * i.heightCm) / 1e6) * i.quantity,
    0
  )

  const calculateAll = async () => {
    if (items.length === 0) {
      setError('Ajoutez au moins un article')
      return
    }
    setError('')
    setLoading(true)
    try {
      const packRes = await api.post('/logistics/packaging', { items })
      setPackagingResult(packRes.data)
      try {
        const truckRes = await api.post('/logistics/trucking', {
          originCountry: origin,
          destinationCountry: destination,
          weightKg: packRes.data.totalWeightKg,
          volumeM3: packRes.data.totalVolumeM3,
          palletCount: packRes.data.totalBoxes
        })
        setTruckingResult(truckRes.data)
      } catch {
        setError('Erreur tarifs transport')
      }
    } catch {
      setError('Erreur calcul packaging')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>📦 Dashboard logistique</h2>
          <p>Packaging, colisage et tarification transport — tout en un seul endroit</p>
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      <div className="card">
        <div className="dashboard-grid">
          <div>
            <h3>🚛 Route</h3>
            <div className="form-row">
              <div className="form-field">
                <label>Origine</label>
                <select value={origin} onChange={(e) => setOrigin(e.target.value)}>
                  {ORIGIN_COUNTRIES.map((c) => (
                    <option key={c.value} value={c.value}>
                      {c.label}
                    </option>
                  ))}
                </select>
              </div>
              <div className="form-field">
                <label>Destination</label>
                <select value={destination} onChange={(e) => setDestination(e.target.value)}>
                  {DEST_COUNTRIES.map((c) => (
                    <option key={c.value} value={c.value}>
                      {c.label}
                    </option>
                  ))}
                </select>
              </div>
            </div>
          </div>
          <div>
            <h3>📊 Résumé</h3>
            <div className="truck-card-grid">
              <div className="couple-stat">
                <span>Articles</span>
                <b>{items.length}</b>
              </div>
              <div className="couple-stat">
                <span>Poids</span>
                <b>{totalWeight.toFixed(1)} kg</b>
              </div>
              <div className="couple-stat">
                <span>Volume</span>
                <b>{totalVolume.toFixed(3)} m³</b>
              </div>
            </div>
          </div>
        </div>

        <h3 style={{ marginTop: 20 }}>📦 Articles</h3>
        {items.length > 0 && (
          <div style={{ marginBottom: 14 }}>
            {items.map((item, idx) => (
              <div key={idx} className="record-card-row">
                <span>
                  {idx + 1}. <strong>{item.sku}</strong>
                </span>
                <span className="muted">
                  {item.quantity}× {item.lengthCm}×{item.widthCm}×{item.heightCm}cm ·{' '}
                  {item.weightKg}kg{' '}
                  <button
                    onClick={() => removeItem(idx)}
                    className="link"
                    style={{
                      background: 'none',
                      border: 'none',
                      cursor: 'pointer',
                      color: 'var(--red)'
                    }}
                  >
                    ✕
                  </button>
                </span>
              </div>
            ))}
          </div>
        )}
        <div
          className="form-row"
          style={{ gridTemplateColumns: 'repeat(7, minmax(60px, 1fr))', alignItems: 'end' }}
        >
          <input
            placeholder="SKU"
            value={newItem.sku}
            onChange={(e) => setNewItem({ ...newItem, sku: e.target.value })}
          />
          <input
            type="number"
            placeholder="L (cm)"
            value={newItem.lengthCm}
            onChange={(e) => setNewItem({ ...newItem, lengthCm: Number(e.target.value) })}
          />
          <input
            type="number"
            placeholder="l (cm)"
            value={newItem.widthCm}
            onChange={(e) => setNewItem({ ...newItem, widthCm: Number(e.target.value) })}
          />
          <input
            type="number"
            placeholder="H (cm)"
            value={newItem.heightCm}
            onChange={(e) => setNewItem({ ...newItem, heightCm: Number(e.target.value) })}
          />
          <input
            type="number"
            placeholder="kg"
            value={newItem.weightKg}
            onChange={(e) => setNewItem({ ...newItem, weightKg: Number(e.target.value) })}
          />
          <input
            type="number"
            placeholder="Qté"
            value={newItem.quantity}
            onChange={(e) =>
              setNewItem({ ...newItem, quantity: Math.max(1, Number(e.target.value)) })
            }
          />
          <button className="btn btn-primary" onClick={addItem}>
            + Ajouter
          </button>
        </div>

        <div className="form-actions">
          <button
            className="btn btn-primary btn-block"
            disabled={loading || items.length === 0}
            onClick={calculateAll}
          >
            {loading ? 'Calcul en cours…' : 'Calculer le packaging & les tarifs'}
          </button>
        </div>
      </div>

      {packagingResult && (
        <div className="dashboard-grid">
          <div className="card">
            <div className="card-title">
              <h3>📦 Résultat packaging</h3>
            </div>
            <div className="truck-card-grid">
              <div className="couple-stat">
                <span>Colis</span>
                <b>{packagingResult.totalBoxes}</b>
              </div>
              <div className="couple-stat">
                <span>Remplissage</span>
                <b>{packagingResult.utilizationPercent}%</b>
              </div>
            </div>
            {packagingResult.boxes.map((box, idx) => (
              <div
                key={idx}
                style={{ border: '1px solid var(--border)', padding: 10, marginBottom: 8 }}
              >
                <div className="couple-card-top">
                  <strong>{box.boxRef}</strong>
                  <span className="muted">
                    {box.lengthCm}×{box.widthCm}×{box.heightCm}cm
                  </span>
                </div>
                <div className="couple-card-footer">
                  <span className="muted">{box.totalWeightKg} kg</span>
                  <span className="muted">{box.utilizationPercent.toFixed(1)}% rempli</span>
                </div>
                <div className="progress-track" style={{ marginTop: 6 }}>
                  <div
                    className="progress-fill"
                    style={{ width: `${Math.min(box.utilizationPercent, 100)}%` }}
                  />
                </div>
              </div>
            ))}
            {(packagingResult.unpackedItems?.length ?? 0) > 0 && (
              <div className="alert alert-error">
                ⚠️ {packagingResult.unpackedItems.length} article(s) non placé(s)
              </div>
            )}
          </div>

          {truckingResult && (
            <div className="card">
              <div className="card-title">
                <h3>🚛 Tarifs transport</h3>
              </div>
              <p className="muted">
                {truckingResult.estimatedPallets} palette
                {truckingResult.estimatedPallets > 1 ? 's' : ''} — {truckingResult.totalWeightKg} kg
                — {truckingResult.totalVolumeM3} m³
              </p>
              {truckingResult.options.map((opt) => (
                <div
                  key={opt.mode}
                  className={`couple-card ${opt.recommended ? 'best-rate' : ''}`}
                  style={{ marginBottom: 10 }}
                >
                  <div className="couple-card-top">
                    <div>
                      <strong>{opt.label}</strong>
                      {opt.recommended && (
                        <span className="badge badge-green" style={{ marginLeft: 8 }}>
                          Recommandé
                        </span>
                      )}
                      <span className="muted block">{opt.description}</span>
                    </div>
                    <span className="score-pill good">{fmtEur(opt.costEur)}</span>
                  </div>
                  <div className="couple-card-footer">
                    <span className="muted">
                      ⏱ {opt.transitDays} jour{opt.transitDays > 1 ? 's' : ''}
                    </span>
                    <span className="muted">💰 {opt.costPerPallet} €/palette</span>
                    <span className="muted">🌱 {opt.co2Kg} kg CO₂</span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  )
}
