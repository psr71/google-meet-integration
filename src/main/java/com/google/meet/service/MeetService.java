package com.google.meet.service;

import org.springframework.stereotype.Service;


public interface MeetService  {
    String getAuthorizationUrl() throws Exception;

    String createSpace(String code, String state) throws Exception;
}
