import { useCallback, useState } from 'react'
import BarcodeScanner, { scanSupported } from '../../components/BarcodeScanner'
import { FAILURE_REASONS } from '../../tours'

const isCollect = (stop) =>
  stop.type === 'COLLECTE' || stop.siteKind === 'PHARMACIE' || stop.siteKind === 'LABORATOIRE'

const hasTempRange = (stop) =>
  stop.temperatureMinCelsius != null || stop.temperatureMaxCelsius != null

/** Un arrêt peut-il être validé en un seul geste (rien à confirmer) ? */
export const canValidateDirectly = (stop) => !hasTempRange(stop) && stop.expectedQuantity == null

function Stepper({ label, value, onChange, hint }) {
  return (
    <div className="qp-field">
      <div className="qp-label">
        {label} {hint && <span className="qp-hint">{hint}</span>}
      </div>
      <div className="qp-stepper">
        <button
          type="button"
          aria-label={`${label} moins un`}
          onClick={() => onChange(Math.max(0, value - 1))}
        >
          −
        </button>
        <output aria-live="polite">{value}</output>
        <button type="button" aria-label={`${label} plus un`} onClick={() => onChange(value + 1)}>
          +
        </button>
      </div>
    </div>
  )
}

/** Pavé numérique large (gants, plein soleil) pour la température. */
function TempPad({ value, onChange, min, max }) {
  const press = (k) => {
    if (k === '⌫') return onChange(value.slice(0, -1))
    if (k === '−') return onChange(value.startsWith('-') ? value.slice(1) : `-${value}`)
    if (k === ',' && value.includes(',')) return undefined
    if (value.replace('-', '').length >= 4) return undefined
    return onChange(value + k)
  }
  const num = value === '' || value === '-' ? null : Number(value.replace(',', '.'))
  const out = num != null && ((min != null && num < min) || (max != null && num > max))
  return (
    <div className="qp-field">
      <div className="qp-label">
        Température{' '}
        <span className="qp-hint">
          attendu {min ?? '…'} à {max ?? '…'} °C
        </span>
      </div>
      <div
        className={'qp-temp-display' + (out ? ' out' : num != null ? ' ok' : '')}
        aria-live="polite"
      >
        {value === '' ? '— —' : value} °C
      </div>
      {out && (
        <div className="qp-warning">
          Hors plage : la rupture de la chaîne du froid sera signalée
        </div>
      )}
      <div className="qp-pad">
        {['1', '2', '3', '4', '5', '6', '7', '8', '9', '−', '0', ','].map((k) => (
          <button key={k} type="button" onClick={() => press(k)}>
            {k}
          </button>
        ))}
        <button
          type="button"
          className="qp-pad-wide"
          onClick={() => press('⌫')}
          aria-label="Effacer"
        >
          ⌫ Effacer
        </button>
      </div>
    </div>
  )
}

