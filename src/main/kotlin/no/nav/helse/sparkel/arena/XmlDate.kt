package no.nav.helse.sparkel.arena

import java.time.LocalDate
import java.time.ZoneId
import java.util.*
import javax.xml.datatype.DatatypeFactory

private val datatypeFactory = DatatypeFactory.newInstance()

internal fun LocalDate.asXmlGregorianCalendar() =
    datatypeFactory.newXMLGregorianCalendar(GregorianCalendar.from(this.atStartOfDay(ZoneId.systemDefault())))
