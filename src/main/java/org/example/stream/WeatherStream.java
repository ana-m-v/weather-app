package org.example.stream;

import com.weather.avro.ProcessedData;
import com.weather.avro.Weather;
import org.apache.kafka.common.serialization.Serdes;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;

import java.util.Collections;
import java.util.Properties;

public class WeatherStream {
    public static void main(String[] args) {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "weather-stream-app");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "<HOST_IP>:9092,<HOST_IP>:9093,<HOST_IP>:9094");
        props.put("schema.registry.url", "http://<HOST_IP>:8081");
        props.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, StreamsConfig.EXACTLY_ONCE_V2);
        props.put("security.protocol", "PLAINTEXT");

        StreamsBuilder builder = new StreamsBuilder();
        SpecificAvroSerde<Weather> weatherSerde = new SpecificAvroSerde<>();
        weatherSerde.configure(Collections.singletonMap("schema.registry.url", "http://localhost:8081"), false);

        SpecificAvroSerde<ProcessedData> processedDataSerde = new SpecificAvroSerde<>();
        processedDataSerde.configure(Collections.singletonMap("schema.registry.url", "http://localhost:8081"), false);

        KStream<String, Weather> stream = builder.stream("weather-topic", Consumed.with(Serdes.String(), weatherSerde));

        stream.mapValues(weather -> {
            String suggestion = weather.getTemperature() > 30 ? "Cold Drink" : "Hot Drink";
            System.out.println("Processing weather data: " + weather + " -> " + suggestion);
            return new ProcessedData(weather.getLocation(), suggestion);
        }).to("processed-data-topic", Produced.with(Serdes.String(), processedDataSerde));

        KafkaStreams streams = new KafkaStreams(builder.build(), props);
        streams.start();
    }
}
