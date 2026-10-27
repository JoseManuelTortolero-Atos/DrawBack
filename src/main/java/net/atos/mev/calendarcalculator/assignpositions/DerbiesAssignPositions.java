package net.atos.mev.calendarcalculator.assignpositions;

import java.util.ArrayList;

import net.atos.mev.calendarcalculator.Environment;
import net.atos.mev.calendarcalculator.Team;
import net.atos.mev.calendarcalculator.Tournament;
import net.atos.mev.calendarcalculator.checks.Check;
import net.atos.mev.calendarcalculator.checks.CheckProcessor;
import net.atos.mev.calendarcalculator.checks.DerbyTeamsCheck;
import net.atos.mev.calendarcalculator.checks.PlayTogetherTeamsCheck;

public class DerbiesAssignPositions {

	Check[] checks;
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
			//if(foundTeams % 1000000 == 0) {
				System.out.println(foundTeams + " - " + tour.mapPosToTeams);
			//}
			return true;
		} else {
			Team nextTeamToAssign = tour.env.teams[assignedTeams.size()];
			for(int attemptedPos = 1; attemptedPos <= tour.env.nTeams && !foundResult; attemptedPos++) {
				if(tour.mapPosToTeams.get(attemptedPos) == null) {
					tour.mapPosToTeams.put(attemptedPos, nextTeamToAssign);
					if(CheckProcessor.runAllChecksForTeam(tour, checks, assignedTeams, nextTeamToAssign)) {
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

	public static void main(String[] args) {
		Environment env = new Environment();
		//env.loadEnvironment("Test6Teams.properties");
		//Tournament t1 = Tournament.readFromLine(env, "010203040506040602050103050403020601030502060104040206030501");
		env.loadEnvironment(net.atos.mev.calendarcalculator.compat.DatabaseBatchSupport.selector(args));
		Tournament t1 = Tournament.readFromLine(env, "010403020506070809101112131415160205030706010804101312151409161101080406050307020916111413121510020903110507080610011204141516130112041006140716090311021305150802130315050907111006120814011604011604140612081009071105130315020201030405150713091112101408160601030402060708051014121613091511020603080501071509041113141216100107040506030802101112091416151302100314051207040915110113061608011504110609081310051207140216030216031205140710090811061301150401090413061508111003120214071605");
//		team.0=RSB
//		team.1=MCO
//		team.2=HAG
//		team.3=OCS
//		team.4=RCA
//		team.5=WAC
//		team.6=FUS
//		team.7=FAR
		Team team1 = env.teams[0];
		Team team2 = env.teams[1];
		Team team3 = env.teams[2];
		Team team4 = env.teams[3];
		Team team5 = env.teams[4];
		Team team6 = env.teams[5];
		Team team7 = env.teams[6];
		Team team8 = env.teams[7];
		//Team[] allTeams = {team1, team2, team3, team4, team5, team6};
		PlayTogetherTeamsCheck checker1 = new PlayTogetherTeamsCheck(team1, team2);
		PlayTogetherTeamsCheck checker2 = new PlayTogetherTeamsCheck(team3, team4);
		DerbyTeamsCheck checker3 = new DerbyTeamsCheck(team1, team3);
		DerbyTeamsCheck checker4 = new DerbyTeamsCheck(team5, team6);
		DerbyTeamsCheck checker5 = new DerbyTeamsCheck(team7, team8);
		Check[] allChecks = {checker1, checker2, checker3, checker4, checker5};
		DerbiesAssignPositions execution = new DerbiesAssignPositions();
		execution.checks = allChecks;
		execution.assignPositions(t1);
	}

}
