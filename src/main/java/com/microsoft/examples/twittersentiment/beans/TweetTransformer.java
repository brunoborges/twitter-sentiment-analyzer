package com.microsoft.examples.twittersentiment.beans;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.camel.Exchange;
import org.apache.camel.component.azure.textanalytics.TextAnalyticsConstants;
import org.springframework.stereotype.Component;

import com.azure.ai.textanalytics.models.AnalyzeSentimentResult;
import com.azure.core.util.IterableStream;
import com.microsoft.examples.twittersentiment.model.AnalyzedTweet;
import com.microsoft.examples.twittersentiment.model.Sentiment;

import twitter4j.Status;

@Component("tweetTransformer")
public class TweetTransformer {

    @SuppressWarnings("unchecked")
    public void analyze(Exchange e) {
        var analyzedCollection = (IterableStream<AnalyzeSentimentResult>) e.getMessage()
                .getHeader(TextAnalyticsConstants.RESULT);

        var statuses = (List<Status>) e.getMessage().getBody();
        var analyzedTweets = new ArrayList<AnalyzedTweet>(statuses.size());
        e.getMessage().setBody(analyzedTweets);

        analyzedCollection.stream().map((result) -> {
            var id = Integer.valueOf(result.getId());
            var status = statuses.get(id);

            var sentiment = result.getDocumentSentiment().getSentiment().toString();
            var sentimentScore = result.getDocumentSentiment().getConfidenceScores();

            double score = switch (sentiment.toUpperCase()) {
                case "POSITIVE" -> sentimentScore.getPositive();
                case "NEGATIVE" -> sentimentScore.getNegative();
                case "NEUTRAL" -> sentimentScore.getNeutral();
                default -> 0.0;
            };

            return new AnalyzedTweet(status, new Sentiment(sentiment, score));
        }).collect(Collectors.toCollection(() -> analyzedTweets));
    }

}
