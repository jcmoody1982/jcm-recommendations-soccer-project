export interface Fixture {
  id: number;
  seasonId: number;
  homeTeamId: number;
  awayTeamId: number;
  homeTeamName: string;
  awayTeamName: string;
  dateUnix: number;
  stadium: string;
  status: string;
  gameWeek: number;
  refereeId?: number;
  refereeName?: string;
}

export interface FixtureOdds {
  fixtureId: number;
  oddsFt1: number;
  oddsFtX: number;
  oddsFt2: number;
  oddsFtOver25: number;
  oddsFtUnder25: number;
  oddsBttsYes: number;
  oddsBttsNo: number;
}

export interface League {
  currentSeasonId: number;
  name: string;
  country: string;
  image: string;
}

export interface Team {
  id: number;
  name: string;
  country: string;
  image?: string;
  stadiumName?: string;
}
