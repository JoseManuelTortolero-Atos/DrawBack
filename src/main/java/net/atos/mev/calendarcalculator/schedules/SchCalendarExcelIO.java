package net.atos.mev.calendarcalculator.schedules;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Row.MissingCellPolicy;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.atos.mev.calendarcalculator.Match;
import net.atos.mev.calendarcalculator.Team;

public class SchCalendarExcelIO {

	private static final Logger log = LoggerFactory.getLogger(SchCalendarExcelIO.class);

	private static final String INVALID_CONFIRMED_MATCH_MESSAGE = "Confirmed match in Excel not resolved";

	public static SchCalendar loadFromExcel(SchEnvironment schEnv) {
		if (schEnv.datesFile == null && schEnv.getDatabaseCalendar() != null) {
			return schEnv.getDatabaseCalendar();
		}
		try {
			log.info("Reading excel {}", schEnv.datesFile);
			URL url = ClassLoader.getSystemResource(schEnv.datesFile);
			if (url == null) {
				throw new IllegalStateException("Excel file not found in classpath: " + schEnv.datesFile);
			}
			try (FileInputStream inputStream = new FileInputStream(url.getFile())) {
				return loadFromExcel(inputStream, schEnv);
			}
		} catch (Exception e) {
			throw new IllegalStateException("Error loading excel from classpath: " + schEnv.datesFile, e);
		}
	}

