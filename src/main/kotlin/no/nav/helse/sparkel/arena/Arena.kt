package no.nav.helse.sparkel.arena

import com.fasterxml.jackson.databind.JsonNode
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.tjeneste.virksomhet.ytelseskontrakt.v3.YtelseskontraktV3
import no.nav.tjeneste.virksomhet.ytelseskontrakt.v3.informasjon.ytelseskontrakt.WSPeriode
import no.nav.tjeneste.virksomhet.ytelseskontrakt.v3.meldinger.WSHentYtelseskontraktListeRequest
import org.slf4j.LoggerFactory

internal class Arena(
    rapidsConnection: RapidsConnection,
    private val ytelseskontraktV3: YtelseskontraktV3,
    private val ytelsetype: String,
    private val behov: String
) :
    River.PacketListener {
    private companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
        private val log = LoggerFactory.getLogger(Arena::class.java)
    }

    init {
        River(rapidsConnection).apply {
            validate { it.requireContains("@behov", behov) }
            validate { it.forbid("@løsning") }
            validate { it.requireKey("@id") }
            validate { it.requireKey("fødselsnummer") }
            validate { it.requireKey("vedtaksperiodeId") }
            validate { it.require("periodeFom", JsonNode::asLocalDate) }
            validate { it.require("periodeTom", JsonNode::asLocalDate) }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
        packet.info(
            "løser behov {} for {}",
            keyValue("id", packet["@id"].asText()),
            keyValue("vedtaksperiodeId", packet["vedtaksperiodeId"].asText())
        )
        try {
            ytelseskontraktV3.hentYtelseskontraktListe(WSHentYtelseskontraktListeRequest().apply {
                personidentifikator = packet["fødselsnummer"].asText()
                periode = WSPeriode().apply {
                    fom = packet["periodeFom"].asLocalDate().asXmlGregorianCalendar()
                    tom = packet["periodeTom"].asLocalDate().asXmlGregorianCalendar()
                }
            }).ytelseskontraktListe
                .filter { ytelsetype == it.ytelsestype }
                .flatMap {
                    it.ihtVedtak
                    .filter { it.vedtaksperiode.fom != null && it.vedtaksperiode.tom != null }
                    .map { mapOf(
                        "fom" to it.vedtaksperiode.fom.asLocalDate(),
                        "tom" to it.vedtaksperiode.tom.asLocalDate()
                    ) }
                }
                .also { packet["@løsning"] = mapOf(behov to it) }
            context.send(packet.toJson()).also {
                sikkerlogg.info(
                    "sender {} som {}",
                    keyValue("id", packet["@id"].asText()),
                    packet.toJson()
                )
            }
        } catch (err: Exception) {
            packet.error("feil ved behov {} for {}: ${err.message}",
                keyValue("id", packet["@id"].asText()),
                keyValue("vedtaksperiodeId", packet["vedtaksperiodeId"].asText()),
                err
            )
        }
    }

    private fun JsonMessage.info(format: String, vararg args: Any) {
        log.info(format, *args)
        sikkerlogg.info(format, *args)
    }

    private fun JsonMessage.error(format: String, vararg args: Any) {
        log.error(format, *args)
        sikkerlogg.error(format, *args)
    }
}
