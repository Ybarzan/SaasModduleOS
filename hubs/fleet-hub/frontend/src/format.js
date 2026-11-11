/**
 * Nombre au format français, avec un nombre fixe de décimales : 1234.5 → « 1 234,50 ».
 * Remplace toFixed, qui écrit le point décimal anglais. Rend une chaîne vide pour une
 * valeur absente, afin que « fmtNum(x, 2) || '—' » continue de fonctionner.
 *
 * @param {number | string | null | undefined} value
 * @param {number} [digits]
 * @returns {string}
 */
export function fmtNum(value, digits = 0) {
  if (value === null || value === undefined || value === '') return ''
  const n = Number(value)
  if (!Number.isFinite(n)) return ''
  return n.toLocaleString('fr-FR', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits
  })
}
