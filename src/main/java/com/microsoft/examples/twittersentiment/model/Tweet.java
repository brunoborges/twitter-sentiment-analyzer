package com.microsoft.examples.twittersentiment.model;

public record Tweet(
                String text,
                String username,
                String profileImageURL,
                long tweetId,
                String lang,
                Sentiment sentiment) {

}
