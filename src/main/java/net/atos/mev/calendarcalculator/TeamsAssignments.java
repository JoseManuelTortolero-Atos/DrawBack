package net.atos.mev.calendarcalculator;

@SuppressWarnings("serial")
public class TeamsAssignments extends java.util.HashMap<Integer, Team> {

	@Override
	public String toString() {
		String result = "";
		for(Integer i : this.keySet()) {
			if(get(i) != null) {
				result+=i+"-"+get(i).getCode()+" ";
			}
		}
		return result;
	}

	public int getKeyOfTeam(Team t) {
		for(Entry<Integer, Team> i : this.entrySet()) {
			if(i.getValue()!=null) {
				if(i.getValue().equals(t)) {
					return i.getKey();
				}
			}
		}
		return -1;
	}



}
