package no.nav.helse.sparkel.arena

import no.nav.helse.rapids_rivers.RapidApplication
import java.nio.file.Files
import java.nio.file.Paths

fun main() {
    val env = System.getenv()
    RapidApplication.create(env).apply {
        val stsClientWs = stsClient(
            stsUrl = env.getValue("STS_URL"),
            username = "/var/run/secrets/nais.io/service_user/username".readFile(),
            password = "/var/run/secrets/nais.io/service_user/password".readFile()
        )
        val ytelseskontraktV3 = YtelseskontraktFactory.create(env.getValue("YTELSESKONTRAKT_BASE_URL"), stsClientWs)
        val meldekortUtbetalingsgrunnlagV1 = MeldekortUtbetalingsgrunnlagV1Factory.create(env.getValue("MELDEKORT_UTBETALINGSGRUNNLAG_ENDPOINTURL"), stsClientWs)
        Arena(this, ytelseskontraktV3, meldekortUtbetalingsgrunnlagV1, "Dagpenger", "DAG", "Dagpenger")
        Arena(this, ytelseskontraktV3, meldekortUtbetalingsgrunnlagV1, "Arbeidsavklaringspenger", "AAP", "Arbeidsavklaringspenger")
    }.start()
}

private fun String.readFile() = Files.readString(Paths.get(this))

