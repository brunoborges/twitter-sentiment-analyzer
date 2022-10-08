package com.microsoft.examples.twittersentiment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.camel.Exchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.azure.ai.textanalytics.TextAnalyticsClient;
import com.azure.ai.textanalytics.TextAnalyticsClientBuilder;
import com.azure.core.credential.AzureKeyCredential;

@Component("twitterSentimentAnalyzer")
public class TweetSentimentAnalyzer {

    private TextAnalyticsClient textAnalyticsClient;

    public TweetSentimentAnalyzer(
            @Value("${azure.cognitive.service.endpoint}") String cogServiceEndpoint,
            @Value("${azure.cognitive.service.key}") String cogServiceKey) {
        textAnalyticsClient = new TextAnalyticsClientBuilder()
                .credential(new AzureKeyCredential(cogServiceKey))
                .endpoint(cogServiceEndpoint)
                .buildClient();
    }

    public void analyze(Exchange e) {
        var body = e.getMessage().getBody();

        final List<Tweet> tweets;
        var aggregationSize = e.getProperty("CamelAggregatedSize");
        if (aggregationSize instanceof Integer ias) {
            tweets = ((List<Exchange>) body).stream().map(ex -> ex.getIn().getBody(Tweet.class))
                    .collect(Collectors.toList());
        } else if (body instanceof Exchange ex) {
            tweets = Collections.singletonList(ex.getIn().getBody(Tweet.class));
        } else if (body instanceof Tweet t) {
            tweets = Collections.singletonList(t);
        } else {
            tweets = Collections.emptyList();
        }

        var analyzed = new ArrayList<Tweet>(tweets.size());
        var documents = tweets.stream().map(t -> t.text()).collect(Collectors.toList());
        var analyzedCollection = textAnalyticsClient.analyzeSentimentBatch(documents, TwitterSentimentRoute.LANG, null);

        analyzedCollection.stream().forEach(r -> {
            var tweet = tweets.get(Integer.valueOf(r.getId()));
            var sentiment = r.getDocumentSentiment().getSentiment();
            var sentimentScore = r.getDocumentSentiment().getConfidenceScores();

            double score = switch (sentiment.toString().toUpperCase()) {
                case "POSITIVE" -> sentimentScore.getPositive();
                case "NEGATIVE" -> sentimentScore.getNegative();
                case "NEUTRAL" -> sentimentScore.getNeutral();
                default -> 0.0;
            };

            analyzed.add(withSentiment(tweet, sentiment.toString(), score));
        });

        e.getIn().setBody(analyzed);
    }

    private Tweet withSentiment(Tweet tweet, String sentiment, double sentimentScore) {
        return new Tweet(tweet.text(), tweet.username(), tweet.profileImageURL(), tweet.tweetId(), tweet.lang(),
                new Sentiment(sentiment, sentimentScore));
    }

}
