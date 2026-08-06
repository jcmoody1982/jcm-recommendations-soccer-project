import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from './contexts/ThemeContext';
import { ShortlistProvider } from './contexts/ShortlistContext';
import { AuthProvider } from './contexts/AuthContext';
import MainLayout from './layouts/MainLayout';
import RequireAuth from './components/RequireAuth';
import { Dashboard, FixtureDetail, Login, Recommendations, Results, Shortlist } from './pages';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 1000 * 60 * 5,
      retry: 1,
    },
  },
});

function App() {
  return (
    <ThemeProvider>
      <AuthProvider>
        <ShortlistProvider>
          <QueryClientProvider client={queryClient}>
            <BrowserRouter>
              <Routes>
                <Route path="/" element={<Login />} />
                <Route
                  element={
                    <RequireAuth>
                      <MainLayout />
                    </RequireAuth>
                  }
                >
                  <Route path="recommendations" element={<Recommendations />} />
                  <Route path="shortlist" element={<Shortlist />} />
                  <Route path="results" element={<Results />} />
                  <Route path="fixtures" element={<Dashboard />} />
                  <Route path="fixtures/:fixtureId" element={<FixtureDetail />} />
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
