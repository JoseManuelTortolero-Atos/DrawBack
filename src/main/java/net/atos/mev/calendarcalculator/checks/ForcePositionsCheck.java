package net.atos.mev.calendarcalculator.checks;

import net.atos.mev.calendarcalculator.Environment;
import net.atos.mev.calendarcalculator.Team;
import net.atos.mev.calendarcalculator.Tournament;

public class ForcePositionsCheck implements Check {

	Team team1;
	int round;

	public ForcePositionsCheck(Team team1, int round) {
		this.team1 = team1;
		this.round = round;
	}

	@Override
	public boolean check(Tournament tour) {
		int posTeam1=tour.mapPosToTeams.getKeyOfTeam(team1);
		if(posTeam1 != this.round) {
			return false;
		} else {
			return true;
		}
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
		ForcePositionsCheck checker1 = new ForcePositionsCheck(team1, 1);
		ForcePositionsCheck checker2 = new ForcePositionsCheck(team2, 3);
		ForcePositionsCheck checker3 = new ForcePositionsCheck(team3, 4);
		System.out.println("ForcePositionsCheck t1=" + checker1.check(t1));
		System.out.println("ForcePositionsCheck t2=" + checker2.check(t1));
		System.out.println("ForcePositionsCheck t3=" + checker3.check(t1));

	}

	public Team getTeam1() {
		return team1;
	}

	public void setTeam1(Team team1) {
		this.team1 = team1;
	}

	public int getRound() {
		return round;
	}

	public void setRound(int round) {
		this.round = round;
	}

}
