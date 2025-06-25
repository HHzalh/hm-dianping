package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;

/**
 * <p>
 * 服务类
 * </p>
 */
public interface IShopTypeService extends IService<ShopType> {


    /**
     * 查询店铺业务类型
     *
     * @return
     */
    Result queryList();
}