	public static SchCalendar loadFromExcel(InputStream inputStream, SchEnvironment schEnv) {
		SchCalendar result = new SchCalendar(schEnv);
		try {
			Workbook workbook = WorkbookFactory.create(inputStream);
			Sheet sheet = workbook.getSheetAt(0);
			DataFormatter dataFormatter = new DataFormatter();
			for (Row row : sheet) {
				if(row.getRowNum()>0) {
					Cell cellCompetition = row.getCell(0, MissingCellPolicy.CREATE_NULL_AS_BLANK);
					if(cellCompetition != null) {
						String competition = cellCompetition.getStringCellValue();

						Cell cell = row.getCell(3, MissingCellPolicy.CREATE_NULL_AS_BLANK);

						String date = dataFormatter.formatCellValue(cell);
						log.debug("Row competition/date: {},{}", competition, date);
						if(competition.equals(schEnv.env.getCode())) {
							Cell cellOfRound = row.getCell(1, MissingCellPolicy.CREATE_NULL_AS_BLANK);
							String competitionRoundString="";
							int competitionRoundInt=-1;
							if(cellOfRound.getCellType() == CellType.STRING) {
								competitionRoundString = cellOfRound.getStringCellValue();
								if(competitionRoundString.matches("[0-9]+")) {
									competitionRoundInt = Integer.valueOf(competitionRoundString);
								}
							} else if (cellOfRound.getCellType() == CellType.NUMERIC) {
								Double doubleRound = cellOfRound.getNumericCellValue();
								competitionRoundInt = doubleRound.intValue();
								competitionRoundString = String.valueOf(competitionRoundInt);
							}
							String hour = row.getCell(4, MissingCellPolicy.CREATE_NULL_AS_BLANK).getStringCellValue();
							String teamHome = row.getCell(5, MissingCellPolicy.CREATE_NULL_AS_BLANK).getStringCellValue();
							String teamAway = row.getCell(6, MissingCellPolicy.CREATE_NULL_AS_BLANK).getStringCellValue();
							hour = hour == null ? null : hour.trim();
							teamHome = teamHome == null ? null : teamHome.trim();
							teamAway = teamAway == null ? null : teamAway.trim();
							competitionRoundString = competitionRoundString == null ? null : competitionRoundString.trim();
							if(teamHome != null && !teamHome.isBlank()
                                                                && teamAway != null && !teamAway.isBlank()
                                                                && competitionRoundString != null && !competitionRoundString.isBlank()) {
								// DEAL WITH CONFIRMED MATCHES
								validateConfirmedMatchTeams(schEnv, competitionRoundString, teamHome, teamAway, date);
								Match m = schEnv.tour.findMatch(competitionRoundInt-1, teamHome, teamAway);
								if (m == null) {
									Integer actualRound = findExistingRound(schEnv, teamHome, teamAway);
									throw new IllegalStateException(
										INVALID_CONFIRMED_MATCH_MESSAGE
											+ ": round=" + competitionRoundString
											+ ", home=" + teamHome
											+ ", away=" + teamAway
											+ ", date=" + date
											+ ". Both teams belong to league " + schEnv.env.getCode()
											+ buildRoundMismatchMessage(actualRound)
									);
								}
								SchMatch newSchMatch = new SchMatch(m);
								newSchMatch.setCompetitionRound(competitionRoundString);
								boolean hasConfirmedTime = hour != null && !hour.isBlank();
                                                                newSchMatch.setConfirmed(hasConfirmedTime);
								newSchMatch.setDate(SchEnvironment.readDate(date));
								newSchMatch.setTime(
                                                                        hasConfirmedTime
                                                                                ? hour
                                                                                : ScheduleMoroccoAlg.EMPTYTIMESLOT
                                                                );
								newSchMatch.setCompetition(competition);
								result.getMatchdays()[result.dateNumber(date)].getMatches().add(newSchMatch);
							} else {
								// DEAL WITH AVAILABLE DATES
								if(!competitionRoundString.equals("")) {
									result.getMatchdays()[result.dateNumber(date)].setCalendarRound(competitionRoundString);
									result.getMatchdays()[result.dateNumber(date)].setAvailableForNewMatches(true);
								}
							}
						} else if (!competition.equals("")) {
							// DEAL WITH OTHER COMPETITION DATES
							Cell cellOfRound = row.getCell(1, MissingCellPolicy.CREATE_NULL_AS_BLANK);
							String calendarRoundString="";
							int calendarRoundInt=-1;
							if(cellOfRound.getCellType() == CellType.STRING) {
								calendarRoundString = cellOfRound.getStringCellValue();
							} else if (cellOfRound.getCellType() == CellType.NUMERIC) {
								Double doubleRound = cellOfRound.getNumericCellValue();
								calendarRoundInt = doubleRound.intValue();
								calendarRoundString = String.valueOf(calendarRoundInt);
							}
                                                        String hour = trimToNull(row.getCell(4, MissingCellPolicy.CREATE_NULL_AS_BLANK).getStringCellValue());
                                                        String teamHome = trimToNull(row.getCell(5, MissingCellPolicy.CREATE_NULL_AS_BLANK).getStringCellValue());
                                                        String teamAway = trimToNull(row.getCell(6, MissingCellPolicy.CREATE_NULL_AS_BLANK).getStringCellValue());
                                                        OtherCompetitionDay compDay = new OtherCompetitionDay();
                                                        compDay.setCompetition(competition);
                                                        compDay.setCompetitionRound(calendarRoundString);
                                                        compDay.setDate(SchEnvironment.readDate(date));
                                                        compDay.setHour(hour);
                                                        compDay.setTeamHome(teamHome);
                                                        compDay.setTeamAway(teamAway);
                                                        if (compDay.hasMatchDetails()) {
                                                                compDay.getTeams().add(teamHome);
                                                                compDay.getTeams().add(teamAway);
                                                        } else if (teamHome != null) {
                                                                String[] stringTeamsPLaying = teamHome.split(",");
                                                                for(String oneTeamPLaying : stringTeamsPLaying) {
                                                                        String normalizedTeam = trimToNull(oneTeamPLaying);
                                                                        if(normalizedTeam != null) {
                                                                                compDay.getTeams().add(normalizedTeam);
                                                                        }
                                                                }
                                                                if (teamAway != null) {
                                                                        compDay.getTeams().add(teamAway);
                                                                }
                                                        }
                                                        result.getMatchdays()[result.dateNumber(date)].getOtherMatches().add(compDay);
						}
					}
				}
			}
			workbook.close();
		} catch (Exception e) {
			throw new IllegalStateException("Error loading calendar from excel input stream", e);
		}
		return result;
	}

	private static void validateConfirmedMatchTeams(
		SchEnvironment schEnv,
		String competitionRound,
		String teamHome,
		String teamAway,
		String date
	) {
		boolean homeExists = teamExists(schEnv, teamHome);
		boolean awayExists = teamExists(schEnv, teamAway);

		if (!homeExists || !awayExists) {
			String invalidTeams = buildInvalidTeamsMessage(teamHome, homeExists, teamAway, awayExists);
			throw new IllegalStateException(
				INVALID_CONFIRMED_MATCH_MESSAGE
					+ ": round=" + competitionRound
					+ ", home=" + teamHome
					+ ", away=" + teamAway
					+ ", date=" + date
					+ ". Team " + invalidTeams
					+ " is not part of league " + schEnv.env.getCode()
					+ ". Check that the Excel file matches the selected competition."
			);
		}
	}

