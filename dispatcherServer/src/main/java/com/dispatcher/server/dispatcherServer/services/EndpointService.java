package com.dispatcher.server.dispatcherServer.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class EndpointService {
    public JsonNode contactToFhir(Object object, String URL, String RESTmethod) throws JsonProcessingException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Object> requestEntity = new HttpEntity<>(object, headers);
        //String address="http://localhost:8080/fhir/Patient"; //jest w argumentach
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response=restTemplate.exchange(
                URL,
                HttpMethod.valueOf(RESTmethod),
                requestEntity,
                String.class
        );
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode responseJson = objectMapper.readTree(response.getBody());
        //long idFromResponse = responseJson.path("id").asLong();
        return responseJson;
    }
}
