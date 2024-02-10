package com.dispatcher.server.dispatcherServer.services;

import com.dispatcher.server.dispatcherServer.model.User;
import org.springframework.stereotype.Service;

import java.util.List;

public interface UserService {

    User createUser(User user, boolean permission);

    User authentication(User user);

    boolean setResourceToUser(long id,long res_id, long idFromResponse);

    boolean authorizeID(long userID, long patientID);

    String checkRole(long userID);

    List<User> getAllUsers();

    List<User> searchUsers(String searchQuery);

    long checkResource(long userToDelete);

    void deleteUser(long userToDelete);

    long findUserByHisResource(long resourceToDelete);

    void setNewResID(long userThatHasThisResource, long newResId);
}
