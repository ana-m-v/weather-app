package org.example.consumer;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
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
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "192.168.0.206:9092,192.168.0.206:9093,192.168.0.206:9094");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "weather-consumer-group");
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", KafkaAvroDeserializer.class.getName());
        props.put("schema.registry.url", "http://192.168.0.206:8081");
        props.put("specific.avro.reader", "true");
        props.put("security.protocol", "PLAINTEXT");


        MongoClient mongoClient = MongoClients.create("mongodb://192.168.0.206:27017");
        MongoCollection<Document> collection = mongoClient.getDatabase("weather").getCollection("processed-data");

        Consumer<String, ProcessedData> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList("processed-data-topic"));

        while (true) {
            ConsumerRecords<String, ProcessedData> records = consumer.poll(Duration.ofMillis(1000));
            for (ConsumerRecord<String, ProcessedData> record : records) {
                ProcessedData data = record.value();
                Document doc = new Document("location", data.getLocation().toString())
                        .append("suggestion", data.getSuggestion().toString());
                collection.insertOne(doc);
                System.out.println("Inserted: " + doc.toJson());
            }
        }
    }
}
