package net.atos.mev.calendarcalculator.fileio;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;

import net.atos.mev.calendarcalculator.Environment;
import net.atos.mev.calendarcalculator.Match;
import net.atos.mev.calendarcalculator.Tournament;

public class AssignmentsFileManager {
	
	static String DATEFORMAT = "YYYYMMdd-HH.mm.ss";
	static String ENVIRONMENT = "environment.properties";
	static String resultsFolder = "results";
	FileOutputStream output;
	String folderName;
	String lastFolderName;
	int writtenLines;
	static int linesPerFile = 1000;
	static byte[] lineseparator = System.lineSeparator().getBytes();
	
	public void prepareOutputTournament(Tournament tour) {
		SimpleDateFormat formatter = new SimpleDateFormat(DATEFORMAT);
		lastFolderName = "assign_" + formatter.format(new Date());
		folderName = "results" + File.separator + tour.env.name + File.separator + lastFolderName;
		File folderToWrite = new File(folderName);
		folderToWrite.mkdirs();
		File environmentFile = new File(folderName + File.separator + ENVIRONMENT);
		try {
			FileOutputStream environmentOut = new FileOutputStream(environmentFile);
			tour.env.getOriginalProp().store(environmentOut, folderName);
		} catch (Exception e) {
			e.printStackTrace();
		}
		writtenLines = 0;
	}

	public void writeTournamentPos(Tournament tour) {
		try {
			if(writtenLines%linesPerFile == 0) {
				String sequential = String.valueOf(writtenLines/linesPerFile);
				while(sequential.length() < 5) {
					sequential = "0" + sequential;
				}
				String filename = "assign_" + sequential + ".txt";
				if(output != null) {
					output.close();
				}
				output = new FileOutputStream(folderName + File.separator + filename);
			}
			output.write(tour.toStringOneLineWithAssignments().getBytes());
			output.write(lineseparator);
			output.flush();
			writeTournamentCsvFormat(tour);
			writtenLines++;
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void writeTournamentCsvFormat(Tournament tour) {
		try {
			tour.applyMapPosToTeams();
			String sequential = String.valueOf(writtenLines);
			while(sequential.length() < 5) {
				sequential = "0" + sequential;
			}
			String filename = "schedule_" + tour.env.name + "_" + sequential + ".csv";
			FileOutputStream outCsv = new FileOutputStream(folderName + File.separator + filename);
			String metadataLine = "sep=,";
			outCsv.write(metadataLine.getBytes());
			outCsv.write(lineseparator);
			String headerLine = "Round,Home,Away,Home Name, Away Name";
			outCsv.write(headerLine.getBytes());
			outCsv.write(lineseparator);
			for(int firstRound=0; firstRound<tour.rounds.length;firstRound++) {
				for(Match m : tour.rounds[firstRound].matches) {
					String newLine = (firstRound+1) + "," + m.getTeam1().getCode() + "," + m.getTeam2().getCode() + "," + m.getTeam1().getName() + "," + m.getTeam2().getName();
					outCsv.write(newLine.getBytes());
					outCsv.write(lineseparator);
				}
			}
			for(int firstRound=0; firstRound<tour.rounds.length;firstRound++) {
				for(Match m : tour.rounds[firstRound].matches) {
					String newLine = (firstRound+1+tour.rounds.length) + "," + m.getTeam2().getCode() + "," + m.getTeam1().getCode() + "," + m.getTeam2().getName() + "," + m.getTeam1().getName();
					outCsv.write(newLine.getBytes());
					outCsv.write(lineseparator);
				}
			}
			outCsv.flush();
			outCsv.close();
		}catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static String calculateFileName(int writtenLines) {
		String sequential = String.valueOf(writtenLines/linesPerFile);
		while(sequential.length() < 5) {
			sequential = "0" + sequential;
		}
		String filename = "assign_" + sequential + ".txt";
		return filename;
		
	}

	public static Tournament readTournamentPos(Environment env, int nTournament, String folder) {
		try {
			String folderName = "results" + File.separator + env.name + File.separator + folder;
			String filename = calculateFileName(nTournament);
			//FileInputStream input = new FileInputStream(folderName + File.separator + filename);
			String line = Files.readAllLines(Paths.get(folderName + File.separator + filename)).get(nTournament%linesPerFile);
	        Tournament result = Tournament.readFromLineWithAssignments(env, line);
	        return result;
		}catch(Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public static void main(String[] args) {
		Environment env = new Environment();
		env.loadEnvironment(net.atos.mev.calendarcalculator.compat.DatabaseBatchSupport.selector(args));
		Tournament tour = AssignmentsFileManager.readTournamentPos(env, 14664, "202010294-22.43.09");
		System.out.println(tour.toStringNames());
	}

	public String getFolderName() {
		return folderName;
	}

	public void setFolderName(String folderName) {
		this.folderName = folderName;
	}

	public int getWrittenLines() {
		return writtenLines;
	}

	public void setWrittenLines(int writtenLines) {
		this.writtenLines = writtenLines;
	}

	public String getLastFolderName() {
		return lastFolderName;
	}

	public void setLastFolderName(String lastFolderName) {
		this.lastFolderName = lastFolderName;
	}

}