	private static boolean teamExists(SchEnvironment schEnv, String teamCode) {
		for (Team team : schEnv.env.teams) {
			if (team.getCode().equals(teamCode)) {
				return true;
			}
		}
		return false;
	}

	private static String buildInvalidTeamsMessage(String teamHome, boolean homeExists, String teamAway, boolean awayExists) {
		if (!homeExists && !awayExists) {
			return "codes [" + teamHome + ", " + teamAway + "]";
		}
		return "code " + (!homeExists ? teamHome : teamAway);
	}

	private static Integer findExistingRound(SchEnvironment schEnv, String teamHome, String teamAway) {
		for (int roundIndex = 0; roundIndex < schEnv.tour.rounds.length; roundIndex++) {
			Match match = schEnv.tour.findMatch(roundIndex, teamHome, teamAway);
			if (match != null) {
				return roundIndex + 1;
			}
		}
		return null;
	}

	private static String buildRoundMismatchMessage(Integer actualRound) {
		if (actualRound == null) {
			return " but this match does not exist in any round for the selected competition.";
		}
		return " but this match does not exist in that round for the selected competition. It is scheduled in round "
			+ actualRound + ".";
	}

	public static void statisticsToCSV(SchCalendar calendar, String fileName) {
		try {
			File outputFile = new File(fileName);
			File containingFolder = outputFile.getParentFile();
			if(!containingFolder.exists()) {
				containingFolder.mkdirs();
			}

			HashMap<String,int[]> statisticPerTeam = new HashMap<String,int[]>();
			for(Team t : calendar.schEnv.env.teams) {
				// 0-6 Weekdays, 7 Total, 8 Midweek, 9 Weekend
				statisticPerTeam.put(t.getCode(), new int[10]);
			}
			for(Matchday md : calendar.matchdays) {
				for(SchMatch sm : md.matches) {
					int nWeekday=0;
					switch (md.date.getDayOfWeek()) {
					case MONDAY:
						nWeekday=0;
						break;
					case TUESDAY:
						nWeekday=1;
						break;
					case WEDNESDAY:
						nWeekday=2;
						break;
					case THURSDAY:
						nWeekday=3;
						break;
					case FRIDAY:
						nWeekday=4;
						break;
					case SATURDAY:
						nWeekday=5;
						break;
					case SUNDAY:
						nWeekday=6;
						break;
					default:
						break;
					}
					statisticPerTeam.get(sm.theMatch.team1.getCode())[nWeekday]++;
					statisticPerTeam.get(sm.theMatch.team1.getCode())[7]++;
					if(nWeekday < 5) {
						statisticPerTeam.get(sm.theMatch.team1.getCode())[8]++;
					} else {
						statisticPerTeam.get(sm.theMatch.team1.getCode())[9]++;
					}
					//statisticPerTeam.get(sm.theMatch.team2.getCode())[nWeekday]++;
				}
			}
			FileOutputStream outStream = new FileOutputStream(outputFile);
			String metadataLine = "sep=,";
			outStream.write(metadataLine.getBytes());
			outStream.write("\r\n".getBytes());
			String headerLine = "Equipe,Lun,Mar,Mer,Jeu,Ven,Sam,Dim,Total,Midweek,Weekend\r\n";
			outStream.write(headerLine.getBytes());
			for(Team t : calendar.schEnv.env.teams) {
				String newLine = t.getCode();
				for(int i=0; i<10; i++) {
					newLine += ","+statisticPerTeam.get(t.getCode())[i];
				}
				newLine+="\r\n";
				outStream.write(newLine.getBytes());
			}

			outStream.flush();
			outStream.close();
		} catch (Exception e) {
			throw new IllegalStateException("Error writing statistics CSV: " + fileName, e);
		}
	}

