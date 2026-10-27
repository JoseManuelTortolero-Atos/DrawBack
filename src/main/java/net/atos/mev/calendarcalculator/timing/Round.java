package net.atos.mev.calendarcalculator.timing;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

public class Round {

	private int roundNumber=0;
	private List<Match> matches;
	private boolean hasCC = false;
	private boolean hasCL = false;
	private Calendar date;
	
	public int getRoundNumber() {
		return roundNumber;
	}
	public void setRoundNumber(int roundNumber) {
		this.roundNumber = roundNumber;
	}
	public List<Match> getMatches() {
		if(matches==null) {
			matches = new ArrayList<Match>();
		}
		return matches;
	}
	public void setMatches(List<Match> matches) {
		this.matches = matches;
	}
	public boolean isHasCC() {
		return hasCC;
	}
	public void setHasCC(boolean hasCC) {
		this.hasCC = hasCC;
	}
	public boolean isHasCL() {
		return hasCL;
	}
	public void setHasCL(boolean hasCL) {
		this.hasCL = hasCL;
	}
	public Match getMatch(int i) {
		return matches.get(i);
	}
	public void addMatch(Match match) {
		if(matches==null) {
			matches = new ArrayList<Match>();
		}
		matches.add(match);
	}
	
	public void resetMatches() {
		matches = new ArrayList<Match>();
	}
	
	public void sortMatches() {
		if(matches!=null) {
			Collections.sort(matches);
		}
	}

	public void reverseSortMatches() {
		if(matches!=null) {
			Collections.reverse(matches);
		}
	}
	
	public Calendar getDate() {
		return date;
	}
	public void setDate(Calendar date) {
		this.date = date;
	}


	public Match getMatch(String team) {
		Match returnMatch = null;
		if(matches==null) {
			return returnMatch;
		}
		for (Match match : matches) {
		    String team1 =  match.getTeam1();
		    String team2 =  match.getTeam2();
		    if(match.getDate()!=null && (team1.equals(team) || team2.equals(team))) {
			//if(team1.equals(team) || team2.equals(team)) {
		    	returnMatch = match;
		    	break;
		    }
		}
		return returnMatch;
	}
	
	public Match getMatchIgnoreDate(String team) {
		Match returnMatch = null;
		if(matches==null) {
			return returnMatch;
		}
		for (Match match : matches) {
		    String team1 =  match.getTeam1();
		    String team2 =  match.getTeam2();
			if(team1.equals(team) || team2.equals(team)) {
		    	returnMatch = match;
		    	break;
		    }
		}
		return returnMatch;
	}
	
	@Override
	public String toString() {
		return "Round [roundNumber=" + roundNumber + ", matches=" + matches + ", hasCC=" + hasCC + ", hasCL=" + hasCL
				+ "]";
	}
	
}
