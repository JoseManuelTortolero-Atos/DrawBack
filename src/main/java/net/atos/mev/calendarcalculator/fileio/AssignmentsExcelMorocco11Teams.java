package net.atos.mev.calendarcalculator.fileio;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URL;
import java.util.Properties;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Row.MissingCellPolicy;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import net.atos.mev.calendarcalculator.Environment;
import net.atos.mev.calendarcalculator.Match;
import net.atos.mev.calendarcalculator.Tournament;

public class AssignmentsExcelMorocco11Teams {
	
	static String propertiesFileD1 = "AssignmentsExcelMoroccoD1.properties";
	static String propertiesFileD2 = "AssignmentsExcelMoroccoD2Nord.properties";
	static String propertiesFileFemD1 = "AssignmentsExcelMoroccoFemD1.properties";
	static String propertiesFileFemD2 = "AssignmentsExcelMoroccoFemD2.properties";
	//static String propertiesFileFemD2P = "AssignmentsExcelMoroccoFemD2Sud.properties";
	String environment;
	String assignFolder;
	String templatePath;
	String folderName;
	int nTournaments;
	Environment env;
	int[][] roundStarts = AssignmentsExcelMorocco.roundStarts(11, 8);
	public AssignmentsExcelMorocco11Teams(String environment, String assignFolder, String templatePath, int nTournaments) {
		this.environment = environment;
		this.assignFolder = assignFolder;
		this.templatePath = templatePath;
		this.nTournaments = nTournaments;
	}
	
	public void execute() {
		env = new Environment();
		env.loadEnvironment(environment);
		folderName = "results" + File.separator + env.name + File.separator + assignFolder;
		for (int nTour = 0; nTour < nTournaments; nTour++) {
			Tournament tour = AssignmentsFileManager.readTournamentPos(env, nTour, assignFolder);
			String tournamentId = String.valueOf(nTour);
			while (tournamentId.length() < 3) {
				tournamentId = "0" + tournamentId;
			}
			generateExcelFromTemplate(tour, tournamentId);
		}
	}
	
	public void generateExcelFromTemplate(Tournament tour, String tournamentId) {
		try {
			System.out.println("Reading excel " + tournamentId);
			URL url = ClassLoader.getSystemResource(templatePath);
			FileInputStream inputStream = new FileInputStream(url.getFile());
			Workbook workbook = WorkbookFactory.create(inputStream);
			updateWorkbookWithTournament(workbook, tour, tournamentId);
			inputStream.close();
			System.out.println("Writing excel " + tournamentId);
			String filename = "calendar_" + tour.env.name + "_00" + tournamentId + ".xlsx";
			FileOutputStream outputStream = new FileOutputStream(folderName + File.separator + filename);
			workbook.write(outputStream);
			workbook.close();
			outputStream.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void updateWorkbookWithTournament(Workbook workbook, Tournament tour, String tournamentId) {
		Sheet sheet = workbook.getSheetAt(0);
		Row idRow = sheet.getRow(3);
		Cell idCell = idRow.getCell(15, MissingCellPolicy.CREATE_NULL_AS_BLANK);
		idCell.setCellType(CellType.STRING);
		idCell.setCellValue(tournamentId);
		for (int nRound = 0; nRound < tour.rounds.length; nRound++) {
			int countBYE = 0;
			for (int nMatch = 0; nMatch < tour.rounds[nRound].matches.length; nMatch++) {
				Match m = tour.rounds[nRound].matches[nMatch];
				if (m.getTeam1().getCode().equals("BYE") || m.getTeam2().getCode().equals("BYE")) {
					countBYE++;
				} else {
					fillMatch(sheet, nRound, nMatch - countBYE, m.getTeam1().getCode(), m.getTeam2().getCode());
					fillMatch(sheet, nRound + tour.rounds.length, nMatch - countBYE, m.getTeam2().getCode(), m.getTeam1().getCode());
				}
			}
		}
	}
	
	public void fillMatch(Sheet sheet, int nRound, int nMatch, String homeTeam, String awayTeam) {
		// System.out.println("fillMatch(sheet,"+nRound+","+nMatch+","+homeTeam+","+awayTeam);
		int rowOfMatch = roundStarts[nRound][0] + nMatch;
		Cell homeCell = sheet.getRow(rowOfMatch).getCell(roundStarts[nRound][1], MissingCellPolicy.CREATE_NULL_AS_BLANK);
		homeCell.setCellType(CellType.STRING);
		homeCell.setCellValue(homeTeam);
		Cell awayCell = sheet.getRow(rowOfMatch).getCell(roundStarts[nRound][1] + 1, MissingCellPolicy.CREATE_NULL_AS_BLANK);
		awayCell.setCellType(CellType.STRING);
		awayCell.setCellValue(awayTeam);
	}
	
	public static void main(String[] args) {
		if (args.length > 0) {
			net.atos.mev.calendarcalculator.compat.DatabaseBatchSupport.exportDraw(args[0], args.length > 1 ? args[1] : null);
			return;
		}
		loadFromConfig(propertiesFileFemD1);
		loadFromConfig(propertiesFileFemD2);
		//loadFromConfig(propertiesFileFemD2P);
	}
	
	public static void loadFromConfig(String configFile) {
		net.atos.mev.calendarcalculator.compat.DatabaseBatchSupport.exportDraw(configFile, null);
	}
}
