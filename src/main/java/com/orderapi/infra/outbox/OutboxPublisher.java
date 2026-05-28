package com.orderapi.infra.outbox;

import com.orderapi.domain.repository.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final int BATCH_SIZE = 50;

    private final OutboxRepository outboxRepository;

    public OutboxPublisher(OutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Scheduled(fixedDelayString = "${outbox.publish-interval-ms}")
    public void publishPendingEvents() {
        var events = outboxRepository.findPending(BATCH_SIZE);
        for (var event : events) {
            log.info("Publishing outbox event: type={}, aggregateId={}",
                    event.getEventType(), event.getAggregateId());
            outboxRepository.markPublished(event);
        }
        if (!events.isEmpty()) {
            log.info("Published {} outbox events", events.size());
        }
    }
}
