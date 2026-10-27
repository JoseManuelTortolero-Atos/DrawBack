package net.atos.mev.calendarcalculator.checks;

import net.atos.mev.calendarcalculator.Environment;
import net.atos.mev.calendarcalculator.Match;
import net.atos.mev.calendarcalculator.Round;
import net.atos.mev.calendarcalculator.Team;
import net.atos.mev.calendarcalculator.Tournament;

public class PlayTogetherTeamsCheck implements Check {
	
	Team team1;
	Team team2;
	
	public PlayTogetherTeamsCheck(Team team1, Team team2) {
		this.team1 = team1;
		this.team2 = team2;
	}

	@Override
	public boolean check(Tournament tour) {
		int posTeam1=tour.mapPosToTeams.getKeyOfTeam(team1);
		int posTeam2=tour.mapPosToTeams.getKeyOfTeam(team2);
		// Go through all the rounds trying to find one where both teams play at home or away
		for(Round currentRound : tour.rounds) {
			boolean team1Home = false;
			boolean team1Away = false;
			boolean team2Home = false;
			boolean team2Away = false;
			boolean playAgainstEachOther = false;
			for(Match currentMatch : currentRound.matches) {
				if(currentMatch.getPos1() == posTeam1 && currentMatch.getPos2() == posTeam2) {
					playAgainstEachOther = true;
				}
				if(currentMatch.getPos1() == posTeam2 && currentMatch.getPos2() == posTeam1) {
					playAgainstEachOther = true;
				}
				if(currentMatch.getPos1() == posTeam1) {
					team1Home = true;
				}
				if(currentMatch.getPos1() == posTeam2) {
					team2Home = true;
				}
				if(currentMatch.getPos2() == posTeam1) {
					team1Away = true;
				}
				if(currentMatch.getPos2() == posTeam2) {
					team2Away = true;
				}
			}
			if((team1Home && team2Away && !playAgainstEachOther) || (team1Away && team2Home && !playAgainstEachOther)) {
				return false;
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
		PlayTogetherTeamsCheck checker1 = new PlayTogetherTeamsCheck(team1, team2);
		PlayTogetherTeamsCheck checker2 = new PlayTogetherTeamsCheck(team1, team3);
		System.out.println("PlayTogetherTeamsCheck t1=" + checker1.check(t1));
		System.out.println("PlayTogetherTeamsCheck t2=" + checker2.check(t1));

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
