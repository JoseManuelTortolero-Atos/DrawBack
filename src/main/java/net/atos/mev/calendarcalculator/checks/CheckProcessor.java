package net.atos.mev.calendarcalculator.checks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import net.atos.mev.calendarcalculator.Environment;
import net.atos.mev.calendarcalculator.Team;
import net.atos.mev.calendarcalculator.Tournament;

public class CheckProcessor {

	public static boolean runAllChecksForTeam(Tournament tour, Check[] checks, ArrayList<Team> assignedTeams, Team nextTeamToAssign) {
//		System.out.println(tour.mapPosToTeams + " - " + nextTeamToAssign.getCode());
		for(Check currentCheckGeneric : checks) {
			if(currentCheckGeneric instanceof DerbyTeamsCheck) {
				DerbyTeamsCheck currentCheck = (DerbyTeamsCheck)currentCheckGeneric;
				if(currentCheck.getTeam1().equals(nextTeamToAssign) && assignedTeams.contains(currentCheck.getTeam2()) || currentCheck.getTeam2().equals(nextTeamToAssign) && assignedTeams.contains(currentCheck.getTeam1())) {
//					System.out.println("------------- RUNNING CHECK DerbyTeamsCheck -------------");
					boolean passesCheck = currentCheck.check(tour);
					if(!passesCheck) {
						return false;
					}
				}
			}
			if(currentCheckGeneric instanceof LimitHomeTogetherCheck) {
				LimitHomeTogetherCheck currentCheck = (LimitHomeTogetherCheck)currentCheckGeneric;
				if(currentCheck.getTeam1().equals(nextTeamToAssign) && assignedTeams.contains(currentCheck.getTeam2()) || currentCheck.getTeam2().equals(nextTeamToAssign) && assignedTeams.contains(currentCheck.getTeam1())) {
//					System.out.println("------------- RUNNING CHECK LimitHomeTogetherCheck -------------");
					boolean passesCheck = currentCheck.check(tour);
					if(!passesCheck) {
						return false;
					}
				}
			}
			else if(currentCheckGeneric instanceof PlayTogetherTeamsCheck) {
				PlayTogetherTeamsCheck currentCheck = (PlayTogetherTeamsCheck)currentCheckGeneric;
				if(currentCheck.getTeam1().equals(nextTeamToAssign) && assignedTeams.contains(currentCheck.getTeam2()) || currentCheck.getTeam2().equals(nextTeamToAssign) && assignedTeams.contains(currentCheck.getTeam1())) {
//					System.out.println("------------- RUNNING CHECK PlayTogetherTeamsCheck -------------");
					boolean passesCheck = currentCheck.check(tour);
					if(!passesCheck) {
						return false;
					}
				}
			}
			else if(currentCheckGeneric instanceof MatchNotInRoundCheck) {
				MatchNotInRoundCheck currentCheck = (MatchNotInRoundCheck)currentCheckGeneric;
				if(currentCheck.getTeam1().equals(nextTeamToAssign) && assignedTeams.contains(currentCheck.getTeam2()) || currentCheck.getTeam2().equals(nextTeamToAssign) && assignedTeams.contains(currentCheck.getTeam1())) {
//					System.out.println("------------- RUNNING CHECK MatchNotInRoundCheck -------------");
					boolean passesCheck = currentCheck.check(tour);
					if(!passesCheck) {
						return false;
					}
				}
			}
			else if(currentCheckGeneric instanceof NotConsecutiveFarAwayCheck) {
				NotConsecutiveFarAwayCheck currentCheck = (NotConsecutiveFarAwayCheck)currentCheckGeneric;
				boolean newTeamInCheck = false;
				boolean allTeamsHavePos = true;
				for(Team t : currentCheck.farTeams) {
					if(t.equals(nextTeamToAssign)) {
						newTeamInCheck = true;
					} else if(!assignedTeams.contains(t)) {
						allTeamsHavePos = false;
					}
				}
				if(newTeamInCheck && allTeamsHavePos) {
//					System.out.println("------------- RUNNING CHECK NotConsecutiveFarAwayCheck -------------");
					boolean passesCheck = currentCheck.check(tour);
					if(!passesCheck) {
						return false;
					}
				}
			}
			else if(currentCheckGeneric instanceof LimitAgainstTeamsInRoundsCheck) {
				LimitAgainstTeamsInRoundsCheck currentCheck = (LimitAgainstTeamsInRoundsCheck)currentCheckGeneric;
				if(Arrays.asList(currentCheck.getSeparateTeams()).contains(nextTeamToAssign)) {
//					System.out.println("------------- RUNNING CHECK LimitAgainstTeamsInRoundsCheck -------------");
					boolean passesCheck = currentCheck.check(tour);
					if(!passesCheck) {
						return false;
					}
				}
			}
			else if(currentCheckGeneric instanceof LimitAgainstTeamsInRoundsHomeCheck) {
				LimitAgainstTeamsInRoundsHomeCheck currentCheck = (LimitAgainstTeamsInRoundsHomeCheck)currentCheckGeneric;
				if(Arrays.asList(currentCheck.getSeparateTeams()).contains(nextTeamToAssign)) {
//					System.out.println("------------- RUNNING CHECK LimitAgainstTeamsInRoundsCheck -------------");
					boolean passesCheck = currentCheck.check(tour);
					if(!passesCheck) {
						return false;
					}
				}
			}
			else if(currentCheckGeneric instanceof SeparateMatchesCheck) {
				SeparateMatchesCheck currentCheck = (SeparateMatchesCheck)currentCheckGeneric;
				if(Arrays.asList(currentCheck.getSeparateTeams()).contains(nextTeamToAssign)) {
//					System.out.println("------------- RUNNING CHECK SeparateMatchesCheck -------------");
					boolean passesCheck = currentCheck.check(tour);
					if(!passesCheck) {
						return false;
					}
				}
			}
			else if(currentCheckGeneric instanceof ForcePositionsCheck) {
				ForcePositionsCheck currentCheck = (ForcePositionsCheck)currentCheckGeneric;
				if(currentCheck.getTeam1().equals(nextTeamToAssign)) {
//					System.out.println("------------- RUNNING CHECK ForcePositionsCheck -------------");
					boolean passesCheck = currentCheck.check(tour);
					if(!passesCheck) {
						return false;
					}
				}
			}
		}
		return true;
	}

	
	/**
	 * @param checkDescription: For example:
	 * 	check.1=PlayTogetherTeamsCheck,RSB,MCO
		check.3=DerbyTeamsCheck,RSB,HAG
		check.6=MatchNotInRoundCheck,RCA,WAC
	 * @return
	 */
	public static Check createCheck(Environment env, String checkDescription) {
		if (env == null || checkDescription == null || checkDescription.isBlank()) {
			return null;
		}
		String[] parts = Arrays.stream(checkDescription.split(",", -1)).map(String::trim).toArray(String[]::new);
		if (parts.length == 0 || parts[0].isEmpty()) {
			return null;
		}
		int rounds = env.nRounds;
		try {
			return switch (parts[0]) {
			case "DerbyTeamsCheck" -> {
				if (parts.length != 3 || !validPair(parts[1], parts[2])) yield null;
				Team first = lookupTeam(env, parts[1]);
				Team second = lookupTeam(env, parts[2]);
				yield first == null || second == null ? null : new DerbyTeamsCheck(first, second);
			}
			case "LimitHomeTogetherCheck" -> {
				if (parts.length != 4 || !validPair(parts[1], parts[2])) yield null;
				Integer limit = integer(parts[3]);
				if (limit == null || limit < 0 || limit > 2 * rounds) yield null;
				Team first = lookupTeam(env, parts[1]);
				Team second = lookupTeam(env, parts[2]);
				yield first == null || second == null ? null : new LimitHomeTogetherCheck(first, second, limit);
			}
			case "PlayTogetherTeamsCheck" -> {
				if (parts.length != 3 || !validPair(parts[1], parts[2])) yield null;
				Team first = lookupTeam(env, parts[1]);
				Team second = lookupTeam(env, parts[2]);
				yield first == null || second == null ? null : new PlayTogetherTeamsCheck(first, second);
			}
			case "MatchNotInRoundCheck" -> {
				if (parts.length < 4 || !validPair(parts[1], parts[2]) || rounds < 1) yield null;
				Team first = lookupTeam(env, parts[1]);
				Team second = lookupTeam(env, parts[2]);
				int[] forbidden = parseRounds(parts, 3, rounds, true);
				yield first == null || second == null || forbidden == null ? null : new MatchNotInRoundCheck(first, second, forbidden);
			}
			case "NotConsecutiveFarAwayCheck" -> {
				if (parts.length < 2 || !uniqueNonBlank(parts, 1)) yield null;
				Team[] farTeams = lookupTeams(env, parts, 1);
				yield farTeams == null ? null : new NotConsecutiveFarAwayCheck(farTeams);
			}
			case "LimitAgainstTeamsInRoundsCheck", "LimitAgainstTeamsInRoundsHomeCheck" -> {
				if (parts.length < 5 || rounds < 1) yield null;
				Integer limit = integer(parts[1]);
				Integer count = integer(parts[2]);
				if (limit == null || limit < 0 || count == null || count < 1 || count > parts.length - 4
						|| !uniqueNonBlank(parts, 3, 3 + count)) yield null;
				Team[] targetTeams = lookupTeams(env, parts, 3, 3 + count);
				int[] selectedRounds = parseRounds(parts, 3 + count, rounds, false);
				if (targetTeams == null || selectedRounds == null) yield null;
				yield "LimitAgainstTeamsInRoundsCheck".equals(parts[0])
						? new LimitAgainstTeamsInRoundsCheck(limit, targetTeams, selectedRounds)
						: new LimitAgainstTeamsInRoundsHomeCheck(limit, targetTeams, selectedRounds);
			}
			case "SeparateMatchesCheck" -> {
				if (parts.length < 4 || !uniqueNonBlank(parts, 2)) yield null;
				Integer separation = integer(parts[1]);
				if (separation == null || separation < 1 || separation > rounds) yield null;
				Team[] separateTeams = lookupTeams(env, parts, 2);
				yield separateTeams == null ? null : new SeparateMatchesCheck(separateTeams, separation);
			}
			case "ForcePositionsCheck" -> {
				if (parts.length != 3) yield null;
				Integer position = integer(parts[2]);
				if (position == null || position < 1 || position > env.nTeams) yield null;
				Team team = lookupTeam(env, parts[1]);
				yield team == null ? null : new ForcePositionsCheck(team, position);
			}
			default -> null;
			};
		} catch (NumberFormatException | IndexOutOfBoundsException e) {
			return null;
		}
	}

