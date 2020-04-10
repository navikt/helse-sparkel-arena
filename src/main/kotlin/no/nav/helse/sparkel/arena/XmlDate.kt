package no.nav.helse.sparkel.arena

import java.time.LocalDate
import java.time.ZoneId
import java.util.*
import javax.xml.datatype.DatatypeFactory
import javax.xml.datatype.XMLGregorianCalendar

private val datatypeFactory = DatatypeFactory.newInstance()

internal fun LocalDate.asXmlGregorianCalendar() =
    datatypeFactory.newXMLGregorianCalendar(GregorianCalendar.from(this.atStartOfDay(ZoneId.systemDefault())))

internal fun XMLGregorianCalendar.asLocalDate() = LocalDate.of(year, month, day)
