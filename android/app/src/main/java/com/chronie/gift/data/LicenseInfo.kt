package com.chronie.gift.data

// The `LicensesData` object that backs the in-app Licenses screen is generated
// at build time by the `generateLicenseInfo` Gradle task from
// app/licenses/licenses.json (and the referenced .txt files). Edit those
// config files to change the displayed content; do not add entries here.
// This file only declares the data model consumed by the generated object.
data class LicenseInfo(
    val name: String,
    val version: String,
    val license: String,
    val licenseText: String,
    val url: String
)
