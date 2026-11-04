package com.agri.trace.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
public class LoginVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String token;
    private String tokenType;
    private long expiresIn;
    private UserVO user;
    private List<String> permissions;
}
