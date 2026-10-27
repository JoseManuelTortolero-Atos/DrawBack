package net.atos.mev.calendarcalculator.generatepositions;

import java.util.ArrayList;
import java.util.Random;

import net.atos.mev.calendarcalculator.Environment;
import net.atos.mev.calendarcalculator.Match;
import net.atos.mev.calendarcalculator.Tournament;
import net.atos.mev.calendarcalculator.checks.HalfMatchesAtHomeCheck;

public class BacktrackingBruteforceGenPosAlg {

	Environment env;
	String lastTournamentFound = "";
	HalfMatchesAtHomeCheck hMATHChecker = new HalfMatchesAtHomeCheck();
	ArrayList<String> resultTourList = new ArrayList<String>();
	Random rand = new Random();
	//int[] forceRepetitions = {0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0};
//	int[] forceRepetitions = {0,0,0,0,0,0,0,1,1,1,1,1,1,1,0};
	int[] forceRepetitions = {0,0,1,1,0};

	public Tournament execute() {
		Tournament result = new Tournament(env);
		result.rounds[0].fillWithFirstRoundTransposedPos();
		int totalAmountOfMatches = (env.nTeams+1)*(env.nTeams+1);
		boolean[] usedMatches = new boolean[totalAmountOfMatches];
		int[] teamRepeatsOfHomeOrAway = new int[env.nTeams+1];
		for(Match m: result.rounds[0].matches){
			usedMatches[m.getPosNoOrderId()]= true;
		}

		boolean [] newUsedPosInRound  = new boolean[env.nTeams+1];
		boolean foundResult = recursiveGenerateNextMatchPos(result, 1, 0, usedMatches, newUsedPosInRound, resultTourList, teamRepeatsOfHomeOrAway, 0, 0);
		System.out.println("Found results: " + resultTourList.size());
		if(!foundResult) {
			System.out.println("RESULT NOT FOUND");
		}

		result = Tournament.readFromLine(env, lastTournamentFound);
		result.generateRandomMapPosToTeams();
		result.applyMapPosToTeams();
		return result;
	}

