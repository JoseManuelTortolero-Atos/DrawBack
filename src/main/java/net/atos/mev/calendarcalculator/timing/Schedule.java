package net.atos.mev.calendarcalculator.timing;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

public class Schedule implements Comparable<Schedule>{
	private Map<String,Round> rounds;
	private List<String> roundNameList;
	private String fileName;
	private Map<String,Round> finalMatches;
	private int cost;
	private int postponedGames;
	
	public Schedule(Map<String, Round> rounds, List<String> roundNameList,String fileName) {
		super();
		this.rounds = rounds;
		this.roundNameList = roundNameList;
		this.fileName = fileName;
	}
	public Map<String, Round> getRounds() {
		return rounds;
	}
	public void setRounds(Map<String, Round> rounds) {
		this.rounds = rounds;
	}
	public List<String> getRoundNameList() {
		return roundNameList;
	}
	public void setRoundNameList(List<String> roundNameList) {
		this.roundNameList = roundNameList;
	}	
	public String getFileName() {
		return fileName;
	}
	public void setFileName(String fileName) {
		this.fileName = fileName;
	}
	public Map<String, Round> getFinalMatches() {
		return finalMatches;
	}
	public void setFinalMatches(Map<String, Round> finalMatches) {
		this.finalMatches = finalMatches;
	}
	public int getCost() {
		return cost;
	}
	public void setCost(int cost) {
		this.cost = cost;
	}
	public int getPostponedGames() {
		return postponedGames;
	}
	public void setPostponedGames(int postponedGames) {
		this.postponedGames = postponedGames;
	}
	public Result getMatchesForTeam(String team, String ramadanStartDate, String ramadanEndDate,boolean isCCTeam, boolean isCLTeam, String oldCCDate, String newCCDate) {
		String previousRoundName=null;
		Result toReturn = new Result();
		toReturn.setTeam(team);
		Match previousMatch = null;
		for (String roundName : roundNameList) {
			Round round = rounds.get(roundName);
			Match match = round.getMatch(team);
			boolean calculateHomeAway = true;
			if(isCCTeam && round.isHasCC() && (match==null || match.getDate()==null)) {
				match = new Match();
				match.setTeam1(team);
				match.setTeam2("XXX");
				match.setRound(-1);
				match.setDate(round.getDate());
				Calendar cal = Calendar.getInstance();
				SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
				try {
					cal.setTime(sdf.parse(oldCCDate));
				}catch(Exception e) {}
				if(cal.equals(round.getDate())) {
					try {
						cal.setTime(sdf.parse(newCCDate));
					}catch(Exception e) {}
					match.setDate(cal);
				}

				calculateHomeAway = false;
			}else if(isCLTeam && round.isHasCL() && (match==null || match.getDate()==null)) {
				match = new Match();
				match.setTeam1(team);
				match.setTeam2("XXX");
				match.setRound(-2);
				Calendar compDate = ((Calendar)round.getDate().clone());
				compDate.add(Calendar.DATE, -1);
				match.setDate(compDate);
				calculateHomeAway = false;
			}else if(match==null || match.getDate()==null) {
				continue;
			}
			int daysBetween=0;
			if(previousRoundName!=null) {
			
				//Round previousRound = rounds.get(previousRoundName);
				//Match previousMatch = previousRound.getMatch(team);
				Calendar matchDate = (Calendar)match.getDate().clone();
				Calendar prevMatchDate = (Calendar)previousMatch.getDate().clone();
				matchDate.set(Calendar.HOUR_OF_DAY, 0);
				prevMatchDate.set(Calendar.HOUR_OF_DAY, 0);
				daysBetween = Math.toIntExact(ChronoUnit.DAYS.between(prevMatchDate.getTime().toInstant(),matchDate.getTime().toInstant()))-1;		
			}
			
			boolean homeMatch = false;
			if(match.getTeam1().equals(team)) {
				homeMatch = true;
			}
			
			//Recalculate hours
			Calendar theDate = match.getDate();
			if(theDate.get(Calendar.HOUR_OF_DAY) == 22 || theDate.get(Calendar.HOUR_OF_DAY) == 20 ||
					theDate.get(Calendar.HOUR_OF_DAY) == 18 || theDate.get(Calendar.HOUR_OF_DAY) == 16 ) {
				theDate.add(Calendar.MINUTE, -30);
				if(theDate.get(Calendar.HOUR_OF_DAY)==21) {
					theDate.add(Calendar.HOUR_OF_DAY, -2);
				}
				match.setDate(theDate);
			}
			try {
				SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
				Calendar startRamadanCalendar = Calendar.getInstance();
				startRamadanCalendar.setTime(sdf.parse(ramadanStartDate));
				Calendar endRamadanCalendar = Calendar.getInstance();
				endRamadanCalendar.setTime(sdf.parse(ramadanEndDate));
				endRamadanCalendar.set(Calendar.HOUR_OF_DAY, 23);
				if(match.getDate().after(startRamadanCalendar) && match.getDate().before(endRamadanCalendar)) {
					match.getDate().set(Calendar.HOUR_OF_DAY, 22);
				}
			}catch( ParseException pe) {
				//No problem
			}
			
			toReturn.addResult(round.getRoundNumber(), match.getDate(), daysBetween,match.getTeam1()+" vs "+match.getTeam2(),homeMatch,match.getRound(),calculateHomeAway,match.getTeam1(),match.getTeam2());
			previousRoundName=roundName;
			previousMatch = match;
		}
		return toReturn;
	}
	
	@Override
	public int compareTo(Schedule otherSchedule) {
		if(this.cost>otherSchedule.getCost()) {
			return 1;
		}else if(this.cost<otherSchedule.getCost()) {
			return -1;
		}else {
			return 0;
		}
	}

	
}
