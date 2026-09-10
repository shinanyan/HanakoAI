package `fun`.kirari.hanako.core.network

import `fun`.kirari.hanako.core.data.ModelProviderConfig
import `fun`.kirari.llm.core.ConnectionTestResult
import `fun`.kirari.llm.core.ProviderCatalog

internal class ProviderModelsApi(
    private val clientProvider: NetworkClientProvider = NetworkClientProvider()
) {
    private val coreApi = `fun`.kirari.llm.core.ProviderModelsApi(clientProvider)
    private val providerResolver = ProviderConfigResolver()

    suspend fun getCatalog(
        provider: ModelProviderConfig,
        trustAllHttpsCertificates: Boolean = false
    ): ProviderCatalog = coreApi.getCatalog(
        provider = providerResolver.resolve(provider),
        trustAllHttpsCertificates = trustAllHttpsCertificates
    )

    suspend fun testConnection(
        provider: ModelProviderConfig,
        trustAllHttpsCertificates: Boolean = false
    ): ConnectionTestResult = coreApi.testConnection(
        provider = providerResolver.resolve(provider),
        trustAllHttpsCertificates = trustAllHttpsCertificates
    )

}
