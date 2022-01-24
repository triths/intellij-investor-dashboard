package com.vermouthx.stocker.utils

import com.intellij.openapi.diagnostic.Logger
import com.vermouthx.stocker.entities.StockerSuggestion
import com.vermouthx.stocker.enums.StockerMarketType
import com.vermouthx.stocker.enums.StockerQuoteProvider
import org.apache.commons.lang.StringEscapeUtils
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.util.EntityUtils

object StockerSuggestHttpUtil {

    private val log = Logger.getInstance(javaClass)

    private val httpClientPool = run {
        val connectionManager = PoolingHttpClientConnectionManager()
        connectionManager.maxTotal = 20
        val requestConfig = RequestConfig.custom().build()
        HttpClients.custom().setConnectionManager(connectionManager).setDefaultRequestConfig(requestConfig)
            .useSystemProperties().build()
    }

    fun suggest(key: String, provider: StockerQuoteProvider): List<StockerSuggestion> {
        val url = "${provider.suggestHost}$key"
        val httpGet = HttpGet(url)
        return try {
            val response = httpClientPool.execute(httpGet)
            when (provider) {
                StockerQuoteProvider.SINA -> {
                    val responseText = EntityUtils.toString(response.entity, "UTF-8")
                    parseSinaSuggestion(responseText)
                }
                StockerQuoteProvider.TENCENT -> {
                    val responseText = EntityUtils.toString(response.entity, "UTF-8")
                    parseTencentSuggestion(responseText)
                }
                StockerQuoteProvider.SNOWBALL -> {
                    emptyList()
                }
            }
        } catch (e: Exception) {
            log.warn(e)
            emptyList()
        }
    }

    private fun parseSinaSuggestion(responseText: String): List<StockerSuggestion> {
        val result = mutableListOf<StockerSuggestion>()
        val regex = Regex("var suggestvalue=\"(.*?)\";")
        val matchResult = regex.find(responseText)
        val (_, snippetsText) = matchResult!!.groupValues
        val snippets = snippetsText.split(";")
        for (snippet in snippets) {
            val columns = snippet.split(",")
            when (columns[1]) {
                "11" -> {
                    if (columns[4].startsWith("S*ST")) {
                        continue
                    }
                    result.add(StockerSuggestion(columns[3].toUpperCase(), columns[4], StockerMarketType.AShare))
                }
                "22" -> {
                    val code = columns[3].replace("of", "")
                    when {
                        code.startsWith("15") || code.startsWith("16") || code.startsWith("18") ->
                            result.add(StockerSuggestion("SZ$code", columns[4], StockerMarketType.AShare))
                        code.startsWith("50") || code.startsWith("51") ->
                            result.add(StockerSuggestion("SH$code", columns[4], StockerMarketType.AShare))
                    }
                }
                "31" -> result.add(StockerSuggestion(columns[3].toUpperCase(), columns[4], StockerMarketType.HKStocks))
                "41" -> result.add(StockerSuggestion(columns[3].toUpperCase(), columns[4], StockerMarketType.USStocks))
                "71" -> result.add(StockerSuggestion(columns[3].toUpperCase(), columns[4], StockerMarketType.Crypto))
            }
        }
        return result
    }

    private fun parseTencentSuggestion(responseText: String): List<StockerSuggestion> {
        val result = mutableListOf<StockerSuggestion>()
        val snippets = responseText.replace("v_hint=\"", "")
            .replace("\"", "")
            .split("^")
        for (snippet in snippets) {
            val columns = snippet.split("~")
            val type = columns[0]
            val code = columns[1]
            val rawName = columns[2]
            val name = StringEscapeUtils.unescapeJava(rawName)
            when (type) {
                "sz", "sh" ->
                    result.add(StockerSuggestion(type.toUpperCase() + code, name, StockerMarketType.AShare))
                "hk" ->
                    result.add(StockerSuggestion(code, name, StockerMarketType.HKStocks))
                "us" ->
                    result.add(StockerSuggestion(code.split(".")[0].toUpperCase(), name, StockerMarketType.USStocks))
            }
        }
        return result
    }
}
