package com.microsoft.examples.twittersentiment.model;

import twitter4j.Status;

public record AnalyzedTweet(
                Status status,
                Sentiment sentiment) {
}
