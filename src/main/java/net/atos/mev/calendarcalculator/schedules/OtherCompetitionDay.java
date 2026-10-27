package net.atos.mev.calendarcalculator.schedules;

import java.time.LocalDate;
import java.util.ArrayList;

public class OtherCompetitionDay {

	String competition;
	String competitionRound;
	String calendarRound;
	LocalDate date;
	ArrayList<String> teams;
	String hour;
	String teamHome;
	String teamAway;

	public OtherCompetitionDay() {
		teams = new ArrayList<String>();
	}

	public String getCompetition() {
		return competition;
	}
	public void setCompetition(String competition) {
		this.competition = competition;
	}
	public String getCompetitionRound() {
		return competitionRound;
	}
	public void setCompetitionRound(String competitionRound) {
		this.competitionRound = competitionRound;
	}
	public String getCalendarRound() {
		return calendarRound;
	}
	public void setCalendarRound(String calendarRound) {
		this.calendarRound = calendarRound;
	}
	public LocalDate getDate() {
		return date;
	}
	public void setDate(LocalDate date) {
		this.date = date;
	}
	public ArrayList<String> getTeams() {
		return teams;
	}
	public void setTeams(ArrayList<String> teams) {
		this.teams = teams;
	}
	public String getHour() {
		return hour;
	}
	public void setHour(String hour) {
		this.hour = hour;
	}
	public String getTeamHome() {
		return teamHome;
	}
	public void setTeamHome(String teamHome) {
		this.teamHome = teamHome;
	}
	public String getTeamAway() {
		return teamAway;
	}
	public void setTeamAway(String teamAway) {
		this.teamAway = teamAway;
	}
	public boolean hasMatchDetails() {
		return teamHome != null && !teamHome.isBlank() && teamAway != null && !teamAway.isBlank();
	}

}
