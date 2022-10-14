package com.microsoft.examples.twittersentiment.beans;

import java.beans.JavaBean;

import org.apache.camel.Exchange;
import org.springframework.stereotype.Component;

import com.microsoft.examples.twittersentiment.model.Tweet;

@Component("tweetNormalizer")
public class TweetNormalizer {

    public void statusToTweet(Exchange exchange) {
        var status = exchange.getIn().getBody(twitter4j.Status.class);

        var tweet = new Tweet(status.getText(), status.getUser().getName(), status.getUser().getProfileImageURL(),
                status.getId(), status.getLang(), null);

        exchange.getIn().setBody(tweet);
    }

}
