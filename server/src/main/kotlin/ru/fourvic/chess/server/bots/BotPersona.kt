package ru.fourvic.chess.server.bots

import ru.fourvic.chess.shared.engine.BotLevel
import ru.fourvic.chess.shared.protocol.PlayerInfo
import java.util.Base64
import kotlin.math.abs
import kotlin.random.Random

/** A computer opponent dressed as a player: its own id, a common name, a country and a rating close to the user's. */
data class BotPersona(
    val id: String,
    val name: String,
    val country: String,
    val rating: Int,
    val level: BotLevel,
) {
    val player: PlayerInfo get() = PlayerInfo(id, name, rating, provisional = false, country = country)

    companion object {
        /**
         * A persona for a game against a user from [userCountry] rated [userRating]. Most opponents come from the
         * user's own country or region; strength follows the rating, so the game is close and the rating fair.
         */
        fun create(userCountry: String?, userRating: Int, userProvisional: Boolean, random: Random): BotPersona {
            val country = pickCountry(userCountry, random)
            // New players start at 1500 but are usually weaker; their opponents lean lower until the rating settles.
            val target = if (userProvisional) userRating - random.nextInt(0, 200) else userRating
            val rating = (target + random.nextInt(-80, 81)).coerceIn(600, 2300)
            return BotPersona(newId(random), NameGenerator.name(country, random), country, rating, levelFor(rating))
        }

        /** The level whose calibrated rating is closest. */
        fun levelFor(rating: Int): BotLevel = BotLevel.entries.minBy { abs(it.approximateRating - rating) }

        private fun pickCountry(userCountry: String?, random: Random): String {
            val home = userCountry?.uppercase()?.takeIf { it in NameGenerator.countries }
            val roll = random.nextDouble()
            return when {
                home != null && home in NameGenerator.cisCountries && roll < 0.75 -> home
                home != null && home in NameGenerator.cisCountries -> NameGenerator.cisCountries.random(random)
                home != null && roll < 0.5 -> home
                home == null && roll < 0.6 -> "RU"
                else -> NameGenerator.countries.random(random)
            }
        }

        /** Same shape as real account ids: 9 random bytes in URL-safe Base64. */
        private fun newId(random: Random): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(random.nextBytes(9))
    }
}

/** Ordinary player names, the kind people pick themselves: a first name with a number, an initial or a year. */
internal object NameGenerator {
    private val cyrillic = listOf(
        "Алексей", "Дмитрий", "Сергей", "Андрей", "Максим", "Иван", "Артём", "Никита", "Михаил", "Егор", "Олег",
        "Павел", "Роман", "Денис", "Кирилл", "Илья", "Антон", "Виктор", "Юрий", "Игорь", "Анна", "Мария", "Ольга",
        "Елена", "Ирина", "Наталья", "Татьяна", "Светлана", "Дарья", "Алина", "Ксения", "Полина",
    )
    private val translit = listOf(
        "alex", "dima", "sergey", "andrey", "max", "ivan", "artem", "nikita", "misha", "egor", "oleg", "pavel",
        "roman", "denis", "kirill", "ilya", "anton", "vitya", "yura", "igor", "anya", "masha", "olya", "lena",
        "ira", "natasha", "tanya", "sveta", "dasha", "alina", "ksyusha", "polina", "vlad", "zhenya", "sasha",
    )
    private val letters = "АБВГДЕЗИКЛМНОПРСТФЧШ"

    private val latinByCountry = mapOf(
        "US" to listOf("Jake", "Ryan", "Tyler", "Emily", "Josh", "Kevin", "Megan", "Chris", "Brandon", "Ashley"),
        "GB" to listOf("Oliver", "Harry", "Jack", "Charlie", "Amelia", "George", "Sophie", "Tom", "Lewis", "Ellie"),
        "DE" to listOf("Lukas", "Jonas", "Felix", "Leon", "Lena", "Paul", "Finn", "Moritz", "Hannah", "Tobias"),
        "FR" to listOf("Hugo", "Lucas", "Louis", "Léa", "Théo", "Nathan", "Camille", "Julien", "Manon", "Antoine"),
        "ES" to listOf("Pablo", "Javier", "Lucía", "Diego", "Sergio", "Carmen", "Álvaro", "Marta", "Adrián", "Raúl"),
        "IT" to listOf("Luca", "Marco", "Giulia", "Matteo", "Andrea", "Lorenzo", "Chiara", "Davide", "Sara", "Simone"),
        "PL" to listOf("Kuba", "Piotr", "Kasia", "Bartek", "Michał", "Ola", "Tomek", "Wojtek", "Ania", "Paweł"),
        "TR" to listOf("Emre", "Burak", "Mert", "Can", "Elif", "Kerem", "Zeynep", "Ozan", "Baris", "Deniz"),
        "IN" to listOf("Arjun", "Rahul", "Priya", "Rohan", "Aditya", "Sneha", "Vikram", "Karthik", "Ananya", "Ravi"),
        "BR" to listOf("Lucas", "Gabriel", "Mateus", "Pedro", "Rafael", "Bruna", "Thiago", "Felipe", "Larissa", "Diego"),
        "RS" to listOf("Nikola", "Marko", "Stefan", "Luka", "Milica", "Jovan", "Nemanja", "Ana", "Uros", "Filip"),
    )

    /** Russian-speaking countries: Cyrillic names and their transliterations. */
    val cisCountries = listOf("RU", "BY", "KZ", "KG", "UZ", "AM", "AZ", "MD", "TJ")
    val countries: List<String> = cisCountries + latinByCountry.keys

    fun name(country: String, random: Random): String {
        val name = if (country in cisCountries) cisName(country, random) else latinName(latinByCountry.getValue(country), random)
        return name.take(20)
    }

    private fun cisName(country: String, random: Random): String = when (random.nextInt(6)) {
        0 -> "${cyrillic.random(random)}${random.nextInt(10, 100)}"
        1 -> "${cyrillic.random(random)} ${letters.random(random)}"
        2 -> "${translit.random(random)}_${random.nextInt(1975, 2012)}"
        3 -> "${translit.random(random)}${random.nextInt(1, 1000)}"
        4 -> {
            // City tags only for Russia; elsewhere the same kind of suffix without them.
            val tags = if (country == "RU") listOf("chess", "pro", "msk", "spb", "77") else listOf("chess", "pro", "ok", "88")
            "${translit.random(random).replaceFirstChar(Char::uppercase)}_${tags.random(random)}"
        }
        else -> "${cyrillic.random(random)}_${letters.random(random)}"
    }

    private fun latinName(names: List<String>, random: Random): String {
        val first = names.random(random)
        return when (random.nextInt(4)) {
            0 -> "$first${random.nextInt(10, 100)}"
            1 -> "${first.lowercase()}_${random.nextInt(1980, 2011)}"
            2 -> "$first ${('A'..'Z').random(random)}"
            else -> "${first}_${listOf("chess", "plays", "gm", "k", "x").random(random)}"
        }
    }
}
