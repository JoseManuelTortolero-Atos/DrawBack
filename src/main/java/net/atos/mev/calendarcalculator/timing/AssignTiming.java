package net.atos.mev.calendarcalculator.timing;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import net.atos.mev.calendarcalculator.timing.Result.ResultLine;

public class AssignTiming {
	
	private static final int[] SUN_DATES = {0,-1,-2,0,-1,-2,0,-1,-2,-1,0,-2};//{0,-1,0,-1,0,-1,-2,-2,-1,0,-2,-2}; 
	private static final int[] SUN_HOURS = {20,20,20,18,18,18,16,16,16,22,22,22};//{20,20,18,18,16,16,20,18,22,22,22,16};
	private static final int[] SUN_USAGE = {0,0,0,0,0,0,0,0,0,1,1,1};
	private static final int[] SUN_DATES_ALT = {0,-1,-2,0,-1,-2,-1,-2,0,-1,0,-2};//{0,-1,0,-1,0,-1,-2,-2,-1,0,-2,-2}; 
	private static final int[] SUN_HOURS_ALT = {20,20,20,18,18,18,16,16,16,22,22,22};//{20,20,18,18,16,16,20,18,22,22,22,16};
	private static final int[] SUN_USAGE_ALT = {0,0,0,0,0,0,0,0,1,1,1,1};
	private static final int[] WED_DATES = {0,-1,1,0,-1,1,0,-1,1,0,-1,1};//{0,-1,1,0,-1,1,0,0,-1,1,-1,1}; 
	private static final int[] WED_HOURS = {20,20,20,18,18,18,16,16,16,22,22,22};//{20,20,20,18,18,18,16,22,22,22,16,16};
	private static final int[] WED_USAGE = {0,0,0,0,0,0,0,0,0,1,1,1};
	private static final int[] THU_DATES = {-1,-2,-1,-2,-1,-2,0,0,0,-1,-2,0};//{-1,-2,-1,-2,-1,-2,0,0,-2,-1,0,0}; 
	private static final int[] THU_HOURS = {20,20,18,18,16,16,20,18,16,22,22,22};//{20,20,18,18,16,16,20,18,22,22,22,16};
	private static final int[] THU_USAGE = {0,0,0,0,0,0,0,0,0,1,1,1};
	private static final int[] EXTRA_HOURS = {20,18,22,16};
	
	private static Map<String,Round> teamMatches = new HashMap<>();
	private static Map<String,Round> timeMatches = new HashMap<>();
	private static Map<String,Round> finalMatches = new HashMap<>();
	
	private static String[] cCTeams = {"RSB"};
	private static String[] cLTeams = {"RCA","WAC"};
	
	
	private static Map<String,Calendar> lastUsedDateForTeam = new HashMap<>();
	private static Map<String,Calendar> tempLastUsedDateForTeam;
	
	private static String[] validHours = {"16","18","20","22"};
	
	private static List<String> roundNameList = new ArrayList<>();
	
	private static List<Schedule> toOutput;
	
	private static String ramadanStartDate = "13/04/2021";
	private static String ramadanEndDate = "13/05/2021";
	//20,21,22 Jul, 14,15 May and fridays after cup round (05/03,07/05,23/07)
	private static String[] noMatchDates = {"20/07/2021","21/07/2021","22/07/2021","14/05/2021","15/05/2021","05/03/2021","07/05/2021","23/07/2021"};
	
	private static int[] cCPreferedDays = {Calendar.SUNDAY,Calendar.WEDNESDAY};
	private static int[] cLPreferedDays = {Calendar.SATURDAY,Calendar.TUESDAY};
	
	private static String specialCCDate = "21/02/2021";
	private static String specialCCDateChange = "19/02/2021";
	
	//private static String calendarFolder = "results" + File.separator + "MoroccoBotola1" + File.separator;
	//private static String preFilesFolder = "assign_20201106-10.57.01"+ File.separator;
	//private static String postFilesFolder = "times_20201106-10.57.01" + File.separator;

	//D1 16Teamrrrr
	//private static String calendarFolder = "results" + File.separator + "2020-11-12_16Teams" + File.separator;
	//private static String[] allTeams = {"RSB","MCO","HUSA","OCS","RCA","WAC","FUS","FAR","IRT","MAT","MAS","RCOZ","RCAZ","DHJ","SCCM","CAYB"};
	//private static String preFilesFolder = "D1"+ File.separator;
	//private static String postFilesFolder = "D1_Schedule" + File.separator;
	//private static String fileNameForInitFiles = "schedule_MoroccoBotola1_";
	//private static String fileNameForFinalFiles = "MoroccoBotola1 Schedule";
	//private static String firstRoundWithoutMajRemaining = "27";
	//private static String fileNameCalendarSchedule = "V12 Botola Pro Schedule Dates 041120.xlsx\";
	//private static int sheetWhereCalendarIsLocated = 2;
	//private static int makeThisCalendar=0;//0 Means make all calendar, other number make a specific calendar

	//D2v1 16Team
	//private static String calendarFolder = "results" + File.separator + "2020-11-12_16Teams" + File.separator;
	//private static String[] allTeams = {"WAF","CAK","OD","JSS","WST","KAC","OCK","RBM","KACM","CJBG","TAS","RAC","ASS","IZK","SM","UTS"};
	//private static String preFilesFolder = "D2"+ File.separator;
	//private static String postFilesFolder = "D2_Schedule" + File.separator;
	//private static String fileNameForInitFiles = "schedule_MoroccoBotola2v1_";
	//private static String fileNameForFinalFiles = "MoroccoBotola2v1 Schedule";
	//private static String firstRoundWithoutMajRemaining = "27";
	//private static String fileNameCalendarSchedule = "V12 Botola Pro Schedule Dates 041120.xlsx\";
	//private static int sheetWhereCalendarIsLocated = 2;
	//private static int makeThisCalendar=0;//0 Means make all calendar, other number make a specific calendar

	//D1 18Team
	//private static String calendarFolder = "results" + File.separator + "2020-11-12_18Teams" + File.separator;
	//private static String[] allTeams = {"RSB","MCO","HUSA","OCS","RCA","WAC","FUS","FAR","IRT","MAT","MAS","RCOZ","RCAZ","DHJ","SCCM","CAYB","RBM","OCK"};
	//private static String preFilesFolder = "D1"+ File.separator;
	//private static String postFilesFolder = "D1_Schedule" + File.separator;
	//private static String fileNameForInitFiles = "schedule_MoroccoBotola1-18teams_";
	//private static String fileNameForFinalFiles = "MoroccoBotola1 Schedule";
	//private static String firstRoundWithoutMajRemaining = "30";
	//private static String fileNameCalendarSchedule = "V12 Botola Pro Schedule Dates 041120.xlsx\";
	//private static int sheetWhereCalendarIsLocated = 1;
	//private static int makeThisCalendar=0;//0 Means make all calendar, other number make a specific calendar
	
