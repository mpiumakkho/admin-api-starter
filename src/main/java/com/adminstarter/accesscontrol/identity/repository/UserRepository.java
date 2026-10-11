package com.adminstarter.accesscontrol.identity.repository;

import com.adminstarter.accesscontrol.identity.entity.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** Newest first: ids are UUID v7, so they sort by creation */
    List<User> findAllByOrderByIdDesc();

    /** Ignoring case, the way ux_users_email (on lower(email)) keeps emails unique */
    @Query("select u from User u where lower(u.email) = lower(:email)")
    Optional<User> findByEmail(String email);

    @Query("select count(u) > 0 from User u where lower(u.email) = lower(:email) and u.id <> :exceptId")
    boolean emailTakenByOther(String email, UUID exceptId);

    @Query("select count(u) > 0 from User u where lower(u.email) = lower(:email)")
    boolean emailTaken(String email);

    boolean existsByBranchId(UUID branchId);
}
