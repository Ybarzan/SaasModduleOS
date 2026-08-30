#!/usr/bin/env bash
# Crée les Products + Prices Stripe attendus par BillingService.java et affiche
# les variables d'environnement à coller dans .env / docker-compose.yml.
#
# NE S'EXÉCUTE JAMAIS AUTOMATIQUEMENT -- action réelle et irréversible contre
# un compte Stripe réel (création d'objets facturables). À lancer manuellement,
# une seule fois, après avoir vérifié les montants ci-dessous.
#
# Prérequis :
#   export STRIPE_SECRET_KEY=sk_live_... (ou sk_test_... pour tester d'abord en mode test)
#
# Usage :
#   bash create-stripe-prices.sh

set -euo pipefail

if [ -z "${STRIPE_SECRET_KEY:-}" ]; then
  echo "Erreur : STRIPE_SECRET_KEY n'est pas définie." >&2
  echo "  export STRIPE_SECRET_KEY=sk_test_...   (recommandé pour un premier essai)" >&2
  exit 1
fi

# Montants en centimes, EUR -- copiés directement de BillingService.getPlans()
# (hubs/compliance-engine/backend/src/main/java/com/incokalk/service/BillingService.java),
# seule source de vérité affichée sur la page tarifs. Vérifier qu'ils sont
# toujours à jour avant de lancer ce script.
declare -A PLANS=(
  [starter]="IncoKalk — Starter — Import-Export|2900|29000"
  [pro]="IncoKalk — Croissance — Douane & Conformité|14900|151900"
  [enterprise]="IncoKalk — Suite — Grand compte|49900|508900"
)

echo "=== Création des Products + Prices Stripe ==="
echo

RESULT_ENV=""

for plan in starter pro enterprise; do
  IFS='|' read -r NAME MONTHLY_CENTS ANNUAL_CENTS <<< "${PLANS[$plan]}"

  echo "--- Plan: $plan ($NAME) ---"

  PRODUCT_ID=$(curl -s https://api.stripe.com/v1/products \
    -u "${STRIPE_SECRET_KEY}:" \
    -d "name=${NAME}" \
    -d "metadata[plan]=${plan}" \
    | node -e "let d='';process.stdin.on('data',c=>d+=c);process.stdin.on('end',()=>{const j=JSON.parse(d); if(j.error){console.error(JSON.stringify(j.error)); process.exit(1);} console.log(j.id)})")
  echo "Product créé: $PRODUCT_ID"

  MONTHLY_PRICE_ID=$(curl -s https://api.stripe.com/v1/prices \
    -u "${STRIPE_SECRET_KEY}:" \
    -d "product=${PRODUCT_ID}" \
    -d "unit_amount=${MONTHLY_CENTS}" \
    -d "currency=eur" \
    -d "recurring[interval]=month" \
    | node -e "let d='';process.stdin.on('data',c=>d+=c);process.stdin.on('end',()=>{const j=JSON.parse(d); if(j.error){console.error(JSON.stringify(j.error)); process.exit(1);} console.log(j.id)})")
  echo "Price mensuel créé: $MONTHLY_PRICE_ID (${MONTHLY_CENTS} centimes)"

  ANNUAL_PRICE_ID=$(curl -s https://api.stripe.com/v1/prices \
    -u "${STRIPE_SECRET_KEY}:" \
    -d "product=${PRODUCT_ID}" \
    -d "unit_amount=${ANNUAL_CENTS}" \
    -d "currency=eur" \
    -d "recurring[interval]=year" \
    | node -e "let d='';process.stdin.on('data',c=>d+=c);process.stdin.on('end',()=>{const j=JSON.parse(d); if(j.error){console.error(JSON.stringify(j.error)); process.exit(1);} console.log(j.id)})")
  echo "Price annuel créé: $ANNUAL_PRICE_ID (${ANNUAL_CENTS} centimes)"
  echo

  UPPER_PLAN=$(echo "$plan" | tr '[:lower:]' '[:upper:]')
  RESULT_ENV="${RESULT_ENV}STRIPE_PRICE_${UPPER_PLAN}_MONTHLY=${MONTHLY_PRICE_ID}\nSTRIPE_PRICE_${UPPER_PLAN}_ANNUAL=${ANNUAL_PRICE_ID}\n"
done

echo "=== Terminé — à coller dans hubs/compliance-engine/infrastructure/docker/.env ==="
echo -e "$RESULT_ENV"
