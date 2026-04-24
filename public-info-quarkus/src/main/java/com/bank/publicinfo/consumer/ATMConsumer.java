package com.bank.publicinfo.consumer;

import com.bank.publicinfo.dto.ATMDto;
import com.bank.publicinfo.metrics.PublicInfoMetrics;
import com.bank.publicinfo.service.ATMService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ATMConsumer {

    private static final Logger LOG = Logger.getLogger(ATMConsumer.class);

    @Inject ATMService        service;
    @Inject PublicInfoMetrics metrics;
    @Inject ObjectMapper      objectMapper;

    @Incoming("atm-create") @Blocking
    public void onCreate(String message) {
        try {
            ATMDto dto = objectMapper.readValue(message, ATMDto.class);
            service.create(dto);
            metrics.getAtmCreated().increment();
        } catch (Exception e) {
            LOG.errorf(e, "atm-create failed: %s", e.getMessage());
            metrics.getKafkaErrors().increment();
        }
    }

    @Incoming("atm-update") @Blocking
    public void onUpdate(String message) {
        try {
            ATMDto dto = objectMapper.readValue(message, ATMDto.class);
            if (dto.getId() == null) { LOG.warn("atm-update: id is null"); return; }
            service.update(dto);
            metrics.getAtmUpdated().increment();
        } catch (Exception e) {
            LOG.errorf(e, "atm-update failed: %s", e.getMessage());
            metrics.getKafkaErrors().increment();
        }
    }

    @Incoming("atm-delete") @Blocking
    public void onDelete(String message) {
        try {
            ATMDto dto = objectMapper.readValue(message, ATMDto.class);
            if (dto.getId() == null) return;
            service.deleteById(dto.getId());
            metrics.getAtmDeleted().increment();
        } catch (Exception e) {
            LOG.errorf(e, "atm-delete failed: %s", e.getMessage());
            metrics.getKafkaErrors().increment();
        }
    }
}
