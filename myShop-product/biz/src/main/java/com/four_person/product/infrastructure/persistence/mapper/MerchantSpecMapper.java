package com.four_person.product.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.four_person.product.domain.model.MerchantSpec;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MerchantSpecMapper extends BaseMapper<MerchantSpec> {
}
