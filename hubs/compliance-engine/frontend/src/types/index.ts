// === Compliance Types ===
export interface ComplianceAlert {
  severity: 'INFO' | 'WARNING' | 'CRITICAL';
  message: string;
  category: string;
}

// === Authentication Types ===
export interface User {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  role?: 'ADMIN' | 'MANAGER' | 'OPERATOR' | 'MEMBER' | 'VIEWER';
  company?: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  company?: string;
}

export interface AuthResponse {
  token: string;
  user: User;
}

// === Incoterm Types ===
export interface Incoterm {
  id: string;
  code: string;
  fullName: string;
  mode: string;
  buyerRiskScore: number;
  description?: string;
  risks?: string[];
  costs?: string[];
}

// === Simulation Types ===
export interface SimulationParams {
  incotermId: number;
  incotermCode?: string;
  productValue: number;
  currency: string;
  originCountry: string;
  destinationCountry: string;
  weight: number; // en kg
  volume?: number;
  transportMode: 'SEA' | 'AIR' | 'ROAD';
  distance?: number;
  quantity?: number;
}

export interface SimulationResponse {
  incoterm: string;
  incotermFullName: string;
  buyerRiskScore: number;
  riskLevel: string;
  estimatedDays: number;
  buyerCosts: CostBreakdown;
  sellerCosts: CostBreakdown;
  totalBuyerCost: number;
  totalSellerCost: number;
  responsibilities: ResponsibilityMatrix;
  recommendations: string[];
  warnings: string[];
  buyerRisks: string[];
  complianceAlerts: ComplianceAlert[];
  comparison?: unknown[];
  isWhatIf?: boolean;
  params?: SimulationParams;
  logistics?: LogisticsInfo;
}

export interface LogisticsInfo {
  totalBoxes: number;
  totalVolumeM3: number;
  totalWeightKg: number;
  utilizationPercent: number;
  recommendedMode: string;
  modeReason: string;
  totalPackageVolumeM3: number;
}

export interface CostBreakdown {
  goodsValue: number;
  exportCustoms: number;
  originHandling: number;
  originDocumentation: number;
  freight: number;
  insurance: number;
  destinationHandling: number;
  destinationDocumentation: number;
  importDuties: number;
  importVat: number;
  lastMileDelivery: number;
}

export interface ResponsibilityMatrix {
  sellerExportClearance: boolean;
  sellerOriginCharges: boolean;
  sellerMainFreight: boolean;
  sellerInsurance: boolean;
  sellerDestinationCharges: boolean;
  sellerImportDuties: boolean;
  sellerVat: boolean;
}

export interface SimulationResult {
  id: number;
  userId: number;
  incotermCode: string;
  incotermName: string;
  productValue: number;
  transportCost: number;
  insuranceCost: number;
  customsDuty: number;
  handlingCost: number;
  totalCost: number;
  currency: string;
  originCountry: string;
  destinationCountry: string;
  weight: number;
  transportMode: string;
  createdAt: string;
  updatedAt?: string;
}

export interface ShipmentItem {
  id?: string;
  shipmentId?: string;
  itemId?: string;
  sku: string;
  name: string;
  description?: string;
  hsCode?: string;
  quantity: number;
  unit: string;
  unitPrice?: number;
}


// === CalculationRequest/Result Types ===
export interface CalculationRequest {
  incoterms: string;
  unitPrice?: number;
  quantity?: number;
  weight: number;
  volume?: number;
  distance?: number;
  currency?: string;
  originCountry?: string;
  destinationCountry?: string;
  [key: string]: unknown;
}

export interface CalculationResult {
  totalCost: number;
  breakdown: CostBreakdown;
  incoterms: string;
  currency: string;
  createdAt: string;
  isWhatIf?: boolean;
  params?: SimulationParams;
}

export interface SaveSimulationRequest {
  incotermId: number;
  originCountry: string;
  destinationCountry: string;
  productValue: number;
  transportCost: number;
  insuranceCost: number;
  customsDuty: number;
  handlingCost: number;
  totalCost: number;
  currency: string;
}

export interface SimulationFromAPI {
  id: string;  // UUID en string
  incotermCode: string;
  originCountry: string;
  destinationCountry: string;
  goodsValue: number;
  currency: string;
  transportMode?: string;
  totalBuyerCost: number;
  createdAt: string;
}

// === Carrier Types ===
export interface Carrier {
  id: string;
  companyId?: string;
  name: string;
  code: string;
  logoUrl?: string;
  transportModes: string; // comma separated: SEA,AIR,ROAD
  apiEndpoint?: string;
  contactName?: string;
  contactEmail?: string;
  contactPhone?: string;
  country?: string;
  active: boolean;
  createdAt: string;
  updatedAt?: string;
}

