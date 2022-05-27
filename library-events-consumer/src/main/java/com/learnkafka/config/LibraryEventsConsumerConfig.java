package com.learnkafka.config;

import com.learnkafka.service.FailureService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.util.backoff.FixedBackOff;

import java.util.List;

@Configuration
@EnableKafka
@Slf4j
public class LibraryEventsConsumerConfig {

    public static final String RETRY = "RETRY";
    public static final String DEAD = "DEAD";
    public static final String SUCCESS = "SUCCESS";
    @Autowired
    KafkaTemplate<Integer, String> kafkaTemplate;

    @Autowired
    FailureService failureService;

    @Value("${topics.retry}")
    private String retryTopic;

    @Value("${topics.dlt}")
    private String deadLetterTopic;

    public DeadLetterPublishingRecoverer publishingRecoverer() {
        return new DeadLetterPublishingRecoverer(kafkaTemplate,
                (r, e) -> {
                    log.error("Exception in publishingRecoverer: {}", e.getMessage(), e);
                    if (e.getCause() instanceof RecoverableDataAccessException) {
                        return new TopicPartition(retryTopic, r.partition());
                    } else {
                        return new TopicPartition(deadLetterTopic, r.partition());
                    }
                });
    }

    public ConsumerRecordRecoverer consumerRecordRecoverer() {
        return (consumerRecord, e) -> {
            var record = (ConsumerRecord<Integer, String>) consumerRecord;
            log.error("Exception in consumerRecordRecoverer: {}", e.getMessage(), e);
            if (e.getCause() instanceof RecoverableDataAccessException) {
                failureService.saveFailedRecord(record, e, RETRY);
            } else {
                failureService.saveFailedRecord(record, e, DEAD);
            }
        };
    }

    public DefaultErrorHandler errorHandler() {
        var fixedBackOff = new FixedBackOff(1000L, 2);

        var exponentialBackoff = new ExponentialBackOffWithMaxRetries(2);
        exponentialBackoff.setInitialInterval(1_000L);
        exponentialBackoff.setMultiplier(2.0);
        exponentialBackoff.setMaxInterval(2_000L);

        var errorHandler = new DefaultErrorHandler(
//                publishingRecoverer(),
                consumerRecordRecoverer(),
                exponentialBackoff
        );

        var exceptionsToIgnoreList = List.of(IllegalArgumentException.class);
        var exceptionsToRetryList = List.of(RecoverableDataAccessException.class);

        exceptionsToIgnoreList.forEach(errorHandler::addNotRetryableExceptions);
        exceptionsToRetryList.forEach(errorHandler::addRetryableExceptions);

        errorHandler.setRetryListeners((record, ex, deliveryAttempt) -> {
            log.info("Failed record in retry listener. Exception is: {}, delivery attempt: {}",
                    ex.getMessage(), deliveryAttempt);
        });

        return errorHandler;
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<?, ?> kafkaListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ObjectProvider<ConsumerFactory<Object, Object>> kafkaConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory();
        configurer.configure(factory, kafkaConsumerFactory.getIfAvailable());

//        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

        factory.setConcurrency(3);
        factory.setCommonErrorHandler(errorHandler());

        return factory;
    }

}
