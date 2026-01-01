package com.spire.dfcbridge.service.rest;

import com.spire.dfcbridge.model.SessionInfo;
import lombok.Data;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Holds REST session information including the configured WebClient.
 */
@Data
public class RestSessionHolder {
    private WebClient webClient;
    private SessionInfo sessionInfo;
    private String repository;
    private String username;
    private String password;
}
