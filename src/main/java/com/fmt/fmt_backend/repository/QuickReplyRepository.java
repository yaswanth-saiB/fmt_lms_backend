package com.fmt.fmt_backend.repository;

import com.fmt.fmt_backend.entity.QuickReply;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QuickReplyRepository extends JpaRepository<QuickReply, UUID> {
    List<QuickReply> findAllByOrderByTitleAsc();
}
