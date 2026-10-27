package net.atos.mev.calendarcalculator;

public class TeamData {

	private String teamName, teamCode,deleg,event,tenant;
	private String teamId;

	public TeamData(String teamName, String teamCode, String deleg, String event, String tenant) {
		super();
		this.teamName = teamName;
		this.teamCode = teamCode;
		this.deleg = deleg;
		this.event = event;
		this.tenant = tenant;
	}

	public String getTeamName() {
		return teamName;
	}

	public String getTeamCode() {
		return teamCode;
	}

	public String getDeleg() {
		return deleg;
	}

	public String getEvent() {
		return event;
	}

	public String getTenant() {
		return tenant;
	}

	public String getTeamId() {
		return teamId;
	}

	public void setTeamId(String teamId) {
		this.teamId=teamId;
	}
	
}
