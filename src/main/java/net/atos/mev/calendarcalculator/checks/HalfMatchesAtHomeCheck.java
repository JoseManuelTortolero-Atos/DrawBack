package net.atos.mev.calendarcalculator.checks;

import net.atos.mev.calendarcalculator.Environment;
import net.atos.mev.calendarcalculator.Match;
import net.atos.mev.calendarcalculator.Round;
import net.atos.mev.calendarcalculator.Tournament;

public class HalfMatchesAtHomeCheck implements Check{

	@Override
	public boolean check(Tournament tour) {
		int minMatches = tour.rounds.length/2;
		int maxMatches = minMatches;
		if(tour.rounds.length%2 > 0) {
			maxMatches++;
		}
		int[] matchesHome=new int[tour.env.nTeams+1];
		int[] matchesAway=new int[tour.env.nTeams+1];
		for(Round r: tour.rounds) {
			for(Match m: r.matches) {
				matchesHome[m.getPos1()]++;
				matchesAway[m.getPos2()]++;
			}
		}
		for(int checkingPos=1; checkingPos <= tour.env.nTeams; checkingPos++) {
			if(matchesHome[checkingPos] < minMatches || matchesHome[checkingPos] > maxMatches || matchesAway[checkingPos] < minMatches || matchesAway[checkingPos] > maxMatches ) {
				return false;
			}
		}
		return true;
	}

	public static void main(String[] args) {
		Environment env = new Environment();
		env.loadEnvironment(net.atos.mev.calendarcalculator.compat.DatabaseBatchSupport.selector(args));
		Tournament t1 = Tournament.readFromLine(env, "010203040506040602050103050403020601030502060104040206030501");
		Tournament t2 = Tournament.readFromLine(env, "010203040506040602050103030602040501010403050602010604050203");
		HalfMatchesAtHomeCheck checker = new HalfMatchesAtHomeCheck();
		System.out.println("HalfMatchesAtHomeCheck t1=" + checker.check(t1));
		System.out.println("HalfMatchesAtHomeCheck t2=" + checker.check(t2));

	}

}
