package net.atos.mev.calendarcalculator.timing;

import java.util.Calendar;

public class Match implements Comparable<Match>{

	private String team1;
	private String team2;
	private Calendar date;
	private int basicValue;
	private boolean isInternationalExtra;
	private int round;
	private int usage;
	
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
	public Calendar getDate() {
		return date;
	}
	public void setDate(Calendar date) {
		this.date = date;
	}
	public int getBasicValue() {
		return basicValue;
	}
	public void setBasicValue(int basicValue) {
		this.basicValue = basicValue;
	}
	public boolean isInternationalExtra() {
		return isInternationalExtra;
	}
	public void setInternationalExtra(boolean isInternationalExtra) {
		this.isInternationalExtra = isInternationalExtra;
	}
	public int getRound() {
		return round;
	}
	public void setRound(int round) {
		this.round = round;
	}
	public int getUsage() {
		return usage;
	}
	public void setUsage(int usage) {
		this.usage = usage;
	}
	@Override
	public String toString() {
		return "Match [team1=" + team1 + ", team2=" + team2 + ", date=" + (date!=null?date.getTime():"") + ", basicValue=" + basicValue
				+ ", isInternationalExtra=" + isInternationalExtra + ", Round=" + round + "]";
	}

	@Override
	public int compareTo(Match otherMatch) {
		if(this.date==null) {
			return 1;
		}
		if(otherMatch.getDate()==null) {
			return -1;
		}
		return this.date.compareTo(otherMatch.date);
	}
	
	
}
