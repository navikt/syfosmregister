package no.nav.syfo.util

import com.ibm.mq.jms.MQConnectionFactory
import com.ibm.msg.client.wmq.WMQConstants
import com.ibm.msg.client.wmq.compat.base.internal.MQC
import no.nav.syfo.ApplicationConfig

fun connectionFactory(config: ApplicationConfig) = MQConnectionFactory().apply {
    hostName = config.mqHostname
    port = config.mqPort
    queueManager = config.mqGatewayName
    transportType = WMQConstants.WMQ_CM_CLIENT
    channel = config.mqChannelName
    ccsid = 1208
    setIntProperty(WMQConstants.JMS_IBM_ENCODING, MQC.MQENC_NATIVE)
    setIntProperty(WMQConstants.JMS_IBM_CHARACTER_SET, 1208)
}
