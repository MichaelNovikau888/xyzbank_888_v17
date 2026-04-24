package com.bank.profile.kafka.producer;

import com.bank.profile.dto.ProfileDto;
import com.bank.profile.util.KafkaTopic;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ProfileProducer {

    private static final Logger log = Logger.getLogger(ProfileProducer.class);

    @Inject
    @Channel("profile-out")
    Emitter<ProfileDto> profileEmitter;

    @Inject
    KafkaTopic topicsConfig;

    public void sendUpdate(ProfileDto dto) {
        send(topicsConfig.getTopicProfileUpdate(), dto);
    }

    public void sendDelete(Long id) {
        // Для отправки Long-id используем тот же ProfileDto с заполненным id
        ProfileDto idDto = new ProfileDto();
        idDto.setId(id);
        send(topicsConfig.getTopicProfileDelete(), idDto);
    }

    public void sendGet(Long id) {
        ProfileDto idDto = new ProfileDto();
        idDto.setId(id);
        send(topicsConfig.getTopicProfileGet(), idDto);
    }

    public void sendGetResponse(ProfileDto dto) {
        send(topicsConfig.getTopicProfileGetResponse(), dto);
    }

    private void send(String topic, ProfileDto dto) {
        log.debugf("Sending profile event to topic=%s, profileId=%d", topic, dto.getId());
        profileEmitter.send(
                Message.of(dto).addMetadata(
                        OutgoingKafkaRecordMetadata.<String>builder()
                                .withTopic(topic)
                                .build()
                )
        );
    }
}
