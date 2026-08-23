// `n.toLocaleString('fr-FR', ...)` délègue le regroupement par milliers à
// Intl.NumberFormat, dont le caractère séparateur (espace normale, insécable,
// ou insécable étroite U+202F) dépend de la version d'ICU du runtime -- donc
// du navigateur/OS/version Node, pas du code. Le rendu change silencieusement
// d'un environnement à l'autre pour la même valeur. Ici le séparateur est
// choisi une fois pour toutes et ne dépend d'aucune donnée externe.
const THOUSANDS_SEPARATOR = String.fromCharCode(32);
const DECIMAL_SEPARATOR = ',';

export interface FormatNumberOptions {
  minimumFractionDigits?: number;
  maximumFractionDigits?: number;
}

/** Remplace `n.toLocaleString('fr-FR', options)` pour un rendu déterministe. */
export function formatNumber(value: number, options: FormatNumberOptions = {}): string {
  const maximumFractionDigits = options.maximumFractionDigits ?? options.minimumFractionDigits ?? 0;
  const minimumFractionDigits = Math.min(options.minimumFractionDigits ?? 0, maximumFractionDigits);

  const isNegative = value < 0;
  const rounded = Math.abs(value).toFixed(maximumFractionDigits);
  const [intPart, initialFracPart = ''] = rounded.split('.');
  let fracPart = initialFracPart;

  // toFixed a déjà arrondi à maximumFractionDigits ; on ne retire des zéros
  // de fin que jusqu'au plancher minimumFractionDigits.
  if (fracPart) {
    while (fracPart.length > minimumFractionDigits && fracPart.endsWith('0')) {
      fracPart = fracPart.slice(0, -1);
    }
  }

  const grouped = intPart.replace(/\B(?=(\d{3})+(?!\d))/g, THOUSANDS_SEPARATOR);
  const sign = isNegative && Number(rounded) !== 0 ? '-' : '';
  return fracPart ? `${sign}${grouped}${DECIMAL_SEPARATOR}${fracPart}` : `${sign}${grouped}`;
}

/** `formatNumber` + suffixe " €", pour les montants en euros affichés partout
 * dans l'app comme `{formatNumber(n)} €` (espace normale avant le symbole,
 * pour rester cohérent avec toutes les occurrences existantes du pattern). */
export function formatEur(value: number, options: FormatNumberOptions = {}): string {
  return `${formatNumber(value, options)} €`;
}
