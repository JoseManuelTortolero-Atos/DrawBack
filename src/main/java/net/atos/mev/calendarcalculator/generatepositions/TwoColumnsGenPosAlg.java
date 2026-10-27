package net.atos.mev.calendarcalculator.generatepositions;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;
import java.util.Random;
import net.atos.mev.calendarcalculator.Environment;
import net.atos.mev.calendarcalculator.Match;
import net.atos.mev.calendarcalculator.Tournament;
import net.atos.mev.calendarcalculator.checks.HalfMatchesAtHomeCheck;
import net.atos.mev.calendarcalculator.fileio.PositionsFileManager;

public class TwoColumnsGenPosAlg {
	
	private static final int REPETITIONS = 4;//AQ antes 4 para D1 Men
	Random random = new Random();
	Environment env;
	String lastTournamentFound = "";
	HalfMatchesAtHomeCheck hMATHChecker = new HalfMatchesAtHomeCheck();
	// ArrayList<String> resultTourList = new ArrayList<String>();
	int nGeneratedResults = 0;
	Random rand = new Random();
	// int[] forceRepetitions = {0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0};
	// int[] forceRepetitions = {0,0,0,0,0,0,0,1,1,1,1,1,1,1,0};
	// Six teams
	// int[] forceRepetitions = {0,1,0,1,0};
	// Eight teams
	// int[] forceRepetitions = {0,1,0,1,0,1,0};
	// Ten teams
	// int[] forceRepetitions = {0,1,0,1,0,1,0,1,0};
	// Twelve teams
	// int[] forceRepetitions = {0,1,0,1,0,1,0,1,0,1,0};
	// Sixteen teams
	// int[] forceRepetitions = {0,1,0,1,0,1,0,1,1,0,1,0,1,0,0};
	// int[] forceRepetitions = {0,0,0,1,0,1,1,0,1,0,1,0,1,1,0};
	// Eighteen teams
	// int[] forceRepetitions = {0,0,1,0,1,0,1,1,0,1,0,1,0,1,0,1,0};
	// Twenty teams
	//int[] forceRepetitions= {0, 1, 0, 1, 0, 1, 0, 1, 1, 0, 1, 0, 1, 0, 0};
	static int[] defaultForceRepetitions= { 0, 1, 0, 1, 0, 1, 0, 1, 1, 0, 1, 0, 1, 0, 0 };
	int[] forceRepetitions = defaultForceRepetitions;
	static int[] forceRepetitionsD1= {0, 1, 0, 1, 0, 1, 0, 1, 1, 0, 1, 0, 1, 0, 0};
	//static int[] forceRepetitionsCNPFF = { 0, 1, 0, 1, 0, 1, 0, 1, 1, 0, 1, 0, 0 };
	static int[] forceRepetitionsCNPFF = { 0, 1, 0, 1, 0, 1, 0, 1, 1, 0, 0 };
	int[] avoidConsecutive = {};
	// int[] avoidConsecutive = {7,8,9,10};
	// int[] avoidConsecutive = {13,14,15,16};
	// static String environmentFile = "MoroccoDiv1-18teams.properties";
	//static String environmentFileFemD2N = "MoroccoFemDiv2Nord.properties";
	static String environmentFileFemD2 = "MoroccoFemDiv2.properties";
	static String environmentFileFemD1 = "MoroccoFemDiv1.properties";
	static String environmentFileD1 = "MoroccoDiv1.properties";
	static String environmentFileD2 = "MoroccoDiv2v1.properties";

	//static String environmentFile = environmentFileD1;
	static String environmentFile = environmentFileFemD1;
	int[] teamsCurrentInColumnA;
	int[] teamsCurrentInColumnB;
	int[] teamsInitialInColumnA;
	int[] teamsInitialInColumnB;
	int[] roundOfSwitching;
	ArrayList<Integer>[] teamsPendingToPlayBeforeSwitch;
	boolean[] usedMatches;
	boolean showGeneration = false;
	boolean showProgress = true;
	int timesCalculatePossibleMatchesNextRound = 0;
	long initialExecutionTime;
	int[] timesTriedRound;
	int[] timesSuccessfulRound;
	PositionsFileManager fileManager;
	int forbidRepeatsAfterRound = 2;
	boolean randomizeGeneration = true;
	int limitCombinations = 2;
	long numberOfFirstRounds;
	boolean[][][] possibleMatchesCalculated;
	// Modo en memoria (BD): las plantillas se recogen aquí en vez de escribirse en results/
	java.util.List<String> collector;
	int maxPatterns;
	long deadlineMillis;
	java.util.function.BooleanSupplier cancelled = () -> false;

