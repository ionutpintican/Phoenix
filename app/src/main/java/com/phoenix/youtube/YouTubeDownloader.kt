package com.phoenix.youtube

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.downloader.Request as NpRequest
import org.schabi.newpipe.extractor.downloader.Response as NpResponse
import java.util.concurrent.TimeUnit

/**
 * The HTTP bridge NewPipeExtractor calls out through, backed by OkHttp. NewPipe hands us its own
 * [NpRequest] (method, url, headers, optional body) and expects an [NpResponse]; we translate both
 * ways. A 429 is surfaced as [ReCaptchaException] so the extractor can report a rate-limit / bot
 * check rather than treating the challenge page as real content.
 */
class YouTubeDownloader(private val client: OkHttpClient) : Downloader() {

    override fun execute(request: NpRequest): NpResponse {
        val httpMethod = request.httpMethod()
        val url = request.url()

        val builder = okhttp3.Request.Builder().url(url)
        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { builder.addHeader(name, it) }
        }
        if (request.headers()["User-Agent"] == null) {
            builder.header("User-Agent", USER_AGENT)
        }

        val dataToSend = request.dataToSend()
        val body = if (dataToSend != null) dataToSend.toRequestBody(null, 0, dataToSend.size) else null
        builder.method(httpMethod, body)

        client.newCall(builder.build()).execute().use { response ->
            if (response.code == 429) {
                throw ReCaptchaException("reCaptcha Challenge requested", url)
            }
            val responseBody = response.body?.string() ?: ""
            return NpResponse(
                response.code,
                response.message,
                response.headers.toMultimap(),
                responseBody,
                response.request.url.toString(),
            )
        }
    }

    companion object {
        /** A current desktop Chrome UA — YouTube's InnerTube endpoints are picky about this. */
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

        fun build(): YouTubeDownloader {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            return YouTubeDownloader(client)
        }
    }
}
