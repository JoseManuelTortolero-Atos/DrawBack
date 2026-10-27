package net.atos.mev.calendarcalculator.assignpositions;

import java.security.SecureRandom;
import java.util.*;

import net.atos.mev.calendarcalculator.Environment;
import net.atos.mev.calendarcalculator.Team;
import net.atos.mev.calendarcalculator.TeamsAssignments;
import net.atos.mev.calendarcalculator.Tournament;
import net.atos.mev.calendarcalculator.checks.Check;
import net.atos.mev.calendarcalculator.checks.CheckProcessor;
import net.atos.mev.calendarcalculator.checks.ForcePositionsCheck;
import net.atos.mev.calendarcalculator.fileio.AssignmentsFileManager;
import net.atos.mev.calendarcalculator.fileio.PositionsFileManager;

public class AssignPositions {

	int foundTeams;
	AssignmentsFileManager fileManager;
	boolean generateRandom = false;
	boolean generateOnlyOne = false;
	int[] chooseAttemptedPos;
	Environment env;
	ArrayList<AssignPositions> dependentExecutions = new ArrayList<AssignPositions>();
	// Modo en memoria (BD): sin ficheros en results/ y con presupuesto de nodos para no colgarse
	boolean writeFiles = true;
	long maxNodes = Long.MAX_VALUE;
	long visitedNodes;

	public static AssignPositions inMemory(Environment env, long maxNodes) {
		AssignPositions exec = new AssignPositions();
		exec.env = env;
		exec.writeFiles = false;
		exec.maxNodes = maxNodes;
		exec.generateRandom = true;
		exec.generateOnlyOne = true;
		return exec;
	}

	public boolean assignPositions(Tournament tour) {
		ArrayList<Team> assignedTeams = new ArrayList<Team>();
		tour.mapPosToTeams = new TeamsAssignments();
		if(writeFiles && fileManager == null) {
			fileManager = new AssignmentsFileManager();
			fileManager.prepareOutputTournament(tour);
		}
		chooseAttemptedPos = new int[tour.env.nTeams];
		for(int nPos=0; nPos < tour.env.nTeams; nPos++) {
			chooseAttemptedPos[nPos] = nPos+1;
		}
		if(generateRandom) {
			SecureRandom random = new SecureRandom();
			for (int i=0; i < chooseAttemptedPos.length; i++) {
				int randomIndexToSwap = random.nextInt(chooseAttemptedPos.length);
				int temp = chooseAttemptedPos[randomIndexToSwap];
				chooseAttemptedPos[randomIndexToSwap] = chooseAttemptedPos[i];
				chooseAttemptedPos[i] = temp;
			}
		}
		foundTeams = 0;
		visitedNodes = 0;
		boolean foundResult = recursiveAttemptAssignPos(tour, assignedTeams);
		if(foundResult) {
			tour.applyMapPosToTeams();
			if (writeFiles) {
				System.out.println("++++++++++++++");
			}
		} else {
			/////System.out.println("Not found result");
		}
		return foundResult;
	}

	Set<Tournament> generated = new HashSet<>();

	public boolean recursiveAttemptAssignPos(Tournament tour, ArrayList<Team> assignedTeams) {
		boolean foundResult = false;
		if(assignedTeams.size() == tour.env.teams.length) {
			if (!writeFiles) {
				foundTeams++;
				return true;
			}
			if (generated.contains(tour)) {
				System.out.println("REPEATED "+ foundTeams + " - " + tour.mapPosToTeams);
				return false;
			}
			generated.add(tour);
			foundTeams++;
			System.out.println(foundTeams + " - " + tour.mapPosToTeams);
			fileManager.writeTournamentPos(tour);
			return true;
		} else {
			if (++visitedNodes > maxNodes) {
				return false;
			}
			Team nextTeamToAssign = tour.env.teams[assignedTeams.size()];
			for(int chooseAttemptedPosIdx=0; chooseAttemptedPosIdx < chooseAttemptedPos.length; chooseAttemptedPosIdx++) {
				int attemptedPos = chooseAttemptedPos[chooseAttemptedPosIdx];
				if(tour.mapPosToTeams.get(attemptedPos) == null) {
					tour.mapPosToTeams.put(attemptedPos, nextTeamToAssign);
					if(CheckProcessor.runAllChecksForTeam(tour, tour.env.checks, assignedTeams, nextTeamToAssign)) {
						assignedTeams.add(nextTeamToAssign);
						foundResult = recursiveAttemptAssignPos(tour, assignedTeams);
						if(foundResult && generateOnlyOne) {
							return true;
						}
						assignedTeams.remove(nextTeamToAssign);
					}
					tour.mapPosToTeams.put(attemptedPos, null);
				}
			}
		}
		return false;
	}

