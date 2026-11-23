package app.tandemforactual.data.regions.global.pension

import app.tandemforactual.data.regions.Region

/** A government pension a person may draw later: income to expect, not money they hold. Ages are when it can start. */
data class PublicPension(
    val id: String,
    val displayName: String,
    val region: Region,
    val currency: String,
    val description: String,
    val earliestAge: Int,
    val standardAge: Int,
    val latestAge: Int,
    /** Where to find your own estimate. */
    val source: String,
) {
    /** The ages worth comparing: earliest, standard and latest start. */
    val startAges: List<Int> get() = listOf(earliestAge, standardAge, latestAge).distinct()
}

/** Every public pension Tandem knows. Register new ones here. */
object PublicPensions {
    val all: List<PublicPension> =
        listOf(
            PublicPension(
                "us-social-security",
                "Social Security",
                Region.US,
                "USD",
                "Retirement benefit, larger the longer you wait up to 70",
                62,
                67,
                70,
                "Your Social Security Statement at ssa.gov/myaccount",
            ),
            PublicPension(
                "ca-cpp",
                "CPP",
                Region.CA,
                "CAD",
                "Canada Pension Plan retirement pension",
                60,
                65,
                70,
                "Your Statement of Contributions in My Service Canada Account",
            ),
            PublicPension(
                "ca-qpp",
                "QPP",
                Region.CA,
                "CAD",
                "Québec Pension Plan, for work in Québec",
                60,
                65,
                72,
                "Your Statement of Participation at Retraite Québec",
            ),
            PublicPension(
                "ca-oas",
                "OAS",
                Region.CA,
                "CAD",
                "Old Age Security, based on years lived in Canada",
                65,
                65,
                70,
                "Your OAS estimate in My Service Canada Account",
            ),
            PublicPension(
                "in-eps-pension",
                "EPS pension",
                Region.IN,
                "INR",
                "Monthly pension from the Employees’ Pension Scheme after 10 years of service",
                50,
                58,
                60,
                "Your EPFO passbook and the EPS pension formula at epfindia.gov.in",
            ),
        )

    fun byId(id: String): PublicPension? = all.firstOrNull { it.id == id }

    fun forRegions(regions: Collection<Region>): List<PublicPension> = all.filter { it.region in regions }
}
