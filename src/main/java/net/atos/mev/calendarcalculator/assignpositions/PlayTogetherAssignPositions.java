package net.atos.mev.calendarcalculator.assignpositions;

import java.util.ArrayList;

import net.atos.mev.calendarcalculator.Environment;
import net.atos.mev.calendarcalculator.Team;
import net.atos.mev.calendarcalculator.Tournament;
import net.atos.mev.calendarcalculator.checks.DerbyTeamsCheck;

public class PlayTogetherAssignPositions {

	DerbyTeamsCheck[] checks;
	int foundTeams;

	public void assignPositions(Tournament tour) {
		ArrayList<Team> assignedTeams = new ArrayList<Team>();
		foundTeams = 0;
		boolean foundResult = recursiveAttemptAssignPos(tour, assignedTeams);
		if(foundResult) {
			tour.applyMapPosToTeams();
			System.out.println(tour.toStringNames());
		} else {
			System.out.println("Not found result");
		}
	}

	public boolean recursiveAttemptAssignPos(Tournament tour, ArrayList<Team> assignedTeams) {
		boolean foundResult = false;
		if(assignedTeams.size() == tour.env.teams.length) {
			foundTeams++;
			if(foundTeams % 1000000 == 0) {
				System.out.println(foundTeams + " - " + tour.mapPosToTeams);
			}
			return true;
		} else {
			Team nextTeamToAssign = tour.env.teams[assignedTeams.size()];
			for(int attemptedPos = 1; attemptedPos <= tour.env.nTeams && !foundResult; attemptedPos++) {
				if(tour.mapPosToTeams.get(attemptedPos) == null) {
					tour.mapPosToTeams.put(attemptedPos, nextTeamToAssign);
					if(runAllChecksForTeam(tour, assignedTeams, nextTeamToAssign)) {
						assignedTeams.add(nextTeamToAssign);
						foundResult = recursiveAttemptAssignPos(tour, assignedTeams);
//						if(foundResult) {
//							return true;
//						}
						assignedTeams.remove(nextTeamToAssign);
					}
					tour.mapPosToTeams.put(attemptedPos, null);
				}
			}
		}
		return false;
	}

	public boolean runAllChecksForTeam(Tournament tour, ArrayList<Team> assignedTeams, Team nextTeamToAssign) {
		for(DerbyTeamsCheck currentCheck : checks) {
			if(currentCheck.getTeam1().equals(nextTeamToAssign) && assignedTeams.contains(currentCheck.getTeam2()) || currentCheck.getTeam2().equals(nextTeamToAssign) && assignedTeams.contains(currentCheck.getTeam1())) {
				boolean passesCheck = currentCheck.check(tour);
				if(!passesCheck) {
					return false;
				}
			}
		}
		return true;
	}

	public static void main(String[] args) {
		Environment env = new Environment();
		env.loadEnvironment(net.atos.mev.calendarcalculator.compat.DatabaseBatchSupport.selector(args));
		//Tournament t1 = Tournament.readFromLine(env, "010203040506040602050103050403020601030502060104040206030501");
		Tournament t1 = Tournament.readFromLine(env, "011602150314041305120611071008091501140213031204110510060907160801140213031204110510060907081516130112021103100409050806160714150112021103100409050806071513141611011002090308040705160612151314011002090308040705061511141213160901080207030604160510151114121301080207030604051509141013111216070106020503160408150914101311120106020503041507140813091210111605010402160306150714081309121011010402031505140613071208110910160301160204150514061307120811091001021503140413051206110710080916");
//		Team team1 = env.teams[0];
//		Team team2 = env.teams[1];
//		Team team3 = env.teams[2];
//		Team team4 = env.teams[3];
//		Team team5 = env.teams[4];
//		Team team6 = env.teams[5];
		Team team1 = env.teams[2];
		Team team2 = env.teams[14];
		Team team3 = env.teams[11];
		Team team4 = env.teams[7];
		Team team5 = env.teams[13];
		Team team6 = env.teams[15];
		//Team[] allTeams = {team1, team2, team3, team4, team5, team6};
		DerbyTeamsCheck checker1 = new DerbyTeamsCheck(team1, team2);
		DerbyTeamsCheck checker2 = new DerbyTeamsCheck(team3, team4);
		DerbyTeamsCheck checker3 = new DerbyTeamsCheck(team5, team6);
		DerbyTeamsCheck[] allChecks = {checker1, checker2, checker3};
		PlayTogetherAssignPositions execution = new PlayTogetherAssignPositions();
		execution.checks = allChecks;
		execution.assignPositions(t1);
	}

}
