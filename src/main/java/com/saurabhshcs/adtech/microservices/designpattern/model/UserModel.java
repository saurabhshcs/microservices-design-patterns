package com.saurabhshcs.adtech.microservices.designpattern.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Builder
@AllArgsConstructor
@Getter
public class UserModel {
    private String userName;
    private String email;
}
