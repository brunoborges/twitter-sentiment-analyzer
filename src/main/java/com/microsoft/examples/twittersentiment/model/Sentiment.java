package com.microsoft.examples.twittersentiment.model;

public record Sentiment(
        String sentiment,
        double sentimentScore) {
}
