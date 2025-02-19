package ma.enset;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.KStream;

import java.util.Properties;
public class TransactionProcessor {
    private static final String INPUT_TOPIC = "transactions-input";
    private static final String OUTPUT_TOPIC = "fraud-alerts";
    private static final double SUSPICIOUS_AMOUNT = 10000.0;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    public static void main(String[] args) {
        Properties config = new Properties();
        config.put(StreamsConfig.APPLICATION_ID_CONFIG, "transaction-processor");
        config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        config.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        config.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, String> inputStream = builder.stream(INPUT_TOPIC);
        inputStream.mapValues(value -> {try {return MAPPER.readValue(value, Transaction.class);
                    } catch (Exception e) {
                        System.err.println("Error parsing transaction: " + e.getMessage());
                        return null;}})
                .filter((key, transaction) -> transaction != null)
                // Split the stream into fraudulent and normal transactions
                .branch((key, transaction) -> transaction.getAmount() > SUSPICIOUS_AMOUNT)[0]  // Get the fraudulent branch
                .mapValues(transaction -> {
                    try {String json = MAPPER.writeValueAsString(transaction);
                        System.out.println("Fraud alert for transaction: " + json);
                        return json;} catch (Exception e) {
                        System.err.println("Error serializing transaction: " + e.getMessage());
                        return null;}})
                .filter((key, value) -> value != null)
                .to(OUTPUT_TOPIC);
        KafkaStreams streams = new KafkaStreams(builder.build(), config);
        streams.start();
        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));}}