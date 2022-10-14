package com.microsoft.examples.twittersentiment.routes;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.redis.RedisConstants;
import org.apache.camel.component.twitter.TwitterConstants;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.processor.aggregate.GroupedExchangeAggregationStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.microsoft.examples.twittersentiment.model.SearchCommand;

// @Component("twitterSentimentRoute")
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
                getContext().getGlobalOptions().put("CamelJacksonTypeConverterToPojo", "true");
                getContext().getGlobalOptions().put("CamelJacksonEnableTypeConverter", "true");

                from("twitter-search")
                        .autoStartup(false)
                        .filter(simple("${body.isRetweet} == false"))
                        .filter(simple("${body.lang} == '%s'".formatted(LANG)))
                        .to("bean:tweetNormalizer?method=statusToTweet")
                        .aggregate(new GroupedExchangeAggregationStrategy()).constant(true)
                        .completionTimeout(5000) // group tweets every 5 seconds (reduce load on ACS)
                        .to("bean:twitterSentimentAnalyzer?method=analyze") // send to ACS for analysis
                        .split(body())
                        .recipientList(constant("seda:storeOnRedis,seda:pubToWebSockets"))
                        .routeId("mainRoute");

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
                        .to("websocket://0.0.0.0:%s/tweets?sendToAll=true".formatted(websocketPort));

                // It won't start consuming the Twitter Search until the route is started
                from("websocket://0.0.0.0:%s/tweets".formatted(websocketPort))
                        .log("Received command (JSON): ${body}")
                        .choice()
                                .when()
                                        .jsonpath("$.[?(@.command == 'search')]")
                                        .unmarshal().json(JsonLibrary.Jackson, SearchCommand.class)
                                        .setHeader(TwitterConstants.TWITTER_KEYWORDS, simple("${body.searchTerms}"))
                                        .to("bean:twitterSentimentRoute?method=startProcessing")
                                        .to("direct:twitter")
                                .when()
                                        .jsonpath("$.[?(@.command == 'stop')]")
                                        .to("bean:twitterSentimentRoute?method=stopProcessing")
                        .otherwise()
                                .log("Received unknown command from WebSocket: ${body}");
        }

        public void stopProcessing() throws Exception {
                getCamelContext().getRouteController().stopRoute("mainRoute");
        }

        public void startProcessing() throws Exception {
                getCamelContext().getRouteController().startRoute("mainRoute");
        }

}
