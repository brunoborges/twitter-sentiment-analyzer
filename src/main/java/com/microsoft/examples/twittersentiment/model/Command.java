package com.microsoft.examples.twittersentiment.model;

public sealed interface Command permits SearchCommand {
    
    public String command();

}
