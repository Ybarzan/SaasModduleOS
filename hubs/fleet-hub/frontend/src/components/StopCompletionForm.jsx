import { useCallback, useId, useState } from 'react'
import { FAILURE_REASONS } from '../tours'
import BarcodeScanner, { scanSupported } from './BarcodeScanner'

/**
 * Clôture d'un arrêt : preuve de passage (signataire, colis, échantillons,
 * codes scannés, température) ou motif d'échec. Partagé entre l'écran
 * d'exploitation et l'application chauffeur.
 */
export default function StopCompletionForm({ stop, onSubmit, onCancel, busy }) {
  const uid = useId()
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
  const [scanning, setScanning] = useState(false)
  const needsTemperature = stop.temperatureMinCelsius != null || stop.temperatureMaxCelsius != null
  const isCollect =
    stop.type === 'COLLECTE' || stop.siteKind === 'PHARMACIE' || stop.siteKind === 'LABORATOIRE'

  // Chaque code scanné s'ajoute à la liste et met à jour le nombre d'échantillons / colis
  const countKey = isCollect ? 'sampleCount' : 'parcelCount'
  const addCode = useCallback(
    (code) =>
      setForm((f) => {
        const codes = f.scannedCodes
          ? f.scannedCodes
              .split(',')
              .map((c) => c.trim())
              .filter(Boolean)
          : []
        if (codes.includes(code)) return f
        codes.push(code)
        return { ...f, scannedCodes: codes.join(','), [countKey]: String(codes.length) }
      }),
    [countKey]
  )

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
            <label htmlFor={`${uid}-signedBy`}>Remis à / signé par</label>
            <input
              id={`${uid}-signedBy`}
              value={form.signedBy}
              onChange={set('signedBy')}
              autoComplete="off"
            />
          </div>
          {isCollect ? (
            <div className="form-field">
              <label htmlFor={`${uid}-samples`}>Échantillons / sachets</label>
              <input
                id={`${uid}-samples`}
                type="number"
                min="0"
                inputMode="numeric"
                value={form.sampleCount}
                onChange={set('sampleCount')}
              />
            </div>
          ) : (
            <div className="form-field">
              <label htmlFor={`${uid}-parcels`}>Colis</label>
              <input
                id={`${uid}-parcels`}
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
              <label htmlFor={`${uid}-temperature`}>
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
                id={`${uid}-temperature`}
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
            <label htmlFor={`${uid}-codes`}>
              Codes scannés / saisis (séparés par des virgules)
            </label>
            <div className="geocode-bar">
              <input
                id={`${uid}-codes`}
                value={form.scannedCodes}
                onChange={set('scannedCodes')}
                autoComplete="off"
              />
              {scanSupported() && !scanning && (
                <button type="button" className="btn btn-outline" onClick={() => setScanning(true)}>
                  📷 Scanner
                </button>
              )}
            </div>
            {scanning && <BarcodeScanner onCode={addCode} onClose={() => setScanning(false)} />}
          </div>
        </div>
      ) : (
        <div className="data-grid">
          <div className="form-field">
            <label htmlFor={`${uid}-reason`}>Motif *</label>
            <select id={`${uid}-reason`} value={form.failureReason} onChange={set('failureReason')}>
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
        <label htmlFor={`${uid}-notes`}>Commentaire</label>
        <input id={`${uid}-notes`} value={form.notes} onChange={set('notes')} />
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
