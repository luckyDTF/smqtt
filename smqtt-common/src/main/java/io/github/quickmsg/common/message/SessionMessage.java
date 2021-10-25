package io.github.quickmsg.common.message;

import io.github.quickmsg.common.channel.MqttChannel;
import io.github.quickmsg.common.utils.MessageUtils;
import io.github.quickmsg.common.utils.MqttMessageUtils;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttPublishVariableHeader;
import io.netty.handler.codec.mqtt.MqttQoS;
import lombok.Builder;
import lombok.Data;

/**
 * @author luxurong
 */

@Data
@Builder
public class SessionMessage {

    private int qos;

    private String topic;

    private byte[] body;

    private String clientIdentifier;

    private boolean retain;

    public static SessionMessage of(String clientIdentifier, MqttPublishMessage mqttPublishMessage) {
        MqttPublishVariableHeader publishVariableHeader = mqttPublishMessage.variableHeader();
        return SessionMessage.builder()
                .clientIdentifier(clientIdentifier)
                .topic(publishVariableHeader.topicName())
                .qos(mqttPublishMessage.fixedHeader().qosLevel().value())
                .retain(mqttPublishMessage.fixedHeader().isRetain())
                .body(MessageUtils.copyByteBuf(mqttPublishMessage.payload()))
                .build();
    }

    public MqttPublishMessage toPublishMessage(MqttChannel mqttChannel) {
        return MqttMessageUtils.buildPub(
                false,
                MqttQoS.valueOf(this.qos),
                qos > 0 ? mqttChannel.generateMessageId() : 0,
                topic,
                PooledByteBufAllocator.DEFAULT.directBuffer().writeBytes(body));
    }

}
