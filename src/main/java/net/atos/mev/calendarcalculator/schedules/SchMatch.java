package net.atos.mev.calendarcalculator.schedules;

import java.time.LocalDate;

import net.atos.mev.calendarcalculator.Match;

public class SchMatch {
	
	String competition;
	String competitionRound;
	String calendarRound;
	LocalDate date;
	String time;
	boolean confirmed;
	Match theMatch;

	public SchMatch(Match m) {
		theMatch=m;
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

	public String getTime() {
		return time;
	}

	public void setTime(String time) {
		this.time = time;
	}

	public boolean isConfirmed() {
		return confirmed;
	}

	public void setConfirmed(boolean confirmed) {
		this.confirmed = confirmed;
	}

	public Match getTheMatch() {
		return theMatch;
	}

	public void setTheMatch(Match theMatch) {
		this.theMatch = theMatch;
	}
	
	public String getSchMatchId() {
		return theMatch.getSchMatchId();
	}
	
}
