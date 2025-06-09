package com.google.meet.controller;

import com.google.meet.impl.MeetServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/master/google-meet")
public class MeetController {

    @Autowired
    private MeetServiceImpl service;

    @GetMapping("/auth")
    public ResponseEntity<String> getAuthUrl() throws Exception{
        String authorizationUrl = service.getAuthorizationUrl();
        return ResponseEntity.ok(authorizationUrl);
    }
    public ResponseEntity<String> createSpace(@RequestParam String code,@RequestParam String state ){
        try {
            String htmlResponse = service.createSpace(code, state);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "text/html; charset=UTF-8")
                    .body(htmlResponse);
        }catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("<html><body><h1>Error creating Google Meet space.</h1></body></html>");
        }
    }
}

