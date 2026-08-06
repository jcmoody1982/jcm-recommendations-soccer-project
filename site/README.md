# Soccer Recommendations Website

React-based frontend for the Soccer Recommendations system.

## Tech Stack

- **React 19** - UI framework
- **TypeScript** - Type safety
- **Vite** - Build tool and dev server
- **React Router** - Client-side routing
- **TanStack Query** - Data fetching and caching
- **Axios** - HTTP client
- **CSS Modules** - Scoped styling

## Getting Started

### Prerequisites

- Node.js 18+
- npm or yarn
- Backend API running on `http://localhost:8080`

### Installation

```bash
cd site
npm install
```

### Development

```bash
npm run dev
```

The site will be available at `http://localhost:3000`.

API requests are proxied to `http://localhost:8080` during development.

### Production Build

```bash
npm run build
```

The built files will be in the `dist/` directory.

### Preview Production Build

```bash
npm run preview
```

## Project Structure

```
site/
├── src/
│   ├── components/       # Reusable UI components
│   │   ├── RecommendationCard.tsx
│   │   ├── SummaryCard.tsx
│   │   └── FixtureCard.tsx
│   ├── pages/            # Page components
│   │   ├── Dashboard.tsx
│   │   ├── Recommendations.tsx
│   │   └── Fixtures.tsx
│   ├── layouts/          # Layout components
│   │   └── MainLayout.tsx
│   ├── services/         # API services
│   │   └── api.ts
│   ├── hooks/            # Custom React hooks
│   ├── types/            # TypeScript types
│   │   ├── recommendation.ts
│   │   └── fixture.ts
│   ├── App.tsx           # Root component with routing
│   ├── main.tsx          # Entry point
│   └── index.css         # Global styles
├── public/               # Static assets
├── .env                  # Environment variables
├── .env.example          # Example environment variables
├── vite.config.ts        # Vite configuration
└── package.json
```

## Pages

| Route | Page | Description |
|-------|------|-------------|
| `/` | Login | Brand homepage + beta access password |
| `/recommendations` | Recommendations | Filterable list of all recommendations |
| `/results` | Results | Settled Strong/Moderate picks by day |
| `/shortlist` | Shortlist | Saved picks |
| `/fixtures` | Fixtures | Upcoming fixtures grouped by date |

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `VITE_API_BASE_URL` | Backend API URL | `/api` (uses proxy in dev) |

## Running with Backend

1. Start the Spring Boot backend:
   ```bash
   cd ../web
   mvn spring-boot:run -Dspring-boot.run.profiles=local
   ```

2. Start the frontend dev server:
   ```bash
   cd ../site
   npm run dev
   ```

3. Open `http://localhost:3000` in your browser.

## Scripts

| Command | Description |
|---------|-------------|
| `npm run dev` | Start development server |
| `npm run build` | Build for production |
| `npm run preview` | Preview production build |
| `npm run lint` | Run ESLint |
