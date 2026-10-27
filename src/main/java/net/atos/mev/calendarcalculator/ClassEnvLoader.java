package net.atos.mev.calendarcalculator;

import java.util.Properties;

public class ClassEnvLoader {

	public static Properties loadFromMasterFile(String masterFile) {
		return net.atos.mev.calendarcalculator.compat.DatabaseBatchSupport.properties(masterFile);
	}

	public static Properties loadFromConfigFile(String configFile) {
		if (configFile == null || configFile.isBlank()) {
			throw new IllegalArgumentException("configFile cannot be null/blank");
		}
		return net.atos.mev.calendarcalculator.compat.DatabaseBatchSupport.properties(configFile);
	}

	public static void main(String[] args) {

	}

}
