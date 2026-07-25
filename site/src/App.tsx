import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from './contexts/ThemeContext';
import { ShortlistProvider } from './contexts/ShortlistContext';
import MainLayout from './layouts/MainLayout';
import { Dashboard, Recommendations, Shortlist } from './pages';

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
      <ShortlistProvider>
        <QueryClientProvider client={queryClient}>
          <BrowserRouter>
            <Routes>
              <Route path="/" element={<MainLayout />}>
                <Route index element={<Recommendations />} />
                <Route path="shortlist" element={<Shortlist />} />
                <Route path="fixtures" element={<Dashboard />} />
              </Route>
            </Routes>
          </BrowserRouter>
        </QueryClientProvider>
      </ShortlistProvider>
    </ThemeProvider>
  );
}

export default App;
