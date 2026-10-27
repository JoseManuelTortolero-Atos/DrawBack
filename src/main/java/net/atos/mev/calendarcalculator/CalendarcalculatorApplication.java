package net.atos.mev.calendarcalculator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CalendarcalculatorApplication {

	public static void main(String[] args) {
		SpringApplication.run(CalendarcalculatorApplication.class, args);
	}

}
