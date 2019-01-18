package no.nav.syfo.vault

import com.fasterxml.jackson.annotation.JsonProperty

class AuthenticationDTO {
    var auth: Auth? = null

    class Auth {
        @JsonProperty("client_token")
        var clientToken: String? = null
    }
}