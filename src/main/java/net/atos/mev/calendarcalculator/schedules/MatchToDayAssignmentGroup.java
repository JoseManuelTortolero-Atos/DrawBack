package net.atos.mev.calendarcalculator.schedules;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class MatchToDayAssignmentGroup {
	
	String[] matchCodes;
	SchMatch[] theMatchSch;
	String[] matchTeamHome;
	String[] matchTeamAway;
	SchCalendar calendar;
	int[] roundOfMatch;
	int[] dayAssigned;
	int[] matchesAssignedPerDay;
	boolean canUseMAJ;
	String[] teamsInGroup;
	String[] allTeams;
	HashMap<String,ArrayList<SchDayAssignment>> assignedMatchesPerTeam = new HashMap<String,ArrayList<SchDayAssignment>>();
	int amountOfMAJ=0;
	int amountOf2dayRest=0;
	int amountOfDaysWith4Matches=0;
	int amountOfNotWeekendMatches=0;
	int amountTimesExtremeTeamsPlaySameDayOrTwoSeparate=0;
	int amountOfMatchesOnMonday=0;
	int differenceTeamsMidweek=0;
	int differenceTeamsMatchesFriday=0;
	int differenceTeamsMatchesSaturday=0;
	int diffBetweenSatAndSun=0;
	HashMap<String,int[]> matchesPerWeekdayPerTeam = new HashMap<String,int[]>();
	double deviationFromWeekdayFair=0;
	String[] teamsToEqual; 
	int nLastMatchNotAssigned = -1;
	String strLastMatchNotAssigned = "";
	
	public MatchToDayAssignmentGroup(SchCalendar calendar, int nMatches, int nDays, String[] teamsInGroup, String[] allTeams, boolean canUseMAJ, String[] teamsToEqual) {
		this.calendar = calendar;
		this.teamsToEqual = teamsToEqual;
		matchCodes = new String[nMatches];
		theMatchSch = new SchMatch[nMatches];
		matchTeamHome = new String[nMatches];
		matchTeamAway = new String[nMatches];
		roundOfMatch = new int[nMatches];
		dayAssigned = new int[nMatches];
		matchesAssignedPerDay = new int[nDays];
		this.teamsInGroup = teamsInGroup;
		this.allTeams = allTeams;
		for(String teamCode : allTeams) {
			assignedMatchesPerTeam.put(teamCode, new ArrayList<SchDayAssignment>());
			matchesPerWeekdayPerTeam.put(teamCode, new int[8]);
		}
		this.canUseMAJ = canUseMAJ;
	}
	
	public MatchToDayAssignmentGroup makeCopy() {
		MatchToDayAssignmentGroup result = new MatchToDayAssignmentGroup(calendar, matchCodes.length, matchesAssignedPerDay.length, teamsInGroup, allTeams, canUseMAJ, teamsToEqual);
		result.dayAssigned = Arrays.copyOf(dayAssigned, dayAssigned.length);
		result.amountOfMAJ = this.amountOfMAJ;
		result.amountOfDaysWith4Matches = this.amountOfDaysWith4Matches;
		result.amountOfNotWeekendMatches = this.amountOfNotWeekendMatches;
		result.amountTimesExtremeTeamsPlaySameDayOrTwoSeparate = this.amountTimesExtremeTeamsPlaySameDayOrTwoSeparate;
		result.amountOfMatchesOnMonday = this.amountOfMatchesOnMonday;
		result.differenceTeamsMatchesFriday=this.differenceTeamsMatchesFriday;
		result.differenceTeamsMidweek=this.calculateDiffTeamsMidWeek();
		//result.differenceTeamsMatchesSaturday=this.differenceTeamsMatchesSaturday;
		result.diffBetweenSatAndSun = this.calculateDiffEachTeamTwoWeekdays(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);;
		return result;
	}
	
	public void addTeamMatchAssignment(String teamCode, int day, int distance,boolean homeTeam, boolean otherCompetition) {
		if(teamCode != null) {
			SchDayAssignment scda = new SchDayAssignment();
			scda.calendarDay=day;
			scda.rest=distance;
			scda.isAtHome = homeTeam || otherCompetition;
			if(assignedMatchesPerTeam.get(teamCode)==null) {
				assignedMatchesPerTeam.put(teamCode, new ArrayList<SchDayAssignment>());
			}
			assignedMatchesPerTeam.get(teamCode).add(scda);
			int weekDay = calendar.matchdays[day].date.getDayOfWeek().getValue();
			if(homeTeam) {
				matchesPerWeekdayPerTeam.get(teamCode)[weekDay]++;
			}
		}
	}
	
	public void addPreviousAssignedMatch(int day, int distance, String teamHome, String teamAway) {
		matchesAssignedPerDay[day]++;
		addTeamMatchAssignment(teamHome, day, distance, true, false);
		addTeamMatchAssignment(teamAway, day, distance, false, false);
	}

	public void addNewAssignedMatch(int nMatch, int day, int distance, String teamHome, String teamAway) {
		dayAssigned[nMatch] = day;
		matchesAssignedPerDay[day]++;
		addTeamMatchAssignment(teamHome, day, distance, true, false);
		addTeamMatchAssignment(teamAway, day, distance, false, false);
	}
	
	public void removeAssignedMatch(int nMatch, int day, String teamHome, String teamAway) {
		dayAssigned[nMatch] = 0;
		matchesAssignedPerDay[day]--;
		assignedMatchesPerTeam.get(teamHome).remove(assignedMatchesPerTeam.get(teamHome).size()-1);
		assignedMatchesPerTeam.get(teamAway).remove(assignedMatchesPerTeam.get(teamAway).size()-1);
		int weekDay = calendar.matchdays[day].date.getDayOfWeek().getValue();
		matchesPerWeekdayPerTeam.get(teamHome)[weekDay]--;
		//matchesPerWeekdayPerTeam.get(teamAway)[weekDay]--;
	}
	
	public int calculateDiffTeamsMidWeek() {
		int maxValue=0;
		int minValue=-1;
		for(String teamInGroup : teamsToEqual) {
			int midWeekMatches = matchesPerWeekdayPerTeam.get(teamInGroup)[DayOfWeek.MONDAY.getValue()] + matchesPerWeekdayPerTeam.get(teamInGroup)[DayOfWeek.TUESDAY.getValue()] + matchesPerWeekdayPerTeam.get(teamInGroup)[DayOfWeek.WEDNESDAY.getValue()] + matchesPerWeekdayPerTeam.get(teamInGroup)[DayOfWeek.THURSDAY.getValue()] + matchesPerWeekdayPerTeam.get(teamInGroup)[DayOfWeek.FRIDAY.getValue()];
			if(maxValue<midWeekMatches) {
				maxValue=midWeekMatches;
			}
			if(minValue == -1 || midWeekMatches<minValue) {
				minValue=midWeekMatches;
			}
		}
		return maxValue-minValue;
	}
	
	public int calculateDiffTeamsInWeekday(DayOfWeek weekDay) {
		int maxValue=0;
		int minValue=-1;
		for(String teamInGroup : teamsToEqual) {
			int fridayMatches = matchesPerWeekdayPerTeam.get(teamInGroup)[weekDay.getValue()];
			if(maxValue<fridayMatches) {
				maxValue=fridayMatches;
			}
			if(minValue == -1 || fridayMatches<minValue) {
				minValue=fridayMatches;
			}
		}
		return maxValue-minValue;
	}
	
	public int calculateDiffEachTeamTwoWeekdays(DayOfWeek saturday, DayOfWeek sunday) {
		int maxValue=-100;
		int minValue=100;
		for(String teamInGroup : teamsToEqual) {
			int saturdayMatches = matchesPerWeekdayPerTeam.get(teamInGroup)[saturday.getValue()];
			int sundayMatches = matchesPerWeekdayPerTeam.get(teamInGroup)[sunday.getValue()];
			int diffTwoWeekdays = saturdayMatches-sundayMatches;
			if(maxValue<diffTwoWeekdays) {
				maxValue=diffTwoWeekdays;
			}
			if(diffTwoWeekdays<minValue) {
				minValue=diffTwoWeekdays;
			}
		}
		return maxValue-minValue;
	}
	
	public boolean canThisAssignementBeBetterThanBest(MatchToDayAssignmentGroup bestAssignment, boolean isPartialAssignment) {
		if(bestAssignment==null) {
			return true;
		} else  {
			if(amountOfMAJ < bestAssignment.amountOfMAJ) {
				return true;
			} else if(amountOfMAJ == bestAssignment.amountOfMAJ) {
				if(amountTimesExtremeTeamsPlaySameDayOrTwoSeparate < bestAssignment.amountTimesExtremeTeamsPlaySameDayOrTwoSeparate) {
					return true;
				} else if(amountTimesExtremeTeamsPlaySameDayOrTwoSeparate == bestAssignment.amountTimesExtremeTeamsPlaySameDayOrTwoSeparate) {
					if(amountOfDaysWith4Matches < bestAssignment.amountOfDaysWith4Matches) {
						return true;
					} else if(amountOfDaysWith4Matches == bestAssignment.amountOfDaysWith4Matches) {
						if(amountOfNotWeekendMatches < bestAssignment.amountOfNotWeekendMatches) {
							return true;
						} else if(amountOfNotWeekendMatches == bestAssignment.amountOfNotWeekendMatches) {
							if(amountOfMatchesOnMonday < bestAssignment.amountOfMatchesOnMonday) {
								return true;
							} else if(amountOfMatchesOnMonday == bestAssignment.amountOfMatchesOnMonday) {
								if(isPartialAssignment) {
									return true;
								}
								differenceTeamsMidweek = calculateDiffTeamsMidWeek();
								if(differenceTeamsMidweek < bestAssignment.differenceTeamsMidweek) {
									return true;
								} else if(differenceTeamsMidweek == bestAssignment.differenceTeamsMidweek) {
									//									if(differenceTeamsMatchesFriday < bestAssignment.differenceTeamsMatchesFriday) {
									//										return true;
									//									} else if(differenceTeamsMatchesFriday == bestAssignment.differenceTeamsMatchesFriday) {
									diffBetweenSatAndSun = calculateDiffEachTeamTwoWeekdays(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
									if(diffBetweenSatAndSun < bestAssignment.diffBetweenSatAndSun) {
										return true;
									} else if(diffBetweenSatAndSun == bestAssignment.diffBetweenSatAndSun) {
										return false;
									}
									//									if(differenceTeamsMatchesSaturday < bestAssignment.differenceTeamsMatchesSaturday) {
									//										return true;
									//									} else if(differenceTeamsMatchesSaturday == bestAssignment.differenceTeamsMatchesSaturday) {
									//										return false;
									//									}				
								}				
								//								}
							}				
						}				
					}				
				}				
			}
		}
		
		return false;
	}

	// 1 = the day is available
	// 0 =- the day is not available because of other matches
	// -1 = the day is not available only because of a MAJ
	public int newMatchEnoughRest(String teamCode, int day) {
		int result = 1;
		for(SchDayAssignment scda : assignedMatchesPerTeam.get(teamCode)) {
			// TODO: Not needed for Morocco, but this comparison assumes that the rest of the new match is never bigger that the other match
			if(Math.abs(scda.calendarDay-day) <= scda.rest) {
				if(scda.isMAJ) {
					result = 0;
				} else {
					return -1;
				}
			}
		}
		return result; 
	}

	public String[] getMatchCodes() {
		return matchCodes;
	}



	public void setMatchCodes(String[] matchCodes) {
		this.matchCodes = matchCodes;
	}



	public int[] getDayAssigned() {
		return dayAssigned;
	}



	public void setDayAssigned(int[] dayAssigned) {
		this.dayAssigned = dayAssigned;
	}

	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}


}
