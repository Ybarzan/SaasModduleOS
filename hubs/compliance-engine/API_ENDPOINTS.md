# 📡 IncoKalk API — Index complet des endpoints

> **Comment lire cette doc**
> - Base URL : `http://localhost:8080/api`
> - Header obligatoire (sauf auth/register/login) : `Authorization: Bearer <jwt>`
> - Header optionnel clé API : `X-API-Key: <key>`
> - Swagger UI : `http://localhost:8080/api/swagger-ui.html` (généré à partir de `@OpenAPIDefinition`)
> - Rôles : `OWNER`, `ADMIN`, `MANAGER`, `USER`, `CLIENT` (selon endpoint)

---

## Sommaire par domaine

- [Authentification & comptes](#authentification--comptes) — 18 endpoints
- [Multi-tenant / Companies](#multi-tenant--companies) — 14 endpoints
- [Incoterms / Simulation](#incoterms--simulation) — 8 endpoints
- [Shipments](#shipments) — 9 endpoints
- [Carriers](#carriers) — 11 endpoints
- [Shipping Rates](#shipping-rates) — 7 endpoints
- [Quotes & Booking](#quotes--booking) — 10 endpoints
- [Tracking](#tracking) — 9 endpoints
- [ETA & ML](#eta--ml) — 11 endpoints
- [Optimization](#optimization) — 12 endpoints
- [Logistics](#logistics) — 5 endpoints
- [Customs & Taric](#customs--taric) — 26 endpoints
- [Compliance](#compliance) — 15 endpoints
- [Declarations (ICS2 / DEB / EXPORT / Customs / EUR1)](#declarations) — 50+ endpoints
- [Landed cost & partage](#landed-cost--partage) — 11 endpoints
- [Invoicing & Finance](#invoicing--finance) — 30+ endpoints
- [Billing / Stripe](#billing--stripe) — 6 endpoints
- [Warehousing & Inventory](#warehousing--inventory) — 26 endpoints
- [ESG / Carbon / CSRD](#esg--carbon--csrd) — 13 endpoints
- [E-commerce / ERP / Fintech](#e-commerce--erp--fintech) — 30+ endpoints
- [Notifications](#notifications) — 14 endpoints
- [Email intake & Document parser](#email-intake--document-parser) — 11 endpoints
- [Academy / Training](#academy--training) — 11 endpoints
- [Analytics & Audit](#analytics--audit) — 13 endpoints
- [Groupage / Multi-branch](#groupage--multi-branch) — 19 endpoints
- [Branding / Mobile](#branding--mobile) — 18 endpoints
- [Webhooks](#webhooks) — 4 endpoints
- [Files / Documents exports](#files--documents-exports) — 12 endpoints

---

## Authentification & comptes

### `AuthController` — `/v1/auth`
| Verb | Path | Méthode |
|---|---|---|
| POST | `/v1/auth/register` | `register` |
| POST | `/v1/auth/login` | `login` |
| POST | `/v1/auth/refresh` | `refresh` |
| POST | `/v1/auth/forgot-password` | `forgotPassword` |
| POST | `/v1/auth/reset-password` | `resetPassword` |
| GET | `/v1/auth/verify-email` | `verifyEmail` |
| GET | `/v1/auth/me` | `getCurrentUser` |
| PUT | `/v1/auth/me` | `updateCurrentUser` |

### `ClientAuthController` — `/v1/client/auth`
| Verb | Path | Méthode |
|---|---|---|
| POST | `/v1/client/auth/login` | `login` |
| GET | `/v1/client/auth/me` | `me` |

### `ApiKeyController` — `/v1/api-keys`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/api-keys` | `list` |
| POST | `/v1/api-keys` | `create` |
| DELETE | `/v1/api-keys/{id}` | `revoke` |

### `TeamController` — `/v1/team`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/team` | `list` |
| POST | `/v1/team` | `invite` |
| PUT | `/v1/team/{userId}` | `update` |
| DELETE | `/v1/team/{userId}` | `remove` |
| GET | `/v1/team/stats` | `stats` |

### `RoleController` — `/v1/companies/{companyId}/roles`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/companies/{companyId}/roles` | `list` |
| PUT | `/v1/companies/{companyId}/roles/{targetUserId}` | `assign` |
| DELETE | `/v1/companies/{companyId}/roles/{targetUserId}` | `revoke` |

### `ClientManagementController` — `/v1/clients`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/clients` | `list` |
| POST | `/v1/clients` | `create` |
| GET | `/v1/clients/{id}` | `get` |
| PUT | `/v1/clients/{id}` | `update` |
| DELETE | `/v1/clients/{id}` | `delete` |
| POST | `/v1/clients/{id}/reset-password` | `resetPassword` |
| GET | `/v1/clients/stats` | `stats` |

### `ClientPortalController` — `/v1/client/shipments`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/client/shipments` | `list` |
| GET | `/v1/client/shipments/{id}` | `get` |

---

## Multi-tenant / Companies

### `CompanyController` — `/v1/companies`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/companies/me` | `getCurrent` |
| PUT | `/v1/companies/{id}` | `update` |
| POST | `/v1/companies/{id}/invite` | `invite` |

### `MultiBranchController` — `/v1/branches`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/branches` | `list` |
| POST | `/v1/branches` | `create` |
| DELETE | `/v1/branches/{id}` | `delete` |
| GET | `/v1/branches/parent` | `getParent` |
| POST | `/v1/branches/add` | `addChild` |
| GET | `/v1/branches/consolidated-report` | `consolidatedReport` |
| GET | `/v1/branches/transfers` | `transfers` |
| POST | `/v1/branches/transfers` | `createTransfer` |

---

## Incoterms / Simulation

### `IncotermController` — `/incoterms`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/incoterms` | `list` |
| GET | `/incoterms/{code}` | `get` |

### `SimulationController` — `/v1/simulate`
| Verb | Path | Méthode |
|---|---|---|
| POST | `/v1/simulate` | `simulate` |
| GET | `/v1/simulate` | `list` |
| POST | `/v1/simulate/compare` | `compare` |
| GET | `/v1/simulate/incoterms` | `listIncoterms` |
| GET | `/v1/simulate/incoterms/{code}` | `getIncoterm` |
| GET | `/v1/simulate/simulations` | `listSimulations` |
| DELETE | `/v1/simulate/simulations/{id}` | `deleteSimulation` |

### `QuoteController` — `/v1/quotes`
| Verb | Path | Méthode |
|---|---|---|
| POST | `/v1/quotes` | `create` |

### `CurrencyController` — `/v1/currencies`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/currencies` | `list` |
| GET | `/v1/currencies/convert` | `convert` |
| GET | `/v1/currencies/rate` | `rate` |
| GET | `/v1/currencies/rates` | `allRates` |
| GET | `/v1/currencies/exposure-report` | `exposure` |

---

## Shipments

### `ShipmentController` — `/v1/shipments`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/shipments` | `list` |
| POST | `/v1/shipments` | `create` |
| DELETE | `/v1/shipments/{id}` | `delete` |
| GET | `/v1/shipments/{id}` | `get` |
| PATCH | `/v1/shipments/{id}/status` | `updateStatus` |
| GET | `/v1/shipments/{id}/items` | `listItems` |
| POST | `/v1/shipments/{id}/items` | `addItem` |
| DELETE | `/v1/shipments/{id}/items` | `deleteAllItems` |

### `GroupageController` — `/v1/groupages`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/groupages` | `list` |
| POST | `/v1/groupages` | `create` |
| PUT | `/v1/groupages/{id}` | `update` |
| DELETE | `/v1/groupages/{id}` | `delete` |
| GET | `/v1/groupages/{id}` | `get` |
| POST | `/v1/groupages/{id}/members` | `addMember` |
| DELETE | `/v1/groupages/{id}/members/{memberId}` | `removeMember` |
| POST | `/v1/groupages/{id}/status` | `changeStatus` |
| GET | `/v1/groupages/stats` | `stats` |

---

## Carriers

### `CarrierController` — `/v1/carriers`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/carriers` | `list` |
| POST | `/v1/carriers` | `create` |
| PUT | `/v1/carriers/{id}` | `update` |
| DELETE | `/v1/carriers/{id}` | `delete` |
| PATCH | `/v1/carriers/{id}/toggle` | `toggleActive` |

### `CarrierBookingController` — `/v1/carrier-bookings`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/carrier-bookings` | `list` |
| POST | `/v1/carrier-bookings` | `create` |
| GET | `/v1/carrier-bookings/{id}` | `get` |
| POST | `/v1/carrier-bookings/{id}/submit` | `submit` |
| POST | `/v1/carrier-bookings/{id}/cancel` | `cancel` |
| GET | `/v1/carrier-bookings/shipment/{shipmentId}` | `byShipment` |
| GET | `/v1/carrier-bookings/carrier/{carrierId}` | `byCarrier` |
| GET | `/v1/carrier-bookings/stats` | `stats` |

---

## Shipping Rates

### `ShippingRateController` — `/v1/shipping-rates`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/shipping-rates` | `list` |
| POST | `/v1/shipping-rates` | `create` |
| PUT | `/v1/shipping-rates/{id}` | `update` |
| DELETE | `/v1/shipping-rates/{id}` | `delete` |
| PATCH | `/v1/shipping-rates/{id}/toggle` | `toggle` |
| GET | `/v1/shipping-rates/carrier/{carrierId}` | `byCarrier` |
| GET | `/v1/shipping-rates/compare` | `compare` |

---

## Tracking

### `TrackingController` — `/v1/tracking`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/tracking/lookup` | `lookup` |
| POST | `/v1/tracking/lookup` | `manualLookup` |
| GET | `/v1/tracking/shipments/{id}` | `history` |
| GET | `/v1/tracking/shipments/{id}/position` | `currentPosition` |
| POST | `/v1/tracking/shipments/{id}/sync` | `sync` |

### `TrackingMapController` — `/v1/tracking-map`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/tracking-map/flights` | `flights` |
| GET | `/v1/tracking-map/flights/aircraft/{icao24}` | `flight` |
| GET | `/v1/tracking-map/vessels/search` | `searchVessels` |
| GET | `/v1/tracking-map/vessels/position/{mmsi}` | `vesselPosition` |

---

## ETA & ML

### `EtaPredictionController` — `/v1/eta-predictions`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/eta-predictions` | `list` |
| POST | `/v1/eta-predictions/predict` | `predict` |
| GET | `/v1/eta-predictions/{id}` | `get` |
| PUT | `/v1/eta-predictions/{id}/actual` | `recordActual` |
| GET | `/v1/eta-predictions/by-lane` | `byLane` |
| GET | `/v1/eta-predictions/stats` | `stats` |

### `EtaMlController` — `/v1/eta-ml`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/eta-ml/model` | `model` |
| POST | `/v1/eta-ml/model` | `saveModel` |
| POST | `/v1/eta-ml/train` | `train` |

---

## Optimization

### `OptimizationController` — `/v1/optimization`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/optimization` | `list` |
| POST | `/v1/optimization` | `create` |
| PATCH | `/v1/optimization/accept/{id}` | `accept` |
| GET | `/v1/optimization/recommendations` | `recommendations` |
| GET | `/v1/optimization/lane-analysis` | `laneAnalysis` |
| GET | `/v1/optimization/consolidation` | `consolidationOps` |
| POST | `/v1/optimization/consolidation` | `computeConsolidation` |
| PATCH | `/v1/optimization/consolidation/accept/{id}` | `acceptConsolidation` |
| GET | `/v1/optimization/stats` | `stats` |

---

## Logistics

### `LogisticsController` — `/v1/logistics`
| Verb | Path | Méthode |
|---|---|---|
| POST | `/v1/logistics/route-optimization` | `route` |
| POST | `/v1/logistics/customs-duty` | `customsDuty` |
| POST | `/v1/logistics/insurance` | `insurance` |
| POST | `/v1/logistics/packaging` | `packaging` |
| POST | `/v1/logistics/trucking` | `trucking` |

---

## Customs & Taric

### `CustomsController` — `/v1/customs`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/customs/duty` | `duty` |
| GET | `/v1/customs/vat` | `vat` |
| GET | `/v1/customs/vat-rates` | `vatRates` |
| GET | `/v1/customs/tariff-info` | `tariff` |
| GET | `/v1/customs/search` | `search` |
| GET | `/v1/customs/agreements` | `agreements` |
| GET | `/v1/customs/agreement/{code}` | `agreement` |
| GET | `/v1/customs/eu-countries` | `euCountries` |

### `TaricController` — `/v1/taric`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/taric/lookup` | `lookup` |
| POST | `/v1/taric/sync/{hsCode}` | `sync` |
| POST | `/v1/taric/sync/daily` | `dailySync` |
| GET | `/v1/taric/stats` | `stats` |

### `HsCodeSuggestionController` — `/v1/hs-suggestions`
| Verb | Path | Méthode |
|---|---|---|
| POST | `/v1/hs-suggestions/suggest` | `suggest` |
| POST | `/v1/hs-suggestions/suggest-from-image` | `suggestImage` |
| PUT | `/v1/hs-suggestions/{id}/confirm` | `confirm` |
| GET | `/v1/hs-suggestions/history` | `history` |
| GET | `/v1/hs-suggestions/ml/stats` | `mlStats` |

### `TradeAgreementController` — `/v1/trade-agreements`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/trade-agreements` | `list` |
| GET | `/v1/trade-agreements/{code}` | `get` |
| GET | `/v1/trade-agreements/by-country/{countryCode}` | `byCountry` |
| GET | `/v1/trade-agreements/by-chapter/{chapter}` | `byChapter` |

### `PreferentialRegimeController` — `/v1/compliance/preferential`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/compliance/preferential/agreements` | `agreements` |
| GET | `/v1/compliance/preferential/rates` | `rates` |
| POST | `/v1/compliance/preferential/verify-origin` | `verifyOrigin` |
| POST | `/v1/compliance/preferential/calculate` | `calculate` |

### `EoriController` — `/v1/eori`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/eori` | `list` |
| POST | `/v1/eori` | `create` |
| PUT | `/v1/eori/{id}` | `update` |
| DELETE | `/v1/eori/{id}` | `delete` |
| POST | `/v1/eori/validate` | `validate` |
| GET | `/v1/eori/default` | `getDefault` |
| PUT | `/v1/eori/{id}/default` | `setDefault` |

### `Eur1CertificateController` — `/v1/eur1`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/eur1` | `list` |
| POST | `/v1/eur1` | `create` |
| GET | `/v1/eur1/{id}` | `get` |
| DELETE | `/v1/eur1/{id}` | `delete` |
| GET | `/v1/eur1/{id}/validate` | `validate` |

### `ComplianceController` — `/v1/compliance`
| Verb | Path | Méthode |
|---|---|---|
| POST | `/v1/compliance/check` | `check` |
| GET | `/v1/compliance/rules` | `rules` |

### `FrenchFiscalController` — `/v1/french-fiscal`
| Verb | Path | Méthode |
|---|---|---|
| POST | `/v1/french-fiscal/calculate` | `calculate` |
| GET | `/v1/french-fiscal/regimes` | `regimes` |
| GET | `/v1/french-fiscal/accises` | `accises` |
| GET | `/v1/french-fiscal/tai-rates` | `taiRates` |
| GET | `/v1/french-fiscal/safeguard-duties` | `safeguardDuties` |
| GET | `/v1/french-fiscal/check-tai/{hsCode}` | `checkTai` |
| POST | `/v1/french-fiscal/deb/auto-generate` | `autoGenerateDeb` |
| POST | `/v1/french-fiscal/deb/bulk-generate` | `bulkGenerateDeb` |
| POST | `/v1/french-fiscal/dgddi/prepare-submission` | `prepareSubmission` |

### `DeniedPartyScreeningController` — `/v1/dps`
| Verb | Path | Méthode |
|---|---|---|
| POST | `/v1/dps/screen` | `screen` |
| GET | `/v1/dps/{id}` | `get` |
| GET | `/v1/dps/history` | `history` |
| GET | `/v1/dps/alerts` | `alerts` |
| GET | `/v1/dps/sanctioned-entities` | `sanctioned` |
| GET | `/v1/dps/stats` | `stats` |

---

## Declarations

### `CustomsDeclarationController` — `/v1/customs-declarations`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/customs-declarations` | `list` |
| POST | `/v1/customs-declarations` | `create` |
| GET | `/v1/customs-declarations/{id}` | `get` |
| PUT | `/v1/customs-declarations/{id}` | `update` |
| DELETE | `/v1/customs-declarations/{id}` | `delete` |
| PUT | `/v1/customs-declarations/{id}/status` | `updateStatus` |
| GET | `/v1/customs-declarations/{id}/validate` | `validate` |
| GET | `/v1/customs-declarations/{id}/pdf` | `pdf` |
| GET | `/v1/customs-declarations/{id}/xml` | `xml` |
| GET | `/v1/customs-declarations/stats` | `stats` |

### `ExportDeclarationController` — `/v1/export-declarations`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/export-declarations` | `list` |
| POST | `/v1/export-declarations` | `create` |
| GET | `/v1/export-declarations/{id}` | `get` |
| PUT | `/v1/export-declarations/{id}` | `update` |
| DELETE | `/v1/export-declarations/{id}` | `delete` |
| PUT | `/v1/export-declarations/{id}/status` | `updateStatus` |
| GET | `/v1/export-declarations/{id}/validate` | `validate` |
| GET | `/v1/export-declarations/{id}/pdf` | `pdf` |
| GET | `/v1/export-declarations/stats` | `stats` |

### `Ics2DeclarationController` — `/v1/ics2-declarations`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/ics2-declarations` | `list` |
| POST | `/v1/ics2-declarations` | `create` |
| GET | `/v1/ics2-declarations/{id}` | `get` |
| PUT | `/v1/ics2-declarations/{id}` | `update` |
| DELETE | `/v1/ics2-declarations/{id}` | `delete` |
| PUT | `/v1/ics2-declarations/{id}/status` | `updateStatus` |
| GET | `/v1/ics2-declarations/{id}/validate` | `validate` |
| GET | `/v1/ics2-declarations/{id}/pdf` | `pdf` |
| GET | `/v1/ics2-declarations/stats` | `stats` |

### `DebDeclarationController` — `/v1/deb-declarations`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/deb-declarations` | `list` |
| POST | `/v1/deb-declarations` | `create` |
| GET | `/v1/deb-declarations/{id}` | `get` |
| PUT | `/v1/deb-declarations/{id}` | `update` |
| DELETE | `/v1/deb-declarations/{id}` | `delete` |
| PUT | `/v1/deb-declarations/{id}/status` | `updateStatus` |
| GET | `/v1/deb-declarations/{id}/validate` | `validate` |
| GET | `/v1/deb-declarations/{id}/pdf` | `pdf` |
| GET | `/v1/deb-declarations/by-period/{period}` | `byPeriod` |
| GET | `/v1/deb-declarations/stats` | `stats` |

### `CustomsInvoiceController` — `/v1/compliance/customs-invoice`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/compliance/customs-invoice` | `list` |
| POST | `/v1/compliance/customs-invoice` | `create` |
| GET | `/v1/compliance/customs-invoice/{id}` | `get` |
| GET | `/v1/compliance/customs-invoice/shipment/{shipmentId}` | `byShipment` |

---

## Landed cost & partage

### `LandedCostController` — `/v1/landed-costs`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/landed-costs` | `list` |
| POST | `/v1/landed-costs` | `create` |
| GET | `/v1/landed-costs/{id}` | `get` |
| PUT | `/v1/landed-costs/{id}` | `update` |
| DELETE | `/v1/landed-costs/{id}` | `delete` |
| POST | `/v1/landed-costs/calculate` | `calculate` |
| POST | `/v1/landed-costs/what-if` | `whatIf` |
| POST | `/v1/landed-costs/from-shipment/{shipmentId}` | `fromShipment` |
| POST | `/v1/landed-costs/{id}/share` | `share` |
| GET | `/v1/landed-costs/public/{token}` | `publicAccess` |
| GET | `/v1/landed-costs/stats` | `stats` |

### `SharedLinkController` — `/v1/shared`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/shared` | `list` |
| POST | `/v1/shared` | `create` |
| GET | `/v1/shared/{id}` | `get` |
| DELETE | `/v1/shared/{id}` | `delete` |
| GET | `/v1/shared/access/{token}` | `publicAccess` |
| GET | `/v1/shared/shipment/{shipmentId}` | `byShipment` |
| GET | `/v1/shared/stats` | `stats` |

---

## Invoicing & Finance

### `ClientInvoiceController` — `/v1/client-invoices`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/client-invoices` | `list` |
| POST | `/v1/client-invoices` | `create` |
| GET | `/v1/client-invoices/{id}` | `get` |
| PUT | `/v1/client-invoices/{id}` | `update` |
| DELETE | `/v1/client-invoices/{id}` | `delete` |
| PUT | `/v1/client-invoices/{id}/status` | `updateStatus` |
| POST | `/v1/client-invoices/{id}/payment` | `recordPayment` |
| GET | `/v1/client-invoices/overdue` | `overdue` |
| GET | `/v1/client-invoices/dashboard` | `dashboard` |
| GET | `/v1/client-invoices/stats` | `stats` |

### `CarrierInvoiceController` — `/v1/carrier-invoices`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/carrier-invoices` | `list` |
| POST | `/v1/carrier-invoices` | `create` |
| GET | `/v1/carrier-invoices/{id}` | `get` |
| DELETE | `/v1/carrier-invoices/{id}` | `delete` |
| PUT | `/v1/carrier-invoices/{id}/status` | `updateStatus` |
| PUT | `/v1/carrier-invoices/{id}/reconcile` | `reconcile` |
| GET | `/v1/carrier-invoices/stats` | `stats` |

### `FinancialReportingController` — `/v1/financials`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/financials` | `list` |
| POST | `/v1/financials` | `create` |
| GET | `/v1/financials/shipments/{id}` | `getByShipment` |
| GET | `/v1/financials/by-carrier` | `byCarrier` |
| GET | `/v1/financials/by-lane` | `byLane` |
| GET | `/v1/financials/top-carriers` | `topCarriers` |
| GET | `/v1/financials/top-lanes` | `topLanes` |
| GET | `/v1/financials/dashboard` | `dashboard` |

### `PaymentTermController` — `/v1/payment-terms`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/payment-terms` | `list` |
| POST | `/v1/payment-terms` | `create` |
| PUT | `/v1/payment-terms/{id}` | `update` |
| DELETE | `/v1/payment-terms/{id}` | `delete` |
| GET | `/v1/payment-terms/default` | `getDefault` |
| POST | `/v1/payment-terms/seed` | `seed` |

### `CargoInsuranceController` — `/v1/insurance/quotes`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/insurance/quotes` | `list` |
| POST | `/v1/insurance/quotes` | `create` |
| POST | `/v1/insurance/quotes/{id}/policy` | `bindPolicy` |

### `SupplyChainFinanceController` — `/v1/finance`
| Verb | Path | Méthode |
|---|---|---|
| POST | `/v1/finance/request` | `request` |
| POST | `/v1/finance/{id}/approve` | `approve` |
| POST | `/v1/finance/{id}/fund` | `fund` |
| POST | `/v1/finance/{id}/repay` | `repay` |
| GET | `/v1/finance/history` | `history` |
| GET | `/v1/finance/early-payment-discount` | `earlyDiscount` |
| GET | `/v1/finance/stats` | `stats` |

### `FintechController` — `/v1/fintech/connections`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/fintech/connections` | `list` |
| POST | `/v1/fintech/connections` | `create` |
| PUT | `/v1/fintech/connections/{id}` | `update` |
| DELETE | `/v1/fintech/connections/{id}` | `delete` |
| POST | `/v1/fintech/connections/{id}/sync` | `sync` |
| POST | `/v1/fintech/connections/{id}/test` | `test` |
| GET | `/v1/fintech/connections/{id}/data` | `fetchData` |

---

## Billing / Stripe

### `BillingController` — `/v1/billing`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/billing/plans` | `plans` |
| GET | `/v1/billing/subscription` | `subscription` |
| GET | `/v1/billing/status` | `status` |
| GET | `/v1/billing/invoices` | `invoices` |
| POST | `/v1/billing/checkout` | `checkout` |
| POST | `/v1/billing/portal` | `portal` |

### `ApprovalController` — `/v1/approvals`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/approvals/requests` | `list` |
| GET | `/v1/approvals/requests/pending` | `pending` |
| GET | `/v1/approvals/requests/my` | `mine` |
| POST | `/v1/approvals/requests` | `create` |
| PUT | `/v1/approvals/requests/{id}/approve` | `approve` |
| PUT | `/v1/approvals/requests/{id}/reject` | `reject` |
| PUT | `/v1/approvals/requests/{id}/cancel` | `cancel` |
| GET | `/v1/approvals/requests/{id}/history` | `history` |
| GET | `/v1/approvals/workflows` | `listWorkflows` |
| POST | `/v1/approvals/workflows` | `createWorkflow` |
| PUT | `/v1/approvals/workflows/{id}` | `updateWorkflow` |
| DELETE | `/v1/approvals/workflows/{id}` | `deleteWorkflow` |
| GET | `/v1/approvals/stats` | `stats` |

---

## Warehousing & Inventory

### `WarehouseController` — `/v1/warehouses`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/warehouses` | `list` |
| POST | `/v1/warehouses` | `create` |
| GET | `/v1/warehouses/{id}` | `get` |
| PUT | `/v1/warehouses/{id}` | `update` |
| DELETE | `/v1/warehouses/{id}` | `delete` |

### `InventoryController` — `/v1/inventory`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/inventory/items` | `listItems` |
| POST | `/v1/inventory/items` | `createItem` |
| GET | `/v1/inventory/items/{id}` | `getItem` |
| PUT | `/v1/inventory/items/{id}` | `updateItem` |
| DELETE | `/v1/inventory/items/{id}` | `deleteItem` |
| GET | `/v1/inventory/items/{itemId}/barcodes` | `listBarcodes` |
| POST | `/v1/inventory/items/{itemId}/barcodes` | `addBarcode` |
| DELETE | `/v1/inventory/items/{itemId}/barcodes/{barcodeId}` | `removeBarcode` |
| GET | `/v1/inventory/resolve` | `resolveBarcode` |
| GET | `/v1/inventory/balances` | `getBalances` |
| GET | `/v1/inventory/movements` | `getMovements` |
| GET | `/v1/inventory/adjustments` | `listAdjustments` |
| POST | `/v1/inventory/adjustments` | `adjust` |

### `ReceivingController` — `/v1/receivings`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/receivings` | `list` |
| POST | `/v1/receivings` | `create` |
| GET | `/v1/receivings/{id}` | `get` |
| DELETE | `/v1/receivings/{id}` | `delete` |
| POST | `/v1/receivings/{id}/lines` | `addLine` |
| POST | `/v1/receivings/{id}/scan` | `scan` |
| POST | `/v1/receivings/{id}/damage` | `reportDamage` |
| POST | `/v1/receivings/{id}/complete` | `complete` |
| POST | `/v1/receivings/{id}/cancel` | `cancel` |
| GET | `/v1/receivings/discrepancies` | `discrepancies` |
| POST | `/v1/receivings/discrepancies/{id}/resolve` | `resolveDiscrepancy` |

---

## ESG / Carbon / CSRD

### `CarbonOffsetController` — `/v1/carbon-offsets`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/carbon-offsets` | `list` |
| POST | `/v1/carbon-offsets` | `create` |
| GET | `/v1/carbon-offsets/{id}` | `get` |
| PUT | `/v1/carbon-offsets/{id}` | `update` |
| DELETE | `/v1/carbon-offsets/{id}` | `delete` |
| GET | `/v1/carbon-offsets/dashboard` | `dashboard` |
| GET | `/v1/carbon-offsets/stats` | `stats` |
| GET | `/v1/carbon-offsets/csrd-report` | `csrdReport` |

### `CsrdReportingController` — `/v1/csrd`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/csrd/report` | `report` |

---

## E-commerce / ERP / Fintech

### `ECommerceController` — `/v1/ecommerce`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/ecommerce/integrations` | `list` |
| POST | `/v1/ecommerce/integrations` | `create` |
| PUT | `/v1/ecommerce/integrations/{id}` | `update` |
| DELETE | `/v1/ecommerce/integrations/{id}` | `delete` |
| GET | `/v1/ecommerce/integrations/{id}/orders` | `orders` |
| POST | `/v1/ecommerce/integrations/{id}/sync` | `sync` |
| GET | `/v1/ecommerce/sync-log` | `syncLog` |

### `ErpController` — `/v1/erp`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/erp` | `list` |
| POST | `/v1/erp` | `create` |
| PUT | `/v1/erp/{id}` | `update` |
| DELETE | `/v1/erp/{id}` | `delete` |
| POST | `/v1/erp/{id}/sync` | `sync` |
| POST | `/v1/erp/{id}/test` | `test` |
| GET | `/v1/erp/{id}/products` | `products` |
| GET | `/v1/erp/{id}/orders` | `orders` |
| GET | `/v1/erp/{id}/contacts` | `contacts` |
| GET | `/v1/erp/health` | `health` |
| GET | `/v1/erp/sync-logs` | `syncLogs` |

### `ProviderConfigController` — `/v1/providers`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/providers` | `list` |
| POST | `/v1/providers` | `create` |
| DELETE | `/v1/providers/{id}` | `delete` |
| POST | `/v1/providers/{id}/test` | `test` |
| GET | `/v1/providers/health` | `health` |

---

## Notifications

### `NotificationController` — `/v1/notifications`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/notifications` | `list` |
| PATCH | `/v1/notifications` | `bulkUpdate` |
| PATCH | `/v1/notifications/{id}/archive` | `archive` |
| DELETE | `/v1/notifications/{id}` | `delete` |
| PATCH | `/v1/notifications/read` | `markRead` |
| PATCH | `/v1/notifications/read-all` | `markAllRead` |
| GET | `/v1/notifications/unread-count` | `unreadCount` |

### `NotificationRuleController` — `/v1/notification-rules`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/notification-rules` | `list` |
| POST | `/v1/notification-rules` | `create` |
| PUT | `/v1/notification-rules/{id}` | `update` |
| DELETE | `/v1/notification-rules/{id}` | `delete` |
| POST | `/v1/notification-rules/test` | `test` |

---

## Email intake & Document parser

### `EmailIntakeController` — `/v1/email-intake`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/email-intake` | `list` |
| GET | `/v1/email-intake/pending` | `pending` |
| GET | `/v1/email-intake/{id}` | `get` |
| POST | `/v1/email-intake/{id}/confirm` | `confirm` |
| POST | `/v1/email-intake/{id}/reject` | `reject` |
| GET | `/v1/email-intake/stats` | `stats` |

### `DocumentParserController` — `/v1/document-parser`
| Verb | Path | Méthode |
|---|---|---|
| POST | `/v1/document-parser/parse/pdf` | `parsePdf` |
| POST | `/v1/document-parser/parse/text` | `parseText` |
| GET | `/v1/document-parser/{id}` | `get` |
| GET | `/v1/document-parser/history` | `history` |
| GET | `/v1/document-parser/type/{docType}` | `byType` |
| GET | `/v1/document-parser/stats` | `stats` |

---

## Academy / Training

### `TrainingController` — `/v1/academy`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/academy/modules` | `listModules` |
| POST | `/v1/academy/modules` | `createModule` |
| GET | `/v1/academy/modules/{id}` | `getModule` |
| PUT | `/v1/academy/modules/{id}` | `updateModule` |
| POST | `/v1/academy/modules/{id}/enroll` | `enroll` |
| POST | `/v1/academy/modules/{moduleId}/quiz` | `submitQuiz` |
| GET | `/v1/academy/enrollments/{id}/certificate` | `certificate` |
| POST | `/v1/academy/enrollments/{id}/progress` | `updateProgress` |
| PUT | `/v1/academy/enrollments/{id}/progress` | `updateProgressAlt` |
| GET | `/v1/academy/dashboard` | `dashboard` |
| POST | `/v1/academy/dashboard` | `dashboard` |

---

## Analytics & Audit

### `AnalyticsController` — `/v1/analytics`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/analytics/dashboard` | `dashboard` |
| GET | `/v1/analytics/cost-by-carrier` | `costByCarrier` |
| GET | `/v1/analytics/cost-by-mode` | `costByMode` |
| GET | `/v1/analytics/cost-trends` | `costTrends` |
| GET | `/v1/analytics/incoterm-usage` | `incotermUsage` |
| GET | `/v1/analytics/shipments-by-status` | `byStatus` |
| GET | `/v1/analytics/shipments-over-time` | `overTime` |
| GET | `/v1/analytics/top-routes` | `topRoutes` |
| GET | `/v1/analytics/volume-distribution` | `volumeDistribution` |
| GET | `/v1/analytics/weight-distribution` | `weightDistribution` |
| GET | `/v1/analytics/carrier-performance` | `carrierPerformance` |
| POST | `/v1/analytics/carrier-performance` | `carrierPerformancePost` |

### `AuditLogController` — `/v1/audit`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/audit` | `list` |
| GET | `/v1/audit/user/{userId}` | `byUser` |
| GET | `/v1/audit/entity/{entityType}` | `byEntity` |
| GET | `/v1/audit/action/{action}` | `byAction` |
| GET | `/v1/audit/stats` | `stats` |

---

## Branding / Mobile

### `BrandingController` — `/v1/branding`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/branding` | `get` |
| PUT | `/v1/branding` | `update` |
| GET | `/v1/branding/languages` | `languages` |
| GET | `/v1/branding/translations` | `translations` |
| GET | `/v1/branding/portal-config` | `portalConfig` |

### `MobileApiController` — `/v1/mobile`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/mobile/dashboard` | `dashboard` |
| GET | `/v1/mobile/quick-quote` | `quickQuote` |
| GET | `/v1/mobile/recent-shipments` | `recentShipments` |
| GET | `/v1/mobile/profile` | `profile` |
| POST | `/v1/mobile` | `create` |
| GET | `/v1/mobile` | `list` |
| DELETE | `/v1/mobile` | `delete` |
| POST | `/v1/mobile/device/register` | `registerDevice` |
| POST | `/v1/mobile/device/unregister` | `unregisterDevice` |
| GET | `/v1/mobile/notifications` | `notifications` |
| POST | `/v1/mobile/notifications/{id}/read` | `markRead` |
| POST | `/v1/mobile/notifications/read-all` | `markAllRead` |
| GET | `/v1/mobile/notifications/stream` | `stream` |

---

## Webhooks

### `WebhookController` — `/v1/webhooks`
| Verb | Path | Méthode |
|---|---|---|
| POST | `/v1/webhooks/dhl` | `dhl` |
| POST | `/v1/webhooks/shippo` | `shippo` |
| POST | `/v1/webhooks/generic` | `generic` |

### `StripeWebhookController` — `/v1/webhooks`
| Verb | Path | Méthode |
|---|---|---|
| POST | `/v1/webhooks/stripe` | `stripe` |

---

## Files / Documents exports

### `FileController` — `/v1/files`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/files` | `list` |
| POST | `/v1/files` | `upload` |
| DELETE | `/v1/files` | `delete` |
| POST | `/v1/files/upload/document` | `uploadDocument` |
| POST | `/v1/files/upload/logo` | `uploadLogo` |
| GET | `/v1/files/download/{bucket}/{key}` | `download` |

### `DocumentExportController` — `/v1/documents`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/documents/shipments/{id}/pdf` | `shipmentPdf` |
| GET | `/v1/documents/shipments/{id}/cmr` | `cmr` |
| GET | `/v1/documents/shipments/{id}/dgd` | `dgd` |
| GET | `/v1/documents/shipments/{id}/certificate-of-origin` | `certificateOfOrigin` |
| POST | `/v1/documents/quotes/pdf` | `quotePdf` |

### `ExportController` — `/v1/export`
| Verb | Path | Méthode |
|---|---|---|
| GET | `/v1/export/shipments` | `shipments` |
| GET | `/v1/export/carriers` | `carriers` |

### `ImportController` — `/v1/import`
| Verb | Path | Méthode |
|---|---|---|
| POST | `/v1/import/preview` | `preview` |
| POST | `/v1/import/carriers` | `carriers` |

---

## 📋 Total

**~530 endpoints** répartis sur **70 contrôleurs** dans **26 domaines**.

---

## 🔍 Tester rapidement

Une fois ton backend démarré (par exemple `mvn spring-boot:run "-Dspring-boot.run.profiles=local"`), tu peux :

1. **Swagger UI** : http://localhost:8080/api/swagger-ui.html
2. **OpenAPI JSON** : http://localhost:8080/api/api-docs
3. **Santé** : http://localhost:8080/api/actuator/health

Exemple curl (après login en profil `dev` ou `prod`) :

```bash
# Login
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"xxx"}'

# Utiliser le token
curl http://localhost:8080/api/v1/shipments \
  -H "Authorization: Bearer <token>"
```

Pour le profil `local` (H2, données en mémoire) : l'auth n'est pas testable sans données seedées — utilise directement Swagger pour explorer.
