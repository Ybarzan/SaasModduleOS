import { lazy, Suspense } from 'react';
import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useAuthStore, hasMinimumPlan, type UserRole } from './stores/auth';
import { getRequiredPlan } from './config/navigation';
import { useClientAuthStore } from './stores/clientAuth';
import { useProfileRefresh } from './hooks/useProfileRefresh';
import Navbar from './components/Navbar';
import Footer from './components/Footer';
import PWAInstallPrompt from './components/PWAInstallPrompt';
import OfflineIndicator from './components/OfflineIndicator';
import ErrorBoundary from './components/ErrorBoundary';
import GoogleAnalytics from './components/GoogleAnalytics';
import CookieConsent from './components/CookieConsent';
import AppLayout from './layouts/AppLayout';

// Eagerly loaded (landing page + auth — critical path)
import Home from './pages/Home';
import Login from './pages/Login';

// Lazy-loaded pages (code-split chunks)
const Register = lazy(() => import('./pages/Register'));
const ForgotPassword = lazy(() => import('./pages/ForgotPassword'));
const ResetPassword = lazy(() => import('./pages/ResetPassword'));
const VerifyEmail = lazy(() => import('./pages/VerifyEmail'));
const TrackingLookup = lazy(() => import('./pages/TrackingLookup'));
const Simulation = lazy(() => import('./pages/Simulation'));
const Calculator = lazy(() => import('./pages/Calculator'));
const Dashboard = lazy(() => import('./pages/Dashboard'));
const Carriers = lazy(() => import('./pages/Carriers'));
const Quotes = lazy(() => import('./pages/Quotes'));
const Shipments = lazy(() => import('./pages/Shipments'));
const Providers = lazy(() => import('./pages/Providers'));
const ProviderHealth = lazy(() => import('./pages/ProviderHealth'));
const Notifications = lazy(() => import('./pages/Notifications'));
const NotificationRules = lazy(() => import('./pages/NotificationRules'));
const Team = lazy(() => import('./pages/Team'));
const ErpSettings = lazy(() => import('./pages/ErpSettings'));
const AuditLog = lazy(() => import('./pages/AuditLog'));
const Clients = lazy(() => import('./pages/Clients'));
const SharedLinks = lazy(() => import('./pages/SharedLinks'));
const ClientLogin = lazy(() => import('./pages/ClientLogin'));
const ClientDashboard = lazy(() => import('./pages/ClientDashboard'));
const ClientShipmentDetail = lazy(() => import('./pages/ClientShipmentDetail'));
const SharedTracking = lazy(() => import('./pages/SharedTracking'));
const SharedLandedCost = lazy(() => import('./pages/SharedLandedCost'));
const Co2Calculator = lazy(() => import('./pages/Co2Calculator'));
const ShipTracker = lazy(() => import('./pages/ShipTracker'));
const FlightRadar = lazy(() => import('./pages/FlightRadar'));
const LogisticsDashboard = lazy(() => import('./pages/Logistics'));
const CustomsDutyCalculator = lazy(() => import('./pages/CustomsDutyCalculator'));
const CurrencyExchange = lazy(() => import('./pages/CurrencyExchange'));
const VolumetricWeight = lazy(() => import('./pages/VolumetricWeight'));
const CargoInsurance = lazy(() => import('./pages/CargoInsurance'));
const RouteOptimizer = lazy(() => import('./pages/RouteOptimizer'));
const DocumentsPage = lazy(() => import('./pages/DocumentsPage'));
const Pricing = lazy(() => import('./pages/Pricing'));
const PricingComparison = lazy(() => import('./pages/PricingComparison'));
const Faq = lazy(() => import('./pages/Faq'));
const IncotermsGuide = lazy(() => import('./pages/IncotermsGuide'));
const Hubs = lazy(() => import('./pages/Hubs'));
const UseCases = lazy(() => import('./pages/UseCases'));
const MisclassificationCostCalculator = lazy(() => import('./pages/MisclassificationCostCalculator'));
const Developers = lazy(() => import('./pages/Developers'));
const StatusPage = lazy(() => import('./pages/StatusPage'));
const Privacy = lazy(() => import('./pages/Privacy'));
const Terms = lazy(() => import('./pages/Terms'));
const LegalNotice = lazy(() => import('./pages/LegalNotice'));
const NotFound = lazy(() => import('./pages/NotFound'));
const Billing = lazy(() => import('./pages/Billing'));
const EoriSettings = lazy(() => import('./pages/EoriSettings'));
const CustomsDashboard = lazy(() => import('./pages/CustomsDashboard'));
const TradeAgreements = lazy(() => import('./pages/TradeAgreements'));
const DeclarationsPage = lazy(() => import('./pages/DeclarationsPage'));
const DeniedPartyScreening = lazy(() => import('./pages/DeniedPartyScreening'));
const LandedCostCalculator = lazy(() => import('./pages/LandedCostCalculator'));
const DocumentParser = lazy(() => import('./pages/DocumentParser'));
const HsClassification = lazy(() => import('./pages/HsClassification'));
const CarrierInvoicing = lazy(() => import('./pages/CarrierInvoicing'));
const CarrierBookings = lazy(() => import('./pages/CarrierBookings'));
const FinancialReports = lazy(() => import('./pages/FinancialReports'));
const EtaPredictions = lazy(() => import('./pages/EtaPredictions'));
const QualityDashboard = lazy(() => import('./pages/QualityDashboard'));
const ApprovalWorkflows = lazy(() => import('./pages/ApprovalWorkflows'));
const OrchestrationSuggestions = lazy(() => import('./pages/OrchestrationSuggestions'));
const CarbonDashboard = lazy(() => import('./pages/CarbonDashboard'));
const ClientInvoicing = lazy(() => import('./pages/ClientInvoicing'));
const RateComparison = lazy(() => import('./pages/RateComparison'));
const ShippingRates = lazy(() => import('./pages/ShippingRates'));
const OptimizationDashboard = lazy(() => import('./pages/OptimizationDashboard'));
const ECommerce = lazy(() => import('./pages/ECommerce'));
const Academy = lazy(() => import('./pages/Academy'));
const FinanceSupplyChain = lazy(() => import('./pages/FinanceSupplyChain'));
const MultiBranch = lazy(() => import('./pages/MultiBranch'));
const CsrdReport = lazy(() => import('./pages/CsrdReport'));
const BrandingPortal = lazy(() => import('./pages/BrandingPortal'));
const GroupagePage = lazy(() => import('./pages/GroupagePage'));
const FintechPage = lazy(() => import('./pages/FintechPage'));
const EmailIntake = lazy(() => import('./pages/EmailIntake'));
const ApiKeys = lazy(() => import('./pages/ApiKeys'));
const Webhooks = lazy(() => import('./pages/Webhooks'));
const CompanySettings = lazy(() => import('./pages/CompanySettings'));
const RoleManagement = lazy(() => import('./pages/RoleManagement'));
const PaymentTerms = lazy(() => import('./pages/PaymentTerms'));
const TaricData = lazy(() => import('./pages/TaricData'));
const FrenchFiscal = lazy(() => import('./pages/FrenchFiscal'));
const Eur1Certificates = lazy(() => import('./pages/Eur1Certificates'));
const Compliance = lazy(() => import('./pages/Compliance'));
const Warehouses = lazy(() => import('./pages/Warehouses'));
const WarehouseDetail = lazy(() => import('./pages/WarehouseDetail'));
const InventoryItems = lazy(() => import('./pages/InventoryItems'));
const Inventory = lazy(() => import('./pages/Inventory'));
const Receiving = lazy(() => import('./pages/Receiving'));
const ScanReceiving = lazy(() => import('./pages/ScanReceiving'));

