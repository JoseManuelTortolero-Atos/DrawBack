package net.atos.mev.calendarcalculator;

import java.io.Serializable;

public class ParticData implements Serializable {

	private static final long serialVersionUID = 6366730267631010698L;
	private String dob, gender, role, tenant, event, entryMark, status, deleg;
	
	public ParticData(String dob, String gender, String role, String tenant, String event, String entryMark, String status, String deleg) {
		super();
		this.dob = dob;
		this.gender = gender;
		this.role = role;
		this.tenant = tenant;
		this.event = event;
		this.entryMark = entryMark;
		this.status = status;
		this.deleg=deleg;
	}
	
	public static long getSerialversionuid() {
		return serialVersionUID;
	}
	public String getDob() {
		return dob;
	}
	public String getGender() {
		return gender;
	}
	public String getRole() {
		return role;
	}
	public String getTenant() {
		return tenant;
	}
	public String getEvent() {
		return event;
	}
	public String getEntryMark() {
		return entryMark;
	}
	public String getStatus() {
		return status;
	}

	public String getDeleg() {
		return deleg;
	}

}