package com.reto.order.repository;

import com.reto.order.domain.OrderHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderHistoryRepository extends JpaRepository<OrderHistory, UUID> {
    List<OrderHistory> findByOrderIdOrderByChangedAtAsc(UUID orderId);
}
