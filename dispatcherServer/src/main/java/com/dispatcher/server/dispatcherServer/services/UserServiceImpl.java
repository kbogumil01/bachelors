package com.dispatcher.server.dispatcherServer.services;

import com.dispatcher.server.dispatcherServer.entity.UserEntity;
import com.dispatcher.server.dispatcherServer.model.User;
import com.dispatcher.server.dispatcherServer.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService{
    UserRepository userRepository;
    private final PasswordService passwordService;

    public UserServiceImpl(UserRepository userRepository, PasswordService passwordServie) {
        this.userRepository = userRepository;
        this.passwordService = passwordServie;
    }

    @Override
    public User createUser(User user, boolean permission) {
        if(userRepository.findByLogin(user.getLogin()).isEmpty()){ //jeżeli login nie jest zajęty
            UserEntity userEntity=new UserEntity();
            userEntity.setLogin(user.getLogin());
            if(user.getRole().equals("patient") || permission) {
                userEntity.setRole(user.getRole());
            }else{
                return null;
            }
            userEntity.setId(user.getId());
            userEntity.setRes_id(0);
            userEntity.setPassword(passwordService.secure(user.getPassword())); //metoda secure hashuje alg. argon5
            userRepository.save(userEntity);
            user.setPassword("***"); //żeby w odpowiedzi nie było hasła
            return user;
        }else{
            return null;
        }
    }

    @Override
    public User authentication(User user) {
        if(!userRepository.findByLogin(user.getLogin()).isEmpty()){ //jesli taki login istnieje w bazie
            String hash=userRepository.findByLogin(user.getLogin()).get(0).getPassword(); //bierzemy jego hash
            if(passwordService.validatePassword(hash,user.getPassword())){ //funkcja do sprawdzenia czy hasło sie zgadza
                // uwierzytelnianie się powiodło
                String role=userRepository.findByLogin(user.getLogin()).get(0).getRole(); //żeby serwer wiedział jaki typ usera sie zalogował
                long res_id=userRepository.findByLogin(user.getLogin()).get(0).getRes_id();
                long id=userRepository.findByLogin(user.getLogin()).get(0).getId();
                user.setRole(role);
                user.setId(id);
                user.setRes_id(res_id);
                return user;
            }else
            { //niepoprawne hasło
                return null;
            }
        }else{ //niepoprawny login
            return null;
        }
    }

    @Override
    public boolean setResourceToUser(long id, long res_id ,long idFromResponse) {
        UserEntity user = userRepository.findById(id);
        //System.out.println(user.get(0).getLogin());
        if(user.getRes_id()==res_id) { //sprawdzenie czy id zasobu z żądania zgadza się z tym w bazie (żeby nie majstrować przy cudzych pacjentach)
            user.setRes_id(idFromResponse);
            //System.out.println(user.getRes_id());
            userRepository.save(user);
            return true;
        }
        else{
            return false;
        }

    }

    @Override
    public boolean authorizeID(long userID, long patientID) {
        UserEntity user = userRepository.findById(userID);
        long resIdToCheck=user.getRes_id();
        if(resIdToCheck==patientID){
            return true;
        }else{
            return false;
        }
    }

    @Override
    public String checkRole(long userID) {
        UserEntity user = userRepository.findById(userID);
        String role=user.getRole();
        return role;
    }

    @Override
    public List<User> getAllUsers() {
        List<UserEntity> userEntities = userRepository.findAll();
        List<User> users = userEntities.stream().map(usr -> new User(
                usr.getId(),
                usr.getLogin(),
                null,
                usr.getRole(),
                usr.getRes_id()
        )).collect(Collectors.toList());
        return users;
    }

    @Override
    public List<User> searchUsers(String searchQuery) {
        List<UserEntity> userEntities = userRepository.findByLoginContaining(searchQuery);
        List<User> users = userEntities.stream().map(usr -> new User(
                usr.getId(),
                usr.getLogin(),
                null,
                usr.getRole(),
                usr.getRes_id()
        )).collect(Collectors.toList());
        return users;
    }

    @Override
    public long checkResource(long userToDelete) {
        UserEntity usr=userRepository.findById(userToDelete);
        if(usr!=null){
            long resId=usr.getRes_id();
            return resId;
        }else{
            return 0;
        }
    }

    @Override
    public void deleteUser(long userToDelete) {
        userRepository.deleteById(userToDelete);
    }

    @Override
    public long findUserByHisResource(long resourceToFind) {
        UserEntity usr=userRepository.findByRes_id(resourceToFind);
        if(usr!=null){
            long usrID=usr.getId();
            return usrID;
        }
        else{
            return 0;
        }
    }

    @Override
    public void setNewResID(long userThatHasThisResource, long newResId) {
        UserEntity user = userRepository.findById(userThatHasThisResource); // Pobierz użytkownika lub null, jeśli nie istnieje
        if (user != null) {
            user.setRes_id(newResId); // Zmień pole res_id w obiekcie użytkownika
            userRepository.save(user); // Zapisz zmiany w bazie danych
        }
    }
}

