package net.atos.mev.calendarcalculator;

import java.util.Objects;

public class Match {

	public Team team1;
	public Team team2;
	public int pos1;
	public int pos2;
	Environment env;

	public Match(int pos1, int pos2, Environment env) {
		this.pos1=pos1;
		this.pos2=pos2;
		this.env=env;
	}

	public String getSchMatchId() {
		return String.valueOf(team1.getCode()) + "," +String.valueOf(team2.getCode());
	}

	public String toStringPos() {
		String res="";
		if(pos1<10) {
			res += " ";
			res += pos1;
		} else {
			res += pos1;
		}
		res += "-";
		if(pos2<10) {
			res += " ";
			res += pos2;
		} else {
			res += pos2;
		}
		return res;
	}

	public String toStringExportPos() {
		String res="";
		if(pos1<10) {
			res += "0";
			res += pos1;
		} else {
			res += pos1;
		}
		if(pos2<10) {
			res += "0";
			res += pos2;
		} else {
			res += pos2;
		}
		return res;
	}

	public String toString() {
		return team1.getCode() + "-" + team2.getCode(); 
	}

	public String toStringNames() {
		return team1.getName() + "-" + team2.getName(); 
	}

	public String toStringNamesWithPadding(int lenFirstName) {
		String firstName = team1.getName();
		while(firstName.length() < lenFirstName) {
			firstName = " " + firstName;
		}
		return firstName + "-" + team2.getName(); 
	}

	public Team getTeam1() {
		return team1;
	}
	public void setTeam1(Team team1) {
		this.team1 = team1;
	}
	public Team getTeam2() {
		return team2;
	}
	public void setTeam2(Team team2) {
		this.team2 = team2;
	}
	public int getPos1() {
		return pos1;
	}
	public void setPos1(int pos1) {
		this.pos1 = pos1;
	}
	public int getPos2() {
		return pos2;
	}
	public void setPos2(int pos2) {
		this.pos2 = pos2;
	}

	public int getPosId() {
		return getPosId(env, this.pos1, this.pos2);
	}
	
	public static int getPosId(Environment env, int pos1, int pos2) {
		return env.nTeams*pos1+pos2;
	}

	public int getPosNoOrderId() {
		return getPosNoOrderId(env, this.pos1, this.pos2);
	}
	
	public static int getPosNoOrderId(Environment env, int pos1, int pos2) {
		if(pos1<pos2) {
			return env.nTeams*pos1+pos2;
		} else {
			return env.nTeams*pos2+pos1;
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Match match = (Match) o;
		return pos1 == match.pos1 && pos2 == match.pos2;
	}

	@Override
	public int hashCode() {
		return Objects.hash(pos1, pos2);
	}
}
