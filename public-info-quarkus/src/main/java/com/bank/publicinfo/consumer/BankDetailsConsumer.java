package com.bank.publicinfo.consumer;

import com.bank.publicinfo.dto.BankDetailsDto;
import com.bank.publicinfo.metrics.PublicInfoMetrics;
import com.bank.publicinfo.service.BankDetailsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

@ApplicationScoped
public class BankDetailsConsumer {

    private static final Logger LOG = Logger.getLogger(BankDetailsConsumer.class);

    @Inject BankDetailsService service;
    @Inject PublicInfoMetrics  metrics;
    @Inject ObjectMapper       objectMapper;

    @Incoming("bank-create") @Blocking
    public void onCreate(String message) {
        try {
            BankDetailsDto dto = objectMapper.readValue(message, BankDetailsDto.class);
            service.create(dto);
            metrics.getBankDetailsCreated().increment();
            LOG.infof("BankDetails created: bik=%d", dto.getBik());
        } catch (Exception e) {
            LOG.errorf(e, "Failed to process bank-create: %s", e.getMessage());
            metrics.getKafkaErrors().increment();
        }
    }

    @Incoming("bank-update") @Blocking
    public void onUpdate(String message) {
        try {
            BankDetailsDto dto = objectMapper.readValue(message, BankDetailsDto.class);
            service.update(dto);
            metrics.getBankDetailsUpdated().increment();
        } catch (Exception e) {
            LOG.errorf(e, "Failed to process bank-update: %s", e.getMessage());
            metrics.getKafkaErrors().increment();
        }
    }

    @Incoming("bank-delete") @Blocking
    public void onDelete(String message) {
        try {
            BankDetailsDto dto = objectMapper.readValue(message, BankDetailsDto.class);
            if (dto.getId() == null) { LOG.warn("bank-delete: id is null, skipping"); return; }
            service.deleteById(dto.getId());
            metrics.getBankDetailsDeleted().increment();
        } catch (Exception e) {
            LOG.errorf(e, "Failed to process bank-delete: %s", e.getMessage());
            metrics.getKafkaErrors().increment();
        }
    }
}
