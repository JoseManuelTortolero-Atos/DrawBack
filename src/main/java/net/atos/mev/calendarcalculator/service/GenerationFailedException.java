package net.atos.mev.calendarcalculator.service;

public class GenerationFailedException extends IllegalStateException {

    private final String generationId;

    public GenerationFailedException(String generationId, String message, Throwable cause) {
        super(message, cause);
        this.generationId = generationId;
    }

    public String getGenerationId() {
        return generationId;
    }
}
