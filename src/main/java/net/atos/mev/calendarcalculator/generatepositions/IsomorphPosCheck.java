package net.atos.mev.calendarcalculator.generatepositions;

import java.util.ArrayList;
import java.util.Collections;

import net.atos.mev.calendarcalculator.Environment;
import net.atos.mev.calendarcalculator.Match;
import net.atos.mev.calendarcalculator.Tournament;

public class IsomorphPosCheck {

	public static boolean isIsomorphToAtLeastOne(Environment env, ArrayList<Tournament> tourList, Tournament tour2, int upToRound) {
		for(Tournament tour1 : tourList) {
			if(isIsomorph(env, tour1, tour2, upToRound)) {
				return true;
			}
		}
		return false;
	}
	
	public static boolean isIsomorphToAtLeastOneDummy(Environment env, ArrayList<Tournament> tourList, Tournament tour2, int upToRound) {
		return false;
	}

	public static boolean isIsomorph(Environment env, Tournament tour1, Tournament tour2, int upToRound) {
		boolean result = false;
		int[] mappingPosTour1ToTour2 = new int[env.nTeams+1];
		boolean[] usedPosToMap = new boolean[env.nTeams+1];
		result = generateMappings(env, tour1, tour2,mappingPosTour1ToTour2, usedPosToMap, 1, upToRound);
		return result;
	}
	
	static boolean generateMappings(Environment env, Tournament tour1, Tournament tour2, int[] mappingPosTour1ToTour2, boolean[] usedPosToMap, int nextPosToMap, int upToRound) {
		boolean result = false;
		for(int newPos2Map = 1; newPos2Map <= env.nTeams; newPos2Map++) {
			if(usedPosToMap[newPos2Map] == false) {
				mappingPosTour1ToTour2[nextPosToMap] = newPos2Map;
				usedPosToMap[newPos2Map] = true;
				if(nextPosToMap < env.nTeams) {
					result = generateMappings(env, tour1, tour2,mappingPosTour1ToTour2, usedPosToMap, nextPosToMap+1, upToRound);
				} else {
					result = testMappings(env, tour1, tour2,mappingPosTour1ToTour2, upToRound);
				}
				if(result == true) {
					return true;
				}
				mappingPosTour1ToTour2[nextPosToMap] = 0;
				usedPosToMap[newPos2Map] = false;
			}
		}
		return result;
	}
	
	static boolean testMappings(Environment env, Tournament tour1, Tournament tour2, int[] mappingPosTour1ToTour2, int upToRound) {
		for(int currentRound=0; currentRound<=upToRound; currentRound++) {
			ArrayList<Integer> matchesTour1 = new ArrayList<Integer>();
			ArrayList<Integer> matchesTour2 = new ArrayList<Integer>();
			for(Match m1: tour1.rounds[currentRound].matches) {
				int matchCode = Match.getPosId(env, m1.pos1, m1.pos2);
				matchesTour1.add(matchCode);
			}
			for(Match m2: tour2.rounds[currentRound].matches) {
				int matchCode = Match.getPosId(env, mappingPosTour1ToTour2[m2.pos1], mappingPosTour1ToTour2[m2.pos2]);
				matchesTour2.add(matchCode);
			}
			Collections.sort(matchesTour1);
			Collections.sort(matchesTour2);
			boolean foundDifferentRound = false;
			for(int nComparison = 0; nComparison < matchesTour1.size() && !foundDifferentRound; nComparison++) {
				if(!matchesTour1.get(nComparison).equals(matchesTour2.get(nComparison))) {
					return false;
				}
			}
		}
		//System.out.println("Found isomorph mapping: " + Arrays.toString(mappingPosTour1ToTour2));
		return true;
	}
	
	public static void main(String[] args) {
		Environment env = new Environment();
		env.loadEnvironment(net.atos.mev.calendarcalculator.compat.DatabaseBatchSupport.selector(args));
		Tournament t1 = Tournament.readFromLine(env, "010203040506040602050103050403020601030502060104040206030501");
		Tournament t2 = Tournament.readFromLine(env, "060203040501040102050603050403020106030502010604040201030506");
		Tournament t3 = Tournament.readFromLine(env, "010203040506040602050103050403020601030502060104040206030105");
		ArrayList<Tournament> tourList = new ArrayList<Tournament>();
		tourList.add(t1);
		tourList.add(t2);
		System.out.println("Compare t1-t2: " + isIsomorph(env, t1, t2, 4));
		System.out.println("Compare t1-t3: " + isIsomorph(env, t1, t3, 4));
		System.out.println("Compare t1-t3 without final round: " + isIsomorph(env, t1, t3, 3));
		System.out.println("Compare list-t3: " + isIsomorphToAtLeastOne(env, tourList, t3, 4));
		System.out.println("Compare list-t3 without final round: " + isIsomorphToAtLeastOne(env, tourList, t3, 3));
	}

}