	public boolean isGenerateRandom() {
		return generateRandom;
	}

	public void setGenerateRandom(boolean generateRandom) {
		this.generateRandom = generateRandom;
	}

	public boolean isGenerateOnlyOne() {
		return generateOnlyOne;
	}

	public void setGenerateOnlyOne(boolean generateOnlyOne) {
		this.generateOnlyOne = generateOnlyOne;
	}

	public static void main(String[] args) {
		net.atos.mev.calendarcalculator.compat.DatabaseBatchSupport.runDraw(args);
	}

	/*static void linkStadiumSharingTeams(Tournament t1, Tournament t2, String[][] stadiumSharingTeams) {
		for(String[] sharingCouple : stadiumSharingTeams) {
			int posOfTeamInT1 = t1.mapPosToTeams.getKeyOfTeam(t1.env.searchTeamByCode(sharingCouple[0]));
			int posOfTeamInT2;
			if(posOfTeamInT1%2==0) {
				posOfTeamInT2=posOfTeamInT1-1;
			} else {
				posOfTeamInT2=posOfTeamInT1+1;
			}
			for(Check genericCheck : t2.env.checks) {
				if(genericCheck instanceof ForcePositionsCheck) {
					ForcePositionsCheck specificCheck = (ForcePositionsCheck)genericCheck;
					if(specificCheck.getTeam1().getCode().equals(sharingCouple[1])){
						specificCheck.setRound(posOfTeamInT2);
					}
				}
			}
		}
	}*/
	
	public static void linkStadiumSharingTeams(
	        Tournament t1,
	        Tournament t2,
	        String[][] stadiumSharingTeams) {

	    for(String[] sharingCouple : stadiumSharingTeams) {

	        System.out.println(
	            "sharingCouple="
	            + sharingCouple[0]
	            + " / "
	            + sharingCouple[1]
	        );

	        Team team = t1.env.searchTeamByCode(sharingCouple[0]);

	        if(team == null) {
	            System.out.println(
	                "TEAM NOT FOUND: "
	                + sharingCouple[0]
	            );
	            continue;
	        }

	        int posOfTeamInT1 =
	                t1.mapPosToTeams.getKeyOfTeam(team);

	        int posOfTeamInT2;

	        if(posOfTeamInT1 % 2 == 0) {
	            posOfTeamInT2 = posOfTeamInT1 - 1;
	        } else {
	            posOfTeamInT2 = posOfTeamInT1 + 1;
	        }

	        for(Check genericCheck : t2.env.checks) {

	            if(genericCheck instanceof ForcePositionsCheck) {

	                ForcePositionsCheck specificCheck =
	                        (ForcePositionsCheck) genericCheck;

	                if(specificCheck.getTeam1() == null) {
	                    System.out.println(
	                        "ForcePositionsCheck with null team"
	                    );
	                    continue;
	                }

	                if(specificCheck.getTeam1().getCode()
	                        .equals(sharingCouple[1])) {

	                    specificCheck.setRound(posOfTeamInT2);
	                }
	            }
	        }
	    }
	}
	
	
	
}
