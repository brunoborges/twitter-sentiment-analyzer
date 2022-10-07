package com.microsoft.examples.twittersentiment;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.redis.RedisConstants;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.processor.aggregate.GroupedExchangeAggregationStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Component;

@Component("twitterSentimentRoute")
public class TwitterSentimentRoute extends RouteBuilder {

    public static String LANG = "en";

    @Value("${websocket.port}")
    private int websocketPort;

    @Bean("redisSerializer")
    public RedisSerializer<String> redisSerializer() {
        return new StringRedisSerializer();
    }

    @Override
    public void configure() throws Exception {
        // Consume the Twitter Stream
        from("twitter-search://microsoft")
                .filter(simple("${body.isRetweet} == false"))
                .filter(simple("${body.lang} == '%s'".formatted(LANG)))
                .to("bean:tweetNormalizer?method=statusToTweet")
                .aggregate(new GroupedExchangeAggregationStrategy()).constant(true)
                .completionTimeout(5000) // group tweets every 5 seconds (reduce load on Azure Cog Service API)
                .to("bean:twitterSentimentAnalyzer?method=analyze") // send to Azure Cognitive Services for sentiment
                                                                    // analysis
                .split(body())
                .recipientList(constant("seda:storeOnRedis,seda:pubToWebSockets"));

        // Store the tweets on Redis
        from("seda:storeOnRedis")
                .setHeader(RedisConstants.COMMAND, constant("SET"))
                .setHeader(RedisConstants.KEY, simple("${body.tweetId}").convertToString())
                .marshal().json(JsonLibrary.Jackson, true)
                .convertBodyTo(String.class)
                .setHeader(RedisConstants.VALUE, simple("${body}"))
                .log("Stored on Redis: ${body}")
                .to("spring-redis:?serializer=#redisSerializer");

        // Publish the tweets to WebSockets
        from("seda:pubToWebSockets")
                .log("Published to WebSockets!")
                .marshal().json(JsonLibrary.Jackson, true)
                .to("websocket://localhost:%s/tweets?sendToAll=true".formatted(websocketPort));

    }

}
