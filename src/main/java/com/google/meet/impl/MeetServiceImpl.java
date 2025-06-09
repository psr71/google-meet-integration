package com.google.meet.impl;

import ch.qos.logback.core.util.StringUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.apps.meet.v2.CreateSpaceRequest;
import com.google.apps.meet.v2.Space;
import com.google.apps.meet.v2.SpacesServiceClient;
import com.google.apps.meet.v2.SpacesServiceSettings;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.ClientId;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.UserAuthorizer;
import com.google.meet.service.MeetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;

@Service
@Slf4j
public class MeetServiceImpl implements MeetService {

    @Value("${google.oauth.client-id}")
    private String clientId;

    @Value("${google.oauth.client-secret}")
    private String clientSecret;

    @Value("${google.oauth.redirect-uri}")
    private String redirectUri;

    @Value("${google.oauth.token-url}")
    private String tokenUrl;

    @Autowired
    private RestTemplate restTemplate;

    @Override
    public String getAuthorizationUrl() throws Exception {
        try {
            return getAuthUrl();
        } catch (Exception e) {
            log.warn("Error generating auth URL, retrying.", e);
        }
        return null;
    }

    private String getAuthUrl() throws Exception {
        UserAuthorizer userAuthorizer = UserAuthorizer.newBuilder()
                .setClientId(ClientId.newBuilder().setClientId(clientId).build())
                .setCallbackUri(URI.create(redirectUri))
                .setScopes(Arrays.asList(
                        "https://www.googleapis.com/auth/meetings.space.created",
                        "https://www.googleapis.com/auth/meetings",
                        "https://www.googleapis.com/auth/calendar",
                        "https://www.googleapis.com/auth/userinfo.email"
                ))
                .build();
        URL authorizationUrl = userAuthorizer.getAuthorizationUrl("user", null, null);
        log.info("Generated Authorization URL: {}", authorizationUrl);
        return authorizationUrl.toString();

    }

    private String getAccessToken(String code) throws Exception {
        if (StringUtil.isNullOrEmpty(code)) {
            throw new IllegalArgumentException("Authorization code cannot be null or empty.");
        }
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("code", code);
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("redirect_uri", redirectUri);
        body.add("grant_type", "authorization_code");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> responce = restTemplate.postForEntity(tokenUrl, entity, String.class);
        if (responce.getStatusCode() == HttpStatus.OK && responce.getBody() != null) {
            return extractAccessToken(responce.getBody());
        }else {
            log.error("Failed to retrieve access token. Response: {}",responce.getBody());
            throw new Exception("Failed to retrieve access token.");
        }
    }

    private String extractAccessToken(String body) throws IOException {
        return new ObjectMapper().readTree(body).path("access_token").asText();
    }
    public String refreshToken(String token) throws Exception{
        if(StringUtil.notNullNorEmpty(token)){
            throw new IllegalArgumentException("Refresh token cannot be null or empty.");
        }
        MultiValueMap<String,String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("refresh_token", token);
        body.add("grant_type", "token");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String,String>> entity = new HttpEntity<>(body,headers);

        ResponseEntity<String> response = restTemplate.postForEntity(tokenUrl, entity, String.class);
        if(response.getStatusCode() == HttpStatus.OK && response.getBody()!= null){
            return extractAccessToken(response.getBody());
        }else {
            throw new Exception("Failed to refresh access token.");
        }
    }
    @Override
    public String createSpace(String code, String state) throws Exception {
        log.info("Creating Google Meet space with code: {}", code);
        String accessToken = StringUtil.isNullOrEmpty(state) ? getAccessToken(code) : state;
        log.info("Access token: {}", accessToken);

        try {
            GoogleCredentials credentials = GoogleCredentials.newBuilder()
                    .setAccessToken(AccessToken.newBuilder()
                            .setTokenValue(accessToken).build())
                    .build();

            SpacesServiceSettings settings = SpacesServiceSettings.newBuilder()
                    .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                    .build();

            SpacesServiceClient spacesServiceClient = SpacesServiceClient.create(settings);
            CreateSpaceRequest request = CreateSpaceRequest.newBuilder()
                    .setSpace(Space.newBuilder().build())
                    .build();
            Space space = spacesServiceClient.createSpace(request);
            log.info("Successfully created space with URI: {}", space.getMeetingUri());

            String meetingUri = space.getMeetingUri();
            return StringUtil.notNullNorEmpty(accessToken) ? generateHtmlResponse(meetingUri) : meetingUri;
        }catch (Exception e){
            log.error("Error creating Google Meet space.", e);
            throw new Exception("Error creating Google Meet space.");
        }
    }

    private String generateHtmlResponse(String meetingUri) {
        return """
            <html>
            <head>
                <meta http-equiv="refresh" content="0; url=%s" />
                <title>Google Meet</title>
            </head>
            <body>
                <div style="text-align: center;">
                    <h1 style="font-family: inter;">Joining Google Meet...</h1>
                    <iframe src="%s" width="600" height="400" allow="camera; microphone" style="border: 0;"></iframe>
                </div>
            </body>
            </html>
        """.formatted(meetingUri, meetingUri);
    }

}
