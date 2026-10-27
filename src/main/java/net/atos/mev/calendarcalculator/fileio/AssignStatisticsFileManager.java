package net.atos.mev.calendarcalculator.fileio;

import java.io.File;
import java.io.FileOutputStream;

import net.atos.mev.calendarcalculator.Environment;

public class AssignStatisticsFileManager {

	String FILENAME = "statistics.csv";
	FileOutputStream output;
	
	public void saveTournamentsStatistics(Environment env, String folder, int[][][] roundOfMatch) {
		String fullFileName = "results" + File.separator + env.name + File.separator + folder + File.separator + FILENAME;
		System.out.println("Statistics in file: " + fullFileName);
		try {
			output = new FileOutputStream(fullFileName);
			String metadataLine = "sep=,";
			output.write(metadataLine.getBytes());
			output.write("\r\n".getBytes());
			String headerLine = "Team,Home,Away,";
			for(int i=0; i<env.nRounds*2; i++) {
				headerLine+="R"+(i+1)+",";
			}
			headerLine+="\r\n";
			output.write(headerLine.getBytes());
			for(int mainTeam=0; mainTeam<env.nTeams; mainTeam++) {
				for(int oppositeTeam=0;oppositeTeam<env.nTeams; oppositeTeam++) {
					if(mainTeam != oppositeTeam) {
						String newLine = env.teams[mainTeam].getCode() + "," + env.teams[mainTeam].getCode() + "," + env.teams[oppositeTeam].getCode() + ",";
						for(int i=0; i<env.nRounds*2; i++) {
							newLine+=roundOfMatch[mainTeam][oppositeTeam][i]+",";
						}
						newLine += "\r\n"; 
						newLine += env.teams[mainTeam].getCode() + "," + env.teams[oppositeTeam].getCode() + "," + env.teams[mainTeam].getCode() + ",";
						for(int i=0; i<env.nRounds*2; i++) {
							newLine+=roundOfMatch[oppositeTeam][mainTeam][i]+",";
						}
						newLine += "\r\n"; 
						output.write(newLine.getBytes());
					}
				}
				output.write("\r\n".getBytes());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
