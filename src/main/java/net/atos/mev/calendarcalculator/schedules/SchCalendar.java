package net.atos.mev.calendarcalculator.schedules;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class SchCalendar {
	
	SchEnvironment schEnv;
	Matchday[] matchdays;
	
	public SchCalendar(SchEnvironment schEnv) {
		this.schEnv = schEnv;
		Long nMatchdays = ChronoUnit.DAYS.between(schEnv.start, schEnv.end);
		matchdays = new Matchday[nMatchdays.intValue()+1];
		for(int i=0; i<matchdays.length; i++) {
			matchdays[i] = new Matchday();
			LocalDate newDate = schEnv.start.plusDays(i);
			matchdays[i].setDate(newDate);
		}
	}
	
	SchMatch findMatch(String matchId) {
		for(Matchday md : matchdays) {
			for(SchMatch ma : md.getMatches()) {
				if(ma.getSchMatchId().equals(matchId)) {
					return ma;
				}
			}
		}
		return null;
	}
	
	int dateNumber(String dateName) {
		LocalDate finalDate = SchEnvironment.readDate(dateName);
		Long nDays = ChronoUnit.DAYS.between(schEnv.start, finalDate);
		return nDays.intValue();
	}
	
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

	public SchEnvironment getSchEnv() {
		return schEnv;
	}

	public void setSchEnv(SchEnvironment schEnv) {
		this.schEnv = schEnv;
	}

	public Matchday[] getMatchdays() {
		return matchdays;
	}

	public void setMatchdays(Matchday[] matchdays) {
		this.matchdays = matchdays;
	}

}
