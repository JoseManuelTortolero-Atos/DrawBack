package net.atos.mev.calendarcalculator;

public class Team {
	
	String code;
	String name;
	Environment env;
	
	public String toString() {
		return code + "," + name;
	}
	
	public String getCode() {
		return code;
	}
	public void setCode(String code) {
		this.code = code;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}

	public Environment getEnv() {
		return env;
	}

	public void setEnv(Environment env) {
		this.env = env;
	}

	@Override
	public boolean equals(Object obj) {
		if (code == null || obj == null) {
			code = "";
		}
		return this.code.equals(((Team)obj).code);
	}
	
	

}