	public static void summaryPerTeamToCSV(SchCalendar calendar, String fileName) {
		try {
			File outputFile = new File(fileName);
			File containingFolder = outputFile.getParentFile();
			if(!containingFolder.exists()) {
				containingFolder.mkdirs();
			}
			FileOutputStream outStream = new FileOutputStream(outputFile);
			String metadataLine = "sep=,";
			outStream.write(metadataLine.getBytes());
			outStream.write("\r\n".getBytes());
			String headerLine = "Team,Competition,Round,Weekday,Date,Hour,Domicile,Exterieur\r\n";
			outStream.write(headerLine.getBytes());
			for(Team currentTeam : calendar.schEnv.env.teams) {
				for(Matchday currentMatchday : calendar.matchdays) {
					for(SchMatch confirmedMatch : currentMatchday.getMatches()) {
						if(currentTeam.getCode().equals(confirmedMatch.getTheMatch().getTeam1().getCode()) || currentTeam.getCode().equals(confirmedMatch.getTheMatch().getTeam2().getCode())) {
							String newLine = "";
							newLine += currentTeam.getCode()+",";
							newLine += confirmedMatch.getCompetition()+",";
							newLine += confirmedMatch.getCompetitionRound()+",";
							newLine += confirmedMatch.getDate().getDayOfWeek().getDisplayName(TextStyle.SHORT,Locale.ENGLISH)+",";
							newLine += SchEnvironment.writeDate(confirmedMatch.getDate())+",";
							newLine += confirmedMatch.getTime()+",";
							newLine += confirmedMatch.getTheMatch().getTeam1().getCode()+",";
							newLine += confirmedMatch.getTheMatch().getTeam2().getCode()+",";
							newLine += "\r\n";
							outStream.write(newLine.getBytes());
						}
					}
					for(OtherCompetitionDay otherComp : currentMatchday.getOtherMatches()) {
                                                if(otherComp.teams.contains(currentTeam.getCode())) {
                                                        String newLine = "";
                                                        newLine += currentTeam.getCode()+",";
                                                        newLine += otherComp.getCompetition()+",";
                                                        newLine += otherComp.getCompetitionRound()+",";
                                                        newLine += otherComp.getDate().getDayOfWeek().getDisplayName(TextStyle.SHORT,Locale.ENGLISH)+",";
                                                        newLine += SchEnvironment.writeDate(otherComp.getDate())+",";
                                                        newLine += csvValue(otherComp.getHour())+",";
                                                        if (otherComp.hasMatchDetails()) {
                                                                newLine += csvValue(otherComp.getTeamHome())+",";
                                                                newLine += csvValue(otherComp.getTeamAway())+",";
                                                        } else {
                                                                newLine += "\"" + joinTeams(otherComp) + "\",";
                                                                newLine += ",";
                                                        }
                                                        newLine += "\r\n";
                                                        outStream.write(newLine.getBytes());
                                                }
						}
					}
				}
			outStream.flush();
			outStream.close();
		} catch (Exception e) {
			throw new IllegalStateException("Error writing summary-per-team CSV: " + fileName, e);
		}
	}