/** Feuille « Tout est OK » : quantité pré-remplie, température si exigée, signataire en un geste. */
export function OkSheet({ stop, onSubmit, onClose }) {
  const collect = isCollect(stop)
  const needsTemp = hasTempRange(stop)
  const [qty, setQty] = useState(stop.expectedQuantity ?? 1)
  const [temp, setTemp] = useState('')
  const [codes, setCodes] = useState([])
  const [scanning, setScanning] = useState(false)
  const defaultSigner = stop.suggestedSigner || ''
  const [signer, setSigner] = useState(defaultSigner)
  const [otherSigner, setOtherSigner] = useState(false)

  const presets = [
    ...(stop.suggestedSigner ? [stop.suggestedSigner] : []),
    stop.siteKind === 'PHARMACIE'
      ? 'Pharmacien(ne)'
      : stop.siteKind === 'LABORATOIRE'
        ? 'Accueil labo'
        : 'Destinataire',
    'Accueil'
  ].filter((v, i, a) => a.indexOf(v) === i)

  const addCode = useCallback((code) => {
    setCodes((c) => {
      if (c.includes(code)) return c
      const next = [...c, code]
      setQty((q) => Math.max(q, next.length))
      return next
    })
  }, [])

  const tempValue = temp === '' || temp === '-' ? null : Number(temp.replace(',', '.'))
  const ready = !needsTemp || tempValue != null

  const submit = () =>
    onSubmit({
      status: 'FAIT',
      signedBy: signer || null,
      sampleCount: collect ? qty : null,
      parcelCount: collect ? null : qty,
      temperatureCelsius: tempValue,
      scannedCodes: codes.length ? codes.join(',') : null
    })

  return (
    <div className="qp-sheet" role="dialog" aria-label="Valider le passage">
      <div className="qp-head">
        <strong>{stop.siteName}</strong>
        <button type="button" className="qp-close" onClick={onClose} aria-label="Fermer">
          ✕
        </button>
      </div>

      <Stepper
        label={collect ? 'Échantillons' : 'Colis'}
        value={qty}
        onChange={setQty}
        hint={stop.expectedQuantity != null ? `attendu ${stop.expectedQuantity}` : null}
      />

      {scanSupported() &&
        (scanning ? (
          <BarcodeScanner onCode={addCode} onClose={() => setScanning(false)} />
        ) : (
          <button type="button" className="qp-secondary" onClick={() => setScanning(true)}>
            📷 Scanner{' '}
            {codes.length > 0
              ? `(${codes.length} code${codes.length > 1 ? 's' : ''})`
              : 'les codes'}
          </button>
        ))}

      {needsTemp && (
        <TempPad
          value={temp}
          onChange={setTemp}
          min={stop.temperatureMinCelsius}
          max={stop.temperatureMaxCelsius}
        />
      )}

      <div className="qp-field">
        <div className="qp-label">
          Remis à <span className="qp-hint">facultatif</span>
        </div>
        <div className="qp-chips">
          {presets.map((p) => (
            <button
              key={p}
              type="button"
              className={signer === p && !otherSigner ? 'on' : ''}
              onClick={() => {
                setOtherSigner(false)
                setSigner(signer === p ? '' : p)
              }}
            >
              {p}
            </button>
          ))}
          <button
            type="button"
            className={otherSigner ? 'on' : ''}
            onClick={() => {
              setOtherSigner(true)
              setSigner('')
            }}
          >
            Autre…
          </button>
        </div>
        {otherSigner && (
          <input
            className="qp-input"
            autoFocus
            placeholder="Nom de la personne"
            value={signer}
            onChange={(e) => setSigner(e.target.value)}
          />
        )}
      </div>

      <button type="button" className="qp-primary" disabled={!ready} onClick={submit}>
        {ready ? '✓ Valider le passage' : 'Saisissez la température'}
      </button>
    </div>
  )
}

/** Feuille « Problème » : un motif = un geste. */
export function ProblemSheet({ stop, onSubmit, onClose }) {
  const [note, setNote] = useState('')
  return (
    <div className="qp-sheet" role="dialog" aria-label="Signaler un problème">
      <div className="qp-head">
        <strong>Problème — {stop.siteName}</strong>
        <button type="button" className="qp-close" onClick={onClose} aria-label="Fermer">
          ✕
        </button>
      </div>
      <div className="qp-label">Touchez le motif</div>
      <div className="qp-reasons">
        {FAILURE_REASONS.map((r) => (
          <button
            key={r}
            type="button"
            onClick={() =>
              onSubmit({ status: 'ECHEC', failureReason: r, notes: note.trim() || null })
            }
          >
            {r}
          </button>
        ))}
      </div>
      <input
        className="qp-input"
        placeholder="Précision (facultatif, avant de toucher le motif)"
        value={note}
        onChange={(e) => setNote(e.target.value)}
      />
    </div>
  )
}