// Loading fallback for Suspense
function PageLoader() {
  return (
    <div className="flex items-center justify-center min-h-[50vh]">
      <div className="flex flex-col items-center space-y-3">
        <div className="w-8 h-8 border-2 border-accent border-t-transparent rounded-full animate-spin" />
        <p className="text-sm text-ink-soft">Chargement...</p>
      </div>
    </div>
  );
}

// Routes that keep the marketing chrome (Navbar/Footer) even for a logged-in user —
// everything else authenticated renders inside AppLayout (sidebar app shell).
const PUBLIC_PATHS = new Set([
  '/', '/login', '/register', '/forgot-password', '/reset-password', '/verify-email', '/track',
  '/pricing', '/pricing-comparison', '/faq', '/guide-incoterms', '/hubs', '/cas-usage', '/cout-erreur-douane', '/developers', '/status', '/confidentialite', '/cgu', '/mentions-legales',
  '/douane', '/change', '/poids-volumetrique', '/assurance-cargo', '/optimisation-itineraire',
]);

// Create QueryClient instance
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      gcTime: 5 * 60 * 1000,
      staleTime: 1 * 60 * 1000,
    },
    mutations: {
      retry: 0,
    },
  },
});

// ProtectedRoute with optional role checking
function ProtectedRoute({
  children,
  requiredRole,
  requireAdmin = false,
}: {
  children: React.ReactNode;
  requiredRole?: UserRole;
  requireAdmin?: boolean;
}) {
  const location = useLocation();
  const token = useAuthStore((s) => s.token);
  const user = useAuthStore((s) => s.user);
  const isAuthenticated = token !== null && user !== null;
  const hasMinimumRole = useAuthStore((s) => s.hasMinimumRole);
  const isAdmin = useAuthStore((s) => s.isAdmin);

  if (!isAuthenticated) return <Navigate to="/login" replace />;
  if (requireAdmin && !isAdmin()) return <Navigate to="/dashboard" replace />;
  if (requiredRole && !hasMinimumRole(requiredRole)) return <Navigate to="/dashboard" replace />;

  // Ferme côté route ce que la sidebar ne fait que masquer visuellement (état
  // verrouillé) : navigation.ts reste la seule source de vérité du plan requis.
  const requiredPlan = getRequiredPlan(location.pathname);
  if (requiredPlan && !hasMinimumPlan(user?.plan, requiredPlan)) {
    return <Navigate to="/pricing" replace />;
  }

  return <>{children}</>;
}

