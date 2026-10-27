package net.atos.mev.calendarcalculator.checks;

import net.atos.mev.calendarcalculator.Environment;
import net.atos.mev.calendarcalculator.Match;
import net.atos.mev.calendarcalculator.Round;
import net.atos.mev.calendarcalculator.Team;
import net.atos.mev.calendarcalculator.Tournament;

public class MatchNotInRoundCheck implements Check {

	Team team1;
	Team team2;
	int[] forbiddenRounds;

	public MatchNotInRoundCheck(Team team1, Team team2, int[] forbiddenRounds) {
		this.team1 = team1;
		this.team2 = team2;
		this.forbiddenRounds = forbiddenRounds;
	}

	@Override
	public boolean check(Tournament tour) {
		int posTeam1=tour.mapPosToTeams.getKeyOfTeam(team1);
		int posTeam2=tour.mapPosToTeams.getKeyOfTeam(team2);
		// Go to the round and check if the match happens there
		for(int forbiddenRound : forbiddenRounds) {
			Round currentRound = tour.rounds[forbiddenRound];
			for(Match currentMatch : currentRound.matches) {
				if(currentMatch.getPos1() == posTeam1 && currentMatch.getPos2() == posTeam2) {
					return false;
				}
				if(currentMatch.getPos1() == posTeam2 && currentMatch.getPos2() == posTeam1) {
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
		t1.mapPosToTeams.put(2, team2);
		t1.mapPosToTeams.put(5, team3);
		int[] forbiddenRounds = {0,3,4};
		int[] forbiddenRounds2 = {0,3};
		MatchNotInRoundCheck checker1 = new MatchNotInRoundCheck(team1, team2, forbiddenRounds);
		MatchNotInRoundCheck checker2 = new MatchNotInRoundCheck(team1, team3, forbiddenRounds);
		MatchNotInRoundCheck checker3 = new MatchNotInRoundCheck(team1, team3, forbiddenRounds2);
		System.out.println("HalfMatchesAtHomeCheck t1=" + checker1.check(t1));
		System.out.println("HalfMatchesAtHomeCheck t2=" + checker2.check(t1));
		System.out.println("HalfMatchesAtHomeCheck t3=" + checker3.check(t1));

	}

	public Team getTeam1() {
		return team1;
	}

	public void setTeam1(Team team1) {
		this.team1 = team1;
	}

	public Team getTeam2() {
		return team2;
	}

	public void setTeam2(Team team2) {
		this.team2 = team2;
	}
}
