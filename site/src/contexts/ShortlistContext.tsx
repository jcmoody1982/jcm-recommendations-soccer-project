import { createContext, useContext, useState, useEffect, type ReactNode } from 'react';

interface ShortlistItem {
  fixtureId: number;
  type: string;
  addedAt: string;
}

interface ShortlistContextType {
  shortlist: ShortlistItem[];
  isShortlisted: (fixtureId: number, type: string) => boolean;
  toggleShortlist: (fixtureId: number, type: string) => void;
  removeFromShortlist: (fixtureId: number, type: string) => void;
  clearShortlist: () => void;
  shortlistCount: number;
}

const ShortlistContext = createContext<ShortlistContextType | undefined>(undefined);

const STORAGE_KEY = 'accabaccaglory-shortlist';

function loadShortlist(): ShortlistItem[] {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored) {
      return JSON.parse(stored);
    }
  } catch (e) {
    console.error('Failed to load shortlist from localStorage:', e);
  }
  return [];
}

function saveShortlist(shortlist: ShortlistItem[]): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(shortlist));
  } catch (e) {
    console.error('Failed to save shortlist to localStorage:', e);
  }
}

export function ShortlistProvider({ children }: { children: ReactNode }) {
  const [shortlist, setShortlist] = useState<ShortlistItem[]>(() => loadShortlist());

  useEffect(() => {
    saveShortlist(shortlist);
  }, [shortlist]);

  const isShortlisted = (fixtureId: number, type: string): boolean => {
    return shortlist.some(item => item.fixtureId === fixtureId && item.type === type);
  };

  const toggleShortlist = (fixtureId: number, type: string): void => {
    if (isShortlisted(fixtureId, type)) {
      removeFromShortlist(fixtureId, type);
    } else {
      setShortlist(prev => [
        ...prev,
        { fixtureId, type, addedAt: new Date().toISOString() }
      ]);
    }
  };

  const removeFromShortlist = (fixtureId: number, type: string): void => {
    setShortlist(prev => 
      prev.filter(item => !(item.fixtureId === fixtureId && item.type === type))
    );
  };

  const clearShortlist = (): void => {
    setShortlist([]);
  };

  return (
    <ShortlistContext.Provider
      value={{
        shortlist,
        isShortlisted,
        toggleShortlist,
        removeFromShortlist,
        clearShortlist,
        shortlistCount: shortlist.length,
      }}
    >
      {children}
    </ShortlistContext.Provider>
  );
}

export function useShortlist() {
  const context = useContext(ShortlistContext);
  if (context === undefined) {
    throw new Error('useShortlist must be used within a ShortlistProvider');
  }
  return context;
}
