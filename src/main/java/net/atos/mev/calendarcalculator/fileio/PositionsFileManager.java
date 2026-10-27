package net.atos.mev.calendarcalculator.fileio;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

import net.atos.mev.calendarcalculator.Environment;
import net.atos.mev.calendarcalculator.Tournament;

public class PositionsFileManager {
	
	static String DATEFORMAT = "YYYYMMdd-HH.mm.ss";
	static String EXECUTION_FILENAME = "execution.properties";
	
	static String resultsFolder = "results";
	FileOutputStream output;
	String folderName;
	int writtenLines;
	static int linesPerFile = 1000;
	static byte[] lineseparator = System.lineSeparator().getBytes();
	
	public void prepareOutputTournament(Tournament tour) {
		SimpleDateFormat formatter = new SimpleDateFormat(DATEFORMAT);
		folderName = "results" + File.separator + tour.env.name + File.separator + "pos_" + formatter.format(new Date());
		File folderToWrite = new File(folderName);
		folderToWrite.mkdirs();
		writtenLines = 0;
	}
	
	public void writeExecutionProperties(Properties prop) {
		try {
			File execFile = new File(folderName + File.separator + EXECUTION_FILENAME);
			FileOutputStream outProp = new FileOutputStream(execFile);
			prop.store(outProp, "");
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}

	public void writeTournamentPos(Tournament tour) {
		try {
			if(writtenLines%linesPerFile == 0) {
				if(output != null) {
					output.close();
				}
				String filename = calculateFileName(writtenLines);
				output = new FileOutputStream(folderName + File.separator + filename);
			}
			output.write(tour.toStringOneLine().getBytes());
			output.write(lineseparator);
			output.flush();
			writtenLines++;
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static String calculateFileName(int writtenLines) {
		String sequential = String.valueOf(writtenLines/linesPerFile);
		while(sequential.length() < 5) {
			sequential = "0" + sequential;
		}
		String filename = "pos_" + sequential + ".txt";
		return filename;
		
	}

	public static Tournament readTournamentPos(Environment env, int nTournament, String folder) {
		try {
			String folderName = "results" + File.separator + env.name + File.separator + folder;
			String filename = calculateFileName(nTournament);
			//FileInputStream input = new FileInputStream(folderName + File.separator + filename);
			String line = Files.readAllLines(Paths.get(folderName + File.separator + filename)).get(nTournament%linesPerFile);
	        Tournament result = Tournament.readFromLine(env, line);
	        return result;
		}catch(Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
