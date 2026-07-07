package com.forbidad4tieba.hook.symbol.model

data class PrivateMessageSymbols(
    val readReceipt: PrivateReadReceiptSymbolsGroup = PrivateReadReceiptSymbolsGroup(),
    val tab: MessageTabSymbolsGroup = MessageTabSymbolsGroup(),
)

data class PrivateReadReceiptSymbolsGroup(
    val model: PrivateReadReceiptModelSymbolsGroup = PrivateReadReceiptModelSymbolsGroup(),
    val message: PrivateReadReceiptMessageSymbolsGroup = PrivateReadReceiptMessageSymbolsGroup(),
    val request: PrivateReadReceiptRequestSymbolsGroup = PrivateReadReceiptRequestSymbolsGroup(),
    val response: PrivateReadReceiptResponseSymbolsGroup = PrivateReadReceiptResponseSymbolsGroup(),
    val page: PrivateReadReceiptPageSymbolsGroup = PrivateReadReceiptPageSymbolsGroup(),
    val localAccount: PrivateReadReceiptLocalAccountSymbolsGroup = PrivateReadReceiptLocalAccountSymbolsGroup(),
)

data class PrivateReadReceiptModelSymbolsGroup(
    val privateReadReceiptModelClass: String? = null,
    val privateReadReceiptModelReadDispatchMethod: String? = null,
    val privateReadReceiptModelBaseClass: String? = null,
    val privateReadReceiptModelDataField: String? = null,
)

data class PrivateReadReceiptMessageSymbolsGroup(
    val privateReadReceiptMessageManagerClass: String? = null,
    val privateReadReceiptMessageManagerGetInstanceMethod: String? = null,
    val privateReadReceiptMessageManagerGetSocketClientMethod: String? = null,
    val privateReadReceiptMessageSendMethod: String? = null,
    val privateReadReceiptMessageBaseClass: String? = null,
    val privateReadReceiptSocketClientClass: String? = null,
    val privateReadReceiptSocketDuplicateCheckMethod: String? = null,
)

data class PrivateReadReceiptRequestSymbolsGroup(
    val privateReadReceiptRequestClass: String? = null,
    val privateReadReceiptRequestMsgIdField: String? = null,
    val privateReadReceiptRequestToUidField: String? = null,
)

data class PrivateReadReceiptResponseSymbolsGroup(
    val privateReadReceiptCommitResponseClass: String? = null,
    val privateReadReceiptProcessAckMethod: String? = null,
    val privateReadReceiptResponseErrorMethod: String? = null,
)

data class PrivateReadReceiptPageSymbolsGroup(
    val privateReadReceiptPageDataClass: String? = null,
    val privateReadReceiptPageDataChatListMethod: String? = null,
    val privateReadReceiptChatMessageClass: String? = null,
    val privateReadReceiptChatMessageMsgIdMethod: String? = null,
    val privateReadReceiptChatMessageUserIdMethod: String? = null,
    val privateReadReceiptChatMessageLocalDataMethod: String? = null,
)

data class PrivateReadReceiptLocalAccountSymbolsGroup(
    val privateReadReceiptLocalDataClass: String? = null,
    val privateReadReceiptLocalDataStatusMethod: String? = null,
    val privateReadReceiptAccountClass: String? = null,
    val privateReadReceiptCurrentAccountMethod: String? = null,
)

data class MessageTabSymbolsGroup(
    val msgTabLocateToTabMethod: String? = null,
    val msgTabContainerSelectMethod: String? = null,
    val msgTabContainerExtDataField: String? = null,
)
