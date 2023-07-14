package no.nav.syfo.metrics

import io.prometheus.client.Counter
import io.prometheus.client.Histogram

const val METRICS_NS = "smregister"

val SYKMELDING_DUPLIKAT_COUNTER: Counter =
    Counter.build()
        .name(METRICS_NS)
        .name("sykmelding_kafka_duplikat")
        .help("Counts antall duplikate sykmeldinger lest fra kafka")
        .register()

val MESSAGE_STORED_IN_DB_COUNTER: Counter =
    Counter.build()
        .namespace(METRICS_NS)
        .name("message_stored_in_db_count")
        .help("Counts the number of messages stored in db")
        .register()

val HTTP_HISTOGRAM: Histogram =
    Histogram.Builder()
        .labelNames("path")
        .name("requests_duration_seconds")
        .help("http requests durations for incoming requests in seconds")
        .register()

val NYTT_FNR_COUNTER: Counter =
    Counter.build()
        .namespace(METRICS_NS)
        .name("nytt_fnr_count")
        .help("Antall endrede fnr som har blitt oppdatert i registeret")
        .register()

val APP_ID_PATH_COUNTER: Counter =
    Counter.build()
        .namespace(METRICS_NS)
        .name("app_name_count")
        .labelNames("team", "app_name", "path")
        .help("Antall RESt-kall som har blitt gjort med ulike APPs")
        .register()
