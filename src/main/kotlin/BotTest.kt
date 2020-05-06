import bot.InstagramBot
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

@InternalCoroutinesApi
@ExperimentalCoroutinesApi
fun main() = runBlocking  {

    val username = "annon202020"
    val password = "Bahuthard"

    val bot = InstagramBot()
    bot.prepare(username, password)
    bot.login()

    bot.getSelfFollowing(Int.MAX_VALUE, isUsername = true).collect { println(it) }
    bot.getExploreTabMedias(7).collect { println(it) }
}