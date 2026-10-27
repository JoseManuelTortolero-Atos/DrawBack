package net.atos.mev.calendarcalculator.schedules;

import java.time.LocalDate;
import java.util.ArrayList;

public class Matchday {

	LocalDate date;
	String calendarRound;
	boolean availableForNewMatches = false;
	ArrayList<SchMatch> matches = new ArrayList<SchMatch>();
	ArrayList<OtherCompetitionDay> otherMatches = new ArrayList<OtherCompetitionDay>();
	
	public String getCalendarRound() {
		return calendarRound;
	}
	public void setCalendarRound(String calendarRound) {
		this.calendarRound = calendarRound;
	}
	public ArrayList<SchMatch> getMatches() {
		return matches;
	}
	public void setMatches(ArrayList<SchMatch> matches) {
		this.matches = matches;
	}
	public ArrayList<OtherCompetitionDay> getOtherMatches() {
		return otherMatches;
	}
	public void setOtherMatches(ArrayList<OtherCompetitionDay> otherMatches) {
		this.otherMatches = otherMatches;
	}

	public boolean isAvailableForNewMatches() {
		return availableForNewMatches;
	}

	public void setAvailableForNewMatches(boolean availableForNewMatches) {
		this.availableForNewMatches = availableForNewMatches;
	}
	public LocalDate getDate() {
		return date;
	}
	public void setDate(LocalDate date) {
		this.date = date;
	}

}
