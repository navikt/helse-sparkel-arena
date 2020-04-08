package no.nav.helse.sparkel.arena

import no.nav.tjeneste.virksomhet.ytelseskontrakt.v3.YtelseskontraktV3
import org.apache.cxf.ext.logging.LoggingFeature
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean
import org.apache.cxf.ws.addressing.WSAddressingFeature
import org.apache.cxf.ws.security.trust.STSClient
import javax.xml.namespace.QName

object YtelseskontraktFactory {

    private val ServiceClass = YtelseskontraktV3::class.java
    private val Wsdl = "wsdl/tjenestespesifikasjon/no/nav/tjeneste/virksomhet/ytelseskontrakt/v3/Binding.wsdl"
    private val Namespace = "http://nav.no/tjeneste/virksomhet/ytelseskontrakt/v3/Binding"
    private val ServiceName = QName(Namespace, "Ytelseskontrakt_v3")
    private val EndpointName = QName(Namespace, "Ytelseskontrakt_v3Port")

    fun create(endpointUrl: String, stsClient: STSClient) =
            JaxWsProxyFactoryBean().apply {
                address = endpointUrl
                wsdlURL = Wsdl
                serviceName = ServiceName
                endpointName = EndpointName
                serviceClass = ServiceClass
                this.features.addAll(listOf(WSAddressingFeature(), LoggingFeature()))
            }.create(ServiceClass).apply { stsClient.configureFor(this) }
}