	private static final class StopSearch extends RuntimeException {
		private static final long serialVersionUID = 1L;
		StopSearch() {
			super(null, null, false, false);
		}
	}

	/**
	 * Genera hasta {@code maxPatterns} plantillas de posiciones (formato pos_*.txt) sin tocar disco.
	 * {@code forceRepetitions} debe tener nRounds elementos con nTeams/2-1 unos.
	 */
	public static java.util.List<String> generatePatterns(Environment env, int[] forceRepetitions, int maxPatterns, long timeoutMillis,
			java.util.function.BooleanSupplier cancelled) {
		TwoColumnsGenPosAlg alg = new TwoColumnsGenPosAlg();
		alg.env = env;
		alg.forceRepetitions = forceRepetitions;
		alg.showProgress = false;
		alg.collector = new ArrayList<>();
		alg.maxPatterns = maxPatterns;
		alg.deadlineMillis = System.currentTimeMillis() + timeoutMillis;
		if (cancelled != null) {
			alg.cancelled = cancelled;
		}
		try {
			alg.execute();
		} catch (StopSearch stop) {
			// límite alcanzado
		}
		return alg.collector;
	}

	/** Repeticiones por defecto (las usadas históricamente para 12 y 16 equipos; genérica para el resto). */
	public static int[] defaultForceRepetitions(int nTeams) {
		if (nTeams == 12) {
			return forceRepetitionsCNPFF.clone();
		}
		if (nTeams == 16) {
			return forceRepetitionsD1.clone();
		}
		int[] result = new int[nTeams - 1];
		for (int i = 0, ones = 0; i < result.length && ones < nTeams / 2 - 1; i++) {
			if (i % 2 == 1) {
				result[i] = 1;
				ones++;
			}
		}
		return result;
	}
	
