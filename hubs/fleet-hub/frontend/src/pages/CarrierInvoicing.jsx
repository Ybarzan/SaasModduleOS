import { useEffect, useState } from 'react'
import api from '../services/api'
import StatCard from '../components/StatCard'

const STATUS_LABELS = {
  DRAFT: 'Brouillon',
  RECEIVED: 'Reçue',
  UNDER_REVIEW: 'En cours de révision',
  APPROVED: 'Approuvée',
  PAID: 'Payée',
  DISPUTED: 'Litige',
  REJECTED: 'Rejetée'
}

const STATUS_COLORS = {
  DRAFT: 'gray',
  RECEIVED: 'blue',
  UNDER_REVIEW: 'orange',
  APPROVED: 'green',
  PAID: 'green',
  DISPUTED: 'red',
  REJECTED: 'red'
}

const EMPTY_FORM = {
  invoiceNumber: '',
  invoiceDate: '',
  dueDate: '',
  carrierName: '',
  carrierReference: '',
  totalAmount: 0,
  currency: 'USD',
  totalAmountEur: 0,
  freightAmount: 0,
  fuelSurcharge: 0,
  securityFee: 0,
  handlingFee: 0,
  customsFee: 0,
  otherCharges: 0,
  otherChargesDescription: '',
  shipmentReference: '',
  negotiatedRate: 0,
  reconciliationNotes: ''
}

const fmtMoney = (amount, currency) =>
  new Intl.NumberFormat('fr-FR', { style: 'currency', currency: currency || 'EUR' }).format(amount || 0)

const fmtDate = (s) => (s ? new Date(s).toLocaleDateString('fr-FR') : '—')

