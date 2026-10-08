package com.adminstarter.accesscontrol.settings.repository;

import com.adminstarter.accesscontrol.settings.entity.AdminSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminSettingsRepository extends JpaRepository<AdminSettings, Short> {}
