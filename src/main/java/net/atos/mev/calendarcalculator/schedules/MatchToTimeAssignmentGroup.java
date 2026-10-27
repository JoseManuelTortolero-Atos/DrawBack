package net.atos.mev.calendarcalculator.schedules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MatchToTimeAssignmentGroup {

	private static final Logger log = LoggerFactory.getLogger(MatchToTimeAssignmentGroup.class);

	int[] bestTimeAssignment = null;
	int[] bestDiffPerTimeSlot;
	ArrayList<SchMatch> allMatches = new ArrayList<SchMatch>();
	int[] allTimeAssignments;
	HashMap<String, int[]> amountPerSlotPerTeam = new HashMap<String, int[]>();
	ArrayList<Integer> matchdayOfEachMatch = new ArrayList<Integer>();
	ArrayList<Integer> nInMatchdayOfMatch = new ArrayList<Integer>();
	int assignmentsMade=0;
	int nTimeslots=0;
	
	public MatchToTimeAssignmentGroup(int nTimeslots, String[] teamsToEqual) {
		this.nTimeslots = nTimeslots;
		for(String oneTeam : teamsToEqual) {
			amountPerSlotPerTeam.put(oneTeam,new int[nTimeslots]);
		}
	}

	public void addPreviousMatchTimeAssignment(int timeslot, String homeTeam, String awayTeam) {
		if(amountPerSlotPerTeam.get(homeTeam)!=null) {
			amountPerSlotPerTeam.get(homeTeam)[timeslot]++;
		}
//		if(amountPerSlotPerTeam.get(awayTeam)!=null) {
//			amountPerSlotPerTeam.get(awayTeam)[timeslot]++;
//		}
	}
	
	public void addTimeAssignment(int timeslot) {
		allTimeAssignments[assignmentsMade]=timeslot;
		String homeTeam = allMatches.get(assignmentsMade).theMatch.team1.getCode();
//		String awayTeam = allMatches.get(assignmentsMade).theMatch.team2.getCode();
		if(amountPerSlotPerTeam.get(homeTeam)!=null) {
			amountPerSlotPerTeam.get(homeTeam)[timeslot]++;
		}
//		if(amountPerSlotPerTeam.get(awayTeam)!=null) {
//			amountPerSlotPerTeam.get(awayTeam)[timeslot]++;
//		}
		assignmentsMade++;
	}
	
	public void removeLastTimeAssignment() {
		assignmentsMade--;
		String homeTeam = allMatches.get(assignmentsMade).theMatch.team1.getCode();
//		String awayTeam = allMatches.get(assignmentsMade).theMatch.team2.getCode();
		if(amountPerSlotPerTeam.get(homeTeam) != null) {
			amountPerSlotPerTeam.get(homeTeam)[allTimeAssignments[assignmentsMade]]--;
		}
//		if(amountPerSlotPerTeam.get(awayTeam) != null) {
//			amountPerSlotPerTeam.get(awayTeam)[allTimeAssignments[assignmentsMade]]--;
//		}
	}
	
	public void saveAsBestAssignment() {
		bestTimeAssignment = Arrays.copyOf(allTimeAssignments, allTimeAssignments.length);
	}
	
	public boolean canBeBetterThanBestAssignment() {
		int[] newDiffTimeslot = new int[this.nTimeslots];
		for(int iTimeslot=0; iTimeslot < nTimeslots; iTimeslot++) {
			int maxValue=0;
			int minValue=-1;
			for(int[] teamTimeslots : amountPerSlotPerTeam.values()) {
				if(teamTimeslots[iTimeslot]>maxValue) {
					maxValue=teamTimeslots[iTimeslot];
				}
				if(minValue==-1 || teamTimeslots[iTimeslot]<minValue) {
					minValue=teamTimeslots[iTimeslot];
				}
			}
			newDiffTimeslot[iTimeslot]=maxValue-minValue;
		}
		if(bestTimeAssignment == null) {
			bestDiffPerTimeSlot=newDiffTimeslot;
			log.debug("Found first solution: {}", Arrays.toString(bestDiffPerTimeSlot));
			return true;
		}
		//for(int iTimeslot=0; iTimeslot < nTimeslots; iTimeslot++) {
		for(int iTimeslot=nTimeslots-1; iTimeslot >=0; iTimeslot--) {
			if(newDiffTimeslot[iTimeslot]>bestDiffPerTimeSlot[iTimeslot]) {
				return false;
			} else if (newDiffTimeslot[iTimeslot]<bestDiffPerTimeSlot[iTimeslot]) {
				bestDiffPerTimeSlot=newDiffTimeslot;
				log.debug("Found new solution: {}", Arrays.toString(bestDiffPerTimeSlot));
				return true;
			}
		}
		return false;
	}

}
