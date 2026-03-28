package com.house.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.house.model.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserRepository extends BaseMapper<User> {
}
