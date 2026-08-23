import { useEffect, useState } from 'react'
import api from '../services/api'

const TYPE_LABEL = {
  MAINTENANCE_ECHEANCE: 'Entretien à échéance',
  TACHYGRAPHIE_NON_CONFORME: 'Non-conformité tachygraphe',
  TEMPS_CONDUITE: 'Temps de conduite',
  USAGE_ANORMAL: 'Usage anormal',
  PAIEMENT: 'Paiement'
}

const RULE_META = {
  MAINTENANCE_ECHEANCE: { label: 'Entretien à échéance', unit: 'jours avant échéance', desc: 'Alerte si un entretien planifié arrive à échéance sous ce délai.' },
  TACHYGRAPHIE_NON_CONFORME: { label: 'Non-conformité tachygraphe', unit: '', desc: 'Alerte dès qu’un jour non conforme est détecté (règlement 561/2006).' },
  TEMPS_CONDUITE: { label: 'Temps de conduite', unit: 'h / 7 jours', desc: 'Alerte si le temps de conduite hebdomadaire dépasse ce seuil.' },
  USAGE_ANORMAL: { label: 'Usage anormal', unit: '', desc: 'Alerte en cas d’événement grave (freinage brusque, excès de vitesse…).' },
  PAIEMENT: { label: 'Paiement', unit: '', desc: 'Alerte en cas d’échec de paiement de l’abonnement.' }
}

const fmtDate = (s) => (s ? new Date(s).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' }) : '—')

export default function Notifications() {
  const [notifications, setNotifications] = useState([])
  const [rules, setRules] = useState([])
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [loading, setLoading] = useState(true)
  const [scanning, setScanning] = useState(false)

  const load = () =>
    Promise.all([
      api.get('/notifications'),
      api.get('/notifications/rules')
    ])
      .then(([n, r]) => {
        setNotifications(n.data)
        setRules(r.data)
      })
      .catch(() => setError('Impossible de charger les notifications'))
      .finally(() => setLoading(false))

  useEffect(() => {
    load()
  }, [])

  const scan = async () => {
    setError('')
    setNotice('')
    setScanning(true)
    try {
      const res = await api.post('/notifications/scan')
      setNotice(
        res.data.created > 0
          ? `${res.data.created} nouvelle(s) alerte(s) détectée(s)`
          : 'Balayage effectué : aucune nouvelle alerte'
      )
      await load()
    } catch {
      setError('Le balayage a échoué')
    } finally {
      setScanning(false)
    }
  }

  const markRead = async (id) => {
    try {
      await api.patch(`/notifications/${id}/read`)
      setNotifications((list) => list.map((n) => (n.id === id ? { ...n, read: true } : n)))
    } catch {
      setError('Impossible de marquer la notification comme lue')
    }
  }

  const markAllRead = async () => {
    try {
      await Promise.all(notifications.filter((n) => !n.read).map((n) => api.patch(`/notifications/${n.id}/read`)))
      setNotifications((list) => list.map((n) => ({ ...n, read: true })))
    } catch {
      setError('Impossible de marquer les notifications comme lues')
    }
  }

  const saveRule = async (rule) => {
    setError('')
    setNotice('')
    try {
      const res = await api.post('/notifications/rules', {
        id: rule.id,
        type: rule.type,
        threshold: rule.threshold,
        enabled: rule.enabled
      })
      setNotice('Règle mise à jour')
      setRules((list) => list.map((r) => (r.id === res.data.id ? res.data : r)))
    } catch {
      setError('La mise à jour de la règle a échoué')
    }
  }

  const toggleRule = (rule) => saveRule({ ...rule, enabled: !rule.enabled })
  const changeThreshold = (rule, threshold) => saveRule({ ...rule, threshold: threshold === '' ? null : Number(threshold) })

  const unread = notifications.filter((n) => !n.read).length

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Alertes & notifications</h2>
          <p>Suivez la conformité, la maintenance et l’usage de votre flotte</p>
        </div>
        <div className="header-actions">
          {unread > 0 && (
            <button className="btn btn-outline btn-sm" onClick={markAllRead}>
              Tout marquer comme lu
            </button>
          )}
          <button className="btn btn-primary btn-sm" disabled={scanning} onClick={scan}>
            {scanning ? 'Balayage…' : '↻ Balayer maintenant'}
          </button>
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}
      {notice && <div className="alert">{notice}</div>}

      <div className="card desktop-table">
        <div className="card-title">
          <h3>Notifications</h3>
          <span className="muted">{unread} non lue(s)</span>
        </div>
        {loading ? (
          <p className="muted table-empty">Chargement…</p>
        ) : notifications.length === 0 ? (
          <p className="muted table-empty">Aucune alerte pour le moment</p>
        ) : (
          <div className="table-scroll">
            <table className="table">
              <thead>
                <tr>
                  <th>Type</th>
                  <th>Message</th>
                  <th>Date</th>
                  <th>Statut</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {notifications.map((n) => (
                  <tr key={n.id} className={n.read ? '' : 'notif-unread'}>
                    <td>
                      <span className="badge badge-blue">{TYPE_LABEL[n.type] || n.type}</span>
                    </td>
                    <td>
                      <strong>{n.title}</strong>
                      <span className="muted block">{n.message}</span>
                    </td>
                    <td>{fmtDate(n.createdAt)}</td>
                    <td>
                      {n.read ? (
                        <span className="badge badge-gray">Lu</span>
                      ) : (
                        <span className="badge badge-green">Nouveau</span>
                      )}
                    </td>
                    <td>
                      {!n.read && (
                        <button className="btn btn-outline btn-sm" onClick={() => markRead(n.id)}>
                          Marquer lu
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <div className="card">
        <div className="card-title">
          <h3>Règles d’alerte</h3>
          <span className="muted">Chaque règle génère au maximum une alerte par entité et par type sur 24 h</span>
        </div>
        <div className="table-scroll">
          <table className="table">
            <thead>
              <tr>
                <th>Règle</th>
                <th>Description</th>
                <th>Seuil</th>
                <th>Active</th>
              </tr>
            </thead>
            <tbody>
              {rules.map((r) => {
                const meta = RULE_META[r.type] || { label: r.type, unit: '', desc: '' }
                const hasThreshold = r.type === 'MAINTENANCE_ECHEANCE' || r.type === 'TEMPS_CONDUITE'
                return (
                  <tr key={r.id}>
                    <td>
                      <strong>{meta.label}</strong>
                    </td>
                    <td className="muted">{meta.desc}</td>
                    <td>
                      {hasThreshold ? (
                        <div className="row-actions">
                          <input
                            type="number"
                            min="1"
                            step="any"
                            style={{ width: 90 }}
                            value={r.threshold ?? ''}
                            disabled={!r.enabled}
                            onChange={(e) => changeThreshold(r, e.target.value)}
                          />
                          <span className="muted">{meta.unit}</span>
                        </div>
                      ) : (
                        <span className="muted">—</span>
                      )}
                    </td>
                    <td>
                      <button
                        className={'btn btn-sm ' + (r.enabled ? 'btn-primary' : 'btn-outline')}
                        onClick={() => toggleRule(r)}
                      >
                        {r.enabled ? 'Activée' : 'Désactivée'}
                      </button>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