	@SuppressWarnings("unchecked")
	public Tournament execute() {
		Tournament result = new Tournament(env);
		if (collector == null) {
			fileManager = new PositionsFileManager();
			fileManager.prepareOutputTournament(result);
			Properties executionParameters = new Properties();
			executionParameters.put("environmentFile", environmentFile);
			executionParameters.put("forceRepetitions", Arrays.toString(forceRepetitions));
			executionParameters.put("avoidConsecutive", Arrays.toString(avoidConsecutive));
			executionParameters.put("forbidRepeatsAfterRound", String.valueOf(forbidRepeatsAfterRound));
			executionParameters.put("randomizeGeneration", String.valueOf(randomizeGeneration));
			executionParameters.put("limitCombinations", String.valueOf(limitCombinations));
			fileManager.writeExecutionProperties(executionParameters);
		}
		result.rounds[0].fillWithFirstRoundTransposedPos();
		int totalAmountOfMatches = (env.nTeams + 1) * (env.nTeams + 1);
		boolean[][] usedPosInRound = new boolean[env.nRounds][env.nTeams + 1];
		usedMatches = new boolean[totalAmountOfMatches];
		roundOfSwitching = new int[env.nTeams + 1];
		teamsPendingToPlayBeforeSwitch = new ArrayList[env.nTeams + 1];
		teamsCurrentInColumnA = new int[env.nTeams / 2];
		teamsCurrentInColumnB = new int[env.nTeams / 2];
		teamsInitialInColumnA = new int[env.nTeams / 2];
		teamsInitialInColumnB = new int[env.nTeams / 2];
		timesTriedRound = new int[env.nRounds];
		timesSuccessfulRound = new int[env.nRounds];
		int teamsListsIdx = 0;
		numberOfFirstRounds = 1;
		for (int fact = 2; fact <= env.nTeams / 2; fact++) {
			numberOfFirstRounds *= fact;
		}
		for (Match m : result.rounds[0].matches) {
			// usedMatches[m.getPosNoOrderId()]= true;
			teamsInitialInColumnA[teamsListsIdx] = m.getPos1();
			teamsCurrentInColumnA[teamsListsIdx] = m.getPos1();
			teamsInitialInColumnB[teamsListsIdx] = m.getPos2();
			teamsCurrentInColumnB[teamsListsIdx] = m.getPos2();
			teamsPendingToPlayBeforeSwitch[m.getPos1()] = new ArrayList<Integer>();
			teamsPendingToPlayBeforeSwitch[m.getPos2()] = new ArrayList<Integer>();
			teamsListsIdx++;
		}
		// Before switching, a team has to play the teams that were in his column and switched before him, and the team of the other column that won't switch
		teamsPendingToPlayBeforeSwitch[teamsInitialInColumnA[1]].add(teamsInitialInColumnB[0]);
		teamsPendingToPlayBeforeSwitch[teamsInitialInColumnB[1]].add(teamsInitialInColumnA[0]);
		for (int idxToCalculatePreSwitchMatches = 2; idxToCalculatePreSwitchMatches < teamsInitialInColumnA.length; idxToCalculatePreSwitchMatches++) {
			teamsPendingToPlayBeforeSwitch[teamsInitialInColumnA[idxToCalculatePreSwitchMatches]].addAll(teamsPendingToPlayBeforeSwitch[teamsInitialInColumnA[idxToCalculatePreSwitchMatches - 1]]);
			teamsPendingToPlayBeforeSwitch[teamsInitialInColumnA[idxToCalculatePreSwitchMatches]].add(teamsInitialInColumnA[idxToCalculatePreSwitchMatches - 1]);
			teamsPendingToPlayBeforeSwitch[teamsInitialInColumnB[idxToCalculatePreSwitchMatches]].addAll(teamsPendingToPlayBeforeSwitch[teamsInitialInColumnB[idxToCalculatePreSwitchMatches - 1]]);
			teamsPendingToPlayBeforeSwitch[teamsInitialInColumnB[idxToCalculatePreSwitchMatches]].add(teamsInitialInColumnB[idxToCalculatePreSwitchMatches - 1]);
		}
		int repetitionsDone = 0;
		for (int ros = 0; ros < forceRepetitions.length; ros++) {
			if (forceRepetitions[ros] == 1) {
				roundOfSwitching[teamsInitialInColumnA[repetitionsDone + 1]] = ros;
				roundOfSwitching[teamsInitialInColumnB[repetitionsDone + 1]] = ros;
				repetitionsDone++;
			}
		}
		// For the last two teams of each column, there's no switch
		roundOfSwitching[teamsInitialInColumnA[0]] = -1;
		roundOfSwitching[teamsInitialInColumnB[0]] = -1;
		initialExecutionTime = System.currentTimeMillis();
		boolean[][] possibleMatchesNextRound = calculatePossibleMatchesNextRound(result, 0);
		possibleMatchesCalculated = new boolean[env.nRounds * env.nTeams / 2][][];
		for (int pmc1 = 0; pmc1 < possibleMatchesCalculated.length; pmc1++) {
			possibleMatchesCalculated[pmc1] = new boolean[env.nTeams + 1][env.nTeams + 1];
		}
		boolean foundResult = false;
		if (checkAllTeamsHaveAtLeastOnePossibleMatch(possibleMatchesNextRound)) {
			foundResult = recursiveGenerateNextMatchPos(result, 0, 0, usedPosInRound, possibleMatchesNextRound);
		}
		System.out.println("Found results: " + nGeneratedResults);
		if (!foundResult) {
			System.out.println("RESULT NOT FOUND");
		}
		if (collector != null) {
			return null;
		}
		result = Tournament.readFromLine(env, lastTournamentFound);
		result.generateRandomMapPosToTeams();
		result.applyMapPosToTeams();
		return result;
	}
	
