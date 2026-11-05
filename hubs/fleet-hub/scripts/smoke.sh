#!/usr/bin/env bash
#
# Contrôle d'un déploiement Fleet Hub depuis l'extérieur, comme un visiteur :
#
#   ./scripts/smoke.sh https://fleethub-demo.fr
#
# Vérifie que le site répond, que le backend est sain à travers les proxys (Caddy puis
# nginx), que les en-têtes de sécurité sont en place et que rien d'interne n'est
# exposé. Code de sortie 0 si tout passe, 1 sinon (utilisable en CI ou en cron).
# INSECURE=1 accepte un certificat non reconnu (déploiement local, certificat interne).

set -uo pipefail

BASE="${1:-}"
[ -n "$BASE" ] || { echo "Usage : $0 https://DOMAIN" >&2; exit 2; }
BASE="${BASE%/}"
CURL=(curl -sS -m 15)
[ "${INSECURE:-0}" = "1" ] && CURL+=(-k)

failures=0
ok()   { echo -e "  \033[1;32m✓\033[0m $*"; }
fail() { echo -e "  \033[1;31m✗\033[0m $*"; failures=$((failures + 1)); }

echo "Contrôle de $BASE"

# 1. Santé du backend, à travers Caddy et nginx (pas le fallback du frontend).
health="$("${CURL[@]}" -w '\n%{http_code}' "$BASE/actuator/health" 2>/dev/null)"
code="${health##*$'\n'}"; body="${health%$'\n'*}"
if [ "$code" = "200" ] && [[ "$body" == *'"status":"UP"'* ]]; then
  ok "backend sain (/actuator/health → UP)"
else
  fail "backend : /actuator/health a répondu $code : ${body:0:120}"
fi

# 2. Page d'accueil et en-têtes de sécurité.
headers="$("${CURL[@]}" -D - -o /dev/null "$BASE/" 2>/dev/null | tr -d '\r')"
if [[ "$headers" =~ ^HTTP/[0-9.]+\ 200 ]]; then ok "page d'accueil (200)"; else fail "page d'accueil : ${headers%%$'\n'*}"; fi
csp="$(grep -i '^content-security-policy:' <<<"$headers" || true)"
if [ -z "$csp" ]; then
  fail "en-tête Content-Security-Policy absent"
elif [[ "$csp" == *"unsafe-eval"* ]] || [[ "$csp" =~ script-src[^\;]*unsafe-inline ]]; then
  fail "CSP trop permissive pour les scripts : $csp"
else
  ok "CSP stricte pour les scripts"
fi
for h in x-content-type-options x-frame-options referrer-policy; do
  grep -qi "^$h:" <<<"$headers" && ok "en-tête $h" || fail "en-tête $h absent"
done
if [[ "$BASE" == https://* ]]; then
  grep -qi '^strict-transport-security:' <<<"$headers" && ok "HSTS" || fail "en-tête Strict-Transport-Security absent"
fi

# 3. L'API répond à travers les proxys (identifiants volontairement faux : 401 attendu).
code="$("${CURL[@]}" -o /dev/null -w '%{http_code}' -X POST "$BASE/api/auth/login" \
  -H 'Content-Type: application/json' -d '{"username":"smoke-test","password":"smoke-test"}' 2>/dev/null)"
if [ "$code" = "401" ]; then ok "API joignable (/api/auth/login → 401 pour de faux identifiants)"
else fail "API : /api/auth/login a répondu $code (401 attendu)"; fi

# 4. Rien d'interne exposé publiquement.
metrics="$("${CURL[@]}" "$BASE/actuator/prometheus" 2>/dev/null | head -c 2000)"
if [[ "$metrics" == *"# HELP"* ]]; then fail "métriques Prometheus exposées publiquement (/actuator/prometheus)"
else ok "métriques non exposées"; fi
swagger="$("${CURL[@]}" "$BASE/v3/api-docs" 2>/dev/null | head -c 200)"
if [[ "$swagger" == *'"openapi"'* ]]; then fail "documentation OpenAPI exposée (/v3/api-docs)"
else ok "documentation OpenAPI non exposée"; fi

echo
if [ "$failures" -eq 0 ]; then
  echo -e "\033[1;32mTout est OK.\033[0m"
  exit 0
fi
echo -e "\033[1;31m$failures contrôle(s) en échec.\033[0m"
exit 1
