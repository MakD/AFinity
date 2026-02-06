package com.makd.afinity.data.models.jellyseerr

data class Network(val id: Int, val name: String, val logoPath: String?) {
    companion object {
        fun getPopularNetworks(): List<Network> =
            listOf(
                Network(id = 213, name = "Netflix", logoPath = "/wwemzKWzjKYJFfCeiB57q3r4Bcm.png"),
                Network(
                    id = 1024,
                    name = "Prime Video",
                    logoPath = "/ifhbNuuVnlwYy5oXA5VIb2YR8AZ.png",
                ),
                Network(id = 49, name = "HBO", logoPath = "/tuomPhY2UtuPTqqFnKMVHvSb724.png"),
                Network(id = 2739, name = "Disney+", logoPath = "/gJ8VX6JSu3ciXHuC2dDGAo2lvwM.png"),
                Network(
                    id = 2552,
                    name = "Apple TV+",
                    logoPath = "/4KAy34EHvRM25Ih8wb82AuGU7zJ.png",
                ),
                Network(id = 453, name = "Hulu", logoPath = "/pqUTCleNUiTLAVlelGxUgWn1ELh.png"),
                Network(
                    id = 4353,
                    name = "Discovery+",
                    logoPath = "/1D1bS3Dyw4ScYnFWTlBOvJXC3nb.png",
                ),
                Network(id = 2, name = "ABC", logoPath = "/ndAvF4JLsliGreX87jAc9GdjmJY.png"),
                Network(id = 19, name = "Fox", logoPath = "/1DSpHrWyOORkL9N2QHX7Adt31mQ.png"),
                Network(id = 359, name = "Cinemax", logoPath = "/6mSHSquNpfLgDdv6VnOOvC5Uz2h.png"),
                Network(id = 174, name = "AMC", logoPath = "/pmvRmATOCaDykE6JrVoeYxlFHw3.png"),
                Network(id = 67, name = "ShowTime", logoPath = "/Allse9kbjiP6ExaQrnSpIhkurEi.png"),
                Network(id = 318, name = "Starz", logoPath = "/8GJjw3HHsAJYwIWKIPBPfqMxlEa.png"),
                Network(id = 71, name = "The CW", logoPath = "/ge9hzeaU7nMtQ4PjkFlc68dGAJ9.png"),
                Network(id = 6, name = "NBC", logoPath = "/o3OedEP0f9mfZr33jz2BfXOUK5.png"),
                Network(id = 16, name = "CBS", logoPath = "/nm8d7P7MJNiBLdgIzUK0gkuEA4r.png"),
                Network(
                    id = 4330,
                    name = "Paramount+",
                    logoPath = "/fi83B1oztoS47xxcemFdPMhIzK.png",
                ),
                Network(id = 4, name = "BBC One", logoPath = "/mVn7xESaTNmjBUyUtGNvDQd3CT1.png"),
                Network(
                    id = 56,
                    name = "Cartoon Network",
                    logoPath = "/c5OC6oVCg6QP4eqzW6XIq17CQjI.png",
                ),
                Network(
                    id = 80,
                    name = "Adult Swim",
                    logoPath = "/9AKyspxVzywuaMuZ1Bvilu8sXly.png",
                ),
                Network(id = 13, name = "Nick", logoPath = "/ikZXxg6GnwpzqiZbRPhJGaZapqB.png"),
                Network(id = 3353, name = "Peacock", logoPath = "/gIAcGTjKKr0KOHL5s4O36roJ8p7.png"),
            )
    }

    fun getImageUrl(
        baseUrl: String = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)"
    ): String? {
        return logoPath?.let { "$baseUrl$it" }
    }
}
