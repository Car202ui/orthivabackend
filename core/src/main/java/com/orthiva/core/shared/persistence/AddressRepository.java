package com.orthiva.core.shared.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressRepository extends JpaRepository<Address, UUID> {
}