export interface CarrierFormData {
  name: string;
  code: string;
  transportModes: string;
  country?: string;
  contactName?: string;
  contactEmail?: string;
  contactPhone?: string;
  logoUrl?: string;
}

// === Shipping Rate Types ===
export interface ShippingRate {
  id: string;
  carrierId: string;
  carrierName?: string;
  carrierCode?: string;
  companyId?: string;
  name: string;
  originCountry: string;
  destinationCountry: string;
  transportMode: string;
  minWeightKg?: number;
  maxWeightKg?: number;
  baseRate: number;
  currency: string;
  ratePerKg: number;
  ratePerCbm: number;
  transitDaysMin?: number;
  transitDaysMax?: number;
  co2EstimateKg?: number;
  active: boolean;
  createdAt: string;
  validFrom?: string;
  validUntil?: string;
}

export interface ShippingRateFormData {
  carrierId: string;
  name: string;
  originCountry: string;
  destinationCountry: string;
  transportMode: string;
  minWeightKg?: number;
  maxWeightKg?: number;
  baseRate: number;
  currency?: string;
  ratePerKg?: number;
  ratePerCbm?: number;
  transitDaysMin?: number;
  transitDaysMax?: number;
  co2EstimateKg?: number;
}

// === Quote Types ===
export interface QuoteRequest {
  originCountry: string;
  destinationCountry: string;
  transportMode?: string;
  weightKg: number;
  volumeM3: number;
  goodsValue: number;
  currency?: string;
  hsCode?: string;
}

export interface QuoteResponse {
  carrierId: string;
  carrierName: string;
  carrierLogo?: string;
  transportMode: string;
  baseRate: number;
  totalCost: number;
  currency: string;
  transitDaysMin?: number;
  transitDaysMax?: number;
  co2EstimateKg?: number;
  rateName: string;
  providerType?: string;
  providerLogo?: string;
  providerName?: string;
  totalCostConverted?: number;
  displayCurrency?: string;
  conversionRate?: number;
}

export interface CurrencyRate {
  from: string;
  to: string;
  rate: number;
}

export interface CurrencyConversion {
  from: string;
  to: string;
  originalAmount: number;
  convertedAmount: number;
  rate: number;
}

// === Shipment Types ===
export interface ShipmentOrder {
  id: string;
  companyId?: string;
  userId?: string;
  orderNumber: string;
  status: 'DRAFT' | 'QUOTED' | 'BOOKED' | 'IN_TRANSIT' | 'DELIVERED' | 'CANCELLED';
  carrierId?: string;
  carrierName?: string;
  shipperName?: string;
  shipperAddress?: string;
  shipperCity?: string;
  shipperCountry?: string;
  shipperPostalCode?: string;
  consigneeName?: string;
  consigneeAddress?: string;
  consigneeCity?: string;
  consigneeCountry?: string;
  consigneePostalCode?: string;
  goodsDescription?: string;
  goodsValue?: number;
  currency?: string;
  weightKg?: number;
  volumeM3?: number;
  packagesCount?: number;
  hsCode?: string;
  incotermCode?: string;
  isDangerous?: boolean;
  quotedCost?: number;
  finalCost?: number;
  requestedPickupDate?: string;
  estimatedDeliveryDate?: string;
  actualDeliveryDate?: string;
  trackingEvents?: TrackingEvent[];
  items?: ShipmentItem[];
  createdAt: string;
  updatedAt?: string;
}

export interface TrackingEvent {
  id: string;
  shipmentId: string;
  status: string;
  location?: string;
  latitude?: number;
  longitude?: number;
  description?: string;
  eventTime: string;
  source?: string;
  dataSource?: 'LIVE' | 'MANUAL';
}

export interface ShipmentFormData {
   carrierId?: string;
   shippingRateId?: string;
   shipperName: string;
   shipperAddress: string;
   shipperCity: string;
   shipperCountry: string;
   shipperPostalCode: string;
   consigneeName: string;
   consigneeAddress: string;
   consigneeCity: string;
   consigneeCountry: string;
   consigneePostalCode: string;
   goodsDescription: string;
   goodsValue: number;
   currency?: string;
   weightKg: number;
   volumeM3: number;
   packagesCount?: number;
   hsCode?: string;
   incotermCode?: string;
   isDangerous?: boolean;
   requestedPickupDate?: string;
   items?: ShipmentItem[];
 }

// === Provider Types ===
export interface ProviderConfig {
  id: string;
  companyId?: string;
  providerType: string;
  apiKey?: string;
  apiSecret?: string;
  active: boolean;
  priority: number;
  configJson?: string;
  lastHealthCheck?: string;
  healthStatus: 'HEALTHY' | 'DEGRADED' | 'DOWN' | 'UNKNOWN';
  consecutiveFailures: number;
  createdAt: string;
  updatedAt?: string;
}

