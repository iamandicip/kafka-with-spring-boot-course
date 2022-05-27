package com.learnkafka.jpa;

import com.learnkafka.entity.FailureRecord;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FailureRecordRepository extends CrudRepository<FailureRecord, Integer> {
    List<FailureRecord> findAllByStatus(String retry);
}
