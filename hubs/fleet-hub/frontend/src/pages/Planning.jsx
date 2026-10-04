import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import api from '../services/api'
import { duration, hhmm, shiftDate, stopTypeLabel, todayIso } from '../tours'

const CSV_TEMPLATE =
  '﻿reference;code_client;nom;adresse;code_postal;ville;type;debut;fin;duree;quantite;telephone;notes\n' +
  'CMD-001;CLI-42;Boulangerie Martin;12 rue de la République;69001;Lyon;livraison;08:00;12:00;5;3;04 78 00 00 00;Sonner à l’arrière\n' +
  'CMD-002;;Pharmacie Centrale;5 place Bellecour;69002;Lyon;collecte;09:00;11:30;6;2;;\n'

function downloadTemplate() {
  const blob = new Blob([CSV_TEMPLATE], { type: 'text/csv;charset=utf-8' })
  const href = window.URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = href
  a.download = 'modele_commandes.csv'
  a.click()
  window.URL.revokeObjectURL(href)
}

function ImportCard({ date, onImported }) {
  const [busy, setBusy] = useState(false)
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')

  const upload = async (file) => {
    if (!file) return
    setBusy(true)
    setError('')
    setResult(null)
    const data = new FormData()
    data.append('file', file)
    try {
      const res = await api.post('/orders/import', data, { params: { date } })
      setResult(res.data)
      onImported()
    } catch (err) {
      setError(err.response?.data?.message || 'Import impossible')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="card">
      <div className="card-title">
        <h3>1. Importer les commandes</h3>
        <button type="button" className="btn btn-outline btn-sm" onClick={downloadTemplate}>
          Modèle CSV
        </button>
      </div>
      <p className="muted" style={{ marginBottom: 10 }}>
        Fichier CSV (export de votre logiciel, ou Excel « Enregistrer sous… CSV »). Les sites
        inconnus sont créés et leurs adresses localisées automatiquement.
      </p>
      <label className={'drop-zone' + (busy ? ' uploading' : '')}>
        <input
          type="file"
          accept=".csv,text/csv"
          style={{ display: 'none' }}
          disabled={busy}
          onChange={(e) => {
            upload(e.target.files[0])
            e.target.value = ''
          }}
        />
        {busy ? 'Import et géolocalisation en cours…' : 'Cliquez pour choisir un fichier CSV'}
      </label>
      {error && <div className="alert alert-error">{error}</div>}
      {result && (
        <div className="alert">
          {result.ordersCreated} commande(s) importée(s) sur {result.rowsRead} ligne(s) ·{' '}
          {result.sitesCreated} site(s) créé(s) · {result.sitesGeocoded} adresse(s) localisée(s)
          {result.ordersWithoutLocation > 0 && (
            <div className="text-red">
              {result.ordersWithoutLocation} commande(s) sans position : complétez l'adresse dans{' '}
              <Link to="/sites" className="link">
                Sites
              </Link>
              .
            </div>
          )}
          {result.errors.length > 0 && (
            <ul className="import-errors">
              {result.errors.map((e) => (
                <li key={e}>{e}</li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  )
}

function DispatchCard({ date, pendingCount, onDone }) {
  const [depots, setDepots] = useState([])
  const [trucks, setTrucks] = useState([])
  const [drivers, setDrivers] = useState([])
  const [form, setForm] = useState({
    depotId: '',
    start: '08:00',
    end: '18:00',
    namePrefix: 'Tournée'
  })
  const [slots, setSlots] = useState({})
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [result, setResult] = useState(null)

  useEffect(() => {
    api
      .get('/sites')
      .then((r) => {
        const d = r.data.filter((s) => s.kind === 'DEPOT' && s.active)
        setDepots(d)
        if (d.length) setForm((f) => ({ ...f, depotId: f.depotId || String(d[0].id) }))
      })
      .catch(() => {})
    api
      .get('/trucks')
      .then((r) => setTrucks(r.data.filter((t) => t.active)))
      .catch(() => {})
    api
      .get('/drivers')
      .then((r) => setDrivers(r.data.filter((d) => d.active !== false)))
      .catch(() => {})
  }, [])

  const toggle = (truck) =>
    setSlots((s) => {
      const next = { ...s }
      if (next[truck.id]) delete next[truck.id]
      else next[truck.id] = { driverId: truck.driverId ? String(truck.driverId) : '', capacity: '' }
      return next
    })

  const setSlot = (id, k, v) => setSlots((s) => ({ ...s, [id]: { ...s[id], [k]: v } }))

  const selected = Object.keys(slots)

  const run = async () => {
    setBusy(true)
    setError('')
    setResult(null)
    try {
      const res = await api.post('/orders/dispatch', {
        date,
        depotId: Number(form.depotId),
        start: form.start || null,
        end: form.end || null,
        namePrefix: form.namePrefix,
        vehicles: selected.map((id) => ({
          truckId: Number(id),
          driverId: slots[id].driverId ? Number(slots[id].driverId) : null,
          capacity: slots[id].capacity ? Number(slots[id].capacity) : null
        }))
      })
      setResult(res.data)
      onDone()
    } catch (err) {
      setError(err.response?.data?.message || 'Répartition impossible')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="card">
      <div className="card-title">
        <h3>2. Répartir automatiquement</h3>
        <span className="muted">{pendingCount} commande(s) à planifier</span>
      </div>
      {depots.length === 0 && (
        <div className="alert">
          Créez d'abord votre dépôt (type « Dépôt », localisé) dans{' '}
          <Link to="/sites" className="link">
            Sites
          </Link>
          .
        </div>
      )}
      <div className="data-grid">
        <div className="form-field">
          <label htmlFor="dsp-depot">Dépôt</label>
          <select
            id="dsp-depot"
            value={form.depotId}
            onChange={(e) => setForm({ ...form, depotId: e.target.value })}
          >
            {depots.map((d) => (
              <option key={d.id} value={d.id}>
                {d.name}
              </option>
            ))}
          </select>
        </div>
        <div className="form-field">
          <label htmlFor="dsp-start">Départ</label>
          <input
            id="dsp-start"
            type="time"
            value={form.start}
            onChange={(e) => setForm({ ...form, start: e.target.value })}
          />
        </div>
        <div className="form-field">
          <label htmlFor="dsp-end">Retour au plus tard</label>
          <input
            id="dsp-end"
            type="time"
            value={form.end}
            onChange={(e) => setForm({ ...form, end: e.target.value })}
          />
        </div>
        <div className="form-field">
          <label htmlFor="dsp-prefix">Nom des tournées</label>
          <input
            id="dsp-prefix"
            value={form.namePrefix}
            onChange={(e) => setForm({ ...form, namePrefix: e.target.value })}
          />
        </div>
      </div>

      <h4 className="dispatch-subtitle">Véhicules disponibles</h4>
      <div className="vehicle-picks">
        {trucks.map((t) => {
          const on = !!slots[t.id]
          return (
            <div key={t.id} className={'vehicle-pick' + (on ? ' on' : '')}>
              <label className="form-checkbox-row">
                <input type="checkbox" checked={on} onChange={() => toggle(t)} />
                <span>
                  <strong>{t.registration}</strong>{' '}
                  <span className="muted">
                    {t.brand} {t.model}
                  </span>
                </span>
              </label>
              {on && (
                <div className="vehicle-pick-fields">
                  <select
                    aria-label={`Chauffeur de ${t.registration}`}
                    value={slots[t.id].driverId}
                    onChange={(e) => setSlot(t.id, 'driverId', e.target.value)}
                  >
                    <option value="">Chauffeur…</option>
                    {drivers.map((d) => (
                      <option key={d.id} value={d.id}>
                        {d.firstName} {d.lastName}
                      </option>
                    ))}
                  </select>
                  <input
                    type="number"
                    min="1"
                    placeholder="Capacité (colis)"
                    aria-label={`Capacité de ${t.registration}`}
                    value={slots[t.id].capacity}
                    onChange={(e) => setSlot(t.id, 'capacity', e.target.value)}
                  />
                </div>
              )}
            </div>
          )
        })}
        {trucks.length === 0 && <p className="muted">Aucun véhicule actif.</p>}
      </div>

      {error && <div className="alert alert-error">{error}</div>}
      <div className="form-actions">
        <button
          className="btn btn-primary"
          disabled={busy || !form.depotId || selected.length === 0 || pendingCount === 0}
          onClick={run}
        >
          {busy ? 'Calcul des tournées…' : `⚡ Répartir sur ${selected.length} véhicule(s)`}
        </button>
      </div>

      {result && (
        <div className="dispatch-result">
          <div className="alert">
            {result.tours.length} tournée(s) créée(s) · {result.totalKm} km au total · moteur{' '}
            {result.solver}
          </div>
          <div className="tour-cards">
            {result.tours.map((t) => (
              <Link key={t.id} to={`/tours/${t.id}`} className="tour-card">
                <strong>{t.name}</strong>
                <span className="muted block">
                  {t.stopsTotal} arrêt(s) · {t.plannedDistanceKm} km ·{' '}
                  {duration(t.plannedDurationMinutes)}
                  {t.stopsLate > 0 ? ` · ${t.stopsLate} hors créneau` : ''}
                </span>
                <span className="link">Ouvrir →</span>
              </Link>
            ))}
          </div>
          {result.unassigned.length > 0 && (
            <div className="card unassigned">
              <h4>{result.unassigned.length} commande(s) non placée(s)</h4>
              <ul>
                {result.unassigned.map((u) => (
                  <li key={u.orderId}>
                    <strong>{u.siteName}</strong>
                    {u.reference ? ` (${u.reference})` : ''} —{' '}
                    <span className="muted">{u.reason}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}
    </div>
  )
}

export default function Planning() {
  const [date, setDate] = useState(shiftDate(todayIso(), 1))
  const [orders, setOrders] = useState([])
  const [error, setError] = useState('')

  const load = useCallback(
    () =>
      api
        .get('/orders', { params: { date } })
        .then((res) => setOrders(res.data))
        .catch(() => setError('Impossible de charger les commandes')),
    [date]
  )

  useEffect(() => {
    load()
  }, [load])

  const pending = useMemo(() => orders.filter((o) => o.status === 'A_PLANIFIER'), [orders])
  const totalQty = pending.reduce((n, o) => n + o.quantity, 0)

  const remove = async (o) => {
    setError('')
    try {
      await api.delete(`/orders/${o.id}`)
      load()
    } catch (err) {
      setError(err.response?.data?.message || 'Suppression impossible')
    }
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Planification</h2>
          <p>Importez les commandes du jour et laissez Fleet Hub construire les tournées</p>
        </div>
        <div className="header-actions">
          <button
            className="btn btn-outline btn-sm"
            onClick={() => setDate(shiftDate(date, -1))}
            aria-label="Jour précédent"
          >
            ←
          </button>
          <input
            type="date"
            value={date}
            onChange={(e) => e.target.value && setDate(e.target.value)}
          />
          <button
            className="btn btn-outline btn-sm"
            onClick={() => setDate(shiftDate(date, 1))}
            aria-label="Jour suivant"
          >
            →
          </button>
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      <ImportCard date={date} onImported={load} />
      <DispatchCard date={date} pendingCount={pending.length} onDone={load} />

      <div className="card">
        <div className="card-title">
          <h3>Commandes du {new Date(`${date}T12:00:00`).toLocaleDateString('fr-FR')}</h3>
          <span className="muted">
            {pending.length} à planifier ({totalQty} unité(s)) · {orders.length - pending.length}{' '}
            planifiée(s)
          </span>
        </div>
        <div className="table-scroll">
          <table className="table table-hover">
            <thead>
              <tr>
                <th>Réf.</th>
                <th>Site</th>
                <th>Type</th>
                <th>Créneau</th>
                <th>Qté</th>
                <th>Statut</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {orders.map((o) => (
                <tr key={o.id}>
                  <td>{o.reference || '—'}</td>
                  <td className="cell-strong">
                    {o.siteName}
                    <span className="muted block">
                      {[o.address, o.city].filter(Boolean).join(', ')}
                      {o.latitude == null && <span className="text-red"> · non localisé</span>}
                    </span>
                  </td>
                  <td>{stopTypeLabel(o.type)}</td>
                  <td>
                    {o.windowStart || o.windowEnd
                      ? `${hhmm(o.windowStart)}–${hhmm(o.windowEnd)}`
                      : '—'}
                  </td>
                  <td>{o.quantity}</td>
                  <td>
                    {o.status === 'PLANIFIEE' ? (
                      <Link to={`/tours/${o.tourId}`} className="badge badge-green">
                        {o.tourName}
                      </Link>
                    ) : (
                      <span className="badge badge-orange">À planifier</span>
                    )}
                  </td>
                  <td>
                    {o.status === 'A_PLANIFIER' && (
                      <button
                        className="btn btn-danger btn-sm"
                        onClick={() => remove(o)}
                        aria-label="Supprimer"
                      >
                        ✕
                      </button>
                    )}
                  </td>
                </tr>
              ))}
              {orders.length === 0 && (
                <tr>
                  <td colSpan="7" className="table-empty">
                    Aucune commande pour ce jour : importez un fichier ci-dessus.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
