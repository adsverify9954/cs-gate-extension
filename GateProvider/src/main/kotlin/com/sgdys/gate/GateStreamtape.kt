package com.sgdys.gate

/**
 * Streamtape resolver — worker ke /stx/{id} endpoint ka use karta hai.
 * Worker API se fresh dlticket→dl link banata hai aur 302 redirect deta hai —
 * device apne IP se video fetch karta hai (Streamtape IP-lock satisfy).
 */
object GateStreamtape {
    private val ID_RE = Regex("/(?:e|v|embed)/([A-Za-z0-9]+)", RegexOption.IGNORE_CASE)
    private const val WORKER = "https://my-anime-gate.unityads9954.workers.dev"

    fun resolve(input: String): String? {
        val id = ID_RE.find(input)?.groupValues?.get(1) ?: return null
        return "$WORKER/stx/$id"
    }
}
