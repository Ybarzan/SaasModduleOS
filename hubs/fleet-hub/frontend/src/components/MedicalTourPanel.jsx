import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import api from '../services/api'
import {
  CUSTODY_EVENTS,
  MEDICAL_PRODUCTS,
  NC_SEVERITY,
  NC_STATUS,
  NC_TYPES,
  dateTime,
  describeProducts,
  minutesLabel,
  parseProducts
} from '../medical'

/**
 * Dossier de traçabilité d'une tournée médicale : rapprochement des produits
 * pris en charge et remis, délais d'acheminement, non-conformités et journal.
 * `version` change à chaque mise à jour de la tournée pour recharger le dossier.
 */
export default function MedicalTourPanel({ tourId, tourStatus, version }) {
  const [data, setData] = useState(null)
  const [showJournal, setShowJournal] = useState(false)

  useEffect(() => {
    api
      .get(`/medical/tours/${tourId}/traceability`)
      .then((res) => setData(res.data))
      .catch(() => setData(null))
  }, [tourId, version])

  if (!data) return null
  const r = data.reconciliation
  const started = r.collectedTotal > 0 || r.deliveredTotal > 0
  // Tant que la tournée roule, la différence est « à bord » ; elle devient un écart à la fin
  const ended = tourStatus === 'TERMINEE' || tourStatus === 'ANNULEE'
  const onBoard = r.collectedTotal - r.deliveredTotal

  return (
    <div className="card medical-panel">
      <div className="card-title">
        <h3>Traçabilité des produits</h3>
        {started &&
          (ended ? (
            <span className={'badge ' + (r.balanced ? 'badge-green' : 'badge-red')}>
              {r.balanced ? '✓ Collecté = remis' : '⚠ Écart collecté / remis'}
            </span>
          ) : (
            <span className="badge badge-blue">
              🚐 {Math.max(0, onBoard)} produit(s) à bord
            </span>
          ))}
      </div>

      <div className="table-scroll">
        <table className="table reconciliation-table">
          <thead>
            <tr>
              <th>Catégorie</th>
              <th>Pris en charge</th>
              <th>Remis au laboratoire</th>
              <th>{ended ? 'Écart' : 'À bord'}</th>
            </tr>
          </thead>
          <tbody>
            {MEDICAL_PRODUCTS.map((p) => {
              const c = r.collected[p.value] || 0
              const d = r.delivered[p.value] || 0
              const gap = r.gap[p.value] || 0
              const alert = ended ? gap !== 0 : gap > 0 // en cours : plus de remis que de collectés
              return (
                <tr key={p.value} className={alert ? 'row-alert' : ''}>
                  <td>
                    <span aria-hidden="true">{p.icon}</span> {p.label}
                  </td>
                  <td>{c}</td>
                  <td>{d}</td>
                  <td>{ended ? (gap === 0 ? '—' : gap > 0 ? `+${gap}` : gap) : c - d || '—'}</td>
                </tr>
              )
            })}
            <tr className="row-total">
              <td>Total</td>
              <td>{r.collectedTotal}</td>
              <td>{r.deliveredTotal}</td>
              <td>{ended ? r.deliveredTotal - r.collectedTotal || '—' : onBoard || '—'}</td>
            </tr>
          </tbody>
        </table>
      </div>

      {r.transits.length > 0 && (
        <>
          <h4 className="panel-sub">
            Délais d'acheminement <span className="muted">(limite {minutesLabel(r.transitLimitMinutes)})</span>
          </h4>
          <ul className="transit-list">
            {r.transits.map((t) => (
              <li key={t.stopId} className={t.exceeded ? 'exceeded' : ''}>
                <span>
                  {t.siteName} → {t.deliverySiteName || <em>pas encore remis</em>}
                </span>
                <b>{t.minutes != null ? minutesLabel(t.minutes) : '…'}</b>
              </li>
            ))}
          </ul>
        </>
      )}

      <h4 className="panel-sub">
        Non-conformités{' '}
        <Link to="/non-conformites" className="link">
          tout voir →
        </Link>
      </h4>
      {data.nonConformities.length === 0 ? (
        <p className="muted">Aucune non-conformité sur cette tournée.</p>
      ) : (
        <ul className="nc-mini">
          {data.nonConformities.map((nc) => (
            <li key={nc.id}>
              <span className={`badge ${(NC_SEVERITY[nc.severity] || {}).badge || 'badge-gray'}`}>
                {(NC_SEVERITY[nc.severity] || {}).label || nc.severity}
              </span>
              <span className={`badge ${(NC_STATUS[nc.status] || {}).badge || 'badge-gray'}`}>
                {(NC_STATUS[nc.status] || {}).label || nc.status}
              </span>
              <span>
                <b>{NC_TYPES[nc.type] || nc.type}</b> — {nc.description}
              </span>
            </li>
          ))}
        </ul>
      )}

      <button type="button" className="link-button" onClick={() => setShowJournal((v) => !v)}>
        {showJournal ? 'Masquer le journal de garde' : `Journal de garde (${data.events.length} événements)`}
      </button>
      {showJournal && (
        <ol className="custody-timeline">
          {data.events.map((e) => {
            const meta = CUSTODY_EVENTS[e.type] || { label: e.type, icon: '•' }
            return (
              <li key={e.id} className={'ev-' + e.type.toLowerCase()}>
                <span className="ct-icon" aria-hidden="true">
                  {meta.icon}
                </span>
                <span className="ct-body">
                  <b>{meta.label}</b>
                  {e.siteName ? ` · ${e.siteName}` : ''} · {dateTime(e.occurredAt)}
                  {e.products && <span className="block">{describeProducts(parseProducts(e.products))}</span>}
                  <small className="muted block">
                    {e.actor}
                    {e.signedBy ? ` · remis à ${e.signedBy}` : ''}
                    {e.temperatureCelsius != null ? ` · ${e.temperatureCelsius} °C` : ''}
                    {e.latitude != null ? ' · 📍' : ''}
                  </small>
                  {e.details && <small className="block">{e.details}</small>}
                </span>
              </li>
            )
          })}
        </ol>
      )}
    </div>
  )
}
