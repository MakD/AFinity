package com.makd.afinity.data.models.jellyseerr

data class Studio(
    val id: Int,
    val name: String,
    val logoPath: String?
) {
    companion object {
        fun getPopularStudios(): List<Studio> = listOf(
            Studio(id = 2, name = "Walt Disney", logoPath = "/wdrCwmRnLFJhEoH8GSfymY85KHT.png"),
            Studio(
                id = 127928,
                name = "20th Century",
                logoPath = "/h0rjX5vjW5r8yEnUBStFarjcLT4.png"
            ),
            Studio(id = 34, name = "Sony Pictures", logoPath = "/GagSvqWlyPdkFHMfQ3pNq6ix9P.png"),
            Studio(
                id = 4,
                name = "Paramount Pictures",
                logoPath = "/fycMZt242LVjagMByZOLUGbCvv3.png"
            ),
            Studio(
                id = 420,
                name = "Marvel Studios",
                logoPath = "/hUzeosd33nzE5MCNsZxCGEKTXaQ.png"
            ),
            Studio(id = 9993, name = "DC", logoPath = "/2Tc1P3Ac8M479naPp1kYT3izLS5.png"),
            Studio(id = 3, name = "Pixar", logoPath = "/1TjvGVDMYsj6JBxOAkUHpPEwLf7.png"),
            Studio(
                id = 174,
                name = "Warner Bros. Pictures",
                logoPath = "/ky0xOc5OrhzkZ1N6KyUxacfQsCk.png"
            ),
            Studio(
                id = 33,
                name = "Universal Pictures",
                logoPath = "/8lvHyhjr8oUKOOy2dKXoALWKdp0.png"
            ),
            Studio(
                id = 7,
                name = "DreamWorks Pictures",
                logoPath = "/vru2SssLX3FPhnKZGtYw00pVIS9.png"
            ),
            Studio(id = 41077, name = "A24", logoPath = "/1ZXsGaFPgrgS6ZZGS37AqD5uU12.png")
        )
    }

    fun getImageUrl(baseUrl: String = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)"): String? {
        return logoPath?.let { "$baseUrl$it" }
    }
}