// ClientProtectedRoute
function ClientProtectedRoute({ children }: { children: React.ReactNode }) {
  const token = useClientAuthStore((s) => s.token);
  const client = useClientAuthStore((s) => s.client);
  const isAuthenticated = token !== null && client !== null;
  return isAuthenticated ? <>{children}</> : <Navigate to="/client/login" replace />;
}

// PublicRoute component (redirect to dashboard if already authenticated)
function PublicRoute({ children }: { children: React.ReactNode }) {
  const token = useAuthStore((s) => s.token);
  const user = useAuthStore((s) => s.user);
  const isAuthed = token !== null && user !== null;
  return !isAuthed ? <>{children}</> : <Navigate to="/dashboard" replace />;
}

function AppRoutes() {
  return (
    <Suspense fallback={<PageLoader />}>
      <Routes>
              {/* Public Routes */}
              <Route path="/" element={<Home />} />
              <Route path="/login" element={<PublicRoute><Login /></PublicRoute>} />
              <Route path="/register" element={<PublicRoute><Register /></PublicRoute>} />
              <Route path="/forgot-password" element={<PublicRoute><ForgotPassword /></PublicRoute>} />
              <Route path="/reset-password" element={<PublicRoute><ResetPassword /></PublicRoute>} />
              <Route path="/verify-email" element={<VerifyEmail />} />
              <Route path="/track" element={<TrackingLookup />} />
              <Route path="/s/:token" element={<SharedTracking />} />
              <Route path="/s/landed-cost/:token" element={<SharedLandedCost />} />

              {/* Client Portal Routes */}
              <Route path="/client/login" element={<ClientLogin />} />
              <Route path="/client/dashboard" element={<ClientProtectedRoute><ClientDashboard /></ClientProtectedRoute>} />
              <Route path="/client/shipments/:id" element={<ClientProtectedRoute><ClientShipmentDetail /></ClientProtectedRoute>} />

              {/* Protected Routes — Any authenticated user */}
              <Route path="/dashboard" element={<ProtectedRoute><ErrorBoundary><Dashboard /></ErrorBoundary></ProtectedRoute>} />
              <Route path="/simulation" element={<ProtectedRoute><Simulation /></ProtectedRoute>} />
              <Route path="/calculator" element={<ProtectedRoute><Calculator /></ProtectedRoute>} />
              <Route path="/co2" element={<ProtectedRoute><Co2Calculator /></ProtectedRoute>} />
              <Route path="/logistics" element={<ProtectedRoute><LogisticsDashboard /></ProtectedRoute>} />
              <Route path="/ship-tracker" element={<ProtectedRoute><ShipTracker /></ProtectedRoute>} />
              <Route path="/flight-radar" element={<ProtectedRoute><FlightRadar /></ProtectedRoute>} />

              {/* Public tools (no auth required) */}
              <Route path="/douane" element={<CustomsDutyCalculator />} />
              <Route path="/change" element={<CurrencyExchange />} />
              <Route path="/poids-volumetrique" element={<VolumetricWeight />} />
              <Route path="/assurance-cargo" element={<CargoInsurance />} />
              <Route path="/optimisation-itineraire" element={<RouteOptimizer />} />
              <Route path="/documents" element={<ProtectedRoute requiredRole="USER"><DocumentsPage /></ProtectedRoute>} />
              <Route path="/pricing" element={<Pricing />} />
              <Route path="/pricing-comparison" element={<PricingComparison />} />
              <Route path="/faq" element={<Faq />} />
              <Route path="/guide-incoterms" element={<IncotermsGuide />} />
              <Route path="/hubs" element={<Hubs />} />
              <Route path="/cas-usage" element={<UseCases />} />
              <Route path="/cout-erreur-douane" element={<MisclassificationCostCalculator />} />
              <Route path="/developers" element={<Developers />} />
              <Route path="/status" element={<StatusPage />} />
              <Route path="/confidentialite" element={<Privacy />} />
              <Route path="/cgu" element={<Terms />} />
              <Route path="/mentions-legales" element={<LegalNotice />} />

              {/* Protected Routes — USER+ can manage carriers/shipments */}
              <Route path="/carriers" element={<ProtectedRoute requiredRole="USER"><Carriers /></ProtectedRoute>} />
              <Route path="/carrier-bookings" element={<ProtectedRoute requiredRole="USER"><CarrierBookings /></ProtectedRoute>} />
              <Route path="/quotes" element={<ProtectedRoute requiredRole="USER"><Quotes /></ProtectedRoute>} />
              <Route path="/shipments" element={<ProtectedRoute requiredRole="USER"><Shipments /></ProtectedRoute>} />
              <Route path="/providers" element={<ProtectedRoute requiredRole="USER"><Providers /></ProtectedRoute>} />
              <Route path="/providers/health" element={<ProtectedRoute requiredRole="USER"><ProviderHealth /></ProtectedRoute>} />
              <Route path="/notifications" element={<ProtectedRoute><Notifications /></ProtectedRoute>} />
              <Route path="/notification-rules" element={<ProtectedRoute requiredRole="MANAGER"><NotificationRules /></ProtectedRoute>} />
              <Route path="/rate-comparison" element={<ProtectedRoute requiredRole="USER"><RateComparison /></ProtectedRoute>} />
              <Route path="/shipping-rates" element={<ProtectedRoute requiredRole="MANAGER"><ShippingRates /></ProtectedRoute>} />

              {/* Protected Routes — MANAGER+ can manage team/ERP */}
              <Route path="/team" element={<ProtectedRoute requiredRole="USER"><Team /></ProtectedRoute>} />
                <Route path="/erp" element={<ProtectedRoute requiredRole="USER"><ErpSettings /></ProtectedRoute>} />

              {/* Protected Routes — Customs & Trade (MANAGER+) */}
              <Route path="/customs" element={<ProtectedRoute requiredRole="MANAGER"><CustomsDashboard /></ProtectedRoute>} />
              <Route path="/eori" element={<ProtectedRoute requiredRole="MANAGER"><EoriSettings /></ProtectedRoute>} />
              <Route path="/trade-agreements" element={<ProtectedRoute requiredRole="USER"><TradeAgreements /></ProtectedRoute>} />
              <Route path="/declarations" element={<ProtectedRoute requiredRole="MANAGER"><DeclarationsPage /></ProtectedRoute>} />

              {/* Protected tools — P2 Intelligence */}
              <Route path="/landed-cost" element={<ProtectedRoute requiredRole="MANAGER"><LandedCostCalculator /></ProtectedRoute>} />
              <Route path="/hs-classification" element={<ProtectedRoute requiredRole="USER"><HsClassification /></ProtectedRoute>} />

              {/* Protected Routes — P2 Intelligence & Automation */}
              <Route path="/dps" element={<ProtectedRoute requiredRole="MANAGER"><DeniedPartyScreening /></ProtectedRoute>} />

              <Route path="/document-parser" element={<ProtectedRoute requiredRole="MANAGER"><DocumentParser /></ProtectedRoute>} />

              {/* Protected Routes — P3 Optimization */}
              <Route path="/optimization" element={<ProtectedRoute requiredRole="MANAGER"><OptimizationDashboard /></ProtectedRoute>} />

              {/* Protected Routes — P3 Enterprise */}
              <Route path="/carrier-invoices" element={<ProtectedRoute requiredRole="MANAGER"><CarrierInvoicing /></ProtectedRoute>} />
              <Route path="/financials" element={<ProtectedRoute requireAdmin><FinancialReports /></ProtectedRoute>} />
              <Route path="/eta-predictions" element={<ProtectedRoute requiredRole="MANAGER"><EtaPredictions /></ProtectedRoute>} />
              <Route path="/quality" element={<ProtectedRoute requiredRole="MANAGER"><QualityDashboard /></ProtectedRoute>} />

              {/* Protected Routes — P4 Enterprise */}
              <Route path="/approvals" element={<ProtectedRoute requiredRole="MANAGER"><ApprovalWorkflows /></ProtectedRoute>} />
              <Route path="/carbon" element={<ProtectedRoute requiredRole="MANAGER"><CarbonDashboard /></ProtectedRoute>} />
              <Route path="/client-invoices" element={<ProtectedRoute requireAdmin><ClientInvoicing /></ProtectedRoute>} />
              <Route path="/ecommerce" element={<ProtectedRoute requiredRole="MANAGER"><ECommerce /></ProtectedRoute>} />
              <Route path="/groupage" element={<ProtectedRoute requiredRole="MANAGER"><GroupagePage /></ProtectedRoute>} />
              <Route path="/fintech" element={<ProtectedRoute requireAdmin><FintechPage /></ProtectedRoute>} />
              <Route path="/academy" element={<ProtectedRoute><Academy /></ProtectedRoute>} />
              <Route path="/finance" element={<ProtectedRoute requireAdmin><FinanceSupplyChain /></ProtectedRoute>} />
              <Route path="/branches" element={<ProtectedRoute requireAdmin><MultiBranch /></ProtectedRoute>} />
              <Route path="/csrd" element={<ProtectedRoute requireAdmin><CsrdReport /></ProtectedRoute>} />
              <Route path="/branding" element={<ProtectedRoute requireAdmin><BrandingPortal /></ProtectedRoute>} />
              <Route path="/email-intake" element={<ProtectedRoute requireAdmin><EmailIntake /></ProtectedRoute>} />
              <Route path="/orchestration-suggestions" element={<ProtectedRoute requiredRole="MANAGER"><OrchestrationSuggestions /></ProtectedRoute>} />
              <Route path="/api-keys" element={<ProtectedRoute requireAdmin><ApiKeys /></ProtectedRoute>} />
              <Route path="/webhooks" element={<ProtectedRoute requireAdmin><Webhooks /></ProtectedRoute>} />
              <Route path="/company-settings" element={<ProtectedRoute requireAdmin><CompanySettings /></ProtectedRoute>} />
              <Route path="/roles" element={<ProtectedRoute requireAdmin><RoleManagement /></ProtectedRoute>} />
              <Route path="/payment-terms" element={<ProtectedRoute requiredRole="MANAGER"><PaymentTerms /></ProtectedRoute>} />
              <Route path="/taric" element={<ProtectedRoute requiredRole="MANAGER"><TaricData /></ProtectedRoute>} />
              <Route path="/french-fiscal" element={<ProtectedRoute requireAdmin><FrenchFiscal /></ProtectedRoute>} />
              <Route path="/eur1" element={<ProtectedRoute requiredRole="MANAGER"><Eur1Certificates /></ProtectedRoute>} />
              <Route path="/compliance" element={<ProtectedRoute requireAdmin><Compliance /></ProtectedRoute>} />

              {/* Warehousing & Réception (P1) */}
              <Route path="/warehouses" element={<ProtectedRoute requiredRole="USER"><Warehouses /></ProtectedRoute>} />
              <Route path="/warehouses/:id" element={<ProtectedRoute requiredRole="USER"><WarehouseDetail /></ProtectedRoute>} />
              <Route path="/inventory-items" element={<ProtectedRoute requiredRole="USER"><InventoryItems /></ProtectedRoute>} />
              <Route path="/inventory" element={<ProtectedRoute requiredRole="USER"><Inventory /></ProtectedRoute>} />
              <Route path="/receivings" element={<ProtectedRoute requiredRole="USER"><Receiving /></ProtectedRoute>} />
              <Route path="/scan-receiving" element={<ProtectedRoute requiredRole="USER"><ScanReceiving /></ProtectedRoute>} />

              {/* Protected Routes — ADMIN only */}
              <Route path="/audit" element={<ProtectedRoute requireAdmin><AuditLog /></ProtectedRoute>} />
              <Route path="/clients" element={<ProtectedRoute requireAdmin><Clients /></ProtectedRoute>} />
              <Route path="/shared-links" element={<ProtectedRoute requireAdmin><SharedLinks /></ProtectedRoute>} />
              <Route path="/billing" element={<ProtectedRoute requireAdmin><Billing /></ProtectedRoute>} />

              {/* Catch all — 404 personnalisée */}
              <Route path="*" element={<NotFound />} />
      </Routes>
    </Suspense>
  );
}

