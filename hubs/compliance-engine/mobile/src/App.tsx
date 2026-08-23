import { useEffect } from 'react';
import { BrowserRouter, Routes, Route, Navigate, useLocation, Link } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Capacitor } from '@capacitor/core';
import { SplashScreen } from '@capacitor/splash-screen';
import { StatusBar, Style } from '@capacitor/status-bar';
import { LayoutDashboard, Package, Bell, Calculator, Grid2x2 } from 'lucide-react';
import { useAuthStore } from './stores/auth';
import { registerPushNotifications } from './lib/push';
import OfflineBanner from './components/OfflineBanner';
import Login from './pages/Login';
import Dashboard from './pages/Dashboard';
import Shipments from './pages/Shipments';
import ShipmentDetail from './pages/ShipmentDetail';
import Notifications from './pages/Notifications';
import QuickQuote from './pages/QuickQuote';
import ScanDocument from './pages/ScanDocument';
import Approvals from './pages/Approvals';
import More from './pages/More';
import ShipTracker from './pages/ShipTracker';
import FlightRadar from './pages/FlightRadar';
import EtaPredictions from './pages/EtaPredictions';
import CarrierBookings from './pages/CarrierBookings';
import RateComparison from './pages/RateComparison';
import Co2 from './pages/Co2';
import CargoInsurance from './pages/CargoInsurance';
import Receivings from './pages/Receivings';
import ScanReceiving from './pages/ScanReceiving';
import CustomsDuty from './pages/CustomsDuty';
import VolumetricWeight from './pages/VolumetricWeight';
import HsClassification from './pages/HsClassification';
import TradeAgreements from './pages/TradeAgreements';
import Eori from './pages/Eori';
import Dps from './pages/Dps';
import Quality from './pages/Quality';
import NotificationRules from './pages/NotificationRules';
import EmailIntake from './pages/EmailIntake';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, staleTime: 60_000 },
  },
});

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const hasHydrated = useAuthStore((s) => s.hasHydrated);
  const isAuthenticated = useAuthStore((s) => s.token !== null && s.user !== null);

  if (!hasHydrated) return <div className="center-screen text-soft">Chargement…</div>;
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  return <>{children}</>;
}

const TAB_ITEMS = [
  { to: '/', icon: LayoutDashboard, label: 'Accueil' },
  { to: '/shipments', icon: Package, label: 'Expéditions' },
  { to: '/notifications', icon: Bell, label: 'Alertes' },
  { to: '/quick-quote', icon: Calculator, label: 'Devis' },
  { to: '/more', icon: Grid2x2, label: 'Plus' },
];

// Écrans secondaires atteints depuis /more : même convention que /scan et
// /approvals (header avec flèche retour, pas d'onglet actif dédié) -- la
// barre du bas se masque, la navigation se fait par la flèche retour.
const SECONDARY_ROUTES = [
  '/ship-tracker', '/flight-radar', '/eta-predictions', '/carrier-bookings',
  '/rate-comparison', '/co2', '/assurance-cargo', '/receivings', '/scan-receiving',
  '/customs-duty', '/volumetric-weight', '/hs-classification', '/trade-agreements',
  '/eori', '/dps', '/quality', '/notification-rules', '/email-intake',
];

function TabBar() {
  const location = useLocation();
  if (
    location.pathname === '/login' ||
    location.pathname.startsWith('/shipments/') ||
    location.pathname === '/scan' ||
    location.pathname === '/approvals' ||
    SECONDARY_ROUTES.includes(location.pathname)
  ) return null;

  return (
    <nav className="tabbar">
      {TAB_ITEMS.map((item) => {
        const Icon = item.icon;
        const active = location.pathname === item.to;
        return (
          <Link key={item.to} to={item.to} className={`tab-item ${active ? 'active' : ''}`}>
            <Icon size={20} />
            {item.label}
          </Link>
        );
      })}
    </nav>
  );
}

const App = () => {
  const isAuthenticated = useAuthStore((s) => s.token !== null && s.user !== null);

  useEffect(() => {
    if (!Capacitor.isNativePlatform()) return;
    SplashScreen.hide();
    StatusBar.setStyle({ style: Style.Light }).catch(() => {});
  }, []);

  useEffect(() => {
    if (isAuthenticated) registerPushNotifications();
  }, [isAuthenticated]);

  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <div className="app-shell">
          <OfflineBanner />
          <div className="app-content">
            <Routes>
              <Route path="/login" element={<Login />} />
              <Route path="/" element={<ProtectedRoute><Dashboard /></ProtectedRoute>} />
              <Route path="/shipments" element={<ProtectedRoute><Shipments /></ProtectedRoute>} />
              <Route path="/shipments/:id" element={<ProtectedRoute><ShipmentDetail /></ProtectedRoute>} />
              <Route path="/notifications" element={<ProtectedRoute><Notifications /></ProtectedRoute>} />
              <Route path="/quick-quote" element={<ProtectedRoute><QuickQuote /></ProtectedRoute>} />
              <Route path="/scan" element={<ProtectedRoute><ScanDocument /></ProtectedRoute>} />
              <Route path="/approvals" element={<ProtectedRoute><Approvals /></ProtectedRoute>} />
              <Route path="/more" element={<ProtectedRoute><More /></ProtectedRoute>} />
              <Route path="/ship-tracker" element={<ProtectedRoute><ShipTracker /></ProtectedRoute>} />
              <Route path="/flight-radar" element={<ProtectedRoute><FlightRadar /></ProtectedRoute>} />
              <Route path="/eta-predictions" element={<ProtectedRoute><EtaPredictions /></ProtectedRoute>} />
              <Route path="/carrier-bookings" element={<ProtectedRoute><CarrierBookings /></ProtectedRoute>} />
              <Route path="/rate-comparison" element={<ProtectedRoute><RateComparison /></ProtectedRoute>} />
              <Route path="/co2" element={<ProtectedRoute><Co2 /></ProtectedRoute>} />
              <Route path="/assurance-cargo" element={<ProtectedRoute><CargoInsurance /></ProtectedRoute>} />
              <Route path="/receivings" element={<ProtectedRoute><Receivings /></ProtectedRoute>} />
              <Route path="/scan-receiving" element={<ProtectedRoute><ScanReceiving /></ProtectedRoute>} />
              <Route path="/customs-duty" element={<ProtectedRoute><CustomsDuty /></ProtectedRoute>} />
              <Route path="/volumetric-weight" element={<ProtectedRoute><VolumetricWeight /></ProtectedRoute>} />
              <Route path="/hs-classification" element={<ProtectedRoute><HsClassification /></ProtectedRoute>} />
              <Route path="/trade-agreements" element={<ProtectedRoute><TradeAgreements /></ProtectedRoute>} />
              <Route path="/eori" element={<ProtectedRoute><Eori /></ProtectedRoute>} />
              <Route path="/dps" element={<ProtectedRoute><Dps /></ProtectedRoute>} />
              <Route path="/quality" element={<ProtectedRoute><Quality /></ProtectedRoute>} />
              <Route path="/notification-rules" element={<ProtectedRoute><NotificationRules /></ProtectedRoute>} />
              <Route path="/email-intake" element={<ProtectedRoute><EmailIntake /></ProtectedRoute>} />
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </div>
          <TabBar />
        </div>
      </BrowserRouter>
    </QueryClientProvider>
  );
};

export default App;
