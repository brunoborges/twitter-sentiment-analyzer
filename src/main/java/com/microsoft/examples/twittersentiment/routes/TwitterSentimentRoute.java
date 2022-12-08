package com.microsoft.examples.twittersentiment.routes;

import java.util.function.Function;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.redis.RedisConstants;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.processor.aggregate.GroupedBodyAggregationStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Component;

import com.microsoft.examples.twittersentiment.model.SearchCommand;

import twitter4j.Status;

@Component("twitterSentimentRoute")
public class TwitterSentimentRoute extends RouteBuilder {

    @Value("${defaults.language}")
    private String language;

    @Value("${defaults.keyword}")
    private String keyword;

    @Value("${websocket.port}")
    private int websocketPort;

    @Value("${azure.cognitive.service.endpoint}")
    private String serviceEndpoint;

    @Value("${azure.cognitive.service.key}")
    private String serviceKey;

    @Bean("redisSerializer")
    public RedisSerializer<String> redisSerializer() {
        return new StringRedisSerializer();
    }

    @Bean("textExtractor")
    public Function<Object, String> textExtractor() {
        return (Object o) -> ((Status) o).getText();
    }

    @Override
    public void configure() throws Exception {
        getContext().getGlobalOptions().put("CamelJacksonTypeConverterToPojo", "true");
        getContext().getGlobalOptions().put("CamelJacksonEnableTypeConverter", "true");

        from("seda:tweetHose")
                .filter(simple("${body.isRetweet} == false")) // filter out retweets
                .filter(simple("${body.lang} == '%s'".formatted(language))) // only a specific language
                .aggregate(new GroupedBodyAggregationStrategy())
                .constant(true)
                .completionSize(10) // no more than 10 tweets per batch (ACS Limit!)
                .completionTimeout(10000) // group tweets every 5 seconds (reduce load on ACS)
            .to("azure-textanalytics:analyzeSentiment?serviceEndpoint=%s&serviceKey=%s&documentExtractor=#textExtractor&resultDestination=header"
                        .formatted(serviceEndpoint, serviceKey))
            .to("bean:tweetTransformer")
                .split(body())
            .to("seda:sendToRedis");

        // Store on Redis, and Publish to Subscribers through Redis
        from("seda:sendToRedis")
                .setHeader(RedisConstants.COMMAND, constant("SET"))
                .setHeader(RedisConstants.KEY, simple("${body.status.id}").convertToString())
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
        from("websocket://0.0.0.0:%s/tweets".formatted(websocketPort))
            .log("Received command (JSON): ${body}")
            .choice()
                .when()
                    .jsonpath("$.[?(@.command == 'search')]")
                    .unmarshal().json(JsonLibrary.Jackson, SearchCommand.class)
                    .to("bean:twitterSentimentRoute?method=startProcessing")
                .when()
                    .jsonpath("$.[?(@.command == 'suspend')]")
                    .to("bean:twitterSentimentRoute?method=suspendProcessing")
                .when()
                    .jsonpath("$.[?(@.command == 'resume')]")
                    .to("bean:twitterSentimentRoute?method=resumeProcessing")
                .otherwise()
                    .log(LoggingLevel.WARN, "Received unknown command from WebSocket: ${body}");
    }

    public void suspendProcessing() throws Exception {
        getCamelContext().getRouteController().suspendRoute("tweetRoute");
    }

    public void resumeProcessing() throws Exception {
        getCamelContext().getRouteController().resumeRoute("tweetRoute");
    }

    public void startProcessing(SearchCommand command) throws Exception {
        final var keyword = command.searchTerms();
        getCamelContext().addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("twitter-search:%s?type=polling".formatted(keyword))
                    .to("seda:tweetHose")
                    .routeId("tweetRoute");
            }
        });
    }

}
