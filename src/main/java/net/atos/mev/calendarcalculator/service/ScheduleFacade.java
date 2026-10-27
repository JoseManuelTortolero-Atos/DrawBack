package net.atos.mev.calendarcalculator.service;

import java.io.InputStream;
import java.util.Properties;

import org.springframework.stereotype.Component;

import net.atos.mev.calendarcalculator.engine.CalendarEngine;

@Component
public class ScheduleFacade {

    private final CalendarEngine calendarEngine;

    public ScheduleFacade(CalendarEngine calendarEngine) {
        this.calendarEngine = calendarEngine;
    }

    public byte[] run(Properties properties, InputStream inputStream) {
        return calendarEngine.run(properties, inputStream);
    }
}
