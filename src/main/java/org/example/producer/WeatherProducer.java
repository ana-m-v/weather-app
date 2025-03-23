package org.example.producer;

import com.weather.avro.Weather;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Properties;


public class WeatherProducer {

    private static final String TOPIC = "weather-topic";
    private static final String API_KEY = "30ba5da68dc6fada26a601a16cbe29ca"; // Your weatherstack API key

    private final Producer<String, Weather> producer;

    // Constructor to initialize the Kafka producer
    public WeatherProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "<HOST_IP>:9092,<HOST_IP>:9093,<HOST_IP>:9094");
        props.put("key.serializer", KafkaAvroSerializer.class.getName());
        props.put("value.serializer", KafkaAvroSerializer.class.getName());
        props.put("schema.registry.url", "http://<HOST_IP>:8081");
        props.put("security.protocol", "PLAINTEXT");
        props.put("client.dns.lookup", "use_all_dns_ips");

        this.producer = new KafkaProducer<>(props);
    }

    // Method to produce weather data for a given location
    public void produceWeatherData(String location) {
        try {
            // Fetch weather data from the weatherstack API
            String response = fetchWeatherData(location);

            // Parse the response with JSONObject
            JSONObject json = new JSONObject(response);
            double temperature = json.getJSONObject("current").getDouble("temperature");
            double humidity = json.getJSONObject("current").getDouble("humidity");
            String suggestion = getSuggestion(temperature);

            // Build the Weather Avro object
            Weather weather = Weather.newBuilder()
                    .setLocation(location)
                    .setTemperature(temperature)
                    .setHumidity(humidity)
                    .setSuggestion(suggestion)
                    .build();

            // Create a ProducerRecord
            ProducerRecord<String, Weather> record = new ProducerRecord<>(TOPIC, location, weather);

            // Send record with a callback
            producer.send(record, (metadata, exception) -> {
                if (exception == null) {
                    System.out.println("Message sent: " + weather);
                } else {
                    exception.printStackTrace();
                }
            });

            producer.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Fetch weather data from the weatherstack API
    private String fetchWeatherData(String location) throws Exception {
        String apiUrl = "http://api.weatherstack.com/current?access_key=" + API_KEY + "&query=" + location;
        HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
        conn.setRequestMethod("GET");

        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;

        while ((line = in.readLine()) != null) {
            response.append(line);
        }
        in.close();

        return response.toString();
    }

    // Generate a suggestion based on temperature
    private String getSuggestion(double temperature) {
        if (temperature > 15) {
            return "Drink cold beverages";
        } else if (temperature < 10) {
            return "Drink hot beverages";
        } else {
            return "Room temperature drinks are fine";
        }
    }

    // Close the producer
    public void close() {
        producer.close();
    }
}

//public class WeatherProducer {
//    private static final String TOPIC = "weather-topic";
//    private static final String API_KEY = "<API_KEY>";
//
//    public static void main(String[] args) {
//        Properties props = new Properties();
//        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "<HOST_IP>:9092,1<HOST_IP>:9093,<HOST_IP>:9094");
//        props.put("key.serializer", KafkaAvroSerializer.class.getName());
//        props.put("value.serializer", KafkaAvroSerializer.class.getName());
//        props.put("schema.registry.url", "http://<HOST_IP>:8081");
//        props.put("security.protocol", "PLAINTEXT");
//        props.put("client.dns.lookup", "use_all_dns_ips"); // Resolve all DNS entries
//
//
//        Producer<String, Weather> producer = new KafkaProducer<>(props);
//
//        try {
//            String location = "New York";
//            String response = fetchWeatherData(location);
//
//            // Parse the response with JSONObject
//            JSONObject json = new JSONObject(response);
//            double temperature = json.getJSONObject("current").getDouble("temperature");
//            double humidity = json.getJSONObject("current").getDouble("humidity");
//            String suggestion = getSuggestion(temperature);
//
//            Weather weather = Weather.newBuilder()
//                    .setLocation(location)
//                    .setTemperature(temperature)
//                    .setHumidity(humidity)
//                    .setSuggestion(suggestion)
//                    .build();
//
//            // Create a ProducerRecord
//            ProducerRecord<String, Weather> record = new ProducerRecord<>(TOPIC, location, weather);
//
//            // Send record with a callback
//            producer.send(record, (metadata, exception) -> {
//                if (exception == null) {
//                    System.out.println("Message sent: " + weather);
//                } else {
//                    exception.printStackTrace();
//                }
//            });
//
//            producer.flush();
//        } catch (Exception e) {
//            e.printStackTrace();
//        } finally {
//            producer.close();
//        }
//    }
//
//    private static String fetchWeatherData(String location) throws Exception {
//        String apiUrl = "http://api.weatherstack.com/current?access_key=" + API_KEY + "&query=" + location;
//        HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
//        conn.setRequestMethod("GET");
//
//        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
//        StringBuilder response = new StringBuilder();
//        String line;
//
//        while ((line = in.readLine()) != null) {
//            response.append(line);
//        }
//        in.close();
//
//        return response.toString();
//    }
//
//    private static String getSuggestion(double temperature) {
//        if (temperature > 30) {
//            return "Drink cold beverages";
//        } else if (temperature < 10) {
//            return "Drink hot beverages";
//        } else {
//            return "Room temperature drinks are fine";
//        }
//    }
//}
