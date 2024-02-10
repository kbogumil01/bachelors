package com.dispatcher.server.dispatcherServer.services;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Date;

@Service
public class AuthService {
    private TokenService tokenService;
    private byte[] kluczBytes;

    @Autowired
    public AuthService(TokenService tokenService) throws NoSuchAlgorithmException {
        this.tokenService= tokenService;
        this.kluczBytes = tokenService.klucz.getBytes(StandardCharsets.UTF_8);
    }

    public boolean logoutUser(HttpServletRequest request){
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals("jwt")) {
                    String value = cookie.getValue();
                    try {
                        Claims claims = Jwts.parser().setSigningKey(kluczBytes).parseClaimsJws(value).getBody();
                        claims.setExpiration(new Date());
                        return true;
                    }catch(Exception e){
                        System.out.println(e);
                        return false;
                    }
                }
            }
        }
        return false;
    }


    public boolean authUser (HttpServletRequest request, String role, long id){//, long res_id) {
        Cookie[] cookies=request.getCookies();

        if(cookies != null){
            for(Cookie cookie: cookies) {
                if (cookie.getName().equals("jwt")) { //jeśli bedzie nasz token wsrod plikow cookie
                    String value=cookie.getValue();
                    try{
                        Claims claims=Jwts.parser().setSigningKey(kluczBytes).parseClaimsJws(value).getBody();
                        if(claims.get("role")!=null) {
                            String jwtRole = (String) claims.get("role");
                            int jwtId = (int)claims.get("id");
                            //int jwtResId=(int)claims.get("res_id");
                            if (jwtRole.equals(role) && jwtId==id){// && jwtResId==res_id) {
                                return true; //autoryzacja udana
                            }
                        }
                    }
                    catch(Exception e){
                        System.out.println(e);
                        return false;
                    }
                }
            }
        }
        return false;
    }
}
