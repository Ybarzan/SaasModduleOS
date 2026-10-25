import { lazy, Suspense } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useAuthStore } from './stores/auth';
import AppLayout from './layouts/AppLayout';
import Login from './pages/Login';

// Lazy-loaded pages (code-split chunks), like both compliance-engine/frontend
// and fleet-hub/frontend do for their routed pages.
const Warehouses = lazy(() => import('./pages/Warehouses'));
const WarehouseDetail = lazy(() => import('./pages/WarehouseDetail'));
const InventoryItems = lazy(() => import('./pages/InventoryItems'));
const Inventory = lazy(() => import('./pages/Inventory'));
const Receiving = lazy(() => import('./pages/Receiving'));
const ScanReceiving = lazy(() => import('./pages/ScanReceiving'));
const Settings = lazy(() => import('./pages/Settings'));
const SsoEntry = lazy(() => import('./pages/SsoEntry'));

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

// Mirrors fleet-hub's App.jsx shape: a RequireAuth-wrapped layout Route
// holding the app's children, with Outlet-based nesting (see AppLayout.tsx)
// rather than compliance-engine's per-route <ProtectedRoute> wrapper.
function RequireAuth({ children }: { children: React.ReactNode }) {
  const isAuthenticated = useAuthStore((s) => s.token !== null && s.user !== null);
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  return <>{children}</>;
}

function PublicOnly({ children }: { children: React.ReactNode }) {
  const isAuthenticated = useAuthStore((s) => s.token !== null && s.user !== null);
  if (isAuthenticated) return <Navigate to="/warehouses" replace />;
  return <>{children}</>;
}

function AppRoutes() {
  return (
    <Suspense fallback={<PageLoader />}>
      <Routes>
        <Route path="/login" element={<PublicOnly><Login /></PublicOnly>} />
        {/* Pas de PublicOnly ici : relancer le SSO depuis Praxio doit fonctionner
            même si une session (potentiellement différente) est déjà active. */}
        <Route path="/sso-entry" element={<SsoEntry />} />

        <Route element={<RequireAuth><AppLayout /></RequireAuth>}>
          <Route path="/" element={<Navigate to="/warehouses" replace />} />
          <Route path="/warehouses" element={<Warehouses />} />
          <Route path="/warehouses/:id" element={<WarehouseDetail />} />
          <Route path="/inventory-items" element={<InventoryItems />} />
          <Route path="/inventory" element={<Inventory />} />
          <Route path="/receivings" element={<Receiving />} />
          <Route path="/scan-receiving" element={<ScanReceiving />} />
          <Route path="/settings" element={<Settings />} />
        </Route>

        <Route path="*" element={<Navigate to="/warehouses" replace />} />
      </Routes>
    </Suspense>
  );
}

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AppRoutes />
      </BrowserRouter>
    </QueryClientProvider>
  );
}

export default App;
