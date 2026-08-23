import { useEffect, useRef, useState } from 'react'
import api from '../services/api'

const FILE_TYPES = [
  {
    key: 'tachograph',
    label: 'Tachygraphe',
    description: 'Importez les données de conduite depuis un fichier CSV (avec en-têtes) ou un fichier DDD binaire (export carte conducteur tachygraphe européen).',
    accept: '.csv,.ddd',
    endpoint: '/import/tachograph',
    format: 'CSV : licence_number,date,driving_hours,work_hours,rest_minutes\nDDD : Fichier binaire exporté depuis votre lecteur tachygraphe',
    example: '123456789012,2026-08-15,8.5,10.0,480'
  },
  {
    key: 'fuel',
    label: 'Carburant',
    description: 'Importez les transactions carburant (litres, montant, kilométrage) depuis un fichier CSV ou DSW/AS24 Infoservice.',
    accept: '.csv,.dsw,.aul,.txt',
    endpoint: '/import/fuel',
    format: 'registration,date,liters,amount,odometer_km',
    example: 'AA-123-BB,2026-08-15,120.5,185.30,125000'
  }
]

export default function DataImport() {
  const [activeType, setActiveType] = useState('tachograph')
  const [uploading, setUploading] = useState(false)
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')
  const [dragOver, setDragOver] = useState(false)
  const [history, setHistory] = useState([])
  const fileRef = useRef(null)

  const currentType = FILE_TYPES.find((t) => t.key === activeType)

  const fetchHistory = async () => {
    try {
      const res = await api.get('/import/history')
      setHistory(res.data)
    } catch {
      // silencieux
    }
  }

  useEffect(() => {
    fetchHistory()
  }, [])

  const handleFile = async (file) => {
    if (!file) return
    setError('')
    setResult(null)
    setUploading(true)

    const formData = new FormData()
    formData.append('file', file)

    try {
      const res = await api.post(currentType.endpoint, formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      })
      setResult(res.data)
      fetchHistory()
    } catch (err) {
      setError(err.response?.data?.message || err.response?.data?.error || "L'import a échoué")
    } finally {
      setUploading(false)
      if (fileRef.current) fileRef.current.value = ''
    }
  }

  const onDrop = (e) => {
    e.preventDefault()
    setDragOver(false)
    const file = e.dataTransfer.files[0]
    handleFile(file)
  }

  const onDragOver = (e) => {
    e.preventDefault()
    setDragOver(true)
  }

  const onDragLeave = () => setDragOver(false)

  const formatDate = (dateStr) => {
    if (!dateStr) return '-'
    const d = new Date(dateStr)
    return d.toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' })
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Import de fichiers</h2>
          <p>Importez vos données AS24 et tachygraphe en un clic</p>
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      <div className="card">
        <div className="card-title">
          <h3>Type de données</h3>
        </div>
        <div className="tabs">
          {FILE_TYPES.map((t) => (
            <button
              key={t.key}
              className={'tab' + (activeType === t.key ? ' active' : '')}
              onClick={() => {
                setActiveType(t.key)
                setResult(null)
                setError('')
              }}
            >
              {t.label}
            </button>
          ))}
        </div>
      </div>

      <div className="card">
        <div className="card-title">
          <h3>{currentType.label}</h3>
          <span className="muted">{currentType.accept.replace(/\./g, '').toUpperCase()}</span>
        </div>
        <p className="muted" style={{ marginBottom: '1rem' }}>
          {currentType.description}
        </p>

        <div
          className={'drop-zone' + (dragOver ? ' drag-over' : '') + (uploading ? ' uploading' : '')}
          onDrop={onDrop}
          onDragOver={onDragOver}
          onDragLeave={onDragLeave}
          onClick={() => fileRef.current?.click()}
          role="button"
          tabIndex={0}
          onKeyDown={(e) => e.key === 'Enter' && fileRef.current?.click()}
        >
          <input
            ref={fileRef}
            type="file"
            accept={currentType.accept}
            hidden
            onChange={(e) => handleFile(e.target.files[0])}
          />
          {uploading ? (
            <>
              <span className="spinner" />
              <p>Import en cours…</p>
            </>
          ) : (
            <>
              <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" aria-hidden="true">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                <polyline points="17 8 12 3 7 8" />
                <line x1="12" y1="3" x2="12" y2="15" />
              </svg>
              <p>Glissez votre fichier ici ou <strong>cliquez pour parcourir</strong></p>
              <span className="muted">Format : {currentType.format}</span>
            </>
          )}
        </div>

        <details style={{ marginTop: '1rem' }}>
          <summary className="muted" style={{ cursor: 'pointer' }}>Voir un exemple de format</summary>
          <pre className="code-block" style={{ marginTop: '0.5rem', padding: '0.75rem', background: 'var(--bg-secondary)', borderRadius: '6px', fontSize: '0.85rem', overflowX: 'auto' }}>
            <code>{currentType.format}{'\n'}{currentType.example}</code>
          </pre>
        </details>
      </div>

      {result && (
        <div className="card">
          <div className="card-title">
            <h3>Résultat de l'import</h3>
            <span className={'badge ' + (result.rowsImported > 0 ? 'badge-green' : 'badge-red')}>
              {result.rowsImported > 0 ? 'Succès' : 'Aucune donnée importée'}
            </span>
          </div>
          <div className="data-grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))' }}>
            <div className="stat-card">
              <span className="stat-label">Lignes lues</span>
              <span className="stat-value">{result.rowsRead}</span>
            </div>
            <div className="stat-card">
              <span className="stat-label">Importées</span>
              <span className="stat-value" style={{ color: 'var(--color-success)' }}>{result.rowsImported}</span>
            </div>
            <div className="stat-card">
              <span className="stat-label">Ignorées</span>
              <span className="stat-value" style={{ color: result.rowsSkipped > 0 ? 'var(--color-warning)' : undefined }}>{result.rowsSkipped}</span>
            </div>
          </div>
          {result.errors && result.errors.length > 0 && (
            <div style={{ marginTop: '1rem' }}>
              <p className="muted" style={{ marginBottom: '0.5rem' }}>Erreurs :</p>
              <ul style={{ listStyle: 'none', padding: 0, margin: 0 }}>
                {result.errors.map((err, i) => (
                  <li key={i} style={{ padding: '0.25rem 0', color: 'var(--color-danger)', fontSize: '0.85rem' }}>
                    {err}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}

      {history.length > 0 && (
        <div className="card">
          <div className="card-title">
            <h3>Historique des imports</h3>
            <span className="muted">{history.length} import{history.length > 1 ? 's' : ''}</span>
          </div>
          <div className="table-scroll">
            <table className="table">
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Type</th>
                  <th>Fichier</th>
                  <th>Lues</th>
                  <th>Importées</th>
                  <th>Ignorées</th>
                  <th>Erreurs</th>
                </tr>
              </thead>
              <tbody>
                {history.map((h) => (
                  <tr key={h.id}>
                    <td>{formatDate(h.importedAt)}</td>
                    <td>
                      <span className={'badge ' + (h.fileType === 'TACHOGRAPH' ? 'badge-blue' : 'badge-orange')}>
                        {h.fileType === 'TACHOGRAPH' ? 'Tachygraphe' : 'Carburant'}
                      </span>
                    </td>
                    <td className="muted" style={{ maxWidth: '200px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {h.fileName || '-'}
                    </td>
                    <td>{h.rowsRead}</td>
                    <td style={{ color: 'var(--color-success)' }}>{h.rowsImported}</td>
                    <td style={{ color: h.rowsSkipped > 0 ? 'var(--color-warning)' : undefined }}>{h.rowsSkipped}</td>
                    <td style={{ color: h.errorCount > 0 ? 'var(--color-danger)' : undefined }}>{h.errorCount}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      <div className="card">
        <div className="card-title">
          <h3>Format des fichiers attendus</h3>
        </div>
        <div className="table-scroll">
          <table className="table">
            <thead>
              <tr>
                <th>Type</th>
                <th>Colonnes</th>
                <th>Source</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td><strong>Tachygraphe</strong></td>
                <td><code>licence_number, date, driving_hours, work_hours, rest_minutes</code></td>
                <td>Export CSV depuis votre logiciel d'analyse tachygraphe ou l'extranet AS24 Tak&drive</td>
              </tr>
              <tr>
                <td><strong>Carburant</strong></td>
                <td><code>registration, date, liters, amount, odometer_km</code></td>
                <td>Export CSV / DSW / AUL depuis l'espace client AS24 Infoservice</td>
              </tr>
            </tbody>
          </table>
        </div>
        <p className="muted" style={{ marginTop: '0.75rem', fontSize: '0.85rem' }}>
          Les clés de jointure sont le <strong>n° de permis</strong> (tachygraphe) et l'<strong>immatriculation</strong> (carburant).
          Assurez-vous que ces identifiants correspondent à ceux déjà enregistrés dans Fleet Hub.
        </p>
      </div>
    </div>
  )
}