export interface ProviderConfigFormData {
  providerType: string;
  apiKey?: string;
  apiSecret?: string;
  priority?: number;
  isActive?: boolean;
  configJson?: string;
}

export interface ProviderHealth {
  providerType: string;
  providerName: string;
  healthStatus: 'HEALTHY' | 'DEGRADED' | 'DOWN' | 'UNKNOWN';
  lastHealthCheck?: string;
  consecutiveFailures: number;
  active: boolean;
}

// === Notification Types ===
export interface NotificationRule {
  id: string;
  companyId?: string;
  name: string;
  eventType: string;
  active: boolean;
  sendEmail: boolean;
  sendWebhook: boolean;
  sendInApp: boolean;
  emailRecipients?: string;
  webhookUrl?: string;
  webhookSecret?: string;
  filterStatus?: string;
  filterCarrierId?: string;
  filterDataSource?: 'LIVE' | 'MANUAL' | '';
  /** null/"NONE" = notification seule. Non-null → crée une OrchestrationSuggestion en plus (voir docs/04). */
  actionType?: string;
  requiresApproval?: boolean;
  maxBudgetAmount?: number;
  /** UUIDs de transporteurs autorisés, séparés par des virgules, ou vide/undefined = tous autorisés. */
  allowedCarrierIds?: string;
  createdAt: string;
  updatedAt?: string;
}

export interface NotificationRuleFormData {
  name: string;
  eventType: string;
  isActive: boolean;
  sendEmail: boolean;
  sendWebhook: boolean;
  sendInApp: boolean;
  emailRecipients?: string;
  webhookUrl?: string;
  webhookSecret?: string;
  filterStatus?: string;
  filterCarrierId?: string;
  filterDataSource?: 'LIVE' | 'MANUAL' | '';
  actionType?: string;
  maxBudgetAmount?: number;
  allowedCarrierIds?: string;
}

export interface Notification {
  id: string;
  companyId?: string;
  userId?: string;
  ruleId?: string;
  ruleName?: string;
  eventType: string;
  title: string;
  message: string;
  channel: 'IN_APP' | 'EMAIL' | 'WEBHOOK';
  status: 'UNREAD' | 'READ' | 'ARCHIVED';
  sentAt: string;
  readAt?: string;
  entityType?: string;
  entityId?: string;
  webhookStatus?: string;
  webhookResponseCode?: number;
  createdAt: string;
}

// === Analytics Types ===
export interface DashboardStats {
  totalShipments: number;
  activeShipments: number;
  deliveredShipments: number;
  draftShipments: number;
  cancelledShipments: number;
  totalShippingCost: number;
  averageShippingCost: number;
  maxShippingCost: number;
  minShippingCost: number;
  totalWeightKg: number;
  totalVolumeM3: number;
  averageWeightKg: number;
  averageVolumeM3: number;
  totalGoodsValue: number;
  totalSimulations: number;
  simulationsThisMonth: number;
  totalCarriers: number;
  activeCarriers: number;
  totalCo2Kg: number;
  averageCo2PerShipment: number;
  period: string;
}

export interface ShipmentsOverTime {
  date: string;
  count: number;
  totalCost: number;
}

export interface ShipmentByStatus {
  status: string;
  count: number;
  percentage: number;
}

export interface CostByCarrier {
  carrierId: string;
  carrierName: string;
  totalCost: number;
  shipmentCount: number;
  averageCost: number;
}

export interface CostByMode {
  mode: string;
  totalCost: number;
  count: number;
  averageCost: number;
}

export interface TopRoute {
  origin: string;
  destination: string;
  count: number;
  totalCost: number;
}

export interface IncotermUsage {
  code: string;
  count: number;
  percentage: number;
}

export interface ChartData {
  labels: string[];
  values: number[];
  title: string;
  unit: string;
}

// === ERP Types ===
export interface ErpConfig {
  id: string;
  companyId?: string;
  erpType: string; // ODOO, SAP, QUICKBOOKS
  name: string;
  apiEndpoint?: string;
  apiKey?: string;
  apiSecret?: string;
  databaseName?: string;
  username?: string;
  isActive: boolean;
  lastSyncAt?: string;
  syncStatus: 'IDLE' | 'SYNCING' | 'SUCCESS' | 'ERROR';
  lastError?: string;
  configJson?: string;
  createdAt: string;
  updatedAt?: string;
}

export interface ErpConfigFormData {
  erpType: string;
  name: string;
  apiEndpoint?: string;
  apiKey?: string;
  apiSecret?: string;
  databaseName?: string;
  username?: string;
  isActive?: boolean;
  configJson?: string;
}

