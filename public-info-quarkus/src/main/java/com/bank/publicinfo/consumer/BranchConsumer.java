package com.bank.publicinfo.consumer;

import com.bank.publicinfo.dto.BranchDto;
import com.bank.publicinfo.metrics.PublicInfoMetrics;
import com.bank.publicinfo.service.BranchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

@ApplicationScoped
public class BranchConsumer {

    private static final Logger LOG = Logger.getLogger(BranchConsumer.class);

    @Inject BranchService     service;
    @Inject PublicInfoMetrics metrics;
    @Inject ObjectMapper      objectMapper;

    @Incoming("branch-create") @Blocking
    public void onCreate(String message) {
        try {
            BranchDto dto = objectMapper.readValue(message, BranchDto.class);
            service.create(dto);
            metrics.getBranchCreated().increment();
        } catch (Exception e) {
            LOG.errorf(e, "branch-create failed: %s", e.getMessage());
            metrics.getKafkaErrors().increment();
        }
    }

    @Incoming("branch-update") @Blocking
    public void onUpdate(String message) {
        try {
            BranchDto dto = objectMapper.readValue(message, BranchDto.class);
            if (dto.getId() == null) { LOG.warn("branch-update: id is null"); return; }
            service.update(dto.getId(), dto);
            metrics.getBranchUpdated().increment();
        } catch (Exception e) {
            LOG.errorf(e, "branch-update failed: %s", e.getMessage());
            metrics.getKafkaErrors().increment();
        }
    }

    @Incoming("branch-delete") @Blocking
    public void onDelete(String message) {
        try {
            BranchDto dto = objectMapper.readValue(message, BranchDto.class);
            if (dto.getId() == null) return;
            service.deleteById(dto.getId());
            metrics.getBranchDeleted().increment();
        } catch (Exception e) {
            LOG.errorf(e, "branch-delete failed: %s", e.getMessage());
            metrics.getKafkaErrors().increment();
        }
    }
}
