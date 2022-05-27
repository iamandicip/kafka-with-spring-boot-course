package com.learnkafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnkafka.entity.Book;
import com.learnkafka.entity.LibraryEvent;
import com.learnkafka.entity.LibraryEventType;
import com.learnkafka.jpa.FailureRecordRepository;
import com.learnkafka.jpa.LibraryEventsRepository;
import com.learnkafka.service.LibraryEventsService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.IntegerDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.TestPropertySource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@EmbeddedKafka(topics = {"library-events", "library-events.RETRY", "library-events.DLT"}, partitions = 3)
@TestPropertySource(properties = {"spring.kafka.producer.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "retryListener.startup=false"})
public class LibraryEventsConsumerIntegrationTest {

    @Autowired
    EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    KafkaTemplate<Integer, String> kafkaTemplate;

    @Autowired
    KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @SpyBean
    LibraryEventsConsumer libraryEventsConsumerSpy;

    @SpyBean
    LibraryEventsService libraryEventsService;

    @Autowired
    LibraryEventsRepository libraryEventsRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Consumer<Integer, String> consumer;

    @Autowired
    private FailureRecordRepository failureRecordRepository;

    @Value("${topics.retry}")
    private String retryTopic;

    @Value("${topics.dlt}")
    private String deadLetterTopic;

    @BeforeEach
    void setUp() {
//        for (MessageListenerContainer messageListenerContainer : kafkaListenerEndpointRegistry.getListenerContainers()) {
//            ContainerTestUtils.waitForAssignment(messageListenerContainer, embeddedKafkaBroker.getPartitionsPerTopic());
//        }
        var container = kafkaListenerEndpointRegistry.getListenerContainers().stream()
                .filter(messageListenerContainer ->
                        Objects.equals(messageListenerContainer.getGroupId(), "library-events-listener-group"))
                .collect(Collectors.toList())
                .get(0);

        ContainerTestUtils.waitForAssignment(container, embeddedKafkaBroker.getPartitionsPerTopic());
    }

    @AfterEach
    void tearDown() {
        libraryEventsRepository.deleteAll();
    }

    @Test
    void publishNewLibraryEvent() throws ExecutionException, InterruptedException, JsonProcessingException {
        // given
        String json = "{\"libraryEventId\":null,\"book\":{\"bookId\":456,\"bookName\":\"Kafka Using Spring Boot\",\"bookAuthor\":\"Dilip\"},\"libraryEventType\":\"NEW\"}";
        kafkaTemplate.sendDefault(json).get();

        // when
        CountDownLatch latch = new CountDownLatch(1);
        latch.await(3, TimeUnit.SECONDS);

        // then
        verify(libraryEventsConsumerSpy).onMessage(isA(ConsumerRecord.class));
        verify(libraryEventsService).processLibraryEvent(isA(ConsumerRecord.class));

        List<LibraryEvent> libraryEventList = (List<LibraryEvent>) libraryEventsRepository.findAll();
        assertEquals(1, libraryEventList.size());
        libraryEventList.forEach(libraryEvent -> {
            assertNotNull(libraryEvent.getLibraryEventId());
            assertEquals(456, libraryEvent.getBook().getBookId());
        });
    }

    @Test
    void publishUpdateLibraryEvent() throws JsonProcessingException, ExecutionException, InterruptedException {
        // given
        // save the initial event in the DB
        String json = "{\"libraryEventId\":null,\"book\":{\"bookId\":456,\"bookName\":\"Kafka Using Spring Boot\",\"bookAuthor\":\"Dilip\"},\"libraryEventType\":\"NEW\",\"eventStatus\":\"ADD\"}";
        LibraryEvent libraryEvent = objectMapper.readValue(json, LibraryEvent.class);
        libraryEvent.getBook().setLibraryEvent(libraryEvent);
        libraryEventsRepository.save(libraryEvent);

        // publish the update LibraryEvent
        Book updatedBook = Book.builder()
                .bookId(456)
                .bookName("Kafka Using Spring Boot 2.x")
                .bookAuthor("Dilip")
                .build();
        libraryEvent.setLibraryEventType(LibraryEventType.UPDATE);
        libraryEvent.setBook(updatedBook);

        String updatedJson = objectMapper.writeValueAsString(libraryEvent);
        kafkaTemplate.sendDefault(libraryEvent.getLibraryEventId(), updatedJson).get();

        // when
        CountDownLatch latch = new CountDownLatch(1);
        latch.await(3, TimeUnit.SECONDS);

        // then
        verify(libraryEventsConsumerSpy).onMessage(isA(ConsumerRecord.class));
        verify(libraryEventsService).processLibraryEvent(isA(ConsumerRecord.class));

        LibraryEvent persistedLibraryEvent = libraryEventsRepository.findById(libraryEvent.getLibraryEventId()).get();
        assertEquals("Kafka Using Spring Boot 2.x", persistedLibraryEvent.getBook().getBookName());
    }

    @Test
    void publishUpdateLibraryEvent_null() throws JsonProcessingException, ExecutionException, InterruptedException {
        // given
        // save the initial event in the DB
        String json = "{\"libraryEventId\":null,\"book\":{\"bookId\":456,\"bookName\":\"Kafka Using Spring Boot\",\"bookAuthor\":\"Dilip\"},\"libraryEventType\":\"UPDATE\",\"eventStatus\":\"ADD\"}";
        kafkaTemplate.sendDefault(null, json).get();

        // when
        CountDownLatch latch = new CountDownLatch(1);
        latch.await(5, TimeUnit.SECONDS);

        // then
        verify(libraryEventsConsumerSpy, times(1)).onMessage(isA(ConsumerRecord.class));
        verify(libraryEventsService, times(1)).processLibraryEvent(isA(ConsumerRecord.class));

        Map<String, Object> configs = new HashMap<>(
                KafkaTestUtils.consumerProps("group2", "true", embeddedKafkaBroker)
        );
        consumer = new DefaultKafkaConsumerFactory<>(
                configs,
                new IntegerDeserializer(),
                new StringDeserializer()).createConsumer();

        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, deadLetterTopic);

        ConsumerRecord<Integer, String> consumerRecord = KafkaTestUtils.getSingleRecord(consumer, deadLetterTopic);
        System.out.println("ConsumerRecord: " + consumerRecord.value());
        assertEquals(json, consumerRecord.value());
    }

    @Test
    void publishUpdateLibraryEvent_999() throws JsonProcessingException, ExecutionException, InterruptedException {
        // given
        // save the initial event in the DB
        String json = "{\"libraryEventId\":999,\"book\":{\"bookId\":456,\"bookName\":\"Kafka Using Spring Boot\",\"bookAuthor\":\"Dilip\"},\"libraryEventType\":\"UPDATE\",\"eventStatus\":\"ADD\"}";
        kafkaTemplate.sendDefault(json).get();

        // when
        CountDownLatch latch = new CountDownLatch(1);
        latch.await(5, TimeUnit.SECONDS);

        // then
        verify(libraryEventsConsumerSpy, times(3)).onMessage(isA(ConsumerRecord.class));
        verify(libraryEventsService, times(3)).processLibraryEvent(isA(ConsumerRecord.class));

        Map<String, Object> configs = new HashMap<>(
                KafkaTestUtils.consumerProps("group1", "true", embeddedKafkaBroker)
        );
        consumer = new DefaultKafkaConsumerFactory<>(
                configs,
                new IntegerDeserializer(),
                new StringDeserializer()).createConsumer();

        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, retryTopic);

        ConsumerRecord<Integer, String> consumerRecord = KafkaTestUtils.getSingleRecord(consumer, retryTopic);
        System.out.println("ConsumerRecord: " + consumerRecord.value());
        assertEquals(json, consumerRecord.value());
    }

    @Test
    void publishUpdateLibraryEvent_null_failureRecord() throws JsonProcessingException, ExecutionException, InterruptedException {
        // given
        // save the initial event in the DB
        String json = "{\"libraryEventId\":null,\"book\":{\"bookId\":456,\"bookName\":\"Kafka Using Spring Boot\",\"bookAuthor\":\"Dilip\"},\"libraryEventType\":\"UPDATE\",\"eventStatus\":\"ADD\"}";
        kafkaTemplate.sendDefault(null, json).get();

        // when
        CountDownLatch latch = new CountDownLatch(1);
        latch.await(5, TimeUnit.SECONDS);

        // then
        verify(libraryEventsConsumerSpy, times(1)).onMessage(isA(ConsumerRecord.class));
        verify(libraryEventsService, times(1)).processLibraryEvent(isA(ConsumerRecord.class));

        var count = failureRecordRepository.count();
        assertEquals(1, count);

        failureRecordRepository.findAll().forEach(failureRecord -> {
            System.out.println("failureRecord: " + failureRecord);
        });
    }
}
