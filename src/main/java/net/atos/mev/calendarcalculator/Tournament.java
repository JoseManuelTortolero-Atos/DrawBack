package net.atos.mev.calendarcalculator;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;
import java.util.Random;

public class Tournament {

	public Round[] rounds;
	public TeamsAssignments mapPosToTeams;
	public Environment env;

	public Tournament(Environment env) {
		this.env = env;
		rounds = new Round[env.nRounds];
		mapPosToTeams = new TeamsAssignments();
		for(int r=0; r<rounds.length; r++) {
			Round newRound = new Round(env);
			newRound.roundNumber = r+1;
			rounds[r] = newRound;
		}
	}

	public String toString() {
		String result="";
		for(Round r: rounds) {
			for(Match m: r.matches) {
				if(m==null) {
					m=new Match(0,0,env);
				}
				result+=m.toStringExportPos() + " ";
			}
			result += "| ";
		}
		return result;
//		String result = "";
//		for(Round r: rounds) {
//			result += "\r\n" + r.toString();
//		}
//		return result;
	}

	public String toStringMatrixFormatPos() {
		String result="";
		int[][] matrixRounds = new int[env.nTeams+1][env.nTeams+1];
		for(int nRound=0; nRound<this.rounds.length; nRound++) {
			for(Match m : this.rounds[nRound].matches) {
				if(m != null) {
					matrixRounds[m.getPos1()][m.getPos2()] = nRound+1;
					matrixRounds[m.getPos2()][m.getPos1()] = nRound+1;
				}
			}
		}
		for(int i=1; i<=env.nTeams; i++) {
			for(int j=1; j<=env.nTeams; j++) {
				result += matrixRounds[i][j] + " ";
			}
			result +="\r\n";
		}
		return result;
	}

	public String toStringNames() {
		String result = "";
		for(Round r: rounds) {
			result += "\r\n" + r.toStringNames();
		}
		return result;
	}

	public String toStringPos() {
		String result = "";
		for(Round r: rounds) {
			result += "\r\n" + r.toStringPos();
		}
		return result;
	}
	
	public String toStringOneLine() {
		String result="";
		for(Round r: rounds) {
			for(Match m: r.matches) {
				if(m==null) {
					m=new Match(0,0,env);
				}
				result+=m.toStringExportPos();
			}
		}
		return result;
	}
	
	public String toStringOneLineWithAssignments() {
		String result=toStringOneLine();
		result+=";";
		for(int i=1; i<=env.nTeams; i++) {
			result += this.mapPosToTeams.get(i).getCode() + ",";
		}
		return result;
	}
	
	// Sample input
	// 010403020506070809101112131415160205030706010804101312151409161101080406050307020916111413121510020903110507080610011204141516130112041006140716090311021305150802130315050907111006120814011604011604140612081009071105130315020201030405150713091112101408160601030402060708051014121613091511020603080501071509041113141216100107040506030802101112091416151302100314051207040915110113061608011504110609081310051207140216030216031205140710090811061301150401090413061508111003120214071605
	public static Tournament readFromLine(Environment env, String inputLine) {
		Tournament tour = new Tournament(env);
		int expectedLen = 4*env.nRounds*env.matchesPerRound;
		if(inputLine.length() == expectedLen) {
			int indexMatches=0;
			for(Round r: tour.rounds) {
				for(int m=0; m<r.matches.length; m++) {
					String firstTeam = inputLine.substring(indexMatches,indexMatches+2);
					String secondTeam = inputLine.substring(indexMatches+2,indexMatches+4);
					r.matches[m] = new Match(Integer.parseInt(firstTeam), Integer.parseInt(secondTeam), env);
					indexMatches = indexMatches+4;
				}
			}
		} else {
			System.out.println("Error reading tournament, line is " + inputLine.length() + " != " + expectedLen);
		}

		return tour;
	}
	
