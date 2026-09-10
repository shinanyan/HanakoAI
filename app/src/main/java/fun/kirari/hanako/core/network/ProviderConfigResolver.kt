package `fun`.kirari.hanako.core.network

import `fun`.kirari.hanako.core.data.ModelProviderConfig
import `fun`.kirari.llm.core.ProviderConfig

/** Owns the provider -> transport configuration projection for every network API. */
internal class ProviderConfigResolver {
    fun resolve(provider: ModelProviderConfig): ProviderConfig = ProviderConfig(
        kind = provider.kind,
        baseUrl = provider.baseUrl,
        apiKey = provider.apiKey
    )
}
