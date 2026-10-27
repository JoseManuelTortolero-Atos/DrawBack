package net.atos.mev.calendarcalculator.checks;

import java.util.Arrays;

import net.atos.mev.calendarcalculator.Environment;
import net.atos.mev.calendarcalculator.Match;
import net.atos.mev.calendarcalculator.Round;
import net.atos.mev.calendarcalculator.Team;
import net.atos.mev.calendarcalculator.Tournament;

public class LimitAgainstTeamsInRoundsCheck implements Check {
	
	Team[] separateTeams;
	int[] posSeparateTeams;
	int limit=0;
	int[] roundsOfMatches;
	
	public LimitAgainstTeamsInRoundsCheck(int limit, Team[] teams, int[] roundsOfMatches) {
		this.separateTeams = teams;
		this.limit = limit;
		posSeparateTeams=new int[teams.length];
		this.roundsOfMatches = roundsOfMatches;
	}

	@Override
	public boolean check(Tournament tour) {
		// Find the pos assigned to each team
		for(int j=0; j<separateTeams.length; j++) {
			posSeparateTeams[j] = tour.mapPosToTeams.getKeyOfTeam(separateTeams[j]);
			if(posSeparateTeams[j] == -1) {
//				System.out.println("------------- NOT ALL TEAMS ASSIGNED -------------");
				return true;
			}
		}
//		System.out.println("------------- RUN CHECK, ALL TEAMS ASSIGNED -------------");
		Arrays.sort(posSeparateTeams);
		Arrays.sort(roundsOfMatches);
		// Go through all the teams checking that they don't play too many times against the international teams in the specific rounds
		for(int nTeam=1; nTeam<=tour.env.nTeams; nTeam++) {
			if(Arrays.binarySearch(posSeparateTeams, nTeam) < 0) {
				int foundMatches=0;
				for(int nRound = 0; nRound < tour.rounds.length; nRound++) {
					if(Arrays.binarySearch(roundsOfMatches, nRound) >= 0) {
						Round currentRound = tour.rounds[nRound];
//						System.out.println("------ checking round "+nRound + ", ignoring rounds " + Arrays.toString(roundsOfMatches));
						for(Match m: currentRound.matches) {
							if(m.getPos1() == nTeam) {
								if(Arrays.binarySearch(posSeparateTeams, m.getPos2()) >= 0) {
									foundMatches++;
								}
							} else if(m.getPos2() == nTeam) {
								if(Arrays.binarySearch(posSeparateTeams, m.getPos1()) >= 0) {
									foundMatches++;
								}
							}
						}
					}
				}
				for(int nRound = 0; nRound < tour.rounds.length; nRound++) {
					if(Arrays.binarySearch(roundsOfMatches, nRound + tour.env.nRounds) >= 0) {
						Round currentRound = tour.rounds[nRound];
//						System.out.println("------ checking round "+(nRound + tour.env.nRounds) + ", ignoring rounds " + Arrays.toString(roundsOfMatches));
						for(Match m: currentRound.matches) {
							if(m.getPos1() == nTeam) {
								if(Arrays.binarySearch(posSeparateTeams, m.getPos2()) >= 0) {
									foundMatches++;
								}
							} else if(m.getPos2() == nTeam) {
								if(Arrays.binarySearch(posSeparateTeams, m.getPos1()) >= 0) {
									foundMatches++;
								}
							}
						}
					}
				}
//				System.out.println("------ Team " + nTeam + ", foundMatches="+foundMatches);
				if(foundMatches>limit) {
					return false;
				}
			}
		}

		return true;
	}

	public static void main(String[] args) {
		Environment env = new Environment();
		env.loadEnvironment(net.atos.mev.calendarcalculator.compat.DatabaseBatchSupport.selector(args));
		Tournament t1 = Tournament.readFromLine(env, "010203040506040602050103050403020601030502060104040206030501");
		Team team1 = env.teams[0];
		Team team2 = env.teams[1];
		t1.mapPosToTeams.put(1, team1);
		t1.mapPosToTeams.put(2, team2);
		Team[] pair1 = {team1, team2};
		int[] rounds1 = {2,3};
		int[] rounds2 = {2,4};
		LimitAgainstTeamsInRoundsCheck checker1 = new LimitAgainstTeamsInRoundsCheck(1, pair1, rounds1);
		LimitAgainstTeamsInRoundsCheck checker2 = new LimitAgainstTeamsInRoundsCheck(1, pair1, rounds2);
		LimitAgainstTeamsInRoundsCheck checker3 = new LimitAgainstTeamsInRoundsCheck(2, pair1, rounds1);
		LimitAgainstTeamsInRoundsCheck checker4 = new LimitAgainstTeamsInRoundsCheck(2, pair1, rounds2);
		System.out.println("LimitAgainstTeamsInRoundsCheck t1=" + checker1.check(t1));
		System.out.println("LimitAgainstTeamsInRoundsCheck t2=" + checker2.check(t1));
		System.out.println("LimitAgainstTeamsInRoundsCheck t3=" + checker3.check(t1));
		System.out.println("LimitAgainstTeamsInRoundsCheck t4=" + checker4.check(t1));

	}

	public Team[] getSeparateTeams() {
		return separateTeams;
	}

	public void setSeparateTeams(Team[] separateTeams) {
		this.separateTeams = separateTeams;
	}

}
