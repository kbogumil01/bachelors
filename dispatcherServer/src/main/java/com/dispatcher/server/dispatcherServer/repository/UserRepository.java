package com.dispatcher.server.dispatcherServer.repository;

import com.dispatcher.server.dispatcherServer.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {
    List<UserEntity> findByLogin(String login);
    //List<UserEntity> findById(long id);
    UserEntity findById(long id);

    @Query("SELECT u FROM UserEntity u WHERE u.res_id = :resId")
    UserEntity findByRes_id(@Param("resId") long res_id);

    @Query("SELECT u FROM UserEntity u WHERE u.login LIKE %:searchQuery%")
    List<UserEntity> findByLoginContaining(@Param("searchQuery") String searchQuery);
}
