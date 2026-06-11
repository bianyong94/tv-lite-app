package com.globalvision.tvlite.core.network

object TvApiConfig {
    const val BASE_URL = "https://dbe6vejb.qlpru.cn/api/v1"
    const val CLIENT_VERSION = "3100"
    const val ACCEPT_LANGUAGE = "en"
    const val CLIENT_SETTING = """{"pure-mode":0}"""

    val PUBLIC_KEY_PEM = """
        -----BEGIN RSA PUBLIC KEY-----
        MIIBCgKCAQEA02F/kPg5A2NX4qZ5JSns+bjhVMCC6JbTiTKpbgNgiXU+Kkorg6Dj76gS68gB8llhbUKCXjIdygnHPrxVHWfzmzisq9P9awmXBkCk74Skglx2LKHa/mNz9ivg6YzQ5pQFUEWS0DfomGBXVtqvBlOXMCRxp69oWaMsnfjnBV+0J7vHbXzUIkqBLdXSNfM9Ag5qdRDrJC3CqB65EJ3ARWVzZTTcXSdMW9i3qzEZPawPNPe5yPYbMZIoXLcrqvEZnRK1oak67/ihf7iwPJqdc+68ZYEmmdqwunOvRdjq89fQMVelmqcRD9RYe08v+xDxG9Co9z7hcXGTsUquMxkh29uNawIDAQAB
        -----END RSA PUBLIC KEY-----
    """.trimIndent()

    const val SIGN_KEY = "635a580fcb5dc6e60caa39c31a7bde48"
    const val AES_KEY = "e6d5de5fcc51f53d"
    const val AES_IV = "2f13eef7dfc6c613"
}
