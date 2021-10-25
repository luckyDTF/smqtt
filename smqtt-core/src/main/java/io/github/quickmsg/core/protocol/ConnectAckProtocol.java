package io.github.quickmsg.core.protocol;

import io.github.quickmsg.common.channel.MqttChannel;
import io.github.quickmsg.common.message.SmqttMessage;
import io.github.quickmsg.common.protocol.Protocol;
import io.netty.handler.codec.mqtt.MqttConnAckMessage;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import java.util.ArrayList;
import java.util.List;

/**
 * @author luxurong
 */
public class ConnectAckProtocol implements Protocol<MqttConnAckMessage> {


    private final static List<MqttMessageType> MESSAGE_TYPE_LIST = new ArrayList<>();

    static {
        MESSAGE_TYPE_LIST.add(MqttMessageType.CONNACK);
    }


    @Override
    public Mono<Void> parseProtocol(SmqttMessage<MqttConnAckMessage> smqttMessage, MqttChannel mqttChannel, ContextView contextView) {
        MqttMessage message = smqttMessage.getMessage();
        return mqttChannel.cancelRetry(MqttMessageType.CONNECT,-1);
    }

    @Override
    public List<MqttMessageType> getMqttMessageTypes() {
        return MESSAGE_TYPE_LIST;
    }


}
