package com.readme.group;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface GroupInviteRepository extends JpaRepository<GroupInvite, Long> {

	Optional<GroupInvite> findByTokenHash(String tokenHash);

}