	public static void matchdaysToExcel(SchCalendar calendar, String fileName, SchEnvironment schEnv) {
		try {
			File outputFile = new File(fileName);
			File containingFolder = outputFile.getParentFile();
			if(!containingFolder.exists()) {
				containingFolder.mkdirs();
			}

			try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
				matchdaysToExcel(calendar, outputStream, schEnv);
			}
		} catch(Exception e) {
			throw new IllegalStateException("Error writing calendar excel: " + fileName, e);
		}
	}

	public static void matchdaysToExcel(SchCalendar calendar, OutputStream outputStream, SchEnvironment schEnv) {
		try {
			try (XSSFWorkbook workbook = new XSSFWorkbook()) {
				Sheet sheet = workbook.createSheet("Calendar");
				writeCalendarRows(calendar, workbook, sheet, schEnv, false);
				workbook.write(outputStream);
			}
		} catch(Exception e) {
			throw new IllegalStateException("Error writing calendar output excel", e);
		}
	}

	public static void matchdaysToExcel(SchCalendar calendar, InputStream templateInputStream, OutputStream outputStream, SchEnvironment schEnv) {
		try (Workbook workbook = WorkbookFactory.create(templateInputStream)) {
			Sheet sheet = workbook.getNumberOfSheets() == 0 ? workbook.createSheet("Calendar") : workbook.getSheetAt(0);
			writeCalendarRows(calendar, workbook, sheet, schEnv, true);
			workbook.write(outputStream);
		} catch(Exception e) {
			throw new IllegalStateException("Error writing calendar output excel", e);
		}
	}

	public static void summaryPerTeamToExcel(SchCalendar calendar, String fileName, SchEnvironment schEnv) {
		try {

			XSSFWorkbook workbook = new XSSFWorkbook();
			Sheet sheet = workbook.createSheet("Calendar");

			int rowCount = -1;

			String[] headerLine = "Team,Competition,Round,Weekday,Date,Hour,Domicile,Exterieur".split(",");
			Row firstRow = sheet.createRow(++rowCount);

			int firstColumnCount = -1;

			for (String field : headerLine) {
				Cell cell = firstRow.createCell(++firstColumnCount);
				if (field instanceof String) {
					cell.setCellValue((String) field);
				}
			}

			for(Team currentTeam : calendar.schEnv.env.teams) {
				for(int nDay=0; nDay < calendar.getMatchdays().length; nDay++) {
					Matchday currentMatchday = calendar.getMatchdays()[nDay];
					for(SchMatch confirmedMatch : currentMatchday.getMatches()) {
						if(currentTeam.getCode().equals(confirmedMatch.getTheMatch().getTeam1().getCode()) || currentTeam.getCode().equals(confirmedMatch.getTheMatch().getTeam2().getCode())) {
							String[] confirmedMatchRow = {currentTeam.getCode(),confirmedMatch.getCompetition(), confirmedMatch.getCompetitionRound(), confirmedMatch.getDate().getDayOfWeek().getDisplayName(TextStyle.SHORT,Locale.ENGLISH), SchEnvironment.writeDate(confirmedMatch.getDate()), confirmedMatch.getTime(), confirmedMatch.getTheMatch().getTeam1().getCode(), confirmedMatch.getTheMatch().getTeam2().getCode()};
							fillRow(confirmedMatchRow,++rowCount,confirmedMatchRow[1],workbook,sheet,schEnv);
						}
					}
					for(OtherCompetitionDay otherComp : currentMatchday.getOtherMatches()) {
                                                if(otherComp.teams.contains(currentTeam.getCode())) {
                                                        String[] otherCompRow = buildSummaryOtherCompetitionRow(currentTeam.getCode(), otherComp);
                                                        fillRow(otherCompRow,++rowCount,otherCompRow[1],workbook,sheet,schEnv);
                                                }
						}
					}
				}

			// rowCount is zero-based. AutoFilter expects 1-based Excel row indexes.
			int lastExcelRow = Math.max(1, rowCount + 1);
			sheet.setAutoFilter(CellRangeAddress.valueOf("A1:H" + lastExcelRow));

			File outputFile = new File(fileName);
			File containingFolder = outputFile.getParentFile();
			if(!containingFolder.exists()) {
				containingFolder.mkdirs();
			}

			FileOutputStream outputStream = new FileOutputStream(outputFile);
			workbook.write(outputStream);

			workbook.close();
		} catch(Exception e) {
			throw new IllegalStateException("Error writing summary-per-team excel: " + fileName, e);
		}
	}

	private static void writeCalendarRows(SchCalendar calendar, Workbook workbook, Sheet sheet, SchEnvironment schEnv, boolean preserveExistingStyles) {
		int lastTemplateRow = sheet.getLastRowNum();

		int rowCount = 0;

		writeRow("Competition,Round,Weekday,Date,Hour,Domicile,Exterieur".split(","), rowCount, "", workbook, sheet, schEnv, preserveExistingStyles);

		for(int nDay=0; nDay < calendar.getMatchdays().length; nDay++) {
			Matchday currentMatchday = calendar.getMatchdays()[nDay];
			for(SchMatch confirmedMatch : currentMatchday.getMatches()) {
				String[] confirmedMatchRow = {confirmedMatch.getCompetition(), confirmedMatch.getCompetitionRound(), confirmedMatch.getDate().getDayOfWeek().getDisplayName(TextStyle.SHORT,Locale.ENGLISH), SchEnvironment.writeDate(confirmedMatch.getDate()), confirmedMatch.getTime(), confirmedMatch.getTheMatch().getTeam1().getCode(), confirmedMatch.getTheMatch().getTeam2().getCode()};
				writeRow(confirmedMatchRow, ++rowCount, confirmedMatchRow[0], workbook, sheet, schEnv, preserveExistingStyles);
			}
			if(currentMatchday.getMatches().size() == 0 && currentMatchday.isAvailableForNewMatches() && currentMatchday.getCalendarRound() != null && !currentMatchday.equals("")) {
				String[] availableDayRow = {calendar.getSchEnv().env.getCode(), currentMatchday.getCalendarRound(), currentMatchday.getDate().getDayOfWeek().getDisplayName(TextStyle.SHORT,Locale.ENGLISH), SchEnvironment.writeDate(currentMatchday.getDate()),"","",""};
				writeRow(availableDayRow, ++rowCount, availableDayRow[0], workbook, sheet, schEnv, preserveExistingStyles);
			}
			for(OtherCompetitionDay otherComp : currentMatchday.getOtherMatches()) {
				String[] otherCompRow = buildOtherCompetitionRow(otherComp);
				writeRow(otherCompRow, ++rowCount, otherCompRow[0], workbook, sheet, schEnv, preserveExistingStyles);
			}
		}

		int lastExcelRow = Math.max(rowCount + 1, lastTemplateRow + 1);
		sheet.setAutoFilter(CellRangeAddress.valueOf("A1:G" + Math.max(1, lastExcelRow)));
	}

	private static void fillRow(String[] values, int nRow, String competition, Workbook workbook, Sheet sheet, SchEnvironment schEnv) {
		Row row = sheet.createRow(nRow);
		int columnCount = -1;
		for (String field : values) {
			Cell cell = row.createCell(++columnCount);
			cell.setCellValue((String) field);
			applyCompetitionStyle(cell, competition, workbook, schEnv);
		}
	}

	private static void writeRow(String[] values, int nRow, String competition, Workbook workbook, Sheet sheet, SchEnvironment schEnv, boolean preserveExistingStyles) {
		if (!preserveExistingStyles) {
			fillRow(values, nRow, competition, workbook, sheet, schEnv);
			return;
		}

		Row row = sheet.getRow(nRow);
		if (row == null) {
			fillRow(values, nRow, competition, workbook, sheet, schEnv);
			return;
		}

		for (int columnIndex = 0; columnIndex < values.length; columnIndex++) {
			Cell cell = row.getCell(columnIndex);
			boolean createdCell = false;
			if (cell == null) {
				cell = row.createCell(columnIndex);
				createdCell = true;
			}
			cell.setCellValue(values[columnIndex]);
			if (createdCell) {
				applyCompetitionStyle(cell, competition, workbook, schEnv);
			}
		}
	}


	private static void applyCompetitionStyle(Cell cell, String competition, Workbook workbook, SchEnvironment schEnv) {
		String colourThisComp = schEnv.prop.getProperty("ScheduleMoroccoAlg.colour."+competition);
		if(colourThisComp != null) {
			CellStyle style = workbook.createCellStyle();
			style.setFillForegroundColor(IndexedColors.valueOf(colourThisComp).getIndex());
			style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			cell.setCellStyle(style);
		}
	}

	public static void matchdaysToCSV(SchCalendar calendar, String fileName) {
		try {
			File outputFile = new File(fileName);
			File containingFolder = outputFile.getParentFile();
			if(!containingFolder.exists()) {
				containingFolder.mkdirs();
			}
			FileOutputStream outStream = new FileOutputStream(outputFile);
			String metadataLine = "sep=,";
			outStream.write(metadataLine.getBytes());
			outStream.write("\r\n".getBytes());
			String headerLine = "Competition,Round,Weekday,Date,Hour,Domicile,Exterieur,WAC,RCA,RSB,FAR\r\n";
			outStream.write(headerLine.getBytes());
			for(int nDay=0; nDay < calendar.getMatchdays().length; nDay++) {
				Matchday currentMatchday = calendar.getMatchdays()[nDay];
				for(SchMatch confirmedMatch : currentMatchday.getMatches()) {
					String newLine = "";
					newLine += confirmedMatch.getCompetition()+",";
					newLine += confirmedMatch.getCompetitionRound()+",";
					newLine += confirmedMatch.getDate().getDayOfWeek().getDisplayName(TextStyle.SHORT,Locale.ENGLISH)+",";
					newLine += SchEnvironment.writeDate(confirmedMatch.getDate())+",";
					newLine += confirmedMatch.getTime()+",";
					newLine += confirmedMatch.getTheMatch().getTeam1().getCode()+",";
					newLine += confirmedMatch.getTheMatch().getTeam2().getCode()+",";
					newLine += (confirmedMatch.getTheMatch().getTeam1().getCode().equals("WAC")||confirmedMatch.getTheMatch().getTeam2().getCode().equals("WAC")?"Y":"N")+",";
					newLine += (confirmedMatch.getTheMatch().getTeam1().getCode().equals("RCA")||confirmedMatch.getTheMatch().getTeam2().getCode().equals("RCA")?"Y":"N")+",";
					newLine += (confirmedMatch.getTheMatch().getTeam1().getCode().equals("RSB")||confirmedMatch.getTheMatch().getTeam2().getCode().equals("RSB")?"Y":"N")+",";
					newLine += (confirmedMatch.getTheMatch().getTeam1().getCode().equals("FAR")||confirmedMatch.getTheMatch().getTeam2().getCode().equals("FAR")?"Y":"N")+",";
					newLine += "\r\n";
					outStream.write(newLine.getBytes());
				}
				if(currentMatchday.getMatches().size() == 0 && currentMatchday.isAvailableForNewMatches() && currentMatchday.getCalendarRound() != null && !currentMatchday.equals("")) {
					String newLine = "";
					newLine += calendar.getSchEnv().env.getCode()+",";
					newLine += currentMatchday.getCalendarRound()+",";
					newLine += currentMatchday.getDate().getDayOfWeek().getDisplayName(TextStyle.SHORT,Locale.ENGLISH)+",";
					newLine += SchEnvironment.writeDate(currentMatchday.getDate())+",";
					newLine += ",";
					newLine += ",";
					newLine += ",";
					newLine += ",";
					newLine += ",";
					newLine += ",";
					newLine += ",";
					newLine += "\r\n";
					outStream.write(newLine.getBytes());
				}
                                for(OtherCompetitionDay otherComp : currentMatchday.getOtherMatches()) {
                                        String newLine = "";
                                        newLine += otherComp.getCompetition()+",";
                                        newLine += otherComp.getCompetitionRound()+",";
                                        newLine += otherComp.getDate().getDayOfWeek().getDisplayName(TextStyle.SHORT,Locale.ENGLISH)+",";
                                        newLine += SchEnvironment.writeDate(otherComp.getDate())+",";
                                        newLine += csvValue(otherComp.getHour())+",";
                                        if (otherComp.hasMatchDetails()) {
                                                newLine += csvValue(otherComp.getTeamHome())+",";
                                                newLine += csvValue(otherComp.getTeamAway())+",";
                                        } else {
                                                newLine += "\"" + joinTeams(otherComp) + "\",";
                                                newLine += ",";
                                        }
                                        newLine += (otherComp.teams.contains("WAC")?"Y":"N")+",";
                                        newLine += (otherComp.teams.contains("RCA")?"Y":"N")+",";
                                        newLine += (otherComp.teams.contains("RSB")?"Y":"N")+",";
                                        newLine += (otherComp.teams.contains("FAR")?"Y":"N")+",";
                                        newLine += "\r\n";
                                        outStream.write(newLine.getBytes());
                                }
				}
			outStream.flush();
			outStream.close();
		} catch (Exception e) {
			throw new IllegalStateException("Error writing matchdays CSV: " + fileName, e);
		}
	}

	public static void timeStatisticsToCSV(SchCalendar calendar, String fileName) {
		ArrayList<String> allTimeSlots = new ArrayList<String>();
		HashMap<String,HashMap<String,Integer>> timeslotsPerTeam = new HashMap<String,HashMap<String,Integer>>();
		for(int t=0; t<calendar.schEnv.env.teams.length; t++) {
			timeslotsPerTeam.put(calendar.schEnv.env.teams[t].getCode(), new HashMap<String,Integer>());
		}
		for(Matchday md : calendar.matchdays) {
			for(SchMatch sc : md.matches) {
				String teamHome = sc.theMatch.team1.getCode();
				String teamAway = sc.theMatch.team2.getCode();
				if(!allTimeSlots.contains(sc.time)) {
					allTimeSlots.add(sc.time);
				}
				if(timeslotsPerTeam.get(teamHome).get(sc.time)==null) {
					timeslotsPerTeam.get(teamHome).put(sc.time, 0);
				}
				timeslotsPerTeam.get(teamHome).put(sc.time, timeslotsPerTeam.get(teamHome).get(sc.time)+1);
                                if(timeslotsPerTeam.get(teamAway).get(sc.time)==null) {
                                        timeslotsPerTeam.get(teamAway).put(sc.time, 0);
                                }
                                timeslotsPerTeam.get(teamAway).put(sc.time, timeslotsPerTeam.get(teamAway).get(sc.time)+1);
			}
		}

		Collections.sort(allTimeSlots);

		try {
			File outputFile = new File(fileName);
			File containingFolder = outputFile.getParentFile();
			if(!containingFolder.exists()) {
				containingFolder.mkdirs();
			}
			FileOutputStream outStream = new FileOutputStream(outputFile);
			String metadataLine = "sep=,";
			outStream.write(metadataLine.getBytes());
			outStream.write("\r\n".getBytes());
			String headerLine = "Team";
			for(String time : allTimeSlots) {
				headerLine+=","+time;
			}
			headerLine+="\r\n";
			outStream.write(headerLine.getBytes());
			for(Team t : calendar.schEnv.env.teams) {
				String newLine = t.getCode();
				for(String time : allTimeSlots) {
					if(timeslotsPerTeam.get(t.getCode()).get(time)==null) {
						timeslotsPerTeam.get(t.getCode()).put(time,0);
					}
					newLine+=","+timeslotsPerTeam.get(t.getCode()).get(time);
				}
				newLine+="\r\n";
				outStream.write(newLine.getBytes());
			}

			outStream.flush();
			outStream.close();
		} catch (Exception e) {
			throw new IllegalStateException("Error writing time statistics CSV: " + fileName, e);
		}
	}

        private static String[] buildOtherCompetitionRow(OtherCompetitionDay otherComp) {
                if (otherComp.hasMatchDetails()) {
                        return new String[] {
                                otherComp.getCompetition(),
                                otherComp.getCompetitionRound(),
                                otherComp.getDate().getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                                SchEnvironment.writeDate(otherComp.getDate()),
                                defaultString(otherComp.getHour()),
                                defaultString(otherComp.getTeamHome()),
                                defaultString(otherComp.getTeamAway())
                        };
                }
                return new String[] {
                        otherComp.getCompetition(),
                        otherComp.getCompetitionRound(),
                        otherComp.getDate().getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                        SchEnvironment.writeDate(otherComp.getDate()),
                        "",
                        joinTeams(otherComp),
                        ""
                };
        }

        private static String[] buildSummaryOtherCompetitionRow(String teamCode, OtherCompetitionDay otherComp) {
                String[] baseRow = buildOtherCompetitionRow(otherComp);
                return new String[] { teamCode, baseRow[0], baseRow[1], baseRow[2], baseRow[3], baseRow[4], baseRow[5], baseRow[6] };
        }

        private static String joinTeams(OtherCompetitionDay otherComp) {
                String newLine = "";
                for(int i=0; i<otherComp.getTeams().size(); i++) {
                        newLine += otherComp.getTeams().get(i);
                        if(i<otherComp.getTeams().size()-1) {
                                newLine += ",";
                        }
                }
                return newLine;
        }

        private static String trimToNull(String value) {
                if (value == null) {
                        return null;
                }
                String trimmed = value.trim();
                return trimmed.isEmpty() ? null : trimmed;
        }

        private static String defaultString(String value) {
                return value == null ? "" : value;
        }

        private static String csvValue(String value) {
                return value == null ? "" : value;
        }

	public static void main(String[] args) throws java.io.IOException {
		SchEnvironment schEnv = SchEnvironment.createFromDatabase(net.atos.mev.calendarcalculator.compat.DatabaseBatchSupport.selector(args));
		java.nio.file.Files.createDirectories(java.nio.file.Path.of(schEnv.resultsFolder));
		SchCalendar result = loadFromExcel(schEnv);
		matchdaysToCSV(result, schEnv.resultsFolder+"/outSampleIOClass.csv");
		summaryPerTeamToCSV(result, schEnv.resultsFolder+"/summaryPerTeam.csv");
		statisticsToCSV(result, schEnv.resultsFolder+"/statisticsSampleIOClass.csv");
	}
}
