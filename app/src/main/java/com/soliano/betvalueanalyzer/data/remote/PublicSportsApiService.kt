package com.soliano.betvalueanalyzer.data.remote

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

interface PublicSportsApiService {
    @GET("apis/site/v2/sports/{sport}/{league}/scoreboard")
    suspend fun getScoreboard(
        @Path("sport") sport: String,
        @Path("league") league: String,
        @Query("dates") dates: String,
        @Query("limit") limit: Int = 100,
    ): Response<EspnScoreboardDto>

    @GET
    suspend fun getScoreboardUrl(@Url url: String): Response<EspnScoreboardDto>

    @GET
    suspend fun getRawUrl(@Url url: String): Response<ResponseBody>

    companion object {
        fun create(): PublicSportsApiService {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 BetValueAnalyzer/2.3")
                            .header("Accept", "application/json")
                            .header("Accept-Language", "fr-FR,fr;q=0.9,en;q=0.7")
                            .build()
                    )
                }
                .build()
            return Retrofit.Builder()
                .baseUrl("https://site.api.espn.com/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(PublicSportsApiService::class.java)
        }
    }
}

data class EspnScoreboardDto(
    val events: List<EspnEventDto>? = null,
)

data class EspnEventDto(
    val id: String,
    val uid: String? = null,
    val date: String? = null,
    @SerializedName("endDate") val endDate: String? = null,
    val name: String? = null,
    @SerializedName("shortName") val shortName: String? = null,
    val competitions: List<EspnCompetitionDto>? = null,
    val groupings: List<EspnGroupingDto>? = null,
    val status: EspnStatusDto? = null,
    val season: EspnSeasonDto? = null,
    val tournamentName: String? = null,
    val eventTypeOverride: String? = null,
)

data class EspnGroupingDto(
    val grouping: EspnGroupingInfoDto? = null,
    val competitions: List<EspnCompetitionDto>? = null,
)

data class EspnGroupingInfoDto(
    val id: String? = null,
    val slug: String? = null,
    @SerializedName("displayName") val displayName: String? = null,
)

data class EspnSeasonDto(
    val year: Int? = null,
    val type: Int? = null,
    val slug: String? = null,
)

data class EspnCompetitionDto(
    val id: String? = null,
    val uid: String? = null,
    val date: String? = null,
    @SerializedName("startDate") val startDate: String? = null,
    val type: EspnCompetitionTypeDto? = null,
    val round: EspnRoundDto? = null,
    val competitors: List<EspnCompetitorDto>? = null,
    val odds: List<EspnOddsDto?>? = null,
    val status: EspnStatusDto? = null,
)

data class EspnRoundDto(
    val id: String? = null,
    @SerializedName("displayName") val displayName: String? = null,
)

data class EspnCompetitionTypeDto(
    val id: String? = null,
    val abbreviation: String? = null,
)

data class EspnStatusDto(
    val clock: Double? = null,
    @SerializedName("displayClock") val displayClock: String? = null,
    val period: Int? = null,
    val type: EspnStatusTypeDto? = null,
)

data class EspnStatusTypeDto(
    val completed: Boolean = false,
    val state: String? = null,
    val name: String? = null,
    val description: String? = null,
    val detail: String? = null,
    @SerializedName("shortDetail") val shortDetail: String? = null,
)

data class EspnCompetitorDto(
    val id: String? = null,
    val order: Int? = null,
    @SerializedName("homeAway") val homeAway: String? = null,
    val team: EspnParticipantDto? = null,
    val athlete: EspnParticipantDto? = null,
    val form: String? = null,
    val score: String? = null,
    val winner: Boolean? = null,
    val linescores: List<EspnLineScoreDto>? = null,
    val records: List<EspnRecordDto>? = null,
    val statistics: List<EspnStatisticDto>? = null,
)

data class EspnLineScoreDto(
    val value: Double? = null,
    @SerializedName("displayValue") val displayValue: String? = null,
    val period: Int? = null,
    val linescores: List<EspnLineScoreDto>? = null,
)

data class EspnParticipantDto(
    @SerializedName("displayName") val displayName: String? = null,
)

data class EspnRecordDto(
    val summary: String? = null,
)

data class EspnStatisticDto(
    val name: String? = null,
    @SerializedName("displayName") val displayName: String? = null,
    @SerializedName("shortDisplayName") val shortDisplayName: String? = null,
    val abbreviation: String? = null,
    @SerializedName("displayValue") val displayValue: String? = null,
    val value: Double? = null,
)

data class EspnOddsDto(
    @SerializedName("overUnder") val overUnder: Double? = null,
    val provider: EspnProviderDto? = null,
    val moneyline: EspnMarketDto? = null,
    val total: EspnMarketDto? = null,
    @SerializedName("pointSpread") val pointSpread: EspnMarketDto? = null,
)

data class EspnProviderDto(
    val name: String? = null,
)

data class EspnMarketDto(
    val home: EspnMarketSideDto? = null,
    val away: EspnMarketSideDto? = null,
    val draw: EspnMarketSideDto? = null,
    val over: EspnMarketSideDto? = null,
    val under: EspnMarketSideDto? = null,
)

data class EspnMarketSideDto(
    val open: EspnPriceDto? = null,
    val close: EspnPriceDto? = null,
)

data class EspnPriceDto(
    val odds: String? = null,
    val line: String? = null,
)