function AppShell() {
  const { pathname } = useLocation();
  const isClientRoute = pathname.startsWith('/client') || pathname.startsWith('/s/');
  const isFullScreenRoute = pathname === '/ship-tracker' || pathname === '/flight-radar';
  const isAuthenticated = useAuthStore((s) => s.token !== null && s.user !== null);
  const isAppRoute = isAuthenticated && !isClientRoute && !isFullScreenRoute && !PUBLIC_PATHS.has(pathname);
  useProfileRefresh();

  return (
    <>
      <GoogleAnalytics />
      {!isClientRoute && <CookieConsent />}
      {isAppRoute ? (
        <AppLayout>
          <OfflineIndicator />
          <AppRoutes />
          <PWAInstallPrompt />
        </AppLayout>
      ) : (
        <div className={`flex flex-col ${isClientRoute || isFullScreenRoute ? '' : 'min-h-screen'}`}>
          {!isClientRoute && !isFullScreenRoute && <OfflineIndicator />}
          {!isClientRoute && !isFullScreenRoute && <Navbar />}
          <main className={`${isClientRoute || isFullScreenRoute ? '' : 'flex-grow pb-16 md:pb-0'}`}>
            <AppRoutes />
          </main>
          {!isClientRoute && !isFullScreenRoute && <Footer />}
          {!isClientRoute && !isFullScreenRoute && <PWAInstallPrompt />}
        </div>
      )}
    </>
  );
}

function AppContent() {
  return (
    <BrowserRouter>
      <AppShell />
    </BrowserRouter>
  );
}

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AppContent />
    </QueryClientProvider>
  );
}

export default App;
