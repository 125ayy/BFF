package com.fcapps.bff

data class Country(val flag: String, val name: String, val dialCode: String) {
    override fun toString(): String = "$flag $name $dialCode"
}

object CountryCodes {
    val list: List<Country> = listOf(
        Country("🇦🇪", "United Arab Emirates", "+971"),
        Country("🇦🇷", "Argentina", "+54"),
        Country("🇦🇺", "Australia", "+61"),
        Country("🇧🇴", "Bolivia", "+591"),
        Country("🇧🇷", "Brazil", "+55"),
        Country("🇨🇦", "Canada", "+1"),
        Country("🇨🇱", "Chile", "+56"),
        Country("🇨🇳", "China", "+86"),
        Country("🇨🇴", "Colombia", "+57"),
        Country("🇩🇪", "Germany", "+49"),
        Country("🇪🇨", "Ecuador", "+593"),
        Country("🇪🇬", "Egypt", "+20"),
        Country("🇪🇸", "Spain", "+34"),
        Country("🇫🇷", "France", "+33"),
        Country("🇬🇧", "United Kingdom", "+44"),
        Country("🇮🇳", "India", "+91"),
        Country("🇮🇹", "Italy", "+39"),
        Country("🇯🇵", "Japan", "+81"),
        Country("🇰🇪", "Kenya", "+254"),
        Country("🇰🇷", "South Korea", "+82"),
        Country("🇲🇽", "Mexico", "+52"),
        Country("🇳🇬", "Nigeria", "+234"),
        Country("🇵🇪", "Peru", "+51"),
        Country("🇵🇹", "Portugal", "+351"),
        Country("🇵🇾", "Paraguay", "+595"),
        Country("🇷🇺", "Russia", "+7"),
        Country("🇸🇦", "Saudi Arabia", "+966"),
        Country("🇺🇸", "United States", "+1"),
        Country("🇺🇾", "Uruguay", "+598"),
        Country("🇻🇪", "Venezuela", "+58"),
        Country("🇿🇦", "South Africa", "+27")
    ).sortedBy { it.name }
}
