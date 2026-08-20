package top.foxball.cartask.authentication

/** 避免在认证模型中混用普通业务文本和凭据/token 文本。 */
typealias AccessTokenValue = kotlin.String
typealias CredentialValue = kotlin.String
