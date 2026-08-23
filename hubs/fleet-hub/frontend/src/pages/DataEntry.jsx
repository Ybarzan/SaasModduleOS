import { useEffect, useState } from 'react'
import api from '../services/api'

const TAB_DRIVERS = 'drivers'
const TAB_TRUCKS = 'trucks'
const TAB_ASSIGNMENTS = 'assignments'
const TAB_TRIPS = 'trips'
const TAB_FUEL = 'fuel'
const TAB_TACHO = 'tacho'

const tabs = [
  { key: TAB_DRIVERS, label: 'Chauffeurs' },
  { key: TAB_TRUCKS, label: 'Camions' },
  { key: TAB_ASSIGNMENTS, label: 'Affectations' },
  { key: TAB_TRIPS, label: 'Trajets' },
  { key: TAB_FUEL, label: 'Carburant' },
  { key: TAB_TACHO, label: 'Tachygraphe' }
]

const driverFields = [
  { key: 'firstName', label: 'Prénom', type: 'text', required: true },
  { key: 'lastName', label: 'Nom', type: 'text', required: true },
  { key: 'licenseNumber', label: 'N° de permis', type: 'text', required: true },
  { key: 'phone', label: 'Téléphone', type: 'text', required: true },
  { key: 'email', label: 'Email', type: 'email' },
  { key: 'hireDate', label: "Date d'embauche", type: 'date' },
  { key: 'active', label: 'Actif', type: 'checkbox', default: true }
]

const truckFields = [
  { key: 'registration', label: 'Immatriculation', type: 'text', required: true },
  { key: 'brand', label: 'Marque', type: 'text', required: true },
  { key: 'model', label: 'Modèle', type: 'text', required: true },
  { key: 'modelYear', label: 'Année', type: 'number' },
  { key: 'truckType', label: 'Type', type: 'select', required: true, options: ['TRACTEUR', 'PORTEUR', 'FOURGON'] },
  { key: 'fuelType', label: 'Énergie', type: 'select', required: true, options: ['DIESEL', 'ELECTRIC'] },
  { key: 'capacityTons', label: 'Capacité (t)', type: 'number' },
  { key: 'expectedConsumptionL100Km', label: 'Conso réf. (L/100)', type: 'number', required: true },
  { key: 'acquisitionDate', label: "Date d'acquisition", type: 'date' },
  { key: 'purchasePrice', label: "Prix d'achat (€)", type: 'number' },
  { key: 'active', label: 'Actif', type: 'checkbox', default: true }
]

const assignmentFields = [
  { key: 'driverId', label: 'Chauffeur', type: 'select', required: true, ref: 'drivers' },
  { key: 'truckId', label: 'Camion', type: 'select', required: true, ref: 'trucks' },
  { key: 'startDate', label: 'Début', type: 'date', required: true },
  { key: 'endDate', label: 'Fin', type: 'date' },
  { key: 'active', label: 'Active', type: 'checkbox', default: true }
]

const tripFields = [
  { key: 'driverId', label: 'Chauffeur', type: 'select', required: true, ref: 'drivers' },
  { key: 'truckId', label: 'Camion', type: 'select', required: true, ref: 'trucks' },
  { key: 'startTime', label: 'Départ', type: 'datetime', required: true },
  { key: 'endTime', label: 'Arrivée', type: 'datetime', required: true },
  { key: 'distanceKm', label: 'Distance (km)', type: 'number', required: true },
  { key: 'cargoWeightTons', label: 'Charge (t)', type: 'number' },
  { key: 'loaded', label: 'En charge', type: 'checkbox', default: true },
  { key: 'status', label: 'Statut', type: 'select', required: true, options: ['EN_COURS', 'TERMINE', 'ANNULE'] },
  { key: 'onTime', label: 'À l\'heure', type: 'checkbox', default: true }
]

const fuelFields = [
  { key: 'truckId', label: 'Camion', type: 'select', required: true, ref: 'trucks' },
  { key: 'date', label: 'Date', type: 'date', required: true },
  { key: 'liters', label: 'Litres', type: 'number', required: true },
  { key: 'amount', label: 'Montant (€)', type: 'number', required: true },
  { key: 'odometerKm', label: 'Kilométrage', type: 'number' }
]

const tachoFields = [
  { key: 'driverId', label: 'Chauffeur', type: 'select', required: true, ref: 'drivers' },
  { key: 'date', label: 'Date', type: 'date', required: true },
  { key: 'drivingHours', label: 'Conduite (h)', type: 'number', required: true },
  { key: 'workHours', label: 'Travail (h)', type: 'number' },
  { key: 'restMinutes', label: 'Repos (min)', type: 'number' }
]

const fieldsByTab = {
  [TAB_DRIVERS]: driverFields,
  [TAB_TRUCKS]: truckFields,
  [TAB_ASSIGNMENTS]: assignmentFields,
  [TAB_TRIPS]: tripFields,
  [TAB_FUEL]: fuelFields,
  [TAB_TACHO]: tachoFields
}

