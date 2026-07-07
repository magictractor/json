plugins {
    id("uk.co.magictractor.magictractor-plugin")
}

version = "0.0.1-SNAPSHOT"

magictractor {
    javaVersion = 8

    // Converters started life in another project in 2019.
    pomInceptionYear = "2019"
}

dependencies {
    implementation(libs.jsonpath)
    implementation(libs.gson)
    //implementation(libs.jackson)
    
    // Base Converter classes are in magictractor-util.
    implementation("uk.co.magictractor:magictractor-util:0.0.1-SNAPSHOT")
}
