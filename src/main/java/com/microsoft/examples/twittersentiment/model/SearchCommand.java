package com.microsoft.examples.twittersentiment.model;

public record SearchCommand(String command, String searchTerms) implements Command {
    
}
