package com.bank.publicinfo.consumer;

import com.bank.publicinfo.dto.LicenseDto;
import com.bank.publicinfo.metrics.PublicInfoMetrics;
import com.bank.publicinfo.service.LicenseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

@ApplicationScoped
public class LicenseConsumer {

    private static final Logger LOG = Logger.getLogger(LicenseConsumer.class);

    @Inject LicenseService    service;
    @Inject PublicInfoMetrics metrics;
    @Inject ObjectMapper      objectMapper;

    @Incoming("license-create") @Blocking
    public void onCreate(String message) {
        try {
            LicenseDto dto = objectMapper.readValue(message, LicenseDto.class);
            service.create(dto);
            metrics.getLicenseCreated().increment();
        } catch (Exception e) {
            LOG.errorf(e, "license-create failed: %s", e.getMessage());
            metrics.getKafkaErrors().increment();
        }
    }

    @Incoming("license-update") @Blocking
    public void onUpdate(String message) {
        try {
            LicenseDto dto = objectMapper.readValue(message, LicenseDto.class);
            service.update(dto);
        } catch (Exception e) {
            LOG.errorf(e, "license-update failed: %s", e.getMessage());
            metrics.getKafkaErrors().increment();
        }
    }

    @Incoming("license-delete") @Blocking
    public void onDelete(String message) {
        try {
            LicenseDto dto = objectMapper.readValue(message, LicenseDto.class);
            if (dto.getId() == null) return;
            service.deleteById(dto.getId());
        } catch (Exception e) {
            LOG.errorf(e, "license-delete failed: %s", e.getMessage());
            metrics.getKafkaErrors().increment();
        }
    }
}
