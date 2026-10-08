import { BrowserRouter, Navigate, Route, Routes, useLocation, useParams } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from './contexts/ThemeContext';
import { ShortlistProvider } from './contexts/ShortlistContext';
import { AuthProvider } from './contexts/AuthContext';
import MainLayout from './layouts/MainLayout';
import RequireAuth from './components/RequireAuth';
import { Dashboard, FixtureDetail, Login, Recommendations, Results, Shortlist, UsFootball } from './pages';
import {
  SOCCER_FIXTURES,
  SOCCER_RECOMMENDATIONS,
  SOCCER_RESULTS,
  SOCCER_SHORTLIST,
} from './utils/sport';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 1000 * 60 * 5,
      retry: 1,
    },
  },
});

function LegacyRedirect({ to }: { to: string }) {
  const location = useLocation();
  return <Navigate to={{ pathname: to, search: location.search }} replace />;
}

function LegacyFixtureRedirect() {
  const { fixtureId } = useParams();
  const location = useLocation();
  return (
    <Navigate
      to={{ pathname: `${SOCCER_FIXTURES}/${fixtureId}`, search: location.search }}
      replace
    />
  );
}

function App() {
  return (
    <ThemeProvider>
      <AuthProvider>
        <ShortlistProvider>
          <QueryClientProvider client={queryClient}>
            <BrowserRouter>
              <Routes>
                <Route path="/" element={<Login />} />
                <Route path="recommendations" element={<LegacyRedirect to={SOCCER_RECOMMENDATIONS} />} />
                <Route path="shortlist" element={<LegacyRedirect to={SOCCER_SHORTLIST} />} />
                <Route path="results" element={<LegacyRedirect to={SOCCER_RESULTS} />} />
                <Route path="fixtures/:fixtureId" element={<LegacyFixtureRedirect />} />
                <Route path="fixtures" element={<LegacyRedirect to={SOCCER_FIXTURES} />} />
                <Route
                  element={
                    <RequireAuth>
                      <MainLayout />
                    </RequireAuth>
                  }
                >
                  <Route path="soccer/recommendations" element={<Recommendations />} />
                  <Route path="soccer/shortlist" element={<Shortlist />} />
                  <Route path="soccer/results" element={<Results />} />
                  <Route path="soccer/fixtures" element={<Dashboard />} />
                  <Route path="soccer/fixtures/:fixtureId" element={<FixtureDetail />} />
                  <Route path="us-football" element={<UsFootball />} />
                </Route>
                <Route path="*" element={<Navigate to="/" replace />} />
              </Routes>
            </BrowserRouter>
          </QueryClientProvider>
        </ShortlistProvider>
      </AuthProvider>
    </ThemeProvider>
  );
}

export default App;
