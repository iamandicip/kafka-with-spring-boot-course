package com.learnkafka.jpa.scheduler;

import com.learnkafka.config.LibraryEventsConsumerConfig;
import com.learnkafka.entity.FailureRecord;
import com.learnkafka.jpa.FailureRecordRepository;
import com.learnkafka.service.LibraryEventsService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RetryScheduler {

    @Autowired
    private FailureRecordRepository failureRecordRepository;

    @Autowired
    private LibraryEventsService libraryEventsService;

    @Scheduled(fixedRate = 10000)
    public void retryFailedRecords() {
        log.info("Retrying failed records started");
        failureRecordRepository.findAllByStatus(LibraryEventsConsumerConfig.RETRY)
                .forEach(failureRecord -> {
                    log.info("Retrying failed record: {}", failureRecord);
                    var consumerRecord = buildConsumerRecord(failureRecord);
                    try {
                        libraryEventsService.processLibraryEvent(consumerRecord);
                        failureRecord.setStatus(LibraryEventsConsumerConfig.SUCCESS);
                    } catch (Exception e) {
                        log.error("Exception in retryFailedRecords: {}", e.getMessage(), e);
                    }
                });
        log.info("Retrying failed records completed");
    }

    private ConsumerRecord<Integer, String> buildConsumerRecord(FailureRecord failureRecord) {
        return new ConsumerRecord<>(failureRecord.getTopic(),
                failureRecord.getPartition(),
                failureRecord.getOffset_value(),
                failureRecord.getKey(),
                failureRecord.getErrorRecord());
    }
}
