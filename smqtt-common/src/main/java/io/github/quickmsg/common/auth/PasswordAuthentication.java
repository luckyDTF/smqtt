package io.github.quickmsg.common.auth;

import io.github.quickmsg.common.StartUp;
import io.github.quickmsg.common.spi.loader.DynamicLoader;

/**
 * @author luxurong
 */
public interface PasswordAuthentication extends StartUp {

    PasswordAuthentication INSTANCE = DynamicLoader.findFirst(PasswordAuthentication.class).orElse(null);

    /**
     * 认证接口
     *
     * @param userName        用户名称
     * @param passwordInBytes 密钥
     * @param clientIdentifier 设备标志
     * @return 布尔
     */
    boolean auth(String userName, byte[] passwordInBytes, String clientIdentifier);

}