	private static boolean validPair(String first, String second) {
		return first != null && !first.isBlank() && second != null && !second.isBlank() && !first.equals(second);
	}

	private static boolean uniqueNonBlank(String[] parts, int start) {
		return uniqueNonBlank(parts, start, parts.length);
	}

	private static boolean uniqueNonBlank(String[] parts, int start, int end) {
		Set<String> values = new HashSet<>();
		for (int i = start; i < end; i++) {
			if (parts[i].isBlank() || !values.add(parts[i])) {
				return false;
			}
		}
		return true;
	}

	private static Integer integer(String value) {
		try {
			return Integer.valueOf(value);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static int[] parseRounds(String[] parts, int start, int firstLegRounds, boolean mirrorSecondLeg) {
		if (start >= parts.length) {
			return null;
		}
		int[] rounds = new int[parts.length - start];
		for (int i = 0; i < rounds.length; i++) {
			Integer round = integer(parts[start + i]);
			if (round == null || round < 1 || round > 2 * firstLegRounds) {
				return null;
			}
			rounds[i] = mirrorSecondLeg ? (round - 1) % firstLegRounds : round - 1;
		}
		return rounds;
	}

	private static Team[] lookupTeams(Environment env, String[] parts, int start) {
		return lookupTeams(env, parts, start, parts.length);
	}

	private static Team[] lookupTeams(Environment env, String[] parts, int start, int end) {
		Team[] teams = new Team[end - start];
		for (int i = 0; i < teams.length; i++) {
			teams[i] = lookupTeam(env, parts[start + i]);
			if (teams[i] == null) {
				return null;
			}
		}
		return teams;
	}

	private static Team lookupTeam(Environment env, String teamCode) {
		return env.searchTeamByCode(teamCode);
	}

	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
