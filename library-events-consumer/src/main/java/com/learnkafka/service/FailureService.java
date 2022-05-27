package com.learnkafka.service;

import com.learnkafka.entity.FailureRecord;
import com.learnkafka.jpa.FailureRecordRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class FailureService {

    private final FailureRecordRepository failureRecordRepository;

    @Autowired
    public FailureService(FailureRecordRepository failureRecordRepository) {
        this.failureRecordRepository = failureRecordRepository;
    }

    public void saveFailedRecord(ConsumerRecord<Integer, String> consumerRecord, Exception e, String status) {
        var failureRecord = new FailureRecord(null, consumerRecord.topic(), consumerRecord.key(),
                consumerRecord.value(), consumerRecord.partition(), consumerRecord.offset(), e.getCause().getMessage(),
                status);
        failureRecordRepository.save(failureRecord);
    }
}
