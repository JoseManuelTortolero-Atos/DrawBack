package net.atos.mev.calendarcalculator;

import java.util.Arrays;
import java.util.Objects;
import java.util.Random;

public class Round {

	public int roundNumber=0;
	public Match[] matches;
	Environment env;
	
	public Round(Environment env) {
		this.env = env;
		this.matches = new Match[env.matchesPerRound];
		for(int m=0; m<env.matchesPerRound; m++) {
			Match newMatch = new Match(0,0,env);
			this.matches[m] = newMatch;
		}
	}

	public String toString() {
		String result = "Round " + roundNumber;
		for(Match m: matches) {
			result +="\r\n" + m.toString();
		}
		return result;
	}

	public String toStringPos() {
		String result = "Round " + roundNumber;
		for(Match m: matches) {
			result +="\r\n" + m.toStringPos();
		}
		return result;
	}

	public String toStringNames() {
		String result = "Round " + roundNumber;
		int minPadding = 0;
		for(Match m1: matches) {
			if(m1.team1.getName().length() > minPadding) {
				minPadding = m1.team1.getName().length();
			}
		}
		for(Match m: matches) {
			result +="\r\n" + m.toStringNamesWithPadding(minPadding);
		}			
		return result;
	}
	
	public void fillWithFirstRoundPos() {
		for(int i=0; i<matches.length; i++) {
			matches[i].setPos1(i+1);
			matches[i].setPos2(i+1+env.matchesPerRound);
		}
	}
	
	public void fillWithFirstRoundTransposedPos() {
		for(int i=0; i<matches.length; i++) {
			matches[i].setPos1(i*2+1);
			matches[i].setPos2(i*2+2);
		}
	}
	
	public void fillRandomlyPos() {
		Random rand = new Random();
		int teamsToAssign = env.nTeams;
		boolean[] usedPos=new boolean[env.nTeams];
		for(int i=0; i<usedPos.length; i++) {
			usedPos[i] = false;
		}
		for(Match m: matches) {
			int newPos1 = lookupNthFreePos(rand.nextInt(teamsToAssign), usedPos);
			int newPos2 = lookupNthFreePos(rand.nextInt(teamsToAssign-1), usedPos);
			teamsToAssign = teamsToAssign-2;
			usedPos[newPos1]=true;
			usedPos[newPos2]=true;
			m.setPos1(newPos1+1);
			m.setPos2(newPos2+1);
		}			
	}

	public void fillRandomlyForbidPreviousPos(Tournament tourn, int currentRound) {
		Random rand = new Random();
		int teamsToAssign = env.nTeams;
		boolean[] usedPos=new boolean[env.nTeams];
		for(int i=0; i<usedPos.length; i++) {
			usedPos[i] = false;
		}
		for(Match m: matches) {
			int newPos1 = lookupNthFreePos(rand.nextInt(teamsToAssign), usedPos);
			int newPos2 = lookupNthFreePos(rand.nextInt(teamsToAssign-1), usedPos);
			int attempts=0;
			while(foundMatchInPreviousRounds(tourn, currentRound, newPos1, newPos2) || newPos1 == newPos2) {
				newPos1 = lookupNthFreePos(rand.nextInt(teamsToAssign), usedPos);
				newPos2 = lookupNthFreePos(rand.nextInt(teamsToAssign-1), usedPos);
				attempts++;
				System.out.println("Attempt to make new match: " + attempts);
			}
			teamsToAssign=teamsToAssign-2;
			usedPos[newPos1]=true;
			usedPos[newPos2]=true;
			m.setPos1(newPos1+1);
			m.setPos2(newPos2+1);
		}			
	}
	
	private boolean foundMatchInPreviousRounds(Tournament tourn, int currentRound, int newPos1, int newPos2) {
		boolean result = false;
		for(int roundChecking=0; roundChecking<currentRound-1; roundChecking++) {
			for(Match m: tourn.rounds[roundChecking].matches) {
				if(m.getPos1() == newPos1 && m.getPos2() == newPos2) return true;
				if(m.getPos1() == newPos2 && m.getPos2() == newPos1) return true;
			}
		}
		return result;
	}
	
	private int lookupNthFreePos(int n, boolean[] usedPos) {
		int searchIdx=0;
		int freeSpacesFound = 0;
		while(freeSpacesFound <= n && searchIdx < usedPos.length) {
			if(usedPos[searchIdx]) {
				searchIdx++;
			} else {
				freeSpacesFound++;
				if(freeSpacesFound <= n) {
					searchIdx++;
				}
			}
		}
		return searchIdx;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Round round = (Round) o;
		return Arrays.equals(matches, round.matches);
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(matches);
	}
}
