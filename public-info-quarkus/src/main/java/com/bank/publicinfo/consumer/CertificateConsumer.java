package com.bank.publicinfo.consumer;

import com.bank.publicinfo.dto.CertificateDto;
import com.bank.publicinfo.metrics.PublicInfoMetrics;
import com.bank.publicinfo.service.CertificateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

@ApplicationScoped
public class CertificateConsumer {

    private static final Logger LOG = Logger.getLogger(CertificateConsumer.class);

    @Inject CertificateService service;
    @Inject PublicInfoMetrics  metrics;
    @Inject ObjectMapper       objectMapper;

    @Incoming("certificate-create") @Blocking
    public void onCreate(String message) {
        try {
            CertificateDto dto = objectMapper.readValue(message, CertificateDto.class);
            service.create(dto);
            metrics.getCertificateCreated().increment();
        } catch (Exception e) {
            LOG.errorf(e, "certificate-create failed: %s", e.getMessage());
            metrics.getKafkaErrors().increment();
        }
    }

    @Incoming("certificate-update") @Blocking
    public void onUpdate(String message) {
        try {
            CertificateDto dto = objectMapper.readValue(message, CertificateDto.class);
            service.update(dto);
        } catch (Exception e) {
            LOG.errorf(e, "certificate-update failed: %s", e.getMessage());
            metrics.getKafkaErrors().increment();
        }
    }

    @Incoming("certificate-delete") @Blocking
    public void onDelete(String message) {
        try {
            CertificateDto dto = objectMapper.readValue(message, CertificateDto.class);
            if (dto.getId() == null) return;
            service.deleteById(dto.getId());
        } catch (Exception e) {
            LOG.errorf(e, "certificate-delete failed: %s", e.getMessage());
            metrics.getKafkaErrors().increment();
        }
    }
}
