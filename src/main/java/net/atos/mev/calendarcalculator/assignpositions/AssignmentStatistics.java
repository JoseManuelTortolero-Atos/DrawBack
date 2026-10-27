package net.atos.mev.calendarcalculator.assignpositions;

import java.util.HashMap;

import net.atos.mev.calendarcalculator.Environment;
import net.atos.mev.calendarcalculator.Match;
import net.atos.mev.calendarcalculator.Tournament;
import net.atos.mev.calendarcalculator.fileio.AssignStatisticsFileManager;
import net.atos.mev.calendarcalculator.fileio.AssignmentsFileManager;

public class AssignmentStatistics {
	
	int[][][] roundOfMatch;
	int totalTournaments;
	HashMap<String,Integer> teamIntId;
	Environment env;
	int tournamentsToRead;
	
	public void calculateStatistics(Environment env, String folder, int tournamentsToRead) {
		totalTournaments=0;
		this.env = env;
		this.tournamentsToRead = tournamentsToRead;
		loadTeamIds();
		roundOfMatch = new int[env.nTeams][env.nTeams][env.nRounds+env.nRounds];
		while(totalTournaments < tournamentsToRead) {
			Tournament tour = AssignmentsFileManager.readTournamentPos(env, totalTournaments, folder);
			tour.applyMapPosToTeams();
			processMatches(tour);
			totalTournaments++;
			System.out.println("Tournament read: " + totalTournaments);
		}
		System.out.println("Processing complete, saving to file...");
		(new AssignStatisticsFileManager()).saveTournamentsStatistics(env, folder, roundOfMatch);
		System.out.println("Processing complete, file saved");
	}

	private void loadTeamIds() {
		teamIntId = new HashMap<String, Integer>();
		for(int t=0; t<env.teams.length; t++) {
			teamIntId.put(env.teams[t].getCode(), t);
		}
		
	}

	private void processMatches(Tournament tour) {
		for(int nRound=0; nRound<tour.rounds.length; nRound++) {
			for(Match m: tour.rounds[nRound].matches) {
				roundOfMatch[teamIntId.get(m.team1.getCode())][teamIntId.get(m.team2.getCode())][nRound]++;
				roundOfMatch[teamIntId.get(m.team2.getCode())][teamIntId.get(m.team1.getCode())][nRound+env.nRounds]++;
			}
		}
	}

	public static void main(String[] args) {
		AssignmentStatistics stats = new AssignmentStatistics();
		Environment env = new Environment();
		env.loadEnvironment(net.atos.mev.calendarcalculator.compat.DatabaseBatchSupport.selector(args));
		stats.calculateStatistics(env, "assign_20201027-11.45.45", 1000);

	}

}
