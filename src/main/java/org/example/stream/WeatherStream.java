package org.example.stream;

import com.weather.avro.ProcessedData;
import com.weather.avro.Weather;
import org.apache.kafka.common.serialization.Serdes;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.*;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

public class WeatherStream {
    public static void main(String[] args) {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "weather-stream-app");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "192.168.0.206:9092,192.168.0.206:9093,192.168.0.206:9094");
        props.put("schema.registry.url", "http://192.168.0.206:8081");
        props.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, StreamsConfig.EXACTLY_ONCE_V2);
        props.put("security.protocol", "PLAINTEXT");

        StreamsBuilder builder = new StreamsBuilder();

        // Serde for Weather and ProcessedData
        SpecificAvroSerde<Weather> weatherSerde = new SpecificAvroSerde<>();
        weatherSerde.configure(Collections.singletonMap("schema.registry.url", "http://localhost:8081"), false);

        SpecificAvroSerde<ProcessedData> processedDataSerde = new SpecificAvroSerde<>();
        processedDataSerde.configure(Collections.singletonMap("schema.registry.url", "http://localhost:8081"), false);

        // Stream from weather-topic
        KStream<String, Weather> weatherStream = builder.stream("weather-topic", Consumed.with(Serdes.String(), weatherSerde));

        // Process weather data and write to processed-data-topic
        weatherStream.mapValues(weather -> {
            String suggestion = weather.getTemperature() > 30 ? "Cold Drink" : "Hot Drink";
            System.out.println("Processing weather data: " + weather + " -> " + suggestion);
            return new ProcessedData(weather.getLocation(), suggestion);
        }).to("processed-data-topic", Produced.with(Serdes.String(), processedDataSerde));

        // Aggregate data by location (e.g., count records per location)
        KTable<Windowed<String>, Long> aggregatedData = weatherStream
                .groupByKey() // Group by location
                .windowedBy(TimeWindows.of(Duration.ofMinutes(5))) // Aggregate over a 5-minute window
                .count(Materialized.as("weather-counts")); // Count records per location

        // Write aggregated data to a new topic
        aggregatedData.toStream()
                .map((windowedKey, count) -> {
                    String location = windowedKey.key();
                    String windowStart = windowedKey.window().startTime().toString();
                    String windowEnd = windowedKey.window().endTime().toString();
                    String value = "Location: " + location + ", Count: " + count + ", Window: " + windowStart + " to " + windowEnd;
                    return new org.apache.kafka.streams.KeyValue<>(location, value);
                })
                .to("aggregated-weather-topic", Produced.with(Serdes.String(), Serdes.String()));

        // Start the Kafka Streams application
        KafkaStreams streams = new KafkaStreams(builder.build(), props);
        streams.start();
    }
}

//public class WeatherStream {
//    public static void main(String[] args) {
//        Properties props = new Properties();
//        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "weather-stream-app");
//        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "192.168.0.206:9092,192.168.0.206:9093,192.168.0.206:9094");
//        props.put("schema.registry.url", "http://192.168.0.206:8081");
//        props.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, StreamsConfig.EXACTLY_ONCE_V2);
//        props.put("security.protocol", "PLAINTEXT");
//
//        StreamsBuilder builder = new StreamsBuilder();
//        SpecificAvroSerde<Weather> weatherSerde = new SpecificAvroSerde<>();
//        weatherSerde.configure(Collections.singletonMap("schema.registry.url", "http://localhost:8081"), false);
//
//        SpecificAvroSerde<ProcessedData> processedDataSerde = new SpecificAvroSerde<>();
//        processedDataSerde.configure(Collections.singletonMap("schema.registry.url", "http://localhost:8081"), false);
//
//        KStream<String, Weather> stream = builder.stream("weather-topic", Consumed.with(Serdes.String(), weatherSerde));
//
//        stream.mapValues(weather -> {
//            String suggestion = weather.getTemperature() > 15 ? "Cold Drink" : "Hot Drink";
//            System.out.println("Processing weather data: " + weather + " -> " + suggestion);
//            return new ProcessedData(weather.getLocation(), suggestion);
//        }).to("processed-data-topic", Produced.with(Serdes.String(), processedDataSerde));
//
//        KafkaStreams streams = new KafkaStreams(builder.build(), props);
//        streams.start();
//    }
//}
