package com.bank.notification.client;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.Map;

@Path("/fcm")
@RegisterRestClient(configKey = "fcm-api")
@Consumes(MediaType.APPLICATION_JSON)
public interface FcmClient {

    @POST
    @Path("/send")
    void send(Map<String, Object> payload);
}