const endpointByTab = {
  [TAB_DRIVERS]: '/drivers',
  [TAB_TRUCKS]: '/trucks',
  [TAB_ASSIGNMENTS]: '/assignments',
  [TAB_TRIPS]: '/trips',
  [TAB_FUEL]: '/fuel-records',
  [TAB_TACHO]: '/tachograph-days'
}

const columnsByTab = {
  [TAB_DRIVERS]: [
    { key: 'firstName', label: 'Chauffeur', render: (d) => `${d.firstName} ${d.lastName}` },
    { key: 'licenseNumber', label: 'Permis' },
    { key: 'phone', label: 'Téléphone' },
    { key: 'email', label: 'Email' },
    { key: 'active', label: 'Actif', render: (d) => (d.active ? 'Oui' : 'Non') }
  ],
  [TAB_TRUCKS]: [
    { key: 'registration', label: 'Véhicule', render: (t) => `${t.brand} ${t.model}` },
    { key: 'registration', label: 'Immatriculation' },
    { key: 'truckType', label: 'Type' },
    { key: 'fuelType', label: 'Énergie' },
    { key: 'expectedConsumptionL100Km', label: 'Conso réf.', render: (t) => (t.expectedConsumptionL100Km == null ? '—' : `${t.expectedConsumptionL100Km} L`) }
  ],
  [TAB_ASSIGNMENTS]: [
    { key: 'driverName', label: 'Chauffeur' },
    { key: 'truckRegistration', label: 'Camion' },
    { key: 'startDate', label: 'Début' },
    { key: 'endDate', label: 'Fin', render: (a) => a.endDate || '—' },
    { key: 'active', label: 'Active', render: (a) => (a.active ? 'Oui' : 'Non') }
  ],
  [TAB_TRIPS]: [
    { key: 'driverName', label: 'Chauffeur' },
    { key: 'truckRegistration', label: 'Camion' },
    { key: 'startTime', label: 'Départ', render: (t) => new Date(t.startTime).toLocaleString('fr-FR') },
    { key: 'distanceKm', label: 'Km', render: (t) => (t.distanceKm == null ? '—' : Math.round(t.distanceKm)) },
    { key: 'status', label: 'Statut' },
    { key: 'onTime', label: 'À l\'heure', render: (t) => (t.onTime ? 'Oui' : 'Non') }
  ],
  [TAB_FUEL]: [
    { key: 'truckRegistration', label: 'Camion' },
    { key: 'date', label: 'Date' },
    { key: 'liters', label: 'Litres' },
    { key: 'amount', label: 'Montant (€)' },
    { key: 'odometerKm', label: 'Km' }
  ],
  [TAB_TACHO]: [
    { key: 'driverName', label: 'Chauffeur' },
    { key: 'date', label: 'Date' },
    { key: 'drivingHours', label: 'Conduite (h)' },
    { key: 'workHours', label: 'Travail (h)' },
    { key: 'restMinutes', label: 'Repos (min)' },
    { key: 'compliant', label: 'Conforme', render: (d) => (d.compliant ? <span className="badge badge-green">Oui</span> : <span className="badge badge-red">Non</span>) },
    { key: 'reasons', label: 'Motifs', render: (d) => (d.reasons?.length ? <span className="muted">{d.reasons.join(' · ')}</span> : '—') }
  ]
}

function buildForm(fields, item) {
  const form = {}
  for (const f of fields) {
    let v = item ? item[f.key] : f.default
    if (v == null) v = f.default
    if (f.type === 'number') form[f.key] = v === '' ? '' : String(v)
    else if (f.type === 'checkbox') form[f.key] = !!v
    else if (f.type === 'datetime') form[f.key] = v ? String(v).slice(0, 16) : ''
    else form[f.key] = v
  }
  return form
}

function toPayload(fields, form) {
  const payload = {}
  for (const f of fields) {
    const v = form[f.key]
    if (f.type === 'number') payload[f.key] = v === '' || v == null ? null : Number(v)
    else if (f.type === 'checkbox') payload[f.key] = !!v
    else payload[f.key] = v
  }
  return payload
}

