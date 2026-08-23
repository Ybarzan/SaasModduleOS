import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import DOMPurify from 'dompurify'
import api from '../services/api'

const DEFAULT_TITLES = {
  terms: 'Conditions Générales d’Utilisation',
  privacy: 'Politique de Confidentialité'
}

export default function Legal() {
  const { key } = useParams()
  const [content, setContent] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    setLoading(true)
    setError('')
    setContent(null)
    api
      .get(`/legal/${key}`)
      .then((res) => setContent(res.data))
      .catch(() => setError('Ce document n’est pas disponible'))
      .finally(() => setLoading(false))
  }, [key])

  return (
    <div className="legal-page">
      <div className="legal-card">
        <div className="legal-header">
          <span className="legal-logo">🚛 Fleet Hub</span>
          <Link to="/login" className="link">← Retour à la connexion</Link>
        </div>
        {loading ? (
          <p className="muted">Chargement…</p>
        ) : error ? (
          <div className="alert alert-error">{error}</div>
        ) : (
          <>
            <h1>{content.title || DEFAULT_TITLES[key]}</h1>
            <p className="muted">Dernière mise à jour : {content.updatedAt}</p>
            <div
              className="legal-content"
              dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(content.body) }}
            />
            <p className="legal-footer">
              <Link to="/legal/terms" className="link">Conditions d’utilisation</Link>
              <span>·</span>
              <Link to="/legal/privacy" className="link">Politique de confidentialité</Link>
            </p>
          </>
        )}
      </div>
    </div>
  )
}
