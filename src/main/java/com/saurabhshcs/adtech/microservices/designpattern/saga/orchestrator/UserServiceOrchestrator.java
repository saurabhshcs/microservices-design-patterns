package com.saurabhshcs.adtech.microservices.designpattern.saga.orchestrator;

import com.saurabhshcs.adtech.microservices.designpattern.model.UserModel;

public class UserServiceOrchestrator {

    private static final String SAMEER = "Sameer";
    private static final String USER_EMAIL = "user_email";

    public static void main(String[] args) {
        UserModel userModel = UserModel.builder()
                                        .userName(SAMEER)
                                        .email(USER_EMAIL)
                                        .build();
    }
}
