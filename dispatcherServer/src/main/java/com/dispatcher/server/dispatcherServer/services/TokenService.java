package com.dispatcher.server.dispatcherServer.services;

import com.dispatcher.server.dispatcherServer.model.User;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.servlet.http.Cookie;
import org.springframework.stereotype.Service;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

@Service
public class TokenService {
    private SecretKey secretKey = KeyGenerator.getInstance("HmacSHA256").generateKey(); //klucz do podpisywania jwt

    public String klucz = "key"; ///klucz testowy
    private byte[] kluczBytes = klucz.getBytes(StandardCharsets.UTF_8);

    public TokenService() throws NoSuchAlgorithmException {
    }

    public Cookie getCookie(User user) {
        JwtBuilder builder = Jwts.builder()
                .setHeaderParam("type", "JWT")
                .setSubject(user.getLogin())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + (45 * 60 * 1000))) //45 minut
                .claim("role", user.getRole())
                .claim("id", user.getId());
                //.claim("res_id", user.getRes_id());

        //String jwt = builder.signWith(SignatureAlgorithm.HS256, secretKey).compact();
        String jwt = builder.signWith(SignatureAlgorithm.HS256, kluczBytes).compact();
        //System.out.println("\nklucz: "+Base64.getEncoder().encodeToString(secretKey.getEncoded()));
        //System.out.println("\ntoken: "+jwt);

        Cookie cookie = new Cookie("jwt", jwt);
        cookie.setHttpOnly(false); //jeśli true to nie mogę przeglądać zawartości.
        cookie.setMaxAge(60 * 45); // 45 min
        //cookie.setMaxAge(20);
        cookie.setPath("/");
        return cookie;
    }
}
