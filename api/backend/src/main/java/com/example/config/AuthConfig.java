package com.example.config;

public class AuthConfig {
    public static final String JWT_SECRET = "mySuperSecret123!"; // Use env vars in prod
    public static final int TOKEN_EXPIRY_SEC = 3600; 
}
