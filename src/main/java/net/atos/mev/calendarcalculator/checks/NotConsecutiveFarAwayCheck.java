package net.atos.mev.calendarcalculator.checks;

import java.util.ArrayList;
import java.util.Arrays;

import net.atos.mev.calendarcalculator.Environment;
import net.atos.mev.calendarcalculator.Match;
import net.atos.mev.calendarcalculator.Round;
import net.atos.mev.calendarcalculator.Team;
import net.atos.mev.calendarcalculator.Tournament;

public class NotConsecutiveFarAwayCheck implements Check {
	
	Team[] farTeams;
	int[] posFarTeams;
	
	public NotConsecutiveFarAwayCheck(Team[] farTeams) {
		this.farTeams = farTeams;
		posFarTeams=new int[farTeams.length];
	}

	@Override
	public boolean check(Tournament tour) {
		// Find the pos assigned to each team
		for(int j=0; j<farTeams.length; j++) {
			posFarTeams[j] = tour.mapPosToTeams.getKeyOfTeam(farTeams[j]);
		}

		Arrays.sort(posFarTeams);
		// Go through all the rounds trying to find a team that plays two consecutive matches at home or away against two teams in the list
		boolean[] playsHomeFarTeamPreviousRound = new boolean[tour.env.nTeams+1];
		boolean[] playsAwayFarTeamPreviousRound = new boolean[tour.env.nTeams+1];
		for(int nRound = 0; nRound < tour.rounds.length; nRound++) {
			Round currentRound = tour.rounds[nRound];
			boolean[] playsHomeFarTeamCurrentRound = new boolean[tour.env.nTeams+1];
			boolean[] playsAwayFarTeamCurrentRound = new boolean[tour.env.nTeams+1];
			for(Match m: currentRound.matches) {
				if(Arrays.binarySearch(posFarTeams, m.getPos1()) >= 0) {
					playsAwayFarTeamCurrentRound[m.getPos2()] = true;
					if(playsAwayFarTeamPreviousRound[m.getPos2()]) {
						return false;
					}
				}
				if(Arrays.binarySearch(posFarTeams, m.getPos2()) >= 0) {
					playsHomeFarTeamCurrentRound[m.getPos1()] = true;
					if(playsHomeFarTeamPreviousRound[m.getPos1()]) {
						return false;
					}
				}
			}
			playsHomeFarTeamPreviousRound=playsHomeFarTeamCurrentRound;
			playsAwayFarTeamPreviousRound=playsAwayFarTeamCurrentRound;
		}
		// Check that a team doesn't play consecutive away matches against far away teams in the last round of the first leg and first of the second leg
		ArrayList<Integer> teamsHomeFirstRound = new ArrayList<Integer>();
		ArrayList<Integer> teamsAwayLastRound = new ArrayList<Integer>();
		for(Match m: tour.rounds[0].matches) {
			if(Arrays.binarySearch(posFarTeams, m.getPos2()) >= 0) {
				teamsHomeFirstRound.add(m.getPos1());
			}
		}
		for(Match m: tour.rounds[tour.rounds.length-1].matches) {
			if(Arrays.binarySearch(posFarTeams, m.getPos1()) >= 0) {
				teamsAwayLastRound.add(m.getPos2());
			}
		}
		//System.out.println(teamsHomeFirstRound + " - " + teamsAwayLastRound);
		for(Integer fr : teamsHomeFirstRound) {
			for(Integer lr : teamsAwayLastRound) {
				if(fr.equals(lr)) {
					//System.out.println("================ DISCARDED FOR ROUND 16 RULE ================");
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
		t1.mapPosToTeams.put(1, team1);
		t1.mapPosToTeams.put(6, team2);
		t1.mapPosToTeams.put(5, team3);
		Team[] pair1 = {team1, team2};
		Team[] pair2 = {team1, team3};
		NotConsecutiveFarAwayCheck checker1 = new NotConsecutiveFarAwayCheck(pair1);
		NotConsecutiveFarAwayCheck checker2 = new NotConsecutiveFarAwayCheck(pair2);
		System.out.println("HalfMatchesAtHomeCheck t1=" + checker1.check(t1));
		System.out.println("HalfMatchesAtHomeCheck t2=" + checker2.check(t1));

	}

	public Team[] getFarTeams() {
		return farTeams;
	}

	public void setFarTeams(Team[] farTeams) {
		this.farTeams = farTeams;
	}

}
