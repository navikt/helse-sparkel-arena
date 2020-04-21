package no.nav.helse.sparkel.arena

import no.nav.tjeneste.virksomhet.meldekortutbetalingsgrunnlag.v1.binding.MeldekortUtbetalingsgrunnlagV1
import org.apache.cxf.ext.logging.LoggingFeature
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean
import org.apache.cxf.ws.addressing.WSAddressingFeature
import org.apache.cxf.ws.security.trust.STSClient
import javax.xml.namespace.QName

object MeldekortUtbetalingsgrunnlagV1Factory {

    private val ServiceClass = MeldekortUtbetalingsgrunnlagV1::class.java
    private val Wsdl = "wsdl/no/nav/tjeneste/virksomhet/meldekortUtbetalingsgrunnlag/v1/Binding.wsdl"
    private val Namespace = "http://nav.no/tjeneste/virksomhet/meldekortUtbetalingsgrunnlag/v1/Binding"
    private val ServiceName = QName(Namespace, "MeldekortUtbetalingsgrunnlag_v1")
    private val EndpointName = QName(Namespace, "meldekortUtbetalingsgrunnlag_v1Port")

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