	//D2v1 18Team
	//private static String calendarFolder = "results" + File.separator + "2020-11-12_18Teams" + File.separator;
	//private static String[] allTeams = {"WAF","CAK","OD","JSS","WST","KAC","CSMO","CRA","KACM","CJBG","TAS","RAC","ASS","IZK","SM","UTS","USK","UST"};
	//private static String preFilesFolder = "D2"+ File.separator;
	//private static String postFilesFolder = "D2_Schedule" + File.separator;
	//private static String fileNameForInitFiles = "schedule_MoroccoDiv2-18Teamsv3_";
	//private static String fileNameForFinalFiles = "MoroccoBotola2v1 Schedule";
	//private static String firstRoundWithoutMajRemaining = "30";
	//private static String fileNameCalendarSchedule = "V12 Botola Pro Schedule Dates 041120.xlsx\";
	//private static int sheetWhereCalendarIsLocated = 1;
	//private static int makeThisCalendar=0;//0 Means make all calendar, other number make a specific calendar

	//FINAL D1 432
	/*	private static String calendarFolder = "results" + File.separator + "Final_2020_21" + File.separator;
	private static String[] allTeams = {"RSB","MCO","HUSA","OCS","RCA","WAC","FUS","FAR","IRT","MAT","MAS","RCOZ","RCAZ","DHJ","SCCM","CAYB"};
	private static String preFilesFolder = "D1"+ File.separator;
	private static String postFilesFolder = "D1_Schedule" + File.separator;
	private static String fileNameForInitFiles = "schedule_MoroccoBotola1_";
	private static String fileNameForFinalFiles = "MoroccoBotola1 Schedule";
	private static String firstRoundWithoutMajRemaining = "26";
	private static String fileNameCalendarSchedule = "V14 Botola Pro Schedule Dates 171120.xlsx";
	private static int sheetWhereCalendarIsLocated = 1;
	private static int makeThisCalendar=432;
	private static int numberOfGenerations = 2000;
	private static String[] noShareHomeMatch = null;
	private static String otherDivFilesFolder = null;
	private static String otherDivFileName = null;
	private static String otherDivBestSchedule = null;
	private static int expectedMeanRestForCCCLTeams=-130;
	private static int expectedMeanRestForNonCCCLTeams=30;
	private static String[] invalidDatesForCLTeams = {"04/12/2020","05/12/2020"};
*/		

	//FINAL D2 432
	private static String calendarFolder = "results" + File.separator + "Final_2020_21" + File.separator;
	private static String[] allTeams = {"WAF","CAK","OD","JSS","WST","KAC","OCK","RBM","KACM","CJBG","TAS","RAC","ASS","IZK","SM","UTS"};
	private static String preFilesFolder = "D2"+ File.separator;
	private static String postFilesFolder = "D2_Schedule" + File.separator;
	private static String fileNameForInitFiles = "schedule_MoroccoBotola2v1_";
	private static String fileNameForFinalFiles = "MoroccoBotola2v1 Schedule";
	private static String firstRoundWithoutMajRemaining = "26";
	private static String fileNameCalendarSchedule = "V14 Botola Pro Schedule Dates 171120.xlsx";
	private static int sheetWhereCalendarIsLocated = 1;
	private static int makeThisCalendar=432;
	private static int numberOfGenerations = 1000;
	private static String[] noShareHomeMatch = {"OCK","RCOZ"};//Pairs of teams of different divisions that cannot play same day at home (first team this div, second team other div)
	private static String otherDivFilesFolder = "D1_Schedule" + File.separator;
	private static String otherDivFileName = "MoroccoBotola1 Schedule";
	private static String otherDivBestSchedule = "00052";
	private static int expectedMeanRestForCCCLTeams=0;
	private static int expectedMeanRestForNonCCCLTeams=0;
	private static String[] invalidDatesForCLTeams = null;

	
	private static Map<String,List<String>> notValidHomeDates;
	
	private static Map<String, Integer> postponedGamesList;
	
	private static Map<String, Integer> scheduleCostList;
	