	boolean[][] calculatePossibleMatchesNextMatch(Tournament tour, int currentRound, boolean[][] previousPossibleMatches, int nMatch, int homeTeam, int awayTeam) {
		boolean[][] newPossibleMatches = possibleMatchesCalculated[currentRound * env.nTeams / 2 + nMatch];
		for (int i = 1; i < newPossibleMatches.length; i++) {
			for (int j = 1; j < newPossibleMatches[i].length; j++) {
				newPossibleMatches[i][j] = previousPossibleMatches[i][j];
			}
		}
		for (int i2 = 1; i2 < newPossibleMatches.length; i2++) {
			newPossibleMatches[homeTeam][i2] = false;
			newPossibleMatches[i2][homeTeam] = false;
			newPossibleMatches[awayTeam][i2] = false;
			newPossibleMatches[i2][awayTeam] = false;
		}
		boolean simplifyAgain = simplifyPossibleMatches(previousPossibleMatches);
		while (simplifyAgain) {
			System.out.println("Simplifying during round");
			simplifyAgain = simplifyPossibleMatches(previousPossibleMatches);
		}
		return previousPossibleMatches;
	}
	
	boolean[][] calculatePossibleMatchesNextRound(Tournament tour, int nextRound) {
		timesCalculatePossibleMatchesNextRound++;
		if (showProgress && timesCalculatePossibleMatchesNextRound % 1000000 == 0) {
			System.out.println("timesCalculatePossibleMatchesNextRound=" + timesCalculatePossibleMatchesNextRound);
		}
		boolean[][] result = new boolean[env.nTeams + 1][env.nTeams + 1];
		for (int i : teamsCurrentInColumnA) {
			for (int j : teamsCurrentInColumnB) {
				if (!usedMatches[Match.getPosNoOrderId(env, i, j)]) {
					result[i][j] = true;
					result[j][i] = true;
				} else {
					result[i][j] = false;
					result[j][i] = false;
				}
			}
		}
		// Check that if the team has played against one of the avoidConsecutive on the previous round, it doesn't play against another one of them in the switching round
		// This way we avoid playing away against two far away teams in consecutive matches
		for (int notConsec = 1; notConsec <= env.nTeams; notConsec++) {
			if (roundOfSwitching[notConsec] == nextRound) {
				if (Arrays.binarySearch(avoidConsecutive, notConsec) < 0) {
					boolean playedAgainstAvoidConsecutiveTeam = false;
					for (Match m : tour.rounds[nextRound - 1].matches) {
						if (m.getPos1() == notConsec) {
							if (Arrays.binarySearch(avoidConsecutive, m.getPos2()) >= 0) {
								playedAgainstAvoidConsecutiveTeam = true;
							}
						}
						if (m.getPos2() == notConsec) {
							if (Arrays.binarySearch(avoidConsecutive, m.getPos1()) >= 0) {
								playedAgainstAvoidConsecutiveTeam = true;
							}
						}
					}
					if (playedAgainstAvoidConsecutiveTeam) {
						for (int avoidTeam : avoidConsecutive) {
							result[avoidTeam][notConsec] = false;
							result[notConsec][avoidTeam] = false;
						}
					}
				}
			}
		}
		// Check for which positions, it is necessary to play against specific teams before switching column
		for (int checkingSwitchPos = 1; checkingSwitchPos < env.nTeams + 1; checkingSwitchPos++) {
			if (nextRound < roundOfSwitching[checkingSwitchPos]) {
				if (teamsPendingToPlayBeforeSwitch[checkingSwitchPos].size() == roundOfSwitching[checkingSwitchPos] - nextRound) {
					for (int teamCrossingWithSwitch = 1; teamCrossingWithSwitch < env.nTeams + 1; teamCrossingWithSwitch++) {
						// If it's a team that goes before in the same column (therefore it will be switched before)
						// And both teams haven't met yet
						if (teamsPendingToPlayBeforeSwitch[checkingSwitchPos].contains(teamCrossingWithSwitch) && result[checkingSwitchPos][teamCrossingWithSwitch] == true) {
							result[checkingSwitchPos][teamCrossingWithSwitch] = true;
							result[teamCrossingWithSwitch][checkingSwitchPos] = true;
						} else {
							result[checkingSwitchPos][teamCrossingWithSwitch] = false;
							result[teamCrossingWithSwitch][checkingSwitchPos] = false;
						}
					}
				}
			}
		}
		boolean simplifyAgain = simplifyPossibleMatches(result);
		while (simplifyAgain) {
			simplifyAgain = simplifyPossibleMatches(result);
		}
		return result;
	}
	
