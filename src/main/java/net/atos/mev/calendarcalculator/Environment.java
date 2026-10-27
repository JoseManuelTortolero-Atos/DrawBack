package net.atos.mev.calendarcalculator;

import java.util.Properties;

import net.atos.mev.calendarcalculator.checks.Check;

public class Environment {

	public String name;
	public String code;
	public int nTeams = 0;
	public int nRounds = 0;
	public int matchesPerRound = 0;
	public Team[] teams = new Team[0];
	public int nChecks = 0;
	public Check[] checks;
	Properties originalProp;

	/**
	 * v0.4: entorno en memoria a partir de la lista de equipos (orden = posición 1..n).
	 * Sin checks del sorteo (P1): el motor de planificación (P2) no los usa.
	 */
	public static Environment inMemory(String name, String code, java.util.List<String[]> teamsCodeName) {
		Environment env = new Environment();
		env.name = name;
		env.code = code;
		env.nTeams = teamsCodeName.size();
		env.matchesPerRound = env.nTeams / 2;
		env.nRounds = env.nTeams - 1;
		env.teams = new Team[env.nTeams];
		for (int i = 0; i < env.nTeams; i++) {
			Team t = new Team();
			t.setCode(teamsCodeName.get(i)[0]);
			t.setName(teamsCodeName.get(i)[1]);
			t.setEnv(env);
			env.teams[i] = t;
		}
		env.nChecks = 0;
		env.checks = new Check[0];
		env.originalProp = new Properties();
		return env;
	}

	public void loadEnvironment(String competitionSelector) {
		Environment loaded = net.atos.mev.calendarcalculator.compat.DatabaseBatchSupport.environment(competitionSelector);
		name = loaded.name;
		code = loaded.code;
		nTeams = loaded.nTeams;
		nRounds = loaded.nRounds;
		matchesPerRound = loaded.matchesPerRound;
		teams = loaded.teams;
		nChecks = loaded.nChecks;
		checks = loaded.checks;
		originalProp = loaded.originalProp;
		for (Team team : teams) team.setEnv(this);
	}
	
	public Team searchTeamByCode(String code) {
		for(Team t : teams) {
			if(t.getCode().equals(code)) {
				return t;
			}
		}
		return null;
	}

	public static void main(String[] args) {
		Environment env = new Environment();
		env.loadEnvironment(net.atos.mev.calendarcalculator.compat.DatabaseBatchSupport.selector(args));
		for(Team t: env.teams) {
			System.out.println(t.toString());
		}
	}

	public Properties getOriginalProp() {
		return originalProp;
	}

	public void setOriginalProp(Properties originalProp) {
		this.originalProp = originalProp;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

}
