package com.dispatcher.server.dispatcherServer.component;

import com.dispatcher.server.dispatcherServer.entity.UserEntity;
import com.dispatcher.server.dispatcherServer.repository.UserRepository;
import com.dispatcher.server.dispatcherServer.services.PasswordService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AdminAccount {
    @Autowired
    UserRepository userRepository;
    @Autowired
    PasswordService passwordService;

    @PostConstruct
    public void onStartup() {
        if (userRepository.findByLogin("admin").isEmpty()){
            UserEntity userEntity=new UserEntity();
            userEntity.setLogin("admin");
            userEntity.setPassword(passwordService.secure("admin"));
            userEntity.setRole("admin");
            userRepository.save(userEntity);
        }else{
            System.out.println("detected admin account");
        }
    }
}