	boolean recursiveGenerateNextMatchPos(Tournament tour, int currentRound, int currentMatch, boolean[] usedMatches, boolean [] usedPosInRound, ArrayList<String> resultTourList, int[] teamRepeatsOfHomeOrAway, int currentRoundRepeatsHome, int currentRoundRepeatsAway){
		boolean foundResult = false;

		if(currentMatch < env.matchesPerRound) {
			// Next match inside the round
			int firstHomeTeam=1;
			if(currentMatch>0) {
				int lastHome = tour.rounds[currentRound].matches[currentMatch-1].getPos1();
				int lastAway = tour.rounds[currentRound].matches[currentMatch-1].getPos2();
				if(lastHome < lastAway) {
					firstHomeTeam = lastHome+1;
				} else {
					firstHomeTeam = lastAway+1;
				}
				while(firstHomeTeam < env.nTeams && usedPosInRound[firstHomeTeam]) {
					firstHomeTeam++;
				}
			}
			for(int firstTeam=firstHomeTeam; firstTeam<env.nTeams && !foundResult; firstTeam++) {
				if(usedPosInRound[firstTeam]==false) {
					usedPosInRound[firstTeam]=true;
					for(int secondTeam=firstTeam+1; secondTeam<env.nTeams+1 && !foundResult; secondTeam++) {
						if(usedPosInRound[secondTeam]==false) {
							usedPosInRound[secondTeam]=true;
							Match m = new Match(firstTeam, secondTeam, env);
							int homeMatchesBefore1 = tour.lookupTwoHomeMatchesBefore(firstTeam, currentRound);
							int homeMatchesBefore2 = tour.lookupTwoHomeMatchesBefore(secondTeam, currentRound);
							int awayMatchesBefore1 = tour.lookupTwoAwayMatchesBefore(firstTeam, currentRound);
							int awayMatchesBefore2 = tour.lookupTwoAwayMatchesBefore(secondTeam, currentRound);
							if((homeMatchesBefore1 < 2 || homeMatchesBefore2 < 2) && (awayMatchesBefore1 < 2 || awayMatchesBefore2 < 2) ) {
								if(homeMatchesBefore2 < homeMatchesBefore1) {
									m.setPos1(secondTeam);
									m.setPos2(firstTeam);
								}
								if(usedMatches[m.getPosNoOrderId()]==false) {
									int repeatingHome = 0;
									int repeatingAway = 0;
									if(m.getPos1()==firstTeam && homeMatchesBefore1>0) {
										repeatingHome=firstTeam;
									}
									if(m.getPos1()==secondTeam && homeMatchesBefore2>0) {
										repeatingHome=secondTeam;
									}
									if(m.getPos2()==firstTeam && awayMatchesBefore1>0) {
										repeatingAway=firstTeam;
									}
									if(m.getPos2()==secondTeam && awayMatchesBefore2>0) {
										repeatingAway=secondTeam;
									}
									if(repeatingHome > 0) {
										teamRepeatsOfHomeOrAway[repeatingHome]++;
										currentRoundRepeatsHome++;
									}
									if(repeatingAway > 0) {
										teamRepeatsOfHomeOrAway[repeatingAway]++;
										currentRoundRepeatsAway++;
									}
									if(teamRepeatsOfHomeOrAway[repeatingHome] < 2 && teamRepeatsOfHomeOrAway[repeatingAway] <2) {
										if(currentRoundRepeatsHome <2 && currentRoundRepeatsAway <2) {
											tour.rounds[currentRound].matches[currentMatch]=m;
											usedMatches[m.getPosNoOrderId()]=true;
											//if(rand.nextInt(100)<90) {
											foundResult=recursiveGenerateNextMatchPos(tour, currentRound, currentMatch+1, usedMatches, usedPosInRound, resultTourList, teamRepeatsOfHomeOrAway, currentRoundRepeatsHome, currentRoundRepeatsAway);
											//}
											usedMatches[m.getPosNoOrderId()]=false;
										}
									}
									if(repeatingHome > 0) {
										teamRepeatsOfHomeOrAway[repeatingHome]--;
										currentRoundRepeatsHome--;
									}
									if(repeatingAway > 0) {
										teamRepeatsOfHomeOrAway[repeatingAway]--;
										currentRoundRepeatsAway--;
									}
								}
							}
							usedPosInRound[secondTeam]=false;
						}
					}					
					usedPosInRound[firstTeam]=false;
				}
			}

		} else if(currentRound < tour.rounds.length-1 && currentMatch == env.matchesPerRound){
			if(forceRepetitions.length != env.nRounds || currentRoundRepeatsHome == forceRepetitions[currentRound]) {
				// Need to start the next round
				boolean [] newUsedPosInRound  = new boolean[env.nTeams+1];
				foundResult=recursiveGenerateNextMatchPos(tour, currentRound+1, 0, usedMatches, newUsedPosInRound, resultTourList, teamRepeatsOfHomeOrAway, 0, 0);
			}
		} else if(currentRound == tour.rounds.length-1 && currentMatch == env.matchesPerRound){
			if(hMATHChecker.check(tour)) {
				// We have found the final result!
				lastTournamentFound = tour.toStringOneLine();
				resultTourList.add(lastTournamentFound);
				//if(resultTourList.size()%1000==0) {
				System.out.println(lastTournamentFound);
				//}
			}
			return false;
		}

		return foundResult;
	}

	public static void main(String[] args) {
		Environment env = new Environment();
		env.loadEnvironment(net.atos.mev.calendarcalculator.compat.DatabaseBatchSupport.selector(args));
		//		env.loadEnvironment("Spain1.properties");
		BacktrackingBruteforceGenPosAlg alg = new BacktrackingBruteforceGenPosAlg();
		alg.env = env;
		long before = System.currentTimeMillis();
		Tournament result = alg.execute();
		long after = System.currentTimeMillis();
		long elapsedTime = after - before;
		System.out.println("elapsedTime="+elapsedTime);
		System.out.println(result.toStringPos());
		System.out.println(result.toStringNames());
	}

	public int[] getForceRepetitions() {
		return forceRepetitions;
	}

	public void setForceRepetitions(int[] forceRepetitions) {
		this.forceRepetitions = forceRepetitions;
	}

}