export interface ErpSyncLog {
  id: string;
  erpConfigId: string;
  erpTypeName?: string;
  syncType: string;
  direction: string;
  status: string;
  recordsTotal: number;
  recordsSynced: number;
  recordsFailed: number;
  errorMessage?: string;
  startedAt: string;
  completedAt?: string;
}

export interface ErpHealth {
  erpType: string;
  name: string;
  syncStatus: string;
  lastSyncAt?: string;
  lastError?: string;
  isActive: boolean;
}

export interface SyncRequest {
  syncType: string;
  direction: string;
}

// === Live Tracking Types ===
export interface TrackingUpdate {
  status: string;
  location: string;
  latitude: number;
  longitude: number;
  description: string;
  eventTime: string;
  source: string;
}

export interface LivePosition {
  latitude: number;
  longitude: number;
  speed: number;
  course: number;
  heading: string;
  timestamp: string;
  source: string;
  vesselName: string;
}

// === Audit Log Types ===
export interface AuditLog {
  id: string;
  userId: string;
  userEmail: string;
  userRole: string;
  action: string;
  entityType: string;
  entityId: string;
  entityName: string;
  details: string;
  ipAddress: string;
  userAgent: string;
  createdAt: string;
}

export interface AuditLogStats {
  total: number;
  byAction: Record<string, number>;
  byEntity: Record<string, number>;
}

// === Client Portal Types ===
export interface ClientUser {
  id: string;
  companyId: string;
  email: string;
  fullName: string;
  phone?: string;
  active: boolean;
  lastLoginAt?: string;
  createdAt: string;
}

export interface ClientShipment {
  id: string;
  orderNumber: string;
  status: string;
  shipperCity?: string;
  shipperCountry?: string;
  consigneeCity?: string;
  consigneeCountry?: string;
  goodsDescription?: string;
  weightKg?: number;
  volumeM3?: number;
  packagesCount?: number;
  carrierName?: string;
  quotedCost?: number;
  finalCost?: number;
  costCurrency?: string;
  incotermCode?: string;
  estimatedDeliveryDate?: string;
  actualDeliveryDate?: string;
  createdAt: string;
  trackingEvents?: TrackingEvent[];
}

export interface SharedLinkItem {
  id: string;
  token: string;
  label?: string;
  url: string;
  shipmentId: string;
  orderNumber: string;
  active: boolean;
  accessCount: number;
  lastAccessedAt?: string;
  expiresAt?: string;
  createdAt: string;
}

export interface SharedTrackingData {
  shipment: ClientShipment;
  trackingEvents: TrackingEvent[];
  companyName: string;
  companyLogo?: string;
  label?: string;
}

// === Phase 13: Rate Optimization Types ===
export interface RouteAnalytics {
  id: string;
  origin: string;
  destination: string;
  transportMode: string;
  carrierName: string;
  shipmentCount: number;
  avgCost: number;
  minCost: number;
  maxCost: number;
  avgTransitDays: number;
  onTimeRate: number;
  avgWeightKg: number;
  costPerKg: number;
  co2TotalKg: number;
  lastAnalyzedAt: string;
}

export interface RateOptimization {
  id: string;
  origin: string;
  destination: string;
  transportMode: string;
  weightKg: number | null;
  volumeM3: number | null;
  predictedCost: number;
  recommendedCarrier: string | null;
  confidence: number;
  predictedTransitDays: number | null;
  costBreakdown: string;
  alternatives: string;
  savingsEstimate: number;
  savingsPercent: number;
  status: string;
  notes: string | null;
  createdAt: string;
}

export interface ConsolidationOpportunity {
  id: string;
  origin: string;
  destination: string;
  transportMode: string | null;
  shipmentIds: string;
  shipmentCount: number;
  totalWeightKg: number;
  totalVolumeM3: number;
  combinedCost: number;
  consolidatedCost: number;
  estimatedSavings: number;
  savingsPercent: number;
  consolidationWindowDays: number;
  status: string;
  notes: string | null;
  createdAt: string;
}

export interface LaneAnalysis {
  origin: string;
  destination: string;
  totalShipments: number;
  carrierCount: number;
  bestCarrier: string;
  bestCost: number;
  worstCost: number;
  potentialSavings: number;
  avgOnTimeRate: number;
}

export interface OptimizationStats {
  totalRoutes: number;
  totalOptimizations: number;
  pendingOptimizations: number;
  acceptedOptimizations: number;
  totalConsolidationOpportunities: number;
  pendingConsolidations: number;
  totalSavings: number;
  acceptedSavings: number;
  consolidationSavings: number;
  avgConfidence: number;
}
