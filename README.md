# Twitter Sentiment Analyzer

This is an Apache Camel demo based on the Twitter Demo from the Dapr project:

https://github.com/dapr/samples/tree/master/twitter-sentiment-processor/demos/javademo

## This demo

To run this demo, create a file `application-production.properties`, fill in the required keys from Twitter API, Azure Cognitive Services API.

You also need a running Redis database locally.

Finally, run with this command:

```bash
$ ./mvnw package && java -jar -Dspring.profiles.active=production target/twitter-sentiment-0.0.1-SNAPSHOT.jar
```

Then, go to http://localhost:8080.

You should see a Twitter timeline with tweets and sentiments (little icon on the top left corner of a tweet).

Enjoy :-)
