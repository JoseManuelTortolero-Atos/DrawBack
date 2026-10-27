package net.atos.mev.calendarcalculator.timing;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Result {
	String team;
	Map<String,Integer> byHourHome;
	Map<String,Integer> byHourAway;
	List <ResultLine> theList;
	
	
	public String getTeam() {
		return team;
	}


	public void setTeam(String team) {
		this.team = team;
	}


	public Map<String, Integer> getByHourHome() {
		return byHourHome;
	}


	public void setByHourHome(Map<String, Integer> byHourHome) {
		this.byHourHome = byHourHome;
	}

	public Map<String, Integer> getByHourAway() {
		return byHourAway;
	}


	public void setByHourAway(Map<String, Integer> byHourAway) {
		this.byHourAway = byHourAway;
	}

	public void addResult(int round,Calendar date,int daysBefore,String matchLiteral, boolean homeMatch,int recRound, boolean calculateHomeAway,String team1, String team2) {
		if(theList==null) {
			theList = new ArrayList<>();
		}
		SimpleDateFormat format1 = new SimpleDateFormat("dd/MM/yyyy");
		String matchDate = format1.format(date.getTime()); 
		format1 = new SimpleDateFormat("HH:mm");
		String matchHour = format1.format(date.getTime()); 
		matchHour = matchHour.replace(":", "h");
		
		int acumulatedRest = Math.min(daysBefore==0?5:daysBefore, 5);
		int matchToDo = 4;
		for(int i=theList.size()-1;i>=Math.max(0,theList.size()-4);i--) {
			acumulatedRest = acumulatedRest + Math.min(theList.get(i).getDaysBefore()==0?5:theList.get(i).getDaysBefore(), 5);
			matchToDo--;
		}
		acumulatedRest = acumulatedRest + (5*(matchToDo));
		
		ResultLine result = new ResultLine(round,matchDate,matchHour,daysBefore,matchLiteral,recRound,team1,team2,acumulatedRest);
		theList.add(result);
		
		if(calculateHomeAway) {
			format1 = new SimpleDateFormat("HH");
			String hourToPlay = format1.format(date.getTime());
			int prev=1;
			if(byHourHome==null) {
				byHourHome=new HashMap<>();
			}
			if(byHourAway==null) {
				byHourAway=new HashMap<>();
			}
			if(homeMatch) {
				if (byHourHome.get(hourToPlay)!=null) {
					prev = byHourHome.get(hourToPlay).intValue();
					prev++;
				}
				byHourHome.put(hourToPlay,prev);
			}else {
				if (byHourAway.get(hourToPlay)!=null) {
					prev = byHourAway.get(hourToPlay).intValue();
					prev++;
				}
				byHourAway.put(hourToPlay,prev);			
			}
		}
	}
	
	public ResultLine getResult(String match) {
		for (ResultLine resultLine : theList) {
			if(match.equals(resultLine.matchLiteral)) {
				return resultLine;
			}
		}
		return null;
	}
	
	public List <ResultLine> getResults() {
		return theList;
	}


	public class ResultLine {
		int round;
		String matchDate;
		String matchHour;
		int daysBefore;
		String matchLiteral;
		int recRound;
		String team1;
		String team2;
		int fiveMatchRestTime;
		
		public ResultLine(int round,String matchDate, String matchHour, int daysBefore,String matchLiteral,int recRound,String team1,String team2,int fiveMatchRestTime ) {
			super();
			this.round = round;
			this.matchDate = matchDate;
			this.matchHour = matchHour;
			this.daysBefore = daysBefore;
			this.matchLiteral = matchLiteral;
			this.recRound = recRound;
			this.team1=team1;
			this.team2=team2;
			this.fiveMatchRestTime = fiveMatchRestTime;
		}
		public String getMatchDate() {
			return matchDate;
		}
		public void setMatchDate(String matchDate) {
			this.matchDate = matchDate;
		}
		public String getMatchHour() {
			return matchHour;
		}
		public void setMatchHour(String matchHour) {
			this.matchHour = matchHour;
		}
		public int getDaysBefore() {
			return daysBefore;
		}
		public void setDaysBefore(int daysBefore) {
			this.daysBefore = daysBefore;
		}
		public int getRound() {
			return round;
		}
		public void setRound(int round) {
			this.round = round;
		}
		public String getMatchLiteral() {
			return matchLiteral;
		}
		public void setMatchLiteral(String matchLiteral) {
			this.matchLiteral = matchLiteral;
		}
		public int getRecRound() {
			return recRound;
		}
		public void setRecRound(int recRound) {
			this.recRound = recRound;
		}
		public String getTeam1() {
			return team1;
		}
		public void setTeam1(String team1) {
			this.team1 = team1;
		}
		public String getTeam2() {
			return team2;
		}
		public void setTeam2(String team2) {
			this.team2 = team2;
		}
		public int getFiveMatchRestTime() {
			return fiveMatchRestTime;
		}
		public void setFiveMatchRestTime(int fiveMatchRestTime) {
			this.fiveMatchRestTime = fiveMatchRestTime;
		}
		
	}
}
