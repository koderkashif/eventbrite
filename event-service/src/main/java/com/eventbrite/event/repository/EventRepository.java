package com.eventbrite.event.repository;

import com.eventbrite.event.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * JpaSpecificationExecutor enables dynamic filtering (category AND/OR city AND/OR search)
 * via Specifications built in the service layer - instead of one repository method
 * per filter combination.
 */
public interface EventRepository extends JpaRepository<Event, Long>, JpaSpecificationExecutor<Event> {
}
