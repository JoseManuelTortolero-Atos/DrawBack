package net.atos.mev.calendarcalculator.schedules;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

import net.atos.mev.calendarcalculator.Environment;
import net.atos.mev.calendarcalculator.Tournament;

public class SchEnvironment {
	
	LocalDate start;
	LocalDate end;
	Environment env;
	Tournament tour;
	String resultsFolder;
	String datesFile;
	Properties prop;
	private PlanningParameters planningParameters;
	private SchCalendar databaseCalendar;

	public PlanningParameters getPlanningParameters() {
		return planningParameters;
	}

	public void setPlanningParameters(PlanningParameters planningParameters) {
		this.planningParameters = planningParameters;
	}

	public SchCalendar getDatabaseCalendar() {
		return databaseCalendar;
	}

	public void setDatabaseCalendar(SchCalendar databaseCalendar) {
		this.databaseCalendar = databaseCalendar;
	}
	
	public static String masterFileProp = "SchEnvironment.properties";

	public static SchEnvironment createFromMasterFile() {
		return createFromDatabase(masterFileProp);
	}

	public static SchEnvironment createFromDatabase(String selector) {
		SchEnvironment result = new SchEnvironment();
		result.loadFromDatabase(selector, null, null);
		return result;
	}

	public static SchEnvironment createFromProperties(Properties properties) {
		SchEnvironment result = new SchEnvironment();
		result.loadFromProperties(properties);
		return result;
	}

	/**
	 * v0.4: entorno en memoria (sin ficheros). {@code prop} queda vacío: los colores de
	 * competición del Excel legacy se aplican solo si se pasan en {@code colours}.
	 */
	public static SchEnvironment inMemory(LocalDate start, LocalDate end, Environment env, Tournament tour, Properties colours) {
		SchEnvironment result = new SchEnvironment();
		result.start = start;
		result.end = end;
		result.env = env;
		result.tour = tour;
		result.prop = colours == null ? new Properties() : colours;
		result.resultsFolder = null;
		result.datesFile = null;
		return result;
	}

	public LocalDate getStart() {
		return start;
	}

	public LocalDate getEnd() {
		return end;
	}

	public Environment getEnv() {
		return env;
	}

	public Tournament getTour() {
		return tour;
	}

	public void loadFromMasterFile() {
		loadFromDatabase(masterFileProp, null, null);
	}

	public void loadFromProperties(Properties properties) {
		prop = properties;
		String last = properties.getProperty("ScheduleMoroccoAlg.lastRoundToAssign");
		loadFromDatabase(requireProperty("env"), last == null ? null : Integer.valueOf(last), properties);
	}

	private void loadFromDatabase(String selector, Integer last, Properties runtime) {
		SchEnvironment source = net.atos.mev.calendarcalculator.compat.DatabaseBatchSupport.withConfiguration(configuration -> {
			var setup = configuration.setup(selector, null);
			var planning = configuration.planning(setup, last);
			SchEnvironment loaded = planning.built().environment();
			loaded.prop = configuration.properties(setup, null);
			loaded.resultsFolder = loaded.prop.getProperty("resultsFolder");
			return loaded;
		});
		start = source.start;
		end = source.end;
		env = source.env;
		tour = source.tour;
		prop = source.prop;
		planningParameters = source.planningParameters;
		databaseCalendar = source.databaseCalendar;
		resultsFolder = runtime == null ? source.resultsFolder : runtime.getProperty("resultsFolder", source.resultsFolder);
		datesFile = runtime == null ? null : runtime.getProperty("datesFile");
	}

	private String requireProperty(String key) {
		String value = prop.getProperty(key);
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("Missing required property: " + key);
		}
		return value;
	}
	
	public static LocalDate readDate(String textDate) {
		return LocalDate.parse(textDate, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
	}
	
	public static String writeDate(LocalDate objDate) {
		return objDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
	}
	

	public static void main(String[] args) {
		SchEnvironment schEnv = SchEnvironment.createFromDatabase(net.atos.mev.calendarcalculator.compat.DatabaseBatchSupport.selector(args));
		System.out.println("start=" + schEnv.start.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
		System.out.println("end=" + schEnv.end.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
		System.out.println("env.name=" + schEnv.env.name);

	}

}
