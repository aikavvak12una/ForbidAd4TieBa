package com.forbidad4tieba.hook.symbol.model

data class SignInSymbols(
    val autoSignIn: AutoSignInSymbolsGroup = AutoSignInSymbolsGroup(),
)

data class AutoSignInSymbolsGroup(
    val nativeNetwork: AutoSignInNativeNetworkSymbolsGroup = AutoSignInNativeNetworkSymbolsGroup(),
    val hybridNativeProxy: AutoSignInHybridNativeProxySymbolsGroup =
        AutoSignInHybridNativeProxySymbolsGroup(),
)

data class AutoSignInNativeNetworkSymbolsGroup(
    val autoSignInNetworkClass: String? = null,
    val autoSignInNetworkConstructorSpec: String? = null,
    val autoSignInNetworkAddPostDataMethod: String? = null,
    val autoSignInNetworkPostNetDataMethod: String? = null,
    val autoSignInNetworkSetNeedTbsMethod: String? = null,
    val autoSignInNetworkSetNeedSigMethod: String? = null,
    val autoSignInTbConfigClass: String? = null,
    val autoSignInServerAddressField: String? = null,
    val autoSignInCoreApplicationClass: String? = null,
    val autoSignInCurrentAccountMethod: String? = null,
)

data class AutoSignInHybridNativeProxySymbolsGroup(
    val autoSignInHybridProxyClass: String? = null,
    val autoSignInHybridProxyConstructorSpec: String? = null,
    val autoSignInHybridJsBridgeClass: String? = null,
    val autoSignInHybridNativeNetworkProxyMethod: String? = null,
    val autoSignInHybridTaskClass: String? = null,
    val autoSignInHybridTaskConstructorSpec: String? = null,
    val autoSignInHybridTaskDoInBackgroundMethod: String? = null,
)