	// If a team can only play against one team, we make sure that the other team of the match doesn't have other matches
	public boolean simplifyPossibleMatches(boolean[][] possibleMatches) {
		boolean foundChange = false;
		for (int i = 1; i <= env.nTeams; i++) {
			int nPossibleMatches = 0;
			int lastPossibleRival = 0;
			for (int j = 1; j <= env.nTeams; j++) {
				if (possibleMatches[i][j] && i != j) {
					nPossibleMatches++;
					lastPossibleRival = j;
				}
			}
			if (nPossibleMatches == 1) {
				for (int changingJ = 1; changingJ <= env.nTeams; changingJ++) {
					if (i != changingJ && lastPossibleRival != changingJ && possibleMatches[lastPossibleRival][changingJ]) {
						foundChange = true;
						possibleMatches[lastPossibleRival][changingJ] = false;
						possibleMatches[changingJ][lastPossibleRival] = false;
					}
				}
			}
		}
		return foundChange;
	}
	
	boolean checkAllTeamsHaveAtLeastOnePossibleMatch(boolean[][] possibleMatches) {
		boolean result = true;
		for (int t1 = 1; t1 <= env.nTeams; t1++) {
			boolean foundPossibleMatch = false;
			for (int t2 = 1; t2 <= env.nTeams && !foundPossibleMatch; t2++) {
				if (t1 != t2 && possibleMatches[t1][t2] == true) {
					foundPossibleMatch = true;
				}
			}
			if (!foundPossibleMatch) {
				return false;
			}
		}
		return result;
	}
	
