package net.atos.mev.calendarcalculator.fileio;


public class AssignmentsExcelMorocco15Teams extends AssignmentsExcelMorocco {
	static String propertiesFileD1 = "AssignmentsExcelMoroccoD1.properties";
	static String propertiesFileD2 = "AssignmentsExcelMoroccoD2.properties";

	int[][] roundStarts15Teams= {
			{6,0},
			{6,2},
			{6,4},
			{6,6},
			{6,8},
			{16,0},
			{16,2},
			{16,4},
			{16,6},
			{16,8},
			{26,0},
			{26,2},
			{26,4},
			{26,6},
			{26,8},
			{6,11},
			{6,13},
			{6,15},
			{6,17},
			{6,19},
			{16,11},
			{16,13},
			{16,15},
			{16,17},
			{16,19},
			{26,11},
			{26,13},
			{26,15},
			{26,17},
			{26,19},
	};


	public AssignmentsExcelMorocco15Teams(String environment, String assignFolder, String templatePath, int nTournaments) {
		super(environment, assignFolder, templatePath, nTournaments);
		roundStarts = roundStarts15Teams;
	}

	public static void loadFromConfig(String configFile) {
		net.atos.mev.calendarcalculator.compat.DatabaseBatchSupport.exportDraw(configFile, null);
	}

	public static void main(String[] args) {
		if (args.length > 0) {
			net.atos.mev.calendarcalculator.compat.DatabaseBatchSupport.exportDraw(args[0], args.length > 1 ? args[1] : null);
			return;
		}
		//loadFromConfig(propertiesFileD1);
		loadFromConfig(propertiesFileD2);
	}
	

}
