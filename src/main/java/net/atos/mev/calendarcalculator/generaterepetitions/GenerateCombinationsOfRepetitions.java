package net.atos.mev.calendarcalculator.generaterepetitions;

import java.util.ArrayList;

public class GenerateCombinationsOfRepetitions {

	ArrayList<String> result = new ArrayList<String>();
	int nRounds = 5;
	int limitPerWeek = 1;
	int totalRepetitions = 2;

	public ArrayList<String> calculateCombinationsOfRepetitions(){
		// First week cannot repeat home/away from a previous week
		String currentResult="0";
		recursiveCombinationsSearch(currentResult, 1, 0);		
		return result;
	}

	void recursiveCombinationsSearch(String currentResult, int currentRound, int repetitionsUsed) {
		if(currentRound<nRounds-1) {
			for(int i=0; i<=limitPerWeek && i+repetitionsUsed <= totalRepetitions; i++) {
				recursiveCombinationsSearch(currentResult+String.valueOf(i), currentRound+1, repetitionsUsed+i);
			}

		} else {
			// We've reached the final
			// We want to have no repetitions in the last week
			if(repetitionsUsed == totalRepetitions) {
				currentResult+="0";
				result.add(currentResult);
			}
		}
	}

	public static void main(String[] args) {
		GenerateCombinationsOfRepetitions execution = new GenerateCombinationsOfRepetitions();
		ArrayList<String> result = execution.calculateCombinationsOfRepetitions();
		for(String s: result) {
			System.out.println(s);
		}
		System.out.println("Total found: " + result.size());
	}

}
