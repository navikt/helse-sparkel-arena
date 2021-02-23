package no.nav.helse.sparkel.arena

import com.fasterxml.jackson.databind.JsonNode
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.rapids_rivers.*
import no.nav.helse.sparkel.arena.asLocalDate
import no.nav.tjeneste.virksomhet.meldekortutbetalingsgrunnlag.v1.binding.MeldekortUtbetalingsgrunnlagV1
import no.nav.tjeneste.virksomhet.meldekortutbetalingsgrunnlag.v1.informasjon.Bruker
import no.nav.tjeneste.virksomhet.meldekortutbetalingsgrunnlag.v1.informasjon.Periode
import no.nav.tjeneste.virksomhet.meldekortutbetalingsgrunnlag.v1.informasjon.Tema
import no.nav.tjeneste.virksomhet.meldekortutbetalingsgrunnlag.v1.meldinger.FinnMeldekortUtbetalingsgrunnlagListeRequest
import no.nav.tjeneste.virksomhet.ytelseskontrakt.v3.YtelseskontraktV3
import no.nav.tjeneste.virksomhet.ytelseskontrakt.v3.informasjon.ytelseskontrakt.WSPeriode
import no.nav.tjeneste.virksomhet.ytelseskontrakt.v3.meldinger.WSHentYtelseskontraktListeRequest
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.time.LocalDate

internal class Arena(
    rapidsConnection: RapidsConnection,
    private val ytelseskontraktV3: YtelseskontraktV3,
    private val meldekortUtbetalingsgrunnlagV1: MeldekortUtbetalingsgrunnlagV1,
    private val ytelsetype: String,
    private val tematype: String,
    private val behov: String
) :
    River.PacketListener {
    private companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
        private val log = LoggerFactory.getLogger(Arena::class.java)
    }

    init {
        River(rapidsConnection).apply {
            validate { it.demandAll("@behov", listOf(behov)) }
            validate { it.rejectKey("@løsning") }
            validate { it.requireKey("@id") }
            validate { it.requireKey("fødselsnummer") }
            validate { it.requireKey("vedtaksperiodeId") }
            validate { it.require("$behov.periodeFom", JsonNode::asLocalDate) }
            validate { it.require("$behov.periodeTom", JsonNode::asLocalDate) }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        sikkerlogg.error("forstod ikke $behov:\n${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val behovId = packet["@id"].asText()
        val vedtaksperiodeId = packet["vedtaksperiodeId"].asText()
        withMDC(mapOf(
            "behovId" to behovId,
            "vedtaksperiodeId" to vedtaksperiodeId
        )) {
            try {
                packet.info("løser behov {} for {}", keyValue("id", behovId), keyValue("vedtaksperiodeId", vedtaksperiodeId))
                håndter(packet, context)
            } catch (err: Exception) {
                packet.warn("feil ved behov {} for {}: ${err.message}", keyValue("id", behovId), keyValue("vedtaksperiodeId", vedtaksperiodeId), err)
            }
        }
    }

    private fun håndter(packet: JsonMessage, context: MessageContext) {
        val fødselsnummer = packet["fødselsnummer"].asText()
        val søkevindu = packet["$behov.periodeFom"].asLocalDate() to packet["$behov.periodeTom"].asLocalDate()
        packet["@løsning"] = mapOf(
            behov to mapOf(
                "vedtaksperioder" to hentYtelsekontrakt(
                    fødselsnummer = fødselsnummer,
                    søkevindu = søkevindu
                ),
                "meldekortperioder" to hentMeldekortUtbetalingsgrunnlag(
                    fødselsnummer = fødselsnummer,
                    søkevindu = søkevindu,
                    tema = Tema().apply {
                        value = tematype
                    }
                )
            )
        )
        context.publish(packet.toJson()).also {
            sikkerlogg.info("sender {} som {}", keyValue("id", packet["@id"].asText()), packet.toJson())
        }
    }

    private fun hentYtelsekontrakt(fødselsnummer: String, søkevindu: Pair<LocalDate, LocalDate>) =
        ytelseskontraktV3.hentYtelseskontraktListe(WSHentYtelseskontraktListeRequest().apply {
            personidentifikator = fødselsnummer
            periode = WSPeriode().apply {
                fom = søkevindu.first.asXmlGregorianCalendar()
                tom = søkevindu.second.asXmlGregorianCalendar()
            }
        }).ytelseskontraktListe
            .filter { ytelsetype == it.ytelsestype }
            .flatMap {
                it.ihtVedtak
                    .filter { it.periodetypeForYtelse != "Stans" }
                    .filter { it.vedtaksperiode.fom != null }
                    .map { mapOf(
                        "fom" to it.vedtaksperiode.fom.asLocalDate(),
                        "tom" to (it.vedtaksperiode.tom?.asLocalDate() ?: LocalDate.now())
                    ) }
            }

    private fun hentMeldekortUtbetalingsgrunnlag(fødselsnummer: String, søkevindu: Pair<LocalDate, LocalDate>, tema: Tema) =
        meldekortUtbetalingsgrunnlagV1.finnMeldekortUtbetalingsgrunnlagListe(FinnMeldekortUtbetalingsgrunnlagListeRequest().apply {
            ident = Bruker().apply {
                ident = fødselsnummer
            }
            periode = Periode().apply {
                fom = søkevindu.first.asXmlGregorianCalendar()
                tom = søkevindu.second.asXmlGregorianCalendar()
            }
            with (temaListe) {
                add(tema)
            }
        }).meldekortUtbetalingsgrunnlagListe
            .flatMap {
                it.vedtakListe.flatMap {
                    it.meldekortListe.map {
                        mapOf(
                            "fom" to it.meldekortperiode.fom.asLocalDate(),
                            "tom" to it.meldekortperiode.tom.asLocalDate(),
                            "dagsats" to it.dagsats,
                            "beløp" to it.beloep,
                            "utbetalingsgrad" to it.utbetalingsgrad
                        )
                    }
                }
            }

    private fun withMDC(context: Map<String, String>, block: () -> Unit) {
        val contextMap = MDC.getCopyOfContextMap() ?: emptyMap()
        try {
            MDC.setContextMap(contextMap + context)
            block()
        } finally {
            MDC.setContextMap(contextMap)
        }
    }

    private fun JsonMessage.info(format: String, vararg args: Any) {
        log.info(format, *args)
        sikkerlogg.info(format, *args)
    }

    private fun JsonMessage.warn(format: String, vararg args: Any) {
        log.warn(format, *args)
        sikkerlogg.warn(format, *args)
    }
}
