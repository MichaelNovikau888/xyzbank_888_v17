package com.bank.antifraud.kafkaProducer;

import com.bank.antifraud.dto.AntifraudResponseEvent;
import com.bank.antifraud.dto.TransferAntifraudResponseEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;


@Service
@Slf4j
public class SuspiciousTransferProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public SuspiciousTransferProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

 /**
     * Ответ antifraud → payment-api.
     * Partition key = paymentId → payment-api получает ответ в той же партиции.
     * Топик: payment.antifraud.response
 */
    public void sendAntifraudResponse(AntifraudResponseEvent response) {
        String key = response.getPaymentId() != null
                ? response.getPaymentId().toString()
                : "unknown";
        kafkaTemplate.send("payment.antifraud.response", key, response);
        log.debug("Payment antifraud response sent: paymentId={} decision={}",
                response.getPaymentId(), response.getDecision());
    }

 /**
     * Ответ antifraud → transfer-service.
     * Partition key = transferId → transfer-service получает ответ в той же партиции.
     * Топик: transfer.antifraud.response
 */
    public void sendTransferAntifraudResponse(TransferAntifraudResponseEvent response) {
        String key = response.getTransferId() != null
                ? response.getTransferId().toString()
                : "unknown";
        kafkaTemplate.send("transfer.antifraud.response", key, response);
        log.debug("Transfer antifraud response sent: transferId={} decision={}",
                response.getTransferId(), response.getDecision());
    }

}