export default function CarrierInvoicing() {
  const [invoices, setInvoices] = useState([])
  const [stats, setStats] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState(EMPTY_FORM)
  const [submitting, setSubmitting] = useState(false)
  const [selected, setSelected] = useState(null)
  const [deleteConfirm, setDeleteConfirm] = useState(null)
  const [disputeTarget, setDisputeTarget] = useState(null)
  const [disputeReason, setDisputeReason] = useState('')
  const [busyId, setBusyId] = useState(null)

  const load = () => {
    setLoading(true)
    api
      .get('/carrier-invoices', { params: { page: 0, size: 200 } })
      .then((res) => setInvoices(Array.isArray(res.data) ? res.data : res.data.content || []))
      .catch(() => setError('Impossible de charger les factures'))
      .finally(() => setLoading(false))
    api
      .get('/carrier-invoices/stats')
      .then((res) => setStats(res.data))
      .catch(() => {})
  }

  useEffect(() => {
    load()
  }, [])

  const set = (field) => (e) => setForm({ ...form, [field]: e.target.value })
  const setNum = (field) => (e) => setForm({ ...form, [field]: parseFloat(e.target.value) || 0 })

  const handleCreate = async (e) => {
    e.preventDefault()
    setError('')
    setNotice('')
    setSubmitting(true)
    try {
      await api.post('/carrier-invoices', form)
      setNotice('Facture créée avec succès')
      setShowForm(false)
      setForm(EMPTY_FORM)
      load()
    } catch (err) {
      setError(err.response?.data?.message || 'La création a échoué')
    } finally {
      setSubmitting(false)
    }
  }

  const updateStatus = async (id, status) => {
    setBusyId(id)
    setError('')
    try {
      await api.put(`/carrier-invoices/${id}/status`, { status })
      setNotice('Statut mis à jour')
      load()
    } catch (err) {
      setError(err.response?.data?.message || 'La mise à jour a échoué')
    } finally {
      setBusyId(null)
    }
  }

  const remove = async (id) => {
    setBusyId(id)
    try {
      await api.delete(`/carrier-invoices/${id}`)
      setNotice('Facture supprimée')
      setDeleteConfirm(null)
      load()
    } catch (err) {
      setError(err.response?.data?.message || 'La suppression a échoué')
    } finally {
      setBusyId(null)
    }
  }

  const sendDispute = async () => {
    setBusyId(disputeTarget)
    try {
      await api.put(`/carrier-invoices/${disputeTarget}/status`, { status: 'DISPUTED', reason: disputeReason })
      setNotice('Litige signalé')
      setDisputeTarget(null)
      setDisputeReason('')
      load()
    } catch (err) {
      setError(err.response?.data?.message || 'Le signalement a échoué')
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Facturation transporteurs</h2>
          <p>Factures transporteurs (comptes fournisseurs)</p>
        </div>
        <div className="header-actions">
          <button className="btn btn-primary" onClick={() => setShowForm(true)}>
            + Nouvelle facture
          </button>
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}
      {notice && <div className="alert">{notice}</div>}

      <div className="north-star-grid">
        <StatCard label="Total factures" value={stats?.total ?? '—'} icon="📄" tone="blue" />
        <StatCard label="En attente" value={stats?.pending ?? '—'} icon="⏳" tone="purple" />
        <StatCard label="Approuvées" value={stats?.approved ?? '—'} icon="✅" tone="green" />
        <StatCard
          label="Montant total (EUR)"
          value={stats?.totalAmountEur != null ? fmtMoney(stats.totalAmountEur, 'EUR') : '—'}
          icon="💰"
          tone="red"
        />
      </div>

      {showForm && (
        <div className="card">
          <div className="card-title">
            <h3>Nouvelle facture</h3>
          </div>
          <form onSubmit={handleCreate}>
            <div className="data-grid">
              <div className="form-field">
                <label>N° facture *</label>
                <input type="text" value={form.invoiceNumber} required onChange={set('invoiceNumber')} />
              </div>
              <div className="form-field">
                <label>Nom transporteur *</label>
                <input type="text" value={form.carrierName} required onChange={set('carrierName')} />
              </div>
              <div className="form-field">
                <label>Date facture *</label>
                <input type="date" value={form.invoiceDate} required onChange={set('invoiceDate')} />
              </div>
              <div className="form-field">
                <label>Échéance</label>
                <input type="date" value={form.dueDate} onChange={set('dueDate')} />
              </div>
              <div className="form-field">
                <label>Référence transporteur</label>
                <input type="text" value={form.carrierReference} onChange={set('carrierReference')} />
              </div>
              <div className="form-field">
                <label>Référence expédition</label>
                <input type="text" value={form.shipmentReference} onChange={set('shipmentReference')} />
              </div>
            </div>

            <h4 style={{ marginTop: 18, marginBottom: 8 }}>Montants</h4>
            <div className="data-grid">
              <div className="form-field">
                <label>Montant total *</label>
                <input type="number" step="0.01" value={form.totalAmount || ''} required onChange={setNum('totalAmount')} />
              </div>
              <div className="form-field">
                <label>Devise</label>
                <select value={form.currency} onChange={set('currency')}>
                  <option value="USD">USD</option>
                  <option value="EUR">EUR</option>
                  <option value="GBP">GBP</option>
                  <option value="CHF">CHF</option>
                </select>
              </div>
              <div className="form-field">
                <label>Montant EUR</label>
                <input type="number" step="0.01" value={form.totalAmountEur || ''} onChange={setNum('totalAmountEur')} />
              </div>
            </div>

            <h4 style={{ marginTop: 18, marginBottom: 8 }}>Détail des frais</h4>
            <div className="data-grid">
              <div className="form-field">
                <label>Fret</label>
                <input type="number" step="0.01" value={form.freightAmount || ''} onChange={setNum('freightAmount')} />
              </div>
              <div className="form-field">
                <label>Surcharge carburant</label>
                <input type="number" step="0.01" value={form.fuelSurcharge || ''} onChange={setNum('fuelSurcharge')} />
              </div>
              <div className="form-field">
                <label>Frais sécurité</label>
                <input type="number" step="0.01" value={form.securityFee || ''} onChange={setNum('securityFee')} />
              </div>
              <div className="form-field">
                <label>Frais manutention</label>
                <input type="number" step="0.01" value={form.handlingFee || ''} onChange={setNum('handlingFee')} />
              </div>
              <div className="form-field">
                <label>Frais douane</label>
                <input type="number" step="0.01" value={form.customsFee || ''} onChange={setNum('customsFee')} />
              </div>
              <div className="form-field">
                <label>Autres frais</label>
                <input type="number" step="0.01" value={form.otherCharges || ''} onChange={setNum('otherCharges')} />
              </div>
            </div>
            {form.otherCharges > 0 && (
              <div className="form-field" style={{ marginTop: 10 }}>
                <label>Description autres frais</label>
                <input type="text" value={form.otherChargesDescription} onChange={set('otherChargesDescription')} />
              </div>
            )}

            <h4 style={{ marginTop: 18, marginBottom: 8 }}>Réconciliation</h4>
            <div className="data-grid">
              <div className="form-field">
                <label>Taux négocié</label>
                <input type="number" step="0.01" value={form.negotiatedRate || ''} onChange={setNum('negotiatedRate')} />
              </div>
              <div className="form-field">
                <label>Notes</label>
                <input type="text" value={form.reconciliationNotes} onChange={set('reconciliationNotes')} />
              </div>
            </div>

            <div className="form-actions">
              <button type="submit" className="btn btn-primary" disabled={submitting}>
                {submitting ? 'Création…' : 'Créer la facture'}
              </button>
              <button type="button" className="btn btn-outline" onClick={() => setShowForm(false)}>
                Annuler
              </button>
            </div>
          </form>
        </div>
      )}

      {disputeTarget && (
        <div className="card">
          <div className="card-title">
            <h3>Signaler un litige</h3>
          </div>
          <div className="form-field">
            <label>Raison du litige</label>
            <textarea
              rows={3}
              value={disputeReason}
              onChange={(e) => setDisputeReason(e.target.value)}
              placeholder="Décrivez le motif du litige…"
            />
          </div>
          <div className="form-actions">
            <button className="btn btn-primary" disabled={busyId === disputeTarget} onClick={sendDispute}>
              Signaler le litige
            </button>
            <button
              className="btn btn-outline"
              onClick={() => {
                setDisputeTarget(null)
                setDisputeReason('')
              }}
            >
              Annuler
            </button>
          </div>
        </div>
      )}

      {selected && (
        <div className="card">
          <div className="card-title">
            <h3>
              Facture {selected.invoiceNumber}{' '}
              <span className={`badge badge-${STATUS_COLORS[selected.status] || 'gray'}`}>
                {STATUS_LABELS[selected.status] || selected.status}
              </span>
            </h3>
            <button className="btn btn-outline btn-sm" onClick={() => setSelected(null)}>
              Fermer
            </button>
          </div>
          <div className="kpi-grid">
            <div className="kpi-item">
              <span>Transporteur</span>
              <b>{selected.carrierName}</b>
            </div>
            <div className="kpi-item">
              <span>Réf. transporteur</span>
              <b>{selected.carrierReference || '—'}</b>
            </div>
            <div className="kpi-item">
              <span>Réf. expédition</span>
              <b>{selected.shipmentReference || '—'}</b>
            </div>
            <div className="kpi-item">
              <span>Date facture</span>
              <b>{fmtDate(selected.invoiceDate)}</b>
            </div>
            <div className="kpi-item">
              <span>Échéance</span>
              <b>{fmtDate(selected.dueDate)}</b>
            </div>
            <div className="kpi-item">
              <span>Créée le</span>
              <b>{fmtDate(selected.createdAt)}</b>
            </div>
          </div>

          <h4 style={{ marginTop: 16 }}>Ventilation de la facture</h4>
          <div className="record-card-row">
            <span>Fret</span>
            <span>{fmtMoney(selected.freightAmount, selected.currency)}</span>
          </div>
          <div className="record-card-row">
            <span>Surcharge carburant</span>
            <span>{fmtMoney(selected.fuelSurcharge, selected.currency)}</span>
          </div>
          <div className="record-card-row">
            <span>Frais sécurité</span>
            <span>{fmtMoney(selected.securityFee, selected.currency)}</span>
          </div>
          <div className="record-card-row">
            <span>Frais manutention</span>
            <span>{fmtMoney(selected.handlingFee, selected.currency)}</span>
          </div>
          <div className="record-card-row">
            <span>Frais douane</span>
            <span>{fmtMoney(selected.customsFee, selected.currency)}</span>
          </div>
          {selected.otherCharges > 0 && (
            <div className="record-card-row">
              <span>Autres frais{selected.otherChargesDescription ? ` (${selected.otherChargesDescription})` : ''}</span>
              <span>{fmtMoney(selected.otherCharges, selected.currency)}</span>
            </div>
          )}
          <div className="record-card-row">
            <span>Total</span>
            <b>{fmtMoney(selected.totalAmount, selected.currency)}</b>
          </div>

          <h4 style={{ marginTop: 16 }}>Réconciliation</h4>
          <div className="kpi-grid">
            <div className="kpi-item">
              <span>Taux négocié</span>
              <b>{selected.negotiatedRate ? fmtMoney(selected.negotiatedRate, selected.currency) : '—'}</b>
            </div>
            <div className="kpi-item">
              <span>Montant facturé</span>
              <b>{fmtMoney(selected.totalAmount, selected.currency)}</b>
            </div>
            <div className="kpi-item">
              <span>Écart</span>
              <b className={selected.variance > 0 ? 'text-danger' : ''}>
                {selected.variance != null
                  ? `${selected.variance > 0 ? '+' : ''}${fmtMoney(selected.variance, selected.currency)} (${selected.variancePercent > 0 ? '+' : ''}${selected.variancePercent.toFixed(1)}%)`
                  : '—'}
              </b>
            </div>
          </div>
          {selected.reconciliationNotes && (
            <p className="muted" style={{ marginTop: 10 }}>
              {selected.reconciliationNotes}
            </p>
          )}
          {selected.disputeReason && (
            <div className="alert alert-error" style={{ marginTop: 10 }}>
              Litige : {selected.disputeReason}
            </div>
          )}
        </div>
      )}

      {loading && (
        <div className="loading-spinner" aria-label="Chargement des factures…">
          Chargement…
        </div>
      )}

      <div className="card desktop-table">
        <div className="card-title">
          <h3>Factures</h3>
          <span className="muted">{invoices.length} facture(s)</span>
        </div>
        <div className="table-scroll">
          <table className="table table-hover">
            <thead>
              <tr>
                <th>N° facture</th>
                <th>Transporteur</th>
                <th>Date</th>
                <th>Montant</th>
                <th>Statut</th>
                <th>Écart</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {invoices.map((inv) => (
                <tr key={inv.id} className="row-clickable" onClick={() => setSelected(inv)}>
                  <td className="cell-strong">{inv.invoiceNumber}</td>
                  <td>{inv.carrierName}</td>
                  <td className="muted">{fmtDate(inv.invoiceDate)}</td>
                  <td>{fmtMoney(inv.totalAmount, inv.currency)}</td>
                  <td>
                    <span className={`badge badge-${STATUS_COLORS[inv.status] || 'gray'}`}>
                      {STATUS_LABELS[inv.status] || inv.status}
                    </span>
                  </td>
                  <td>{inv.variance != null ? `${inv.variancePercent > 0 ? '+' : ''}${inv.variancePercent.toFixed(1)}%` : '—'}</td>
                  <td onClick={(e) => e.stopPropagation()}>
                    <div className="row-actions">
                      {(inv.status === 'DRAFT' || inv.status === 'RECEIVED') && (
                        <button
                          className="btn btn-outline btn-sm"
                          disabled={busyId === inv.id}
                          onClick={() => updateStatus(inv.id, 'UNDER_REVIEW')}
                        >
                          Soumettre
                        </button>
                      )}
                      {inv.status === 'UNDER_REVIEW' && (
                        <>
                          <button
                            className="btn btn-outline btn-sm"
                            disabled={busyId === inv.id}
                            onClick={() => updateStatus(inv.id, 'APPROVED')}
                          >
                            Approuver
                          </button>
                          <button
                            className="btn btn-danger btn-sm"
                            disabled={busyId === inv.id}
                            onClick={() => updateStatus(inv.id, 'REJECTED')}
                          >
                            Rejeter
                          </button>
                        </>
                      )}
                      {inv.status === 'APPROVED' && (
                        <button
                          className="btn btn-outline btn-sm"
                          disabled={busyId === inv.id}
                          onClick={() => updateStatus(inv.id, 'PAID')}
                        >
                          Marquer payé
                        </button>
                      )}
                      {inv.status !== 'DRAFT' && inv.status !== 'PAID' && inv.status !== 'REJECTED' && (
                        <button className="btn btn-outline btn-sm" onClick={() => setDisputeTarget(inv.id)}>
                          Litige
                        </button>
                      )}
                      {(inv.status === 'DRAFT' || inv.status === 'RECEIVED') &&
                        (deleteConfirm === inv.id ? (
                          <>
                            <button className="btn btn-danger btn-sm" disabled={busyId === inv.id} onClick={() => remove(inv.id)}>
                              Oui
                            </button>
                            <button className="btn btn-outline btn-sm" onClick={() => setDeleteConfirm(null)}>
                              Non
                            </button>
                          </>
                        ) : (
                          <button className="btn btn-outline btn-sm" onClick={() => setDeleteConfirm(inv.id)}>
                            Supprimer
                          </button>
                        ))}
                    </div>
                  </td>
                </tr>
              ))}
              {invoices.length === 0 && !error && !loading && (
                <tr>
                  <td colSpan="7" className="table-empty">
                    Aucune facture
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      {!loading && (
        <div className="record-cards">
          {invoices.length === 0 ? (
            <p className="muted table-empty">Aucune facture</p>
          ) : (
            invoices.map((inv) => (
              <div key={inv.id} className="record-card" onClick={() => setSelected(inv)}>
                <div className="record-card-title">
                  <strong>{inv.invoiceNumber}</strong>
                  <span className={`badge badge-${STATUS_COLORS[inv.status] || 'gray'}`}>
                    {STATUS_LABELS[inv.status] || inv.status}
                  </span>
                </div>
                <div className="record-card-row">
                  <span>Transporteur</span>
                  <span>{inv.carrierName}</span>
                </div>
                <div className="record-card-row">
                  <span>Montant</span>
                  <span>{fmtMoney(inv.totalAmount, inv.currency)}</span>
                </div>
              </div>
            ))
          )}
        </div>
      )}
    </div>
  )
}