	boolean recursiveGenerateNextMatchPos(Tournament tour, int currentRound, int currentMatch, boolean[][] usedPosInRound, boolean[][] possibleMatches) {
		boolean foundResult = false;
		if (currentMatch < env.matchesPerRound) {
			// Next match inside the round
			int[] teamsHomeThisRound = currentRound % 2 == 0 ? teamsCurrentInColumnA : teamsCurrentInColumnB;
			int[] teamsAwayThisRound = currentRound % 2 == 0 ? teamsCurrentInColumnB : teamsCurrentInColumnA;
			int[] orderToCheckAwayTeams = new int[teamsHomeThisRound.length];
			for (int i = 0; i < orderToCheckAwayTeams.length; i++) {
				orderToCheckAwayTeams[i] = i;
			}
			if (randomizeGeneration) {
				randomizeArray(orderToCheckAwayTeams);
			}
			// We force the home teams to be set in order, so we don't have calendars which are the same with the matches in different order in the same round
			int homeTeam = teamsHomeThisRound[currentMatch];
			if (usedPosInRound[currentRound][homeTeam] == false) {
				usedPosInRound[currentRound][homeTeam] = true;
				int triedAwayTeams = 0;
				for (int arrayAwayTeam = 0; arrayAwayTeam < teamsAwayThisRound.length && (triedAwayTeams < limitCombinations || currentRound < forbidRepeatsAfterRound) && (!foundResult || currentRound < forbidRepeatsAfterRound); arrayAwayTeam++) {
					int awayTeam = teamsAwayThisRound[orderToCheckAwayTeams[arrayAwayTeam]];
					if (usedPosInRound[currentRound][awayTeam] == false) {
						usedPosInRound[currentRound][awayTeam] = true;
						Match m = new Match(homeTeam, awayTeam, env);
						if (usedMatches[m.getPosNoOrderId()] == false && possibleMatches[homeTeam][awayTeam]) {
							boolean[][] newPossibleMatches = possibleMatches;
							// We don't take advantage of checking possible matches after every match, because it doesn't improve performance, and it never simplifies
							// boolean[][] newPossibleMatches = calculatePossibleMatchesNextMatch(tour, currentRound, possibleMatches, currentMatch, homeTeam, awayTeam);
							if (checkAllTeamsHaveAtLeastOnePossibleMatch(newPossibleMatches)) {
								usedMatches[m.getPosNoOrderId()] = true;
								int idxInHomePendingList = teamsPendingToPlayBeforeSwitch[homeTeam].indexOf(awayTeam);
								if (idxInHomePendingList >= 0) {
									teamsPendingToPlayBeforeSwitch[homeTeam].remove(idxInHomePendingList);
								}
								int idxInAwayPendingList = teamsPendingToPlayBeforeSwitch[awayTeam].indexOf(homeTeam);
								if (idxInAwayPendingList >= 0) {
									teamsPendingToPlayBeforeSwitch[awayTeam].remove(idxInAwayPendingList);
								}
								tour.rounds[currentRound].matches[currentMatch] = m;
								boolean newFoundResult = recursiveGenerateNextMatchPos(tour, currentRound, currentMatch + 1, usedPosInRound, newPossibleMatches);
								triedAwayTeams++;
								foundResult = foundResult || newFoundResult;
								if (idxInHomePendingList >= 0) {
									teamsPendingToPlayBeforeSwitch[homeTeam].add(awayTeam);
								}
								if (idxInAwayPendingList >= 0) {
									teamsPendingToPlayBeforeSwitch[awayTeam].add(homeTeam);
								}
								tour.rounds[currentRound].matches[currentMatch] = null;
								usedMatches[m.getPosNoOrderId()] = false;
							}
						}
						usedPosInRound[currentRound][awayTeam] = false;
					}
				}
				usedPosInRound[currentRound][homeTeam] = false;
			}
		} else if (currentRound < tour.rounds.length - 1 && currentMatch == env.matchesPerRound) {
			if (collector != null && (System.currentTimeMillis() > deadlineMillis || cancelled.getAsBoolean())) {
				throw new StopSearch();
			}
			// Prepare next round with changes or teams or not
			if (forceRepetitions.length == env.nRounds) {
				int newRound = currentRound + 1;
				timesTriedRound[newRound]++;
				if (forceRepetitions[newRound] == 0) {
					boolean[][] possibleMatchesNextRound = calculatePossibleMatchesNextRound(tour, newRound);
					if (checkAllTeamsHaveAtLeastOnePossibleMatch(possibleMatchesNextRound)) {
						printRoundsStatus(newRound);
						foundResult = recursiveGenerateNextMatchPos(tour, newRound, 0, usedPosInRound, possibleMatchesNextRound);
					}
				} else if (forceRepetitions[newRound] == 1) {
					// We force that we take the teams from column A in order in order to avoid making calendars that are permutations of the numbers
					boolean foundTeamToSwitchFromA = false;
					for (int idxColumnA = 0; idxColumnA < teamsCurrentInColumnA.length && !foundTeamToSwitchFromA; idxColumnA++) {
						if (currentRound < forbidRepeatsAfterRound) {
							foundResult = false;
						}
						// If it's a team that he have already changed of column
						int teamChangingFromA = teamsCurrentInColumnA[idxColumnA];
						if (roundOfSwitching[teamChangingFromA] == newRound) {
							foundTeamToSwitchFromA = true;
							boolean foundTeamToSwitchFromB = false;
							// We have checked that teamChangingFromA can be changed of column, now let's find another one in column B
							for (int idxColumnB = 0; idxColumnB < teamsCurrentInColumnB.length && !foundTeamToSwitchFromB; idxColumnB++) {
								// If it's a team that he have already changed of column
								int teamChangingFromB = teamsCurrentInColumnB[idxColumnB];
								if (roundOfSwitching[teamChangingFromB] == newRound) {
									foundTeamToSwitchFromB = true;
									// Then we can interchange from columns teams teamChangingFromA and teamChangingFromB
									teamsCurrentInColumnA[idxColumnA] = teamChangingFromB;
									teamsCurrentInColumnB[idxColumnB] = teamChangingFromA;
									boolean[][] possibleMatchesNextRound = calculatePossibleMatchesNextRound(tour, newRound);
									if (checkAllTeamsHaveAtLeastOnePossibleMatch(possibleMatchesNextRound)) {
										printRoundsStatus(newRound);
										foundResult = recursiveGenerateNextMatchPos(tour, newRound, 0, usedPosInRound, possibleMatchesNextRound);
									}
									teamsCurrentInColumnA[idxColumnA] = teamChangingFromA;
									teamsCurrentInColumnB[idxColumnB] = teamChangingFromB;
								}
							}
						}
					}
				}
				if (foundResult) {
					timesSuccessfulRound[newRound]++;
				}
				if (showProgress && timesCalculatePossibleMatchesNextRound % 10000 == 0) {
					long percent = timesTriedRound[1] * 100 / numberOfFirstRounds;
					long currentTimestamp = System.currentTimeMillis();
					long elapsed = currentTimestamp - initialExecutionTime;
					double progressLeft = Double.valueOf(numberOfFirstRounds) / Double.valueOf(timesTriedRound[1]);
					long estimatedTotal = new Double(Double.valueOf(elapsed) * progressLeft - Double.valueOf(elapsed)).longValue();
					Duration d1 = Duration.ofMillis(elapsed);
					Duration d2 = Duration.ofMillis(estimatedTotal);
					String textD2 = d2.toDays() + "d,";
					d2 = d2.minusDays(d2.toDays());
					textD2 += d2.toHours() + "h,";
					d2 = d2.minusHours(d2.toHours());
					textD2 += d2.toMinutes() + "m ";
					StringBuilder sb=new StringBuilder();
					sb.append(percent).append("% elapsed:").append(d1.toMinutes()).append("m, estimate:").append(textD2);
					//String showRoundTries = percent + "% elapsed:" + d1.toMinutes() + "m, estimate:" + textD2;
					for (int r = 0; r < env.nRounds; r++) {
						sb.append("R").append(r).append("_").append(timesSuccessfulRound[r]).append("/").append(timesTriedRound[r]).append("  ");
						//showRoundTries += "R" + r + "_" + timesSuccessfulRound[r] + "/" + timesTriedRound[r] + "  ";
					}
					//System.out.println(showRoundTries);
					System.out.println(sb.toString());
				}
				// if(showProgress && !foundResult&&newRound==4) {
				// System.out.println("Failed try: "+tour.toStringMatrixFormatPos());
				// }
			}
		} else if (currentRound == tour.rounds.length - 1 && currentMatch == env.matchesPerRound) {
			// if(hMATHChecker.check(tour)) {
			// We have found the final result!
			lastTournamentFound = tour.toStringOneLine();
			nGeneratedResults++;
			// resultTourList.add(lastTournamentFound);
			if (collector == null && nGeneratedResults % 1000 == 1) {
				long elapsed = System.currentTimeMillis() - initialExecutionTime;
				System.out.println(nGeneratedResults + " - " + elapsed + " \r\n" + tour.toStringMatrixFormatPos());
				System.out.println(lastTournamentFound);
			}
			// if(nGeneratedResults%100000==0) {
			if (collector != null) {
				collector.add(lastTournamentFound);
				if (collector.size() >= maxPatterns) {
					throw new StopSearch();
				}
				return true;
			}
			fileManager.writeTournamentPos(tour);
			// }
			// }
			return true;
		}
		return foundResult;
	}
	
