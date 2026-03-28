package com.house.repo;

import com.house.model.Handler;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HandlerRepository extends JpaRepository<Handler, Long> {
  List<Handler> findByStatusOrderByIdAsc(Integer status);
}
