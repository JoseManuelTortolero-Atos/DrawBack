package net.atos.mev.calendarcalculator.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Deprecated binding retained for external source compatibility only. Services use JDBC;
 * these values are never an authoritative competition catalog or configuration.
 */
@Deprecated
@ConfigurationProperties(prefix = "calendar")
public class CalendarCompetitionsProperties {

    private Map<String, CompetitionConfig> competitions = new LinkedHashMap<>();

    public Map<String, CompetitionConfig> getCompetitions() {
        return competitions;
    }

    public void setCompetitions(Map<String, CompetitionConfig> competitions) {
        this.competitions = competitions;
    }

    public static class CompetitionConfig {
        private String name;
        private String file;
        private String storageDir;
        private boolean enabled = true;
        private String env;
        private String start;
        private String end;
        private Map<String, String> overrides = new LinkedHashMap<>();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getFile() {
            return file;
        }

        public void setFile(String file) {
            this.file = file;
        }

        public String getStorageDir() {
            return storageDir;
        }

        public void setStorageDir(String storageDir) {
            this.storageDir = storageDir;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getEnv() {
            return env;
        }

        public void setEnv(String env) {
            this.env = env;
        }

        public String getStart() {
            return start;
        }

        public void setStart(String start) {
            this.start = start;
        }

        public String getEnd() {
            return end;
        }

        public void setEnd(String end) {
            this.end = end;
        }

        public Map<String, String> getOverrides() {
            return overrides;
        }

        public void setOverrides(Map<String, String> overrides) {
            this.overrides = overrides;
        }
    }
}
