package com.dispatcher.server.dispatcherServer.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class User {
    private long id;
    private String login;
    private String password;
    private String role;
    private long res_id;
}