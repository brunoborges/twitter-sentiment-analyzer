package com.microsoft.examples.twittersentiment.routes;

import org.apache.camel.builder.AggregationStrategies;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.redis.RedisConstants;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.processor.aggregate.GroupedBodyAggregationStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Component;

@Component("twitterSentimentRoute")
public class TwitterSentimentRoute extends RouteBuilder {

        public static String LANG = "en"; // default language

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

                // Main route to consume from Twitter
                from("twitter-timeline:HOME?type=polling&delay=60000&initialDelay=5000")
                        .filter(simple("${body.isRetweet} == false")) // filter out retweets
                        .filter(simple("${body.lang} == '%s'".formatted(LANG))) // only english
                        .to("bean:tweetTransformer") // Convert from Tweet4J.Status to Tweet
                        .aggregate(new GroupedBodyAggregationStrategy())
                                .constant(true)
                                .completionSize(10) // no more than 10 tweets per batch
                                .completionTimeout(5000) // group tweets every 5 seconds (reduce load on ACS)
                        .to("bean:tweetSentimentAnalyzer") // send to ACS for analysis
                        .split(body())
                        .to("seda:sendToRedis")
                        .routeId("mainRoute");

                // Store on Redis, and Publish to Subscribers through Redis
                from("seda:sendToRedis")
                        .setHeader(RedisConstants.COMMAND, constant("SET"))
                        .setHeader(RedisConstants.KEY, simple("${body.tweetId}").convertToString())
                        .marshal().json(JsonLibrary.Jackson, true)
                        .convertBodyTo(String.class)
                        .setHeader(RedisConstants.VALUE, simple("${body}"))
                        .to("spring-redis:?serializer=#redisSerializer")
                        .setHeader(RedisConstants.COMMAND, constant("PUBLISH"))
                        .setHeader(RedisConstants.CHANNEL, constant("tweets"))
                        .setHeader(RedisConstants.MESSAGE, simple("${body}"))
                        .to("spring-redis:?serializer=#redisSerializer");

                // Default route to serve the Web UI
                // This route didn't have to exist, if all we wanted was to publish to WebSocket
                from("spring-redis:?command=SUBSCRIBE&channels=tweets&serializer=#redisSerializer")
                        .to("websocket://0.0.0.0:%s/tweets?sendToAll=true".formatted(websocketPort));

                // It won't start consuming the Twitter Search until the route is started
                /*
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
                */
        }

        public void stopProcessing() throws Exception {
                getCamelContext().getRouteController().stopRoute("mainRoute");
        }

        public void startProcessing() throws Exception {
                getCamelContext().getRouteController().startRoute("mainRoute");
        }

}