	// Sample input:
	// 010403020506070809101112131415160205030706010804101312151409161101080406050307020916111413121510020903110507080610011204141516130112041006140716090311021305150802130315050907111006120814011604011604140612081009071105130315020201030405150713091112101408160601030402060708051014121613091511020603080501071509041113141216100107040506030802101112091416151302100314051207040915110113061608011504110609081310051207140216030216031205140710090811061301150401090413061508111003120214071605;RSB,HUSA,OCS,MCO,RCA,WAC,FUS,FAR,IRT,MAT,MAS,RCOZ,SCCM,DHJ,CAYB,RCAZ,
	public static Tournament readFromLineWithAssignments(Environment env, String inputLine) {
		String[] splitInput = inputLine.split(";");
		Tournament tour = readFromLine(env, splitInput[0]);
		String[] splitWithTeams = splitInput[1].split(",");
		for(int i=0; i<env.nTeams; i++) {
			tour.mapPosToTeams.put(i+1, env.searchTeamByCode(splitWithTeams[i]));
		}
		tour.applyMapPosToTeams();
		return tour;
	}

	public void applyMapPosToTeams() {
		for(Round r: rounds) {
			for(Match m: r.matches) {
				m.setTeam1(mapPosToTeams.get(m.getPos1()));
				m.setTeam2(mapPosToTeams.get(m.getPos2()));
			}
		}
	}

	public void generateRandomMapPosToTeams() {
		SecureRandom rand = new SecureRandom();
		mapPosToTeams = new TeamsAssignments();
		for(int i=0; i<env.nTeams; i++) {
			int newPos = rand.nextInt(env.nTeams)+1;
			while(mapPosToTeams.get(newPos) != null) {
				newPos = rand.nextInt(env.nTeams)+1;
			}
			mapPosToTeams.put(newPos, env.teams[i]);
		}
	}

	public int lookupTwoHomeMatchesBefore(int pos, int currentRound) {
		int result=0;
		if(currentRound>0) {
			for(Match m1: this.rounds[currentRound-1].matches) {
				if(m1.getPos1() == pos) result++;
			}
			if(currentRound>1 && result==1) {
				for(Match m2: this.rounds[currentRound-2].matches) {
					if(m2.getPos1() == pos) result++;
				}
			}
		}
		return result;
	}

	public int lookupTwoAwayMatchesBefore(int pos, int currentRound) {
		int result=0;
		if(currentRound>0) {
			for(Match m1: this.rounds[currentRound-1].matches) {
				if(m1.getPos2() == pos) result++;
			}
			if(currentRound>1 && result==1) {
				for(Match m2: this.rounds[currentRound-2].matches) {
					if(m2.getPos2() == pos) result++;
				}
			}
		}
		return result;
	}
	
	public Match findMatch(int round, String teamHome, String teamAway) {
		if(round >= this.rounds.length) {
			// Searching for a match of second leg
			for(Match m : this.rounds[round-this.rounds.length].matches) {
				if(m.getTeam2().getCode().equals(teamHome) && m.getTeam1().getCode().equals(teamAway)) {
					Match returnMatch = new Match(m.getPos2(), m.getPos1(), env);
					returnMatch.setTeam1(m.getTeam2());
					returnMatch.setTeam2(m.getTeam1());
					return returnMatch;
				}
			}
		} else {
			for(Match m : this.rounds[round].matches) {
				if(m.getTeam1().getCode().equals(teamHome) && m.getTeam2().getCode().equals(teamAway)) {
					return m;
				}
			}
		}
		return null;
	}
	
	public static void main(String[] args) {
		String tournamentSample = "010403020506070809101112131415160205030706010804101312151409161101080406050307020916111413121510020903110507080610011204141516130112041006140716090311021305150802130315050907111006120814011604011604140612081009071105130315020201030405150713091112101408160601030402060708051014121613091511020603080501071509041113141216100107040506030802101112091416151302100314051207040915110113061608011504110609081310051207140216030216031205140710090811061301150401090413061508111003120214071605;RSB,HUSA,OCS,MCO,RCA,WAC,FUS,FAR,IRT,MAT,MAS,RCOZ,SCCM,DHJ,CAYB,RCAZ,";
		Environment env = new Environment();
		env.loadEnvironment(net.atos.mev.calendarcalculator.compat.DatabaseBatchSupport.selector(args));
		Tournament tour = Tournament.readFromLineWithAssignments(env, tournamentSample);
		System.out.println(tour.toStringNames());
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Tournament that = (Tournament) o;
		return Arrays.equals(rounds, that.rounds) && Objects.equals(mapPosToTeams, that.mapPosToTeams);
	}

	@Override
	public int hashCode() {
		int result = Objects.hash(mapPosToTeams);
		result = 31 * result + Arrays.hashCode(rounds);
		return result;
	}
}
