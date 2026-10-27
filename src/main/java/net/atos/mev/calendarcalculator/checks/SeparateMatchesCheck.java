package net.atos.mev.calendarcalculator.checks;

import java.util.Arrays;

import net.atos.mev.calendarcalculator.Environment;
import net.atos.mev.calendarcalculator.Match;
import net.atos.mev.calendarcalculator.Round;
import net.atos.mev.calendarcalculator.Team;
import net.atos.mev.calendarcalculator.Tournament;

public class SeparateMatchesCheck implements Check {
	
	Team[] separateTeams;
	int[] posSeparateTeams;
	int amountOfMatches=0;
	int[] roundsOfMatches;
	int minSeparation;
	
	public SeparateMatchesCheck(Team[] teams, int minSeparation) {
		this.separateTeams = teams;
		this.minSeparation = minSeparation;
		posSeparateTeams=new int[teams.length];
		// Calculate matches. With 2 teams, there's 1 match. With 3 teams, there's 1+2 matches. With 4 teams, there's 1+2+3 matches, etc.
		amountOfMatches = 0;
		for(int i=2; i<=teams.length; i++) {
			amountOfMatches+=(i-1);
		}
		roundsOfMatches = new int[amountOfMatches];
	}

	@Override
	public boolean check(Tournament tour) {
		// Find the pos assigned to each team
		// Find the pos assigned to each team
		for(int j=0; j<separateTeams.length; j++) {
			posSeparateTeams[j] = tour.mapPosToTeams.getKeyOfTeam(separateTeams[j]);
		}
		Arrays.sort(posSeparateTeams);
		// Go through all the roundsll trying to find a match between two teams in the list
		int foundMatches=0;
		for(int nRound = 0; nRound < tour.rounds.length; nRound++) {
			Round currentRound = tour.rounds[nRound];
			for(Match m: currentRound.matches) {
				if(Arrays.binarySearch(posSeparateTeams, m.getPos1()) >= 0 && Arrays.binarySearch(posSeparateTeams, m.getPos2()) >= 0) {
					roundsOfMatches[foundMatches]=nRound;
					foundMatches++;
				}
			}
		}
		// Check the rounds of the matches to see if there's any pair too close
		for(int firstMatch=0; firstMatch < foundMatches; firstMatch++) {
			for(int secondMatch=firstMatch+1; secondMatch < foundMatches; secondMatch++) {
				if(Math.abs(roundsOfMatches[firstMatch]-roundsOfMatches[secondMatch]) < minSeparation) {
					return false;
				}
				// Check that we don't have consecutive matches in the end of the first leg and beginning of the second one
				if(Math.abs(roundsOfMatches[firstMatch]+tour.rounds.length-roundsOfMatches[secondMatch]) < minSeparation) {
					return false;
				}
				if(Math.abs(roundsOfMatches[secondMatch]+tour.rounds.length-roundsOfMatches[firstMatch]) < minSeparation) {
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
		Team team3 = env.teams[2];
		Team team4 = env.teams[3];
		t1.mapPosToTeams.put(2, team1);
		t1.mapPosToTeams.put(3, team2);
		t1.mapPosToTeams.put(4, team3);
		Team[] pair1 = {team1, team2, team3};
		Team[] pair2 = {team1, team2, team3, team4};
		SeparateMatchesCheck checker1 = new SeparateMatchesCheck(pair1, 2);
		SeparateMatchesCheck checker2 = new SeparateMatchesCheck(pair1, 3);
		SeparateMatchesCheck checker3 = new SeparateMatchesCheck(pair2, 2);
		SeparateMatchesCheck checker4 = new SeparateMatchesCheck(pair2, 3);
		System.out.println("HalfMatchesAtHomeCheck t1=" + checker1.check(t1));
		System.out.println("HalfMatchesAtHomeCheck t2=" + checker2.check(t1));
		System.out.println("HalfMatchesAtHomeCheck t3=" + checker3.check(t1));
		System.out.println("HalfMatchesAtHomeCheck t4=" + checker4.check(t1));

	}

	public Team[] getSeparateTeams() {
		return separateTeams;
	}

	public void setSeparateTeams(Team[] separateTeams) {
		this.separateTeams = separateTeams;
	}

}
