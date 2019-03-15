package no.nav.syfo.metrics

import io.prometheus.client.Counter
import io.prometheus.client.Summary

const val METRICS_NS = "syfosmregister"

val MESSAGE_STORED_IN_DB_COUNTER: Counter = Counter.build()
        .namespace(METRICS_NS)
        .name("message_stored_in_db_count")
        .help("Counts the number of messages stored in db")
        .register()

val NETWORK_CALL_SUMMARY: Summary = Summary.Builder()
        .namespace(METRICS_NS)
        .name("network_call_summary")
        .labelNames("http_endpoint")
        .help("Summary for networked call times")
        .register()