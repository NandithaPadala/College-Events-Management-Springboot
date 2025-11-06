package com.example.sb.demo.dto;

import lombok.Data;

@Data
public class RegisterRequest {
    private String username;
    private String password;
    private String email;
    private String fullName;
    private String studentId;
    private String department;
    private String year;
}