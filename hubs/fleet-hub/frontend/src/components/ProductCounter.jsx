import { MEDICAL_PRODUCTS, totalOf } from '../medical'

/**
 * Saisie des quantités par catégorie de produit (transport médical) :
 * gros boutons − / + utilisables avec des gants, total et écart à l'attendu.
 */
export default function ProductCounter({ value, onChange, expected, label = 'Produits' }) {
  const set = (key, qty) => onChange({ ...value, [key]: Math.max(0, Math.min(9999, qty)) })
  const total = totalOf(value)
  const expectedTotal = expected ? totalOf(expected) : null
  const mismatch = expectedTotal != null && total !== expectedTotal

  return (
    <div className="product-counter" role="group" aria-label={label}>
      <div className="pc-head">
        <span className="pc-title">{label}</span>
        <span className={'pc-total' + (mismatch ? ' warn' : '')}>
          Total <b>{total}</b>
          {expectedTotal != null && <small> / {expectedTotal} attendu(s)</small>}
        </span>
      </div>
      {MEDICAL_PRODUCTS.map((p) => {
        const qty = Number(value?.[p.value] || 0)
        const exp = expected ? Number(expected[p.value] || 0) : null
        return (
          <div key={p.value} className={'pc-row' + (exp != null && exp !== qty ? ' warn' : '')}>
            <span className="pc-label">
              <span>
                <span aria-hidden="true">{p.icon}</span> {p.label}
              </span>
              {exp != null && <small className="pc-expected">attendu {exp}</small>}
            </span>
            <span className="pc-stepper">
              <button
                type="button"
                aria-label={`${p.label} moins un`}
                onClick={() => set(p.value, qty - 1)}
                disabled={qty === 0}
              >
                −
              </button>
              <input
                type="number"
                min="0"
                max="9999"
                inputMode="numeric"
                aria-label={`Quantité ${p.label}`}
                value={qty}
                onChange={(e) => set(p.value, Number(e.target.value || 0))}
              />
              <button
                type="button"
                aria-label={`${p.label} plus un`}
                onClick={() => set(p.value, qty + 1)}
              >
                +
              </button>
            </span>
          </div>
        )
      })}
      {mismatch && (
        <div className="pc-warning" role="status">
          Écart avec les produits pris en charge : une non-conformité sera ouverte à la fin de la
          tournée.
        </div>
      )}
    </div>
  )
}