	public void randomizeArray(int[] array) {
		for (int i = 0; i < array.length; i++) {
			int randomIndexToSwap = random.nextInt(array.length);
			int temp = array[randomIndexToSwap];
			array[randomIndexToSwap] = array[i];
			array[i] = temp;
		}
	}
	
	private void printRoundsStatus(int round) {
		if (showGeneration) {
			String result = "|";
			for (int i = 0; i <= round; i++) {
				result += String.valueOf(forceRepetitions[i]);
			}
			for (int i = round + 1; i < forceRepetitions.length; i++) {
				result += " ";
			}
			result += "|";
			System.out.println(result);
		}
	}
	
	public static void main(String[] args) {
		String selector = net.atos.mev.calendarcalculator.compat.DatabaseBatchSupport.selector(args);
		Environment env = new Environment();
		env.loadEnvironment(selector);
		for(int i=0;i<REPETITIONS;i++) {
			executeEnvironment(env, selector, defaultForceRepetitions(env.nTeams));
		}
		
	}
	
	private static void executeEnvironment(Environment env, String envFile, int[] repetitions) {
		environmentFile=envFile;
		TwoColumnsGenPosAlg alg = new TwoColumnsGenPosAlg();
		alg.env = env;
		alg.forceRepetitions = repetitions;
		long before = System.currentTimeMillis();
		Tournament result = alg.execute();
		long after = System.currentTimeMillis();
		long elapsedTime = after - before;
		System.out.println("elapsedTime=" + elapsedTime);
		System.out.println(result.toStringPos());
		System.out.println(result.toStringNames());
	}

	/*public int[] getForceRepetitions() {
		return forceRepetitions;
	}
	
	public void setForceRepetitions(int[] forceRepetitions) {
		this.forceRepetitions = forceRepetitions;
	}*/
}
