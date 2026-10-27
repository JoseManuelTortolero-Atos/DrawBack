package net.atos.mev.calendarcalculator.schedules;

import java.util.ArrayList;

public class SchRound {
	
	String idRound="";
	ArrayList<SchMatch> theMatches= new ArrayList<SchMatch>();
	ArrayList<Integer> dates = new ArrayList<Integer>();
	boolean isMAJ;
	
	public ArrayList<SchMatch> getTheMatches() {
		return theMatches;
	}
	public void setTheMatches(ArrayList<SchMatch> theMatches) {
		this.theMatches = theMatches;
	}
	public ArrayList<Integer> getDates() {
		return dates;
	}
	public void setDates(ArrayList<Integer> dates) {
		this.dates = dates;
	}
	public String getIdRound() {
		return idRound;
	}
	public void setIdRound(String idRound) {
		this.idRound = idRound;
	}
	@Override
	public boolean equals(Object obj) {
		return ((SchRound)obj).getIdRound().equals(this.idRound);
	}
	public boolean isMAJ() {
		return isMAJ;
	}
	public void setMAJ(boolean isMAJ) {
		this.isMAJ = isMAJ;
	}

}
