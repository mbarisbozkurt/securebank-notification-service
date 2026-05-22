package com.securebank.notification.repository;

import com.securebank.notification.model.NotificationRecord;
import com.securebank.notification.model.NotificationStatus;
import com.securebank.notification.model.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRecordRepository extends JpaRepository<NotificationRecord, Long> {

    List<NotificationRecord> findByStatusOrderByCreatedAtAsc(NotificationStatus status);

    Optional<NotificationRecord> findByTransactionIdAndRecipientEmailAndType(
            Long transactionId,
            String recipientEmail,
            NotificationType type
    );
}