	public static void main(String[] args) {

		
		loadCalendar();
		notValidHomeDates=null;
		postponedGamesList= new HashMap<>();
		scheduleCostList= new HashMap<>();
		
		for (int iterations=1;iterations<=numberOfGenerations;iterations++) {
			if(noShareHomeMatch!=null) {
				notValidHomeDates=new HashMap<>();
				int teamsPosition=0;
				while (teamsPosition<noShareHomeMatch.length) {
					List<String> notValidDates = new ArrayList<>();
					String team = noShareHomeMatch[teamsPosition];
					String otherDivTeam = noShareHomeMatch[teamsPosition+1];
					String useSchedule = otherDivBestSchedule!=null?otherDivBestSchedule:String.format("%05d", iterations)+ otherDivFileName;
					XSSFWorkbook wb = null;
					try (FileInputStream fis = new FileInputStream(new File(calendarFolder  + otherDivFilesFolder  +useSchedule+ otherDivFileName + " Teams"+".xlsx"))){
						wb = new XSSFWorkbook(fis);   
						XSSFSheet sheet = wb.getSheetAt(0);   
						Iterator<Row> itr = sheet.iterator();    //iterating over excel file 
						while (itr.hasNext())                 
						{  
							Row row = itr.next();  
							Cell teamCell = row.getCell(0);
							if(teamCell==null) {
								continue;
							}
							String teamName = teamCell.getStringCellValue();
							if(!otherDivTeam.equals(teamName)) {
								continue;
							}
							Cell homeTeamCell = row.getCell(3);
							if(homeTeamCell==null) {
								continue;
							}
							String homeTeamName = homeTeamCell.getStringCellValue();
							if(!otherDivTeam.equals(homeTeamName)) {
								continue;
							}						
							Cell dateCell = row.getCell(5);
							if(dateCell==null) {
								continue;
							}
							notValidDates.add(dateCell.getStringCellValue());
						}
						notValidHomeDates.put(team, notValidDates);
					}catch (Exception e) {
						e.printStackTrace();
					}finally {
						try {
							wb.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
					teamsPosition=teamsPosition+2;
				}
			}
			
			teamMatches = new HashMap<>();
			finalMatches = new HashMap<>();
			lastUsedDateForTeam = new HashMap<>();
			String fileName = fileNameForInitFiles+String.format("%05d", makeThisCalendar)+".csv";
		
			try (BufferedReader br = new BufferedReader(new FileReader(calendarFolder  + preFilesFolder + fileName))) {
			    String line;
			    boolean doNothingUntilRound=true;
			    Round round = null;
			    while ((line = br.readLine()) != null) {
			        String[] values = line.split(",");
			        if(doNothingUntilRound) {
			        	if("Round".equals(values[0])) {
			        		doNothingUntilRound=false;
			        	}
			        	continue;
			        }
			        if(round == null || round.getRoundNumber()!=Integer.parseInt(values[0])) {
			        	round = new Round();
			        	teamMatches.put(values[0],round);
			        	round.setRoundNumber(Integer.parseInt(values[0]));
			        }
			        Match match = new Match();
			        match.setTeam1(values[1]);
			        match.setTeam2(values[2]);
			        match.setRound(Integer.parseInt(values[0]));
			        round.addMatch(match);
			    }
	        	round = new Round();
	        	teamMatches.put("MAJ",round);
	        	round.setRoundNumber(0);
			    
			}catch (Exception e) {
				e.printStackTrace();
			}
			
			
			Map<String,Round> finalMatchesPhoto = new HashMap<>();
			Map<String,Calendar> lastUsedDateForTeamPhoto = new HashMap<>();
			int doSomeTries = 0;
			boolean notSolved=true;
			int roundPositionFor27=0;
			boolean doRandom= true; //iterations==1?false:true;
			boolean hasMissedMatchesAt27 = false;
			int maxTries = 1000;
			while (doSomeTries<maxTries && notSolved && !hasMissedMatchesAt27)
			{
				hasMissedMatchesAt27 = false;
				String prevRound=null;
				for ( int roundPosition = roundPositionFor27; roundPosition < roundNameList.size();roundPosition++) {
					String roundName = roundNameList.get(roundPosition);
					if(roundPositionFor27==0 && firstRoundWithoutMajRemaining.equals(roundName) && teamMatches.get("MAJ").getMatches().size()==0) {
						finalMatchesPhoto = new HashMap<>();
						lastUsedDateForTeamPhoto = new HashMap<>();
						finalMatchesPhoto.putAll(finalMatches);
						lastUsedDateForTeamPhoto.putAll(lastUsedDateForTeam);
						roundPositionFor27=roundPosition;
					}else if (firstRoundWithoutMajRemaining.equals(roundName) && teamMatches.get("MAJ").getMatches().size()>0) {
						hasMissedMatchesAt27 = true;
					}
					String nextRound = null;
					String nextRoundNoMaj = null;
					if(roundPosition+1<roundNameList.size()) {
						nextRound = roundNameList.get(roundPosition+1);
						nextRoundNoMaj= nextRound;
						int extraRound = 2;
						while (nextRoundNoMaj.indexOf("MAJ")>=0) {
							if(roundPosition+extraRound<roundNameList.size()) {
								nextRoundNoMaj = roundNameList.get(roundPosition+extraRound);
								extraRound++;
							}
						}
					}
					findBestScheduleForRound(roundName,prevRound,nextRound,nextRoundNoMaj,doRandom);
					prevRound=roundName;
					//printSchedule(roundName);
				}
				//If we end with some matches left as pending MAJat the end, but we do not have pending matches after the last MAJ round
				//we try to redo the last rounds (from the last MAJ round) in a diferent way (random match order to solve)
				if (doSomeTries<maxTries-1 && !hasMissedMatchesAt27 && teamMatches.get("MAJ").getMatches().size()>0) {
					//System.out.println("Missed matches: "+teamMatches.get("MAJ").getMatches().size());
					doSomeTries++;
					doRandom=true;
					finalMatches = new HashMap<>();
					lastUsedDateForTeam = new HashMap<>();
					finalMatches.putAll(finalMatchesPhoto);
					lastUsedDateForTeam.putAll(lastUsedDateForTeamPhoto);
		        	Round round = new Round();
		        	teamMatches.put("MAJ",round);
				}else {
					notSolved=false;
				}
			}


			if(toOutput==null){
				toOutput = new ArrayList<>();
			}
			
			Round teamRound = teamMatches.get("MAJ");
			if(teamRound.getMatches().size()==0) {
				Schedule schedule = new Schedule(finalMatches,roundNameList,String.valueOf(iterations));
				schedule.setFinalMatches(finalMatches);
				boolean itsGood = true;
				if(itsGood) {
					System.out.println("Valid File: "+fileName+" iteration "+iterations);
					toOutput.add(schedule);
				}
			}else {
				iterations--;
			}
		}
		System.out.println("Valid Files: "+toOutput.size());
		try {
			Arrays.stream(new File(calendarFolder + postFilesFolder).listFiles()).forEach(File::delete);
		}catch(Exception e) {
			//There was no folder, then no need to delete.
		}
		/*for(Schedule schedule : toOutput) {
			writeExcel(schedule);
		}
		
		for (int iterations=1;iterations<=numberOfGenerations;iterations++) {
			scheduleCostList.put(String.format("%05d", iterations), (postponedGamesList.get(String.format("%05d", iterations)).intValue()*100));
		}
		List<Integer> orderedList = new ArrayList<>(scheduleCostList.values());
		Collections.sort(orderedList);*/
		
		calculateRestCost();
	    FileWriter fileWriter;
	    FileWriter fileWriter2;
		try {
			fileWriter = new FileWriter(calendarFolder + postFilesFolder +"stats.csv");
		    PrintWriter printWriter = new PrintWriter(fileWriter);
			String bestSchedule = null;
			int bestCost = 0;
			printWriter.println("Schedule;Postponed games;Cost;");
		    for(Schedule schedule : toOutput) {
		    	printWriter.println(schedule.getFileName()+";" + schedule.getPostponedGames() +";" + schedule.getCost()+";");
		    }
		    printWriter.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		Collections.sort(toOutput);
		for(int i = 0; i<Math.min(5,toOutput.size());i++) {
			writeExcel(toOutput.get(i));
		}
		
		System.out.println("ALL DONE");
	}
	
	private static void findBestScheduleForRound(String roundNumber,String prevRound, String nextRound,String nextRoundNoMaj,boolean doRandom) {
		//System.out.println("Round "+roundNumber);
		boolean isMAJRound = roundNumber.indexOf("MAJ")>=0;
		Round teamRound = teamMatches.get(!isMAJRound?roundNumber:"MAJ");
		if(doRandom && !isMAJRound) {
			Collections.shuffle(teamRound.getMatches());
		}
		Round timeRound = timeMatches.get(roundNumber);
		Round finalRound = new Round();
		finalRound.setDate(timeRound.getDate());
		finalMatches.put(roundNumber, finalRound);
		finalRound.setRoundNumber(timeRound.getRoundNumber());
		finalRound.setHasCC(timeRound.isHasCC());
		finalRound.setHasCL(timeRound.isHasCL());
		
		//Check if next round is CC or CL. Later we will use this to set our maxDate for CC or CL teams
		boolean nextHasCC = false;
		boolean nextHasCL = false;
		Calendar nextRoundDay = null;
		if(nextRound!=null) {
			Round nextTimeRound = timeMatches.get(nextRound);
			nextRoundDay = (Calendar)nextTimeRound.getDate();
			if(nextTimeRound.isHasCC()) {
				nextHasCC = true;
			}
			if(nextTimeRound.isHasCL()) {
				nextHasCL = true;
			}
		}
		Round nextTeamRound = null;
		if(nextRoundNoMaj!=null) {
			nextTeamRound = teamMatches.get(nextRoundNoMaj);
		}
		
		if(prevRound!=null) {
			Round previousRound = finalMatches.get(prevRound);
			if(previousRound.getRoundNumber()==0) {
				pseudoShuffleTeamRound(teamRound,previousRound);
			}
			//previousRound.reverseSortMatches();
			int numberOfIterations = teamRound.getMatches().size();
			List<Match> toRemove = new ArrayList<>();
			boolean redoRound = true;
			int addToStart=0;
			int useUntil=0;
			//We set this while to start from another match when we cannot fit all matchs in a round. It will be done as many times as match has this round.
			while (numberOfIterations>0 && redoRound) {
				tempLastUsedDateForTeam = new HashMap<>();
				for(int i=0;i<numberOfIterations;i++) {
					int position = (i+addToStart)%numberOfIterations;
					Match teamMatch = teamRound.getMatch(position);
					String team1 = teamMatch.getTeam1();
					String team2 = teamMatch.getTeam2();
					boolean matchHasCCTeam = Arrays.stream(cCTeams).anyMatch(team1::equals) || Arrays.stream(cCTeams).anyMatch(team2::equals);
					boolean matchHasCLTeam = Arrays.stream(cLTeams).anyMatch(team1::equals) || Arrays.stream(cLTeams).anyMatch(team2::equals);
					
					Calendar minDate = getMinDate(team1, team2, previousRound, timeRound, matchHasCCTeam, matchHasCLTeam);
					Calendar maxDate = getMaxDate(team1, team2, timeRound, nextRoundDay, nextHasCC, matchHasCCTeam, matchHasCLTeam, nextHasCL);
					
					boolean nextMatchHasCLTeam = getNextMatchHasCLTeam(team1, team2, nextTeamRound);
					
					Match timeMatch = getBestFreeDate(timeRound,finalRound,minDate,maxDate,matchHasCCTeam,matchHasCLTeam,nextMatchHasCLTeam,team1,team2,useUntil);
					
					if(timeMatch==null) {
						//If we don't find a solution and all redo are already done, the mach is stored in MAJ to be played in a MAJ round
						//Else if to prepare for another try if we already can do it.
						if(addToStart==numberOfIterations) {
							if(useUntil==0) {
								useUntil=1;
								addToStart=0;
								finalRound.resetMatches();
								toRemove = new ArrayList<>();
								redoRound = true;
								break;								
							}
							Match finalMatch = new Match();
							finalMatch.setBasicValue(1000);
							finalMatch.setInternationalExtra(false);
							finalMatch.setTeam1(teamMatch.getTeam1());
							finalMatch.setTeam2(teamMatch.getTeam2());	
							finalRound.addMatch(finalMatch);
							if(!isMAJRound) {
								teamMatches.get("MAJ").addMatch(teamMatch);
							}
							redoRound = false;
							continue;
						}else {
							addToStart++;
							finalRound.resetMatches();
							toRemove = new ArrayList<>();
							redoRound = true;
							break;
						}
					}
					redoRound = false;
					//If is a MAJ round and the match has a date we delete the match from MAJ pending games.
					if(isMAJRound)
					{
						toRemove.add(teamMatch);						
					}
					Match finalMatch = createMatch(teamMatch, timeMatch);
					Calendar nextValidDate = (Calendar)finalMatch.getDate().clone();
					tempLastUsedDateForTeam.put(finalMatch.getTeam1(), nextValidDate);
					tempLastUsedDateForTeam.put(finalMatch.getTeam2(), nextValidDate);
					finalRound.addMatch(finalMatch);
				}
				
				if(!redoRound) {
					Iterator it = tempLastUsedDateForTeam.entrySet().iterator();
				    while (it.hasNext()) {
				        Map.Entry<String, Calendar> pair = (Map.Entry)it.next();
				        lastUsedDateForTeam.put(pair.getKey(), pair.getValue());
				    }
					
					if(isMAJRound) {
						for (Match teamMatch : toRemove) {
							teamMatches.get("MAJ").getMatches().remove(teamMatch);
						}
					}
				}
			}

		}else {
			tempLastUsedDateForTeam = new HashMap<>();
			for (Match teamMatch : teamRound.getMatches()) {
				String team1 = teamMatch.getTeam1();
				String team2 = teamMatch.getTeam2();
				boolean matchHasCCTeam = Arrays.stream(cCTeams).anyMatch(team1::equals) || Arrays.stream(cCTeams).anyMatch(team2::equals);
				boolean matchHasCLTeam = Arrays.stream(cLTeams).anyMatch(team1::equals) || Arrays.stream(cLTeams).anyMatch(team2::equals);
				
				Calendar minDate = getMinDate(team1, team2, null, timeRound, matchHasCCTeam, matchHasCLTeam);
				Calendar maxDate = getMaxDate(team1, team2, timeRound, nextRoundDay, nextHasCC, matchHasCCTeam, matchHasCLTeam, nextHasCL);

				boolean nextMatchHasCLTeam = getNextMatchHasCLTeam(team1, team2, nextTeamRound);
				
				Match timeMatch = getBestFreeDate(timeRound,finalRound,minDate,maxDate,matchHasCCTeam,matchHasCLTeam,nextMatchHasCLTeam,team1,team2,0);
				Match finalMatch = new Match();
				if(timeMatch==null) {
					finalMatch.setBasicValue(1000);
					finalMatch.setInternationalExtra(false);
					finalMatch.setTeam1(teamMatch.getTeam1());
					finalMatch.setTeam2(teamMatch.getTeam2());	
					teamMatches.get("MAJ").addMatch(teamMatch);
				}else {
					finalMatch = createMatch(teamMatch, timeMatch);
					Calendar nextValidDate = (Calendar)finalMatch.getDate().clone();
					lastUsedDateForTeam.put(finalMatch.getTeam1(), nextValidDate);
					lastUsedDateForTeam.put(finalMatch.getTeam2(), nextValidDate);
				}
				finalRound.addMatch(finalMatch);
			}
		}
	}
	
	private static Match getBestFreeDate(Round timeRound,Round finalRound,Calendar minDate, Calendar maxDate, boolean matchHasCCTeam, boolean matchHasCLTeam,boolean nextMatchHasCLTeam,String homeTeam,String awayTeam,int useUntil) {
		//If it's a Monday round then we can ignore the prefered day
		boolean  doOnlyPrefered= Calendar.MONDAY!=timeRound.getDate().get(Calendar.DAY_OF_WEEK) && (matchHasCCTeam || matchHasCLTeam || nextMatchHasCLTeam);
		boolean addToDistance = true;
		boolean firstTry = true;
		while (firstTry || doOnlyPrefered) {
			if(!firstTry) {
				if(!addToDistance) {
					doOnlyPrefered=false;
				}
				addToDistance=false;
			}
			firstTry = false;
			for (Match match : timeRound.getMatches()) {
				if(match.getUsage()>useUntil) {
					break;
				}
				if(isInvalidDate(match.getDate(),homeTeam,awayTeam)) {
					continue;
				}
				if(doOnlyPrefered && matchHasCCTeam  && !matchHasCLTeam && !(IntStream.of(cCPreferedDays).anyMatch(x -> x == match.getDate().get(Calendar.DAY_OF_WEEK)))) {
					continue;
				}
				if(doOnlyPrefered && (matchHasCLTeam || nextMatchHasCLTeam) && !(IntStream.of(cLPreferedDays).anyMatch(x -> x == match.getDate().get(Calendar.DAY_OF_WEEK)))) {
					continue;
				}
				Calendar tempMaxDate = (Calendar)maxDate.clone();
				Calendar tempMinDate = (Calendar)minDate.clone();
				if(addToDistance) {
					//tempMaxDate.add(Calendar.DATE, -1);
					//tempMinDate.add(Calendar.DATE, 1);
				}
				if(match.getDate().after(tempMinDate) && match.getDate().before(tempMaxDate)) {
					if(finalRound.getMatches()==null) {
						return match;
					}
					boolean found = false;
					for (Match finalMatch : finalRound.getMatches()) {
						if(finalMatch.getDate()!=null && finalMatch.getDate().equals(match.getDate())) {
							found = true;
							break;
						}
					}
					if(!found) {
						return match;
					}
				}
			}
		}
		return null;
	}
	
	private static Match createMatch(Match teamMatch,Match timeMatch) {
		Match finalMatch = new Match();
		finalMatch.setBasicValue(timeMatch.getBasicValue());
		finalMatch.setDate(timeMatch.getDate());
		finalMatch.setInternationalExtra(timeMatch.isInternationalExtra());
		finalMatch.setTeam1(teamMatch.getTeam1());
		finalMatch.setTeam2(teamMatch.getTeam2());
		finalMatch.setRound(teamMatch.getRound());
		return finalMatch;
		
	}
	
	private static void printSchedule(String roundName) {
		Round round = finalMatches.get(roundName);
		round.sortMatches();
		for (Match match : round.getMatches()) {
			System.out.println((match.getDate()!=null?match.getDate().getTime():"APP")+" "+match.getTeam1()+" "+match.getTeam2());
		}
	}
	
	private static void printData (Result result) {
		System.out.println(result.getTeam());
		result.getByHourHome();
		result.getByHourAway();
		for (String hour : validHours) {
	    	System.out.println(hour+": "+result.getByHourHome().get(hour)+ " "+result.getByHourAway().get(hour));
	    }
	    for (ResultLine match : result.getResults()) {
	    	System.out.println((match.getRound()==0?"MAJ":+match.getRound())+" "+match.getMatchLiteral()+" "+match.getMatchDate()+" "+match.getMatchHour()+" "+match.getDaysBefore());
	    }

	}
	
	private static boolean isInvalidDate(Calendar cal,String homeTeam,String awayTeam) {
		SimpleDateFormat format1 = new SimpleDateFormat("dd/MM/yyyy");
		String matchDate = format1.format(cal.getTime()); 
		if(Arrays.stream(noMatchDates).anyMatch(matchDate::equals)) {
			return true;
		}
		if(notValidHomeDates!=null && notValidHomeDates.get(homeTeam)!=null) {
			List<String> notValidHomeDatesList = notValidHomeDates.get(homeTeam);
			if(notValidHomeDatesList.contains(matchDate)) {
				return true;
			}
		}
		boolean matchHasCLTeam = Arrays.stream(cLTeams).anyMatch(homeTeam::equals) || Arrays.stream(cLTeams).anyMatch(awayTeam::equals);
		if(matchHasCLTeam && Arrays.stream(invalidDatesForCLTeams).anyMatch(matchDate::equals)) {
			return true;
		}
		return false;
	}
	
	private static void writeExcel(Schedule schedule) {
		XSSFWorkbook workbook = null;
		XSSFWorkbook workbook2 = null;
		FileOutputStream fileOut = null;
		FileOutputStream fileOut2 = null;
        try {
	        workbook = new XSSFWorkbook();
	        workbook2 = new XSSFWorkbook();
	        
        	XSSFSheet sheet = workbook.createSheet("Schedule");
	        XSSFRow rowhead = sheet.createRow((short)0);
	        rowhead.createCell(1).setCellValue("Date");
	        rowhead.createCell(2).setCellValue("Hour");
	        rowhead.createCell(3).setCellValue("Home");
	        rowhead.createCell(4).setCellValue("Away");
	        short rowIterator = 1;
        	for(String roundName:schedule.getRoundNameList()) {
        		Round round = schedule.getRounds().get(roundName);
        		round.sortMatches();
        		XSSFRow row = sheet.createRow(rowIterator);
        		rowIterator++;
        		row.createCell(0).setCellValue("Round "+roundName);
        		for (Match match : round.getMatches()) {
        			if(match.getDate()!=null) {
        				row = sheet.createRow(rowIterator);
        				SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
	        			row.createCell(1).setCellValue(sdf.format(match.getDate().getTime()));
	    				sdf = new SimpleDateFormat("dd/MM/yyyy");
	    				Calendar startRamadanCalendar = Calendar.getInstance();
	    				startRamadanCalendar.setTime(sdf.parse(ramadanStartDate));
	    				Calendar endRamadanCalendar = Calendar.getInstance();
	    				endRamadanCalendar.setTime(sdf.parse(ramadanEndDate));
	    				endRamadanCalendar.set(Calendar.HOUR_OF_DAY, 23);
	    				if(match.getDate().after(startRamadanCalendar) && match.getDate().before(endRamadanCalendar)) {
	    					row.createCell(2).setCellValue("22:00");
	    				}else {
		        			sdf = new SimpleDateFormat("HH:mm");
		        			row.createCell(2).setCellValue(sdf.format(match.getDate().getTime()));
	    				}
	        			row.createCell(3).setCellValue(match.getTeam1());
	        			row.createCell(4).setCellValue(match.getTeam2());
	        			rowIterator++;
        			}
        		}	        		
        	}
        	
        	
        	
        	
        	sheet = workbook2.createSheet("Schedule");
        	XSSFSheet sheet2 = workbook2.createSheet("Statistics");
	        rowhead = sheet.createRow((short)0);
	        XSSFRow rowhead2 = sheet2.createRow((short)0);
	        rowhead.createCell(0).setCellValue("Team");
	        rowhead.createCell(1).setCellValue("J");
	        rowhead.createCell(2).setCellValue("MAJ");
	        rowhead.createCell(3).setCellValue("Home");
	        rowhead.createCell(4).setCellValue("Away");
	        rowhead.createCell(5).setCellValue("Date");
	        rowhead.createCell(6).setCellValue("Heure");
	        rowhead.createCell(7).setCellValue("Jour off");
	        rowhead.createCell(8).setCellValue("Acc Jour off (5 matches)");
	        rowhead2.createCell(0).setCellValue("Team");
	        rowhead2.createCell(1).setCellValue("16h00 Home");
	        rowhead2.createCell(2).setCellValue("16h00 Away");
	        rowhead2.createCell(3).setCellValue("18h00 Home");
	        rowhead2.createCell(4).setCellValue("18h00 Away");
	        rowhead2.createCell(5).setCellValue("20h00 Home");
	        rowhead2.createCell(6).setCellValue("20h00 Away");
	        rowhead2.createCell(7).setCellValue("22h00 Home");
	        rowhead2.createCell(8).setCellValue("22h00 Away");
	        short rowNumber = 1;
	        short rowNumber2 = 1;
        	Map<String,Result> resultMap = new HashMap<>();
	        for (String team : allTeams) { 
	        	resultMap.put(team, schedule.getMatchesForTeam(team,ramadanStartDate, ramadanEndDate, Arrays.stream(cCTeams).anyMatch(team::equals), Arrays.stream(cLTeams).anyMatch(team::equals),specialCCDate,specialCCDateChange));
	        }
	        int postponedGames = 0;
			for (String team : allTeams) { 
				Result result = resultMap.get(team);
		        XSSFRow row = sheet2.createRow(rowNumber2);
		        row.createCell(0).setCellValue(team);
		        row.createCell(1).setCellValue((Integer)(result.getByHourHome().get("16")==null?0:result.getByHourHome().get("16")));
		        row.createCell(2).setCellValue((Integer)(result.getByHourAway().get("16")==null?0:result.getByHourAway().get("16")));
		        row.createCell(3).setCellValue((Integer)(result.getByHourHome().get("18")==null?0:result.getByHourHome().get("18")));
		        row.createCell(4).setCellValue((Integer)(result.getByHourAway().get("18")==null?0:result.getByHourAway().get("18")));
		        row.createCell(5).setCellValue((Integer)(result.getByHourHome().get("20")==null?0:result.getByHourHome().get("20")));
		        row.createCell(6).setCellValue((Integer)(result.getByHourAway().get("20")==null?0:result.getByHourAway().get("20")));
		        row.createCell(7).setCellValue((Integer)(result.getByHourHome().get("22")==null?0:result.getByHourHome().get("22")));
		        row.createCell(8).setCellValue((Integer)(result.getByHourAway().get("22")==null?0:result.getByHourAway().get("22")));
		        rowNumber2++;
		        for (ResultLine match : result.getResults()) {
			        row = sheet.createRow(rowNumber);
			        row.createCell(0).setCellValue(team);
			        if(match.getRound()==-1 || match.getRecRound()==-1) {
			        	row.createCell(1).setCellValue("CC");
			        }else if(match.getRound()==-2 || match.getRecRound()==-2) {
			        	row.createCell(1).setCellValue("CL");
			        }else {
			        	row.createCell(1).setCellValue((Integer)(match.getRound()==0?match.getRecRound():match.getRound()));
			        }
			        if(match.getRound()==-1 || match.getRecRound()==-1 || match.getRound()==-2 || match.getRecRound()==-2) {
			        	row.createCell(2).setCellValue(0);
			        	row.createCell(3).setCellValue("");
			        	row.createCell(4).setCellValue("");
			        }else {
			        	postponedGames = postponedGames + (match.getRound()==0?1:0);
			        	row.createCell(2).setCellValue((int)(match.getRound()==0?1:0));
			        	row.createCell(3).setCellValue(match.getTeam1());
			        	row.createCell(4).setCellValue(match.getTeam2());
			        }
			        row.createCell(5).setCellValue(match.getMatchDate());
			        row.createCell(6).setCellValue((String)("00h00".equals(match.getMatchHour())?"":match.getMatchHour()));
			        if(match.getDaysBefore()==0) {
			        	row.createCell(7).setCellValue("");
			        }else {
			        	row.createCell(7).setCellValue(match.getDaysBefore());
			        }
			        
			        if(match.getRound()==-1 || match.getRecRound()==-1 || match.getRound()==-2 || match.getRecRound()==-2) {
			        	row.createCell(8).setCellValue("");
			        }else {				        
				        Result rivalResult = resultMap.get(match.getTeam1().equals(team)?match.getTeam2():match.getTeam1());
				        ResultLine rivalResultLine = rivalResult.getResult(match.getMatchLiteral());
				        row.createCell(8).setCellValue(match.getFiveMatchRestTime()-rivalResultLine.getFiveMatchRestTime());
			        }
			        
			        rowNumber++;
		        }
			}
			
			postponedGamesList.put(String.format("%05d", Integer.parseInt(schedule.getFileName())),postponedGames);
			 
			Files.createDirectories(Paths.get(calendarFolder + postFilesFolder));
	        fileOut = new FileOutputStream(calendarFolder + postFilesFolder +String.format("%05d", Integer.parseInt(schedule.getFileName()))+ fileNameForFinalFiles + " Dates"+".xlsx");
	        fileOut2 = new FileOutputStream(calendarFolder + postFilesFolder +String.format("%05d", Integer.parseInt(schedule.getFileName()))+ fileNameForFinalFiles + " Teams"+".xlsx");
	        workbook.write(fileOut);
	        workbook2.write(fileOut2);
        } catch ( Exception ex ) {
            ex.printStackTrace();
        } finally {
        	try {
        		workbook.close();
        	}catch(Exception e) {
        		
        	}
        	try {
        		workbook2.close();
        	}catch(Exception e) {
        		
        	}
        	try {
        		fileOut.close();
        	}catch(Exception e) {
        		
        	}
        	try {
        		fileOut2.close();
        	}catch(Exception e) {
        		
        	}
        }
        System.out.println("Output files for Schedule "+schedule.getFileName()+" done");

	}
	
	private static void loadCalendar() {
		XSSFWorkbook wb = null;
		try (FileInputStream fis = new FileInputStream(new File("config" + File.separator + fileNameCalendarSchedule)))
		{  
			//creating Workbook instance that refers to .xlsx file  
			wb = new XSSFWorkbook(fis);   
			XSSFSheet sheet = wb.getSheetAt(sheetWhereCalendarIsLocated);     //creating a Sheet object to retrieve object  
			Iterator<Row> itr = sheet.iterator();    //iterating over excel file 
			Round round=null;
			int majNum=1;
			while (itr.hasNext())                 
			{  
				Row row = itr.next();  
				Cell roundCell = row.getCell(3);
				Cell majCell = row.getCell(4);
				CellType roundCellType = roundCell == null ? CellType.BLANK : roundCell.getCellType();
				CellType majCellType = majCell == null ? CellType.BLANK : majCell.getCellType();
				if(!CellType.NUMERIC.equals(roundCellType) && !CellType.NUMERIC.equals(majCellType)) {
					continue;
				}
				Calendar roundDay = Calendar.getInstance();
				roundDay.setTime(row.getCell(0).getDateCellValue());
				round = new Round();
				String roundNumber = CellType.NUMERIC.equals(roundCellType)?String.valueOf((int)roundCell.getNumericCellValue()):"MAJ-"+String.valueOf(majNum);
				timeMatches.put(roundNumber,round);
				roundNameList.add(roundNumber);
				round.setRoundNumber(CellType.NUMERIC.equals(roundCellType)?Integer.parseInt(roundNumber):0);
				round.setDate(roundDay);
				if(CellType.NUMERIC.equals(majCellType)) {
					for (int i = 0; i < WED_DATES.length; i++) { 
						Match match = new Match();
						Calendar matchDate = (Calendar)roundDay.clone();
						matchDate.add(Calendar.DATE, WED_DATES[i]);
						matchDate.add(Calendar.HOUR, WED_HOURS[i]);
						match.setDate(matchDate);
						match.setInternationalExtra(true);
						match.setBasicValue(i);
						match.setRound(round.getRoundNumber());
						round.addMatch(match);	
					}
					majNum++;
				}else {
					if (roundDay.get(Calendar.DAY_OF_WEEK)==Calendar.SUNDAY || roundDay.get(Calendar.DAY_OF_WEEK)==Calendar.SATURDAY) {
						for (int i = 0; i < SUN_DATES.length; i++) { 
							Match match = new Match();
							Calendar matchDate = (Calendar)roundDay.clone();
							if(round.getRoundNumber()==1) {
								matchDate.add(Calendar.DATE, SUN_DATES_ALT[i]);
								matchDate.add(Calendar.HOUR, SUN_HOURS_ALT[i]);
								match.setUsage(SUN_USAGE_ALT[i]);
							}else {
								matchDate.add(Calendar.DATE, SUN_DATES[i]);
								matchDate.add(Calendar.HOUR, SUN_HOURS[i]);
								match.setUsage(SUN_USAGE[i]);
							}
							match.setDate(matchDate);
							match.setInternationalExtra(false);
							match.setBasicValue(i);
							match.setRound(round.getRoundNumber());
							
							round.addMatch(match);
						}
					}
					if (roundDay.get(Calendar.DAY_OF_WEEK)==Calendar.THURSDAY) {
						for (int i = 0; i < THU_DATES.length; i++) { 
							Match match = new Match();
							Calendar matchDate = (Calendar)roundDay.clone();
							matchDate.add(Calendar.DATE, THU_DATES[i]);
							matchDate.add(Calendar.HOUR, THU_HOURS[i]);
							match.setDate(matchDate);
							match.setInternationalExtra(false);
							match.setBasicValue(i);
							match.setRound(round.getRoundNumber());
							match.setUsage(THU_USAGE[i]);
							round.addMatch(match);
						}
					}
					if (roundDay.get(Calendar.DAY_OF_WEEK)==Calendar.WEDNESDAY || roundDay.get(Calendar.DAY_OF_WEEK)==Calendar.TUESDAY) {
						for (int i = 0; i < WED_DATES.length; i++) { 
							Match match = new Match();
							Calendar matchDate = (Calendar)roundDay.clone();
							matchDate.add(Calendar.DATE, WED_DATES[i]);
							matchDate.add(Calendar.HOUR, WED_HOURS[i]);
							match.setDate(matchDate);
							match.setInternationalExtra(false);
							match.setBasicValue(i);
							match.setRound(round.getRoundNumber());
							match.setUsage(WED_USAGE[i]);
							round.addMatch(match);
						}
					}
				}
				
				String internationalDay = "";
				if(row.getCell(1)!=null) {
					internationalDay = row.getCell(1).getStringCellValue()	;
				}
				if(internationalDay.contains("CC")) {
					round.setHasCC(true);
				}
				if(internationalDay.contains("CL")) {
					round.setHasCL(true);
				}
			}  
		}catch(Exception e)  
		{  
			e.printStackTrace();  
		}finally {
			try {
				wb.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}

	private static Calendar getMinDate(String team1, String team2, Round previousRound, Round timeRound,boolean matchHasCCTeam, boolean matchHasCLTeam ) {
		Calendar minDate = null;
		//If previous round has CL round and this match has CL Team then let's set saturday as the last played game by both teams
		if((previousRound!=null && previousRound.isHasCL() && matchHasCLTeam )) {
			minDate = (Calendar)previousRound.getDate().clone();
			minDate.add(Calendar.DATE, -1);
		}
		//If previous round has CC round and this match has CC Team then let's set sunday as the last played game by both teams
		if((previousRound!=null && previousRound.isHasCC() && matchHasCCTeam && previousRound.getRoundNumber()!=8)) {
			minDate = (Calendar)previousRound.getDate().clone();
		}
		//If current round is CC or CL let's set today as the last played game. This will prevent this match to be played in this round
		if((timeRound.isHasCC() && matchHasCCTeam )	|| (timeRound.isHasCL() && matchHasCLTeam)) {
			minDate = (Calendar)timeRound.getDate().clone();
		}
		Calendar date1 = (Calendar)timeRound.getDate().clone();
		date1.add(Calendar.DATE, -6);
		date1.set(Calendar.HOUR_OF_DAY, 0);
		Calendar date2 = (Calendar)date1.clone();
		if(lastUsedDateForTeam.get(team1)!=null) {
			date1 = lastUsedDateForTeam.get(team1);
		}
		if(lastUsedDateForTeam.get(team2)!=null) {
			date2 = lastUsedDateForTeam.get(team2);
		}
		//The teampLastUsed... is to keep track for teams that has more that one match in a Round
		//Can only happen in MAJ rounds were a team has more that one rescheduled match to play.
		if(tempLastUsedDateForTeam.get(team1)!=null) {
			date1 = tempLastUsedDateForTeam.get(team1);
		}
		if(tempLastUsedDateForTeam.get(team2)!=null) {
			date2 = tempLastUsedDateForTeam.get(team2);
		}
		//date1 is the last time home team played a game, date2 the last time away team played a game
		//We compare the and compare too with the already calculated minDate (from CC or CL restrictions)
		//And we will have the last time a game was played were one of this team palyed
		if(date1.after(date2) && (minDate==null ||date1.after(minDate))) {
			minDate = (Calendar)date1.clone();
		}else if(minDate==null ||date2.after(minDate)) {
			minDate = (Calendar)date2.clone();
		}

		//At the end we add a 2 days rest between the last game played and the minimum date this game can be played
		minDate.add(Calendar.DATE, 3);
		minDate.set(Calendar.HOUR_OF_DAY, 0);
		return minDate;

	}
	
	private static Calendar getMaxDate(String team1, String team2, Round timeRound, Calendar nextRoundDay, boolean nextHasCC,boolean matchHasCCTeam, boolean matchHasCLTeam, boolean nextHasCL) {
		//We set a basic value 10 days from this match to avoid any problem form matches without max date.
		Calendar maxDate = (Calendar)timeRound.getDate().clone();
		maxDate.add(Calendar.DATE, 10);
		
		//If Next round is CC Round and this match has a CC Team then we take the next round date minus 2 days(for rest) and set it as the maximum date for this game.
		//As a special case, the CC Round from 19/02/2021 is played on friday not on sunday as others, that's the try/catch/if inside this option
		if(nextHasCC && matchHasCCTeam) {
			//Sunday (some wednesday)
			maxDate=(Calendar)nextRoundDay.clone();
			maxDate.set(Calendar.HOUR_OF_DAY, 0);
			Calendar cal = Calendar.getInstance();
			SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
			try {
				cal.setTime(sdf.parse(specialCCDate));
			}catch(Exception e) {}
			if(cal.equals(maxDate)) {
				try {
					cal.setTime(sdf.parse(specialCCDateChange));
				}catch(Exception e) {}
				maxDate = (Calendar)cal.clone();
			}
			maxDate.add(Calendar.DATE, -2);
		}
		//If Next round is Cl Round and this match has a Cl Team then we take the next round date minus 2 days to move to friday and minus 2 days(for rest) and set it as the maximum date for this game.
		if(nextHasCL && matchHasCLTeam) {
			//Friday or Saturday (some wednesday)
			maxDate=(Calendar)nextRoundDay.clone();
			maxDate.add(Calendar.DATE, -4);//maxDate is sunday, -2 to go to friday and -2 to allow 2 day of rest
			maxDate.set(Calendar.HOUR_OF_DAY, 0);
		}
		return maxDate;
	}
	
	//Checks if next not MAJ match played by any of the teams is a match were a CL team is playing
	//this is to solve cases where we have SUN, WED, SUN(CL Round). 
	//if this match is played in sunday, the next match will be played on WED or THR but this cannot happen if the next round is CL as the CL team will play on FRI.
	//So we kget this information to move the team1 vs team 2 to SAT so the midweek match can be played on TUE without problem with the FRI CL game.
	private static boolean getNextMatchHasCLTeam(String team1, String team2, Round nextTeamRound) {
		boolean nextMatchHasCLTeam = false;
		if(nextTeamRound!=null) {
			Match nextMatch = nextTeamRound.getMatchIgnoreDate(team1);
			nextMatchHasCLTeam = Arrays.stream(cLTeams).anyMatch(nextMatch.getTeam1()::equals) || Arrays.stream(cLTeams).anyMatch(nextMatch.getTeam2()::equals);
			if(!nextMatchHasCLTeam) {
				nextMatch = nextTeamRound.getMatchIgnoreDate(team2);
				nextMatchHasCLTeam = Arrays.stream(cLTeams).anyMatch(nextMatch.getTeam1()::equals) || Arrays.stream(cLTeams).anyMatch(nextMatch.getTeam2()::equals);
			}
		}
		return nextMatchHasCLTeam;
		
	}
	
	private static void calculateRestCost() {
		for(Schedule schedule : toOutput) {
			String scheduleName = String.format("%05d", Integer.parseInt(schedule.getFileName()));
			int cost = 0;
			Map<String,Result> resultMap = new HashMap<>();
	        for (String team : allTeams) { 
	        	resultMap.put(team, schedule.getMatchesForTeam(team,ramadanStartDate, ramadanEndDate, Arrays.stream(cCTeams).anyMatch(team::equals), Arrays.stream(cLTeams).anyMatch(team::equals),specialCCDate,specialCCDateChange));
	        }
	        int postponedGames = 0;
			for (String team : allTeams) { 
				Result result = resultMap.get(team);
				int cumulateRest =0;
				for (ResultLine match : result.getResults()) {
					postponedGames = postponedGames + (match.getRound()==0?1:0);
			        if(match.getRound()==-1 || match.getRecRound()==-1 || match.getRound()==-2 || match.getRecRound()==-2) {
			        	cumulateRest = cumulateRest + 0;
			        }else {				        
				        Result rivalResult = resultMap.get(match.getTeam1().equals(team)?match.getTeam2():match.getTeam1());
				        ResultLine rivalResultLine = rivalResult.getResult(match.getMatchLiteral());
				        cumulateRest = cumulateRest + (match.getFiveMatchRestTime()-rivalResultLine.getFiveMatchRestTime());
			        }
				}
				if(Arrays.stream(cCTeams).anyMatch(team::equals) || Arrays.stream(cLTeams).anyMatch(team::equals)) {
					if(cumulateRest<expectedMeanRestForCCCLTeams) {
						cost = cost + (int)Math.pow(1.2, expectedMeanRestForCCCLTeams-cumulateRest);
					}
				}else {
					if(cumulateRest<expectedMeanRestForNonCCCLTeams) {
						cost = cost + (int)Math.pow(1.2, expectedMeanRestForNonCCCLTeams-cumulateRest);
					}
				}
			}
			//int previousCost = scheduleCostList.get(scheduleName);
			//scheduleCostList.put(scheduleName, previousCost+cost);
			schedule.setCost((postponedGames*100)+cost);
			schedule.setPostponedGames(postponedGames/2);
			
		}
		
	}
	
    private static void pseudoShuffleTeamRound(Round teamRound, Round prevRound) {
        List<Match> matchesWithInternational = new ArrayList<Match>();
        List<Match> matchesWithTeamInPrevMAJ = new ArrayList<Match>();
        List<Match> restOfMatches = new ArrayList<Match>();
        
        for(Match m : teamRound.getMatches()) {
               boolean matchHasCCTeam = Arrays.stream(cCTeams).anyMatch(m.getTeam1()::equals) || Arrays.stream(cCTeams).anyMatch(m.getTeam2()::equals);
               boolean matchHasCLTeam = Arrays.stream(cLTeams).anyMatch(m.getTeam1()::equals) || Arrays.stream(cLTeams).anyMatch(m.getTeam2()::equals);
               boolean matchHasPreviousMAJ = false;
               for(Match pm : prevRound.getMatches()) {
                     if(pm.getTeam1().equals(m.getTeam1()) || pm.getTeam2().equals(m.getTeam1()) || pm.getTeam1().equals(m.getTeam2()) || pm.getTeam2().equals(m.getTeam2())) {
                            matchHasPreviousMAJ = true;
                     }
               }
               if(matchHasCCTeam || matchHasCLTeam) {
                     matchesWithInternational.add(m);
               } else if (matchHasPreviousMAJ) {
                     matchesWithTeamInPrevMAJ.add(m);
               } else {
                     restOfMatches.add(m);
               }
        }
        Collections.shuffle(matchesWithInternational);
        Collections.shuffle(matchesWithTeamInPrevMAJ);
        Collections.shuffle(restOfMatches);
        teamRound.getMatches().clear();
        teamRound.getMatches().addAll(matchesWithInternational);
        teamRound.getMatches().addAll(matchesWithTeamInPrevMAJ);
        teamRound.getMatches().addAll(restOfMatches);
  }	
	
}
