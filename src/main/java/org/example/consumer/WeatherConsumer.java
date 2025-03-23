package org.example.consumer;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOptions;
import com.weather.avro.AggregatedWeather;
import com.weather.avro.ProcessedData;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import org.apache.kafka.clients.consumer.*;
import org.bson.Document;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

public class WeatherConsumer {
    public static void main(String[] args) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "<HOST_IP>:9092,<HOST_IP>:9093,<HOST_IP>:9094");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "weather-consumer-group");
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", KafkaAvroDeserializer.class.getName());
        props.put("schema.registry.url", "http://<HOST_IP>:8081");
        props.put("specific.avro.reader", "true");
        props.put("security.protocol", "PLAINTEXT");


        // MongoDB configuration
        MongoClient mongoClient = MongoClients.create("mongodb://<HOST_IP>:27017");
        MongoCollection<Document> collection = mongoClient.getDatabase("weather").getCollection("aggregated-weather");

        // Create Kafka consumer
        Consumer<String, AggregatedWeather> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList("aggregated-weather-topic")); // Subscribe to the new topic

        // Poll for new records
        while (true) {
            ConsumerRecords<String, AggregatedWeather> records = consumer.poll(Duration.ofMillis(1000));
            for (ConsumerRecord<String, AggregatedWeather> record : records) {
                AggregatedWeather aggregatedData = record.value();

                // Create a MongoDB document
                Document doc = new Document("location", aggregatedData.getLocation().toString())
                        .append("totalTemperature", aggregatedData.getTotalTemperature())
                        .append("count", aggregatedData.getCount())
                        .append("averageTemperature", aggregatedData.getAverageTemperature());

                // Update the document for the location (upsert = create if not exists)
                collection.updateOne(
                        new Document("location", aggregatedData.getLocation().toString()), // Filter by location
                        new Document("$set", doc), // Update with the new values
                        new UpdateOptions().upsert(true) // Create if it doesn't exist
                );

                System.out.println("Updated aggregated data: " + doc.toJson());
            }
        }
    }
}
