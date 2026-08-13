package com.reto.order.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Registro inmutable de cada cambio de estado de un pedido. Permite
 * reconstruir la línea de tiempo completa de un pedido (requerimiento de
 * "consultar su historial").
 */
@Entity
@Table(name = "order_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 20)
    private OrderStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20)
    private OrderStatus newStatus;

    @Column(name = "reason")
    private String reason;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(name = "trace_id")
    private String traceId;

    @PrePersist
    public void onCreate() {
        this.changedAt = Instant.now();
    }
}
