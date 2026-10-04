import { useState } from 'react'
import { FAILURE_REASONS } from '../tours'

/**
 * Clôture d'un arrêt : preuve de passage (signataire, colis, échantillons,
 * codes scannés, température) ou motif d'échec. Partagé entre l'écran
 * d'exploitation et l'application chauffeur.
 */
export default function StopCompletionForm({ stop, onSubmit, onCancel, busy }) {
  const [status, setStatus] = useState('FAIT')
  const [form, setForm] = useState({
    signedBy: '',
    parcelCount: '',
    sampleCount: '',
    temperatureCelsius: '',
    scannedCodes: '',
    failureReason: FAILURE_REASONS[0],
    notes: ''
  })
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })
  const needsTemperature = stop.temperatureMinCelsius != null || stop.temperatureMaxCelsius != null
  const isCollect =
    stop.type === 'COLLECTE' || stop.siteKind === 'PHARMACIE' || stop.siteKind === 'LABORATOIRE'

  const temp = form.temperatureCelsius === '' ? null : Number(form.temperatureCelsius)
  const excursion =
    temp != null &&
    ((stop.temperatureMinCelsius != null && temp < stop.temperatureMinCelsius) ||
      (stop.temperatureMaxCelsius != null && temp > stop.temperatureMaxCelsius))

  const submit = (e) => {
    e.preventDefault()
    const num = (v) => (v === '' ? null : Number(v))
    onSubmit({
      status,
      signedBy: form.signedBy || null,
      parcelCount: num(form.parcelCount),
      sampleCount: num(form.sampleCount),
      temperatureCelsius: num(form.temperatureCelsius),
      scannedCodes: form.scannedCodes.trim() || null,
      failureReason: status === 'ECHEC' ? form.failureReason : null,
      notes: form.notes || null
    })
  }

  return (
    <form className="stop-completion" onSubmit={submit}>
      <div className="segmented" role="radiogroup" aria-label="Résultat du passage">
        <button
          type="button"
          role="radio"
          aria-checked={status === 'FAIT'}
          className={status === 'FAIT' ? 'active ok' : ''}
          onClick={() => setStatus('FAIT')}
        >
          ✓ Réalisé
        </button>
        <button
          type="button"
          role="radio"
          aria-checked={status === 'ECHEC'}
          className={status === 'ECHEC' ? 'active ko' : ''}
          onClick={() => setStatus('ECHEC')}
        >
          ✕ Échec
        </button>
      </div>

      {status === 'FAIT' ? (
        <div className="data-grid">
          <div className="form-field">
            <label>Remis à / signé par</label>
            <input value={form.signedBy} onChange={set('signedBy')} autoComplete="off" />
          </div>
          {isCollect ? (
            <div className="form-field">
              <label>Échantillons / sachets</label>
              <input
                type="number"
                min="0"
                inputMode="numeric"
                value={form.sampleCount}
                onChange={set('sampleCount')}
              />
            </div>
          ) : (
            <div className="form-field">
              <label>Colis</label>
              <input
                type="number"
                min="0"
                inputMode="numeric"
                value={form.parcelCount}
                onChange={set('parcelCount')}
              />
            </div>
          )}
          {(needsTemperature || isCollect) && (
            <div className="form-field">
              <label>
                Température (°C)
                {needsTemperature && (
                  <span className="muted">
                    {' '}
                    attendu {stop.temperatureMinCelsius ?? '…'} à{' '}
                    {stop.temperatureMaxCelsius ?? '…'}
                  </span>
                )}
              </label>
              <input
                type="number"
                step="0.1"
                inputMode="decimal"
                value={form.temperatureCelsius}
                onChange={set('temperatureCelsius')}
                required={needsTemperature}
              />
              {excursion && (
                <span className="text-red">Hors plage : rupture de la chaîne du froid</span>
              )}
            </div>
          )}
          <div className="form-field form-field-wide">
            <label>Codes scannés / saisis (séparés par des virgules)</label>
            <input value={form.scannedCodes} onChange={set('scannedCodes')} autoComplete="off" />
          </div>
        </div>
      ) : (
        <div className="data-grid">
          <div className="form-field">
            <label>Motif *</label>
            <select value={form.failureReason} onChange={set('failureReason')}>
              {FAILURE_REASONS.map((r) => (
                <option key={r} value={r}>
                  {r}
                </option>
              ))}
            </select>
          </div>
        </div>
      )}

      <div className="form-field">
        <label>Commentaire</label>
        <input value={form.notes} onChange={set('notes')} />
      </div>

      <div className="form-actions">
        {onCancel && (
          <button type="button" className="btn btn-outline" onClick={onCancel}>
            Annuler
          </button>
        )}
        <button
          type="submit"
          className={'btn ' + (status === 'FAIT' ? 'btn-primary' : 'btn-danger')}
          disabled={busy}
        >
          {busy ? 'Envoi…' : status === 'FAIT' ? 'Valider le passage' : "Déclarer l'échec"}
        </button>
      </div>
    </form>
  )
}