function CrudTab({ tab, options, onChanged }) {
  const fields = fieldsByTab[tab]
  const endpoint = endpointByTab[tab]
  const columns = columnsByTab[tab]
  const [items, setItems] = useState([])
  const [form, setForm] = useState(() => buildForm(fields, null))
  const [editingId, setEditingId] = useState(null)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [loading, setLoading] = useState(true)

  const load = () => {
    api
      .get(endpoint)
      .then((res) => {
        setItems(res.data)
        setLoading(false)
      })
      .catch(() => {
        setError('Impossible de charger la liste')
        setLoading(false)
      })
  }

  useEffect(() => {
    load()
  }, [endpoint])

  const reset = () => {
    setForm(buildForm(fields, null))
    setEditingId(null)
    setError('')
    setSuccess('')
  }

  const submit = (e) => {
    e.preventDefault()
    setError('')
    setSuccess('')
    const payload = toPayload(fields, form)
    const request = editingId
      ? api.put(`${endpoint}/${editingId}`, payload)
      : api.post(endpoint, payload)
    request
      .then(() => {
        reset()
        load()
        onChanged()
        setSuccess('Enregistré')
      })
      .catch((err) => setError(err.response?.data?.message || 'Erreur lors de l’enregistrement'))
  }

  const startEdit = (item) => {
    setForm(buildForm(fields, item))
    setEditingId(item.id)
    setError('')
    setSuccess('')
  }

  const remove = (item) => {
    if (!window.confirm('Supprimer cet élément ?')) return
    api
      .delete(`${endpoint}/${item.id}`)
      .then(() => {
        load()
        onChanged()
        if (editingId === item.id) reset()
      })
      .catch((err) => setError(err.response?.data?.message || 'Suppression impossible'))
  }

  const setValue = (key, value) => setForm((prev) => ({ ...prev, [key]: value }))

  return (
    <div>
      <div className="card">
        <div className="card-title">
          <h3>{editingId ? 'Modifier' : 'Nouvel élément'}</h3>
          {editingId && (
            <button type="button" className="btn btn-outline btn-sm" onClick={reset}>
              Annuler
            </button>
          )}
        </div>
        {error && <div className="alert alert-error">{error}</div>}
        {success && <div className="alert">{success}</div>}
        <form onSubmit={submit}>
          <div className="data-grid">
            {fields.map((f) => {
              if (f.type === 'checkbox') {
                return (
                  <label key={f.key} className="form-checkbox-row">
                    <input
                      type="checkbox"
                      checked={!!form[f.key]}
                      onChange={(e) => setValue(f.key, e.target.checked)}
                    />
                    {f.label}
                  </label>
                )
              }
              let optionsList = []
              if (f.ref) {
                optionsList = (options[f.ref] || []).map((o) => ({
                  value: o.id,
                  label: o.label
                }))
              } else if (f.options) {
                optionsList = f.options.map((o) => ({ value: o, label: o }))
              }
              return (
                <div key={f.key} className="form-field">
                  <label>{f.label}{f.required ? ' *' : ''}</label>
                  {f.type === 'select' ? (
                    <select
                      value={form[f.key] ?? ''}
                      required={f.required}
                      onChange={(e) => setValue(f.key, e.target.value)}
                    >
                      <option value="">—</option>
                      {optionsList.map((o) => (
                        <option key={o.value} value={o.value}>
                          {o.label}
                        </option>
                      ))}
                    </select>
                  ) : (
                    <input
                      type={f.type}
                      value={form[f.key] ?? ''}
                      required={f.required}
                      step={f.type === 'number' ? 'any' : undefined}
                      onChange={(e) => setValue(f.key, e.target.value)}
                    />
                  )}
                </div>
              )
            })}
          </div>
          <div className="form-actions">
            <button type="submit" className="btn btn-primary">
              {editingId ? 'Enregistrer les modifications' : 'Ajouter'}
            </button>
          </div>
        </form>
      </div>

      <div className="card">
        <div className="card-title">
          <h3>Liste</h3>
          <span className="muted">{items.length} élément(s)</span>
        </div>
        {loading ? (
          <p className="muted table-empty">Chargement…</p>
        ) : items.length === 0 ? (
          <p className="muted table-empty">Aucun élément</p>
        ) : (
          <table className="table table-hover">
            <thead>
              <tr>
                {columns.map((c) => (
                  <th key={`${c.label}-${c.key}`}>{c.label}</th>
                ))}
                <th></th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.id}>
                  {columns.map((c, i) => (
                    <td key={`${c.label}-${i}`}>
                      {c.render ? c.render(item) : item[c.key] ?? '—'}
                    </td>
                  ))}
                  <td>
                    <div className="row-actions">
                      <button className="btn btn-outline btn-sm" onClick={() => startEdit(item)}>
                        Éditer
                      </button>
                      <button className="btn btn-danger btn-sm" onClick={() => remove(item)}>
                        Supprimer
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}

export default function DataEntry() {
  const [activeTab, setActiveTab] = useState(TAB_DRIVERS)
  const [drivers, setDrivers] = useState([])
  const [trucks, setTrucks] = useState([])

  const loadRefs = () => {
    api.get('/drivers').then((res) =>
      setDrivers(
        res.data.map((d) => ({
          id: d.id,
          label: `${d.firstName} ${d.lastName} · ${d.licenseNumber}`
        }))
      )
    )
    api.get('/trucks').then((res) =>
      setTrucks(
        res.data.map((t) => ({
          id: t.id,
          label: `${t.registration} · ${t.brand} ${t.model}`
        }))
      )
    )
  }

  useEffect(() => {
    loadRefs()
  }, [])

  const options = { drivers, trucks }

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Saisie manuelle</h2>
          <p>Ajouter, modifier ou supprimer des données (les KPIs se recalculent)</p>
        </div>
      </div>

      <div className="tabs">
        {tabs.map((t) => (
          <button
            key={t.key}
            className={`tab ${activeTab === t.key ? 'active' : ''}`}
            onClick={() => setActiveTab(t.key)}
          >
            {t.label}
          </button>
        ))}
      </div>

      <CrudTab tab={activeTab} options={options} onChanged={loadRefs} />
    </div>
  )
}
