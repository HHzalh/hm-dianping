package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 发送手机验证码
     */
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            //2.不符合，返回错误信息
            return Result.fail("手机号格式错误");
        }

        //符合,3.生成验证码
        //引用胡图工具包，生成验证码
        String code = RandomUtil.randomNumbers(6);
        //session.setAttribute(VariousConstants.VERIFICATION_CODE, code);  保存验证码到session中

        //4.保存验证码到redis,并设置有效期 2分钟
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code, RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);

        //5.发送验证码（暂时不做真正的发送验证码，需要调用阿里云短信平台等）
        log.debug("发送验证码成功，验证码:{}", code);
        //返回OK
        return Result.ok();
    }

    /**
     * 登录功能
     *
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1.校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            //2.不符合，返回错误信息
            return Result.fail("手机号格式错误");
        }
        //3.从redis中获取验证码，并校验
        //Object cacheCode = session.getAttribute(VariousConstants.VERIFICATION_CODE); 从session中获取验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {
            //不一致，报错
            return Result.fail("验证码错误");
        }

        //4.一致，判断手机号是否存在 select * from tb_user where phone = ?
        User user = query().eq("phone", phone).one();
        //5.判断用户是否存在
        if (user == null) {
            //6.不存在，创建新用户并保存
            user = createUserWithPhone(phone);
        }

        //7.保存用户信息到redis中

        //7.1随机生成token，作为登录令牌

        //引入胡图工具类的UUID
        String token = UUID.randomUUID().toString(true);
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        //7.2 利用胡图工具类，将UserDto对象转换为HashMap存储


        //stringRedisTemplate 中的 putAll 方法在存储数据到 Redis 时，会将所有的值都序列化为字符串。
        // 因此，如的 Map 中包含 Long 类型的值，需要先将它们转换为字符串，以避免类型转换异常。

        // 创建一个空的HashMap，用于存储转换后的用户信息
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                // 使用CopyOptions.create()创建一个CopyOptions对象，用于配置转换选项
                CopyOptions.create()
                        // 设置忽略空值，即在转换过程中不将空值放入Map中
                        .setIgnoreNullValue(true)
                        // 设置字段值编辑器，用于在转换过程中对字段值进行自定义处理
                        // 这里使用了一个Lambda表达式，将字段值转换为字符串
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));


        //引入胡图 BeanUtil.copyProperties 用户信息脱敏
        //session.setAttribute(VariousConstants.USER, BeanUtil.copyProperties(user, UserDTO.class));

        //7.3存储
        stringRedisTemplate.opsForHash().putAll(RedisConstants.LOGIN_USER_KEY + token, userMap);
        //7.4设置token有效期 30分钟
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        //8.返回token
        return Result.ok(token);
    }

    /**
     * 用手机号注册新用户
     *
     * @param phone
     * @return
     */
    private User createUserWithPhone(String phone) {
        //1.创建用户
        User user = new User();
        user.setPhone(phone);
        //随机生成用户名
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
