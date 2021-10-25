package io.github.quickmsg.core.spi;

import io.github.quickmsg.common.integrate.channel.ChannelRegistry;
import io.github.quickmsg.common.channel.MqttChannel;
import io.github.quickmsg.common.enums.ChannelStatus;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author luxurong
 */
@Slf4j
public class DefaultChannelRegistry implements ChannelRegistry {


    private final Map<String, MqttChannel> channelMap = new ConcurrentHashMap<>();


    public DefaultChannelRegistry() {
    }

    @Override
    public void startUp(Map<Object,Object> environmentMap) {

    }

    @Override
    public void close(MqttChannel mqttChannel) {
        Optional.ofNullable(mqttChannel.getClientIdentifier())
                .ifPresent(channelMap::remove);
    }

    @Override
    public void registry(String clientIdentifier, MqttChannel mqttChannel) {
        channelMap.put(clientIdentifier, mqttChannel);
    }

    @Override
    public boolean exists(String clientIdentifier) {
        return channelMap.containsKey(clientIdentifier) && channelMap.get(clientIdentifier).getStatus() == ChannelStatus.ONLINE;
    }

    @Override
    public MqttChannel get(String clientIdentifier) {
        return channelMap.get(clientIdentifier);
    }

    @Override
    public Integer counts() {
        return channelMap.size();
    }

    @Override
    public Collection<MqttChannel> getChannels() {
        return channelMap.values();
    }
}
