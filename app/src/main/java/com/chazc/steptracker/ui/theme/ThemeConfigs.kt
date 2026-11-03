package com.chazc.steptracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import com.chazc.steptracker.R

enum class GaugePattern { SWORDS, FLAMES, CROSSES, SWIRLS, GEARS, SAKURA, CROWN }

data class CharacterStats(
    val stepsLabel: String = "STEPS",
    val distanceLabel: String = "DISTANCE",
    val caloriesLabel: String = "CALORIES",
    val activeLabel: String = "ACTIVE"
)

data class ThemeConfig(
    val characterName: String,
    val moniker: String,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val surface: Color = CardSurface,
    val notchCount: Int = 3,
    val accentSymbol: String = "⚔️",
    val gaugePattern: GaugePattern = GaugePattern.SWORDS,
    val stats: CharacterStats = CharacterStats(),
    val quotes: List<String> = emptyList(),
    val backgroundRes: Int? = null,
    val typography: Typography = ZoroTypography
) {
    val colorScheme = darkColorScheme(
        primary = primary,
        onPrimary = BandageWhite,
        secondary = secondary,
        onSecondary = OnigiriBlack,
        tertiary = tertiary,
        onTertiary = BandageWhite,
        background = OnigiriBlack,
        onBackground = BandageWhite,
        surface = surface,
        onSurface = BandageWhite,
        surfaceVariant = SteelGray,
        onSurfaceVariant = BandageWhite
    )
}

object CharacterRegistry {
    val Zoro = ThemeConfig(
        characterName = "Roronoa Zoro",
        moniker = "THE PIRATE HUNTER",
        primary = HaramakiGreen,
        secondary = KatanaGold,
        tertiary = BloodOathRed,
        notchCount = 3,
        accentSymbol = "⚔️",
        gaugePattern = GaugePattern.SWORDS,
        stats = CharacterStats(
            stepsLabel = "BLADE SWINGS",
            distanceLabel = "DOJO PATH"
        ),
        quotes = listOf(
            "Scars on the back are a swordsman's shame.",
            "I'm going to be the world's greatest swordsman!",
            "Nothing happened.",
            "When I decided to follow my dream, I had already discarded my life."
        ),
        backgroundRes = R.drawable.zoro_bg,
        typography = ZoroTypography
    )

    val Luffy = ThemeConfig(
        characterName = "Monkey D. Luffy",
        moniker = "STRAW HAT",
        primary = GomuRed,
        secondary = StrawHatYellow,
        tertiary = Color.White,
        notchCount = 1,
        accentSymbol = "🍖",
        gaugePattern = GaugePattern.GEARS,
        stats = CharacterStats(
            stepsLabel = "GOMU STEPS",
            distanceLabel = "VOYAGE"
        ),
        quotes = listOf(
            "I'm gonna be the King of the Pirates!",
            "Meat!!",
            "If you don't take risks, you can't create a future.",
            "Being alone is more painful than getting hurt."
        ),
        backgroundRes = R.drawable.luffy_bg,
        typography = LuffyTypography
    )

    val Sanji = ThemeConfig(
        characterName = "Vinsmoke Sanji",
        moniker = "BLACK LEG",
        primary = AllBlue,
        secondary = KatanaGold,
        tertiary = BloodOathRed,
        notchCount = 2,
        accentSymbol = "🍳",
        gaugePattern = GaugePattern.FLAMES,
        stats = CharacterStats(
            distanceLabel = "MARKET RUN",
            caloriesLabel = "CHEF'S BURN"
        ),
        quotes = listOf(
            "Cooking is a gift from the gods. Spices are a gift from the devil.",
            "Men who can't wipe away the tears from a woman's eyes aren't real men.",
            "I'll never kick a woman, even if I die!"
        ),
        backgroundRes = R.drawable.sanji_bg,
        typography = SanjiTypography
    )

    val Nami = ThemeConfig(
        characterName = "Nami",
        moniker = "CAT BURGLAR",
        primary = MikanOrange,
        secondary = ClimaBlue,
        tertiary = KatanaGold,
        notchCount = 1,
        accentSymbol = "🍊",
        gaugePattern = GaugePattern.SWIRLS,
        stats = CharacterStats(
            stepsLabel = "NAVIGATION",
            distanceLabel = "CHARTED KM"
        ),
        quotes = listOf(
            "Life is like a pencil that will surely run out, but will leave the beautiful writing of life.",
            "I want to map the entire world!",
            "Money is important!"
        ),
        backgroundRes = R.drawable.nami_bg,
        typography = NamiTypography
    )

    val Law = ThemeConfig(
        characterName = "Trafalgar Law",
        moniker = "SURGEON OF DEATH",
        primary = OpYellow,
        secondary = OnigiriBlack,
        tertiary = BloodOathRed,
        notchCount = 1,
        accentSymbol = "🏥",
        gaugePattern = GaugePattern.CROSSES,
        stats = CharacterStats(
            stepsLabel = "OPERATIONS",
            distanceLabel = "ROOM RADIUS"
        ),
        quotes = listOf(
            "The weak don't get to decide anything, not even how they die.",
            "ROOM.",
            "There are things you can't see unless you change your standing."
        ),
        backgroundRes = R.drawable.law_bg,
        typography = LawTypography
    )

    val Chopper = ThemeConfig(
        characterName = "Tony Tony Chopper",
        moniker = "COTTON CANDY LOVER",
        primary = SakuraPink,
        secondary = ReindeerCyan,
        tertiary = BandageWhite,
        notchCount = 7,
        accentSymbol = "💊",
        gaugePattern = GaugePattern.SAKURA,
        stats = CharacterStats(
            caloriesLabel = "RUMBLE BURN",
            activeLabel = "POINT TIME"
        ),
        quotes = listOf(
            "I'll be a doctor who can cure any disease!",
            "I'm not a tanuki!",
            "Brain Point!"
        ),
        backgroundRes = R.drawable.chopper_bg,
        typography = ChopperTypography
    )

    val Shanks = ThemeConfig(
        characterName = "Shanks",
        moniker = "RED HAIR",
        primary = HakiCrimson,
        secondary = EmperorBlue,
        tertiary = KatanaGold,
        notchCount = 1,
        accentSymbol = "👒",
        gaugePattern = GaugePattern.CROWN,
        stats = CharacterStats(
            stepsLabel = "LEGEND'S STRIDE",
            activeLabel = "HAKI TIME"
        ),
        quotes = listOf(
            "I've come to end this war!",
            "Betting it on a new era.",
            "You can pour drinks on me, you can throw food at me... but I won't forgive you for hurting my friends."
        ),
        backgroundRes = R.drawable.shanks_bg,
        typography = ShanksTypography
    )

    val list = listOf(Luffy, Zoro, Sanji, Nami, Law, Chopper, Shanks)

    fun getByName(name: String): ThemeConfig {
        return list.find { it.characterName == name } ?: Zoro
    }
}
