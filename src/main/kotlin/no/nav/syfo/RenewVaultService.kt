package no.nav.syfo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.db.VaultCredentialService
import no.nav.syfo.util.util.Unbounded
import no.nav.syfo.vault.Vault

class RenewVaultService(private val vaultCredentialService: VaultCredentialService, private val applicationState: ApplicationState) {
    fun startRenewTasks() {
        GlobalScope.launch(Dispatchers.Unbounded) {
            try {
                Vault.renewVaultTokenTask(applicationState)
            } catch (e: Exception) {
                log.error("Noe gikk galt ved fornying av vault-token", e.message)
            } finally {
                applicationState.ready = false
            }
        }

        GlobalScope.launch(Dispatchers.Unbounded) {
            try {
                vaultCredentialService.runRenewCredentialsTask(applicationState)
            } catch (e: Exception) {
                log.error("Noe gikk galt ved fornying av vault-credentials", e.message)
            } finally {
                applicationState.ready = false
            }
        }
    }
}
