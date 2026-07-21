import axios from 'axios';
import type { Recommendation, RecommendationSummary, RecommendationType, Fixture, League } from '../types';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api';

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

export const recommendationService = {
  getAll: async (daysAhead = 7): Promise<Recommendation[]> => {
    const response = await api.get<Recommendation[]>('/recommendations', {
      params: { daysAhead },
    });
    return response.data;
  },

  getStrong: async (daysAhead = 7): Promise<Recommendation[]> => {
    const response = await api.get<Recommendation[]>('/recommendations/strong', {
      params: { daysAhead },
    });
    return response.data;
  },

  getByFixture: async (fixtureId: number): Promise<Recommendation[]> => {
    const response = await api.get<Recommendation[]>(`/recommendations/fixture/${fixtureId}`);
    return response.data;
  },

  getByType: async (type: RecommendationType, daysAhead = 7): Promise<Recommendation[]> => {
    const response = await api.get<Recommendation[]>(`/recommendations/type/${type}`, {
      params: { daysAhead },
    });
    return response.data;
  },

  getBtts: async (daysAhead = 7): Promise<Recommendation[]> => {
    const response = await api.get<Recommendation[]>('/recommendations/btts', {
      params: { daysAhead },
    });
    return response.data;
  },

  getOverGoals: async (daysAhead = 7): Promise<Recommendation[]> => {
    const response = await api.get<Recommendation[]>('/recommendations/over-goals', {
      params: { daysAhead },
    });
    return response.data;
  },

  getValueBets: async (daysAhead = 7): Promise<Recommendation[]> => {
    const response = await api.get<Recommendation[]>('/recommendations/value-bets', {
      params: { daysAhead },
    });
    return response.data;
  },

  getSummary: async (daysAhead = 7): Promise<RecommendationSummary> => {
    const response = await api.get<RecommendationSummary>('/recommendations/summary', {
      params: { daysAhead },
    });
    return response.data;
  },

  getGrouped: async (daysAhead = 7): Promise<Record<RecommendationType, Recommendation[]>> => {
    const response = await api.get<Record<RecommendationType, Recommendation[]>>(
      '/recommendations/grouped',
      { params: { daysAhead } }
    );
    return response.data;
  },
};

export const fixtureService = {
  getUpcoming: async (): Promise<Fixture[]> => {
    const response = await api.get<Fixture[]>('/fixtures');
    return response.data;
  },

  getById: async (id: number): Promise<Fixture> => {
    const response = await api.get<Fixture>(`/fixtures/${id}`);
    return response.data;
  },
};

export const leagueService = {
  getAll: async (): Promise<League[]> => {
    const response = await api.get<League[]>('/leagues');
    return response.data;
  },
};

export default api;